package app.ren.feature.session

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.ren.core.audio.AudioEngineManager
import app.ren.core.audio.Sfx
import app.ren.core.mvi.MviViewModel
import app.ren.di.ApplicationScope
import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.fsrs.Rating
import app.ren.domain.fsrs.formatInterval
import app.ren.domain.model.Curriculum
import app.ren.domain.model.LessonUnit
import app.ren.domain.repository.ContentLibrary
import app.ren.domain.repository.PreferencesRepository
import app.ren.domain.repository.StudyRepository
import app.ren.domain.usecase.AnswerChecker
import app.ren.domain.usecase.Clock
import app.ren.domain.usecase.CurriculumPlanner
import app.ren.domain.usecase.SessionPlanner
import app.ren.domain.usecase.SessionStep
import app.ren.domain.usecase.Streak
import app.ren.domain.usecase.StudySnapshotSource
import app.ren.domain.usecase.epochDay
import app.ren.widget.WidgetUpdater
import app.ren.work.DeepLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Drives every study flow: the daily session (reviews then today's lessons), the widget's quick
 * round, and a single lesson opened from Today or the Path. Answers are always produced — typed on
 * the flick keyboard or drawn on the pad — never picked (spec §3).
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val source: StudySnapshotSource,
    private val study: StudyRepository,
    private val prefs: PreferencesRepository,
    private val audio: AudioEngineManager,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) : MviViewModel<SessionUiState, SessionIntent, SessionEffect>(SessionUiState()) {

    private val lessonUnitId: String? = handle["unitId"]
    private val quick: Boolean = handle["quick"] ?: false

    private val queue = ArrayDeque<SessionStep>()
    private var lib = ContentLibrary.Empty
    private var position = 0
    private var total = 0
    private var shownAt = 0L
    private var reviewed = 0
    private var correct = 0
    private var studyMs = 0L
    private val learned = mutableListOf<String>()
    private var lastLesson: LessonUnit? = null

    init {
        viewModelScope.launch {
            val s = source.snapshot.filterNotNull().first()
            lib = s.library
            val showRomaji = s.prefs.showRomaji && s.pathDay <= Curriculum.ROMAJI_LAST_DAY
            val steps = when {
                lessonUnitId != null -> listOf(SessionStep.Lesson(lessonUnitId))
                quick -> SessionPlanner.quick(s.cards.values.toList(), s.now, DeepLinks.QUICK_SIZE)
                else -> SessionPlanner.daily(
                    s.cards.values.toList(),
                    CurriculumPlanner.nextUnits(s.units, s.progress, s.summary.newRemaining),
                    s.now,
                    cap = max(s.prefs.reviewGoal, 20),
                )
            }
            queue.addAll(steps)
            total = steps.size
            setState { copy(showRomaji = showRomaji, library = lib) }
            if (steps.isEmpty()) setState { copy(page = SessionPage.Empty) } else advance()
        }
    }

    private fun advance() {
        viewModelScope.launch {
            while (true) {
                val step = queue.removeFirstOrNull() ?: return@launch finish()
                position++
                when (step) {
                    is SessionStep.Review -> {
                        val card = study.cards(listOf(step.itemId)).firstOrNull() ?: continue
                        val prompt = LessonPlanner.prompt(card, lib) ?: continue
                        val now = clock.now()
                        val intervals = FSRSScheduler.preview(card.fsrs, now).mapValues { formatInterval(now, it.value.due) }
                        shownAt = now
                        setState { copy(page = SessionPage.Review(ReviewUi(prompt, intervals = intervals)), position = position, total = total) }
                        if (prompt.kind == ReviewKind.DRAW_KANA || prompt.kind == ReviewKind.LISTEN) speak(prompt)
                        return@launch
                    }
                    is SessionStep.Lesson -> {
                        val unit = study.unit(step.unitId) ?: continue
                        if (unit.isComplete && lessonUnitId == null) continue
                        val steps = LessonPlanner.steps(unit, lib, current.showRomaji)
                        if (steps.isEmpty()) continue
                        shownAt = clock.now()
                        setState { copy(page = SessionPage.Lesson(LessonUi(unit, steps, ready = isReadyByDefault(steps[0]))), position = position, total = total) }
                        autoSpeak(steps[0])
                        return@launch
                    }
                }
            }
        }
    }

    private fun isReadyByDefault(step: LessonStep) = step !is LessonStep.WriteIt && step !is LessonStep.FindIt

    private fun autoSpeak(step: LessonStep) {
        when (step) {
            is LessonStep.MeetKana -> audio.say(step.kana.character)
            is LessonStep.FindIt -> audio.say(step.word.reading, step.word.audioAssetPath)
            is LessonStep.LearnPair -> audio.say(step.vocab.reading)
            else -> Unit
        }
    }

    private fun speak(p: ReviewPrompt) {
        val v = p.vocab
        when {
            p.kana != null -> audio.say(p.kana.character)
            v != null -> audio.say(v.reading.ifEmpty { v.text }, v.audioAssetPath)
            p.kanji != null -> audio.say(p.kanji.kunyomi.firstOrNull()?.substringBefore('.')?.trimStart('-') ?: p.kanji.character)
        }
    }

    override fun onIntent(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.SubmitTyped -> submitTyped(intent.text)
            is SessionIntent.DrawingFinished -> drawingFinished(intent.mistakes, intent.hints)
            is SessionIntent.Reveal -> review { ui -> setState { copy(page = SessionPage.Review(ui.copy(phase = ReviewPhase.Revealed(intent.typed)))) }; speak(ui.prompt) }
            is SessionIntent.Rate -> rate(intent.rating)
            SessionIntent.ContinueAfterMiss -> rate(Rating.Again)
            SessionIntent.Speak -> review { speak(it.prompt) }
            is SessionIntent.SpeakText -> audio.say(intent.text)
            SessionIntent.StepReady -> lesson { ui -> setState { copy(page = SessionPage.Lesson(ui.copy(ready = true))) } }
            SessionIntent.NextStep -> lesson { ui ->
                if (ui.isLast) completeLesson(ui.unit)
                else {
                    val next = ui.copy(index = ui.index + 1, ready = isReadyByDefault(ui.steps[ui.index + 1]))
                    setState { copy(page = SessionPage.Lesson(next)) }
                    autoSpeak(next.step)
                }
            }
            SessionIntent.PreviousStep -> lesson { ui ->
                if (ui.index > 0) setState { copy(page = SessionPage.Lesson(ui.copy(index = ui.index - 1, ready = true))) }
            }
            SessionIntent.ReviewNow -> lastLesson?.let { unit ->
                queue.addAll(unit.itemIds.map { SessionStep.Review(it) })
                total += unit.itemIds.size
                advance()
            }
            SessionIntent.Close -> {
                audio.stopStory()
                flushStudyTime()
                emit(SessionEffect.Close)
            }
        }
    }

    private inline fun review(block: (ReviewUi) -> Unit) {
        (current.page as? SessionPage.Review)?.ui?.let(block)
    }

    private inline fun lesson(block: (LessonUi) -> Unit) {
        (current.page as? SessionPage.Lesson)?.ui?.let(block)
    }

    private fun submitTyped(text: String) = review { ui ->
        if (ui.phase != ReviewPhase.Answering) return@review
        val ok = AnswerChecker.matches(text, *ui.prompt.answers.toTypedArray())
        when {
            ok -> {
                audio.sfx(Sfx.Correct)
                emit(SessionEffect.PlaySuccessHaptic)
                val suggested = if (ui.attempt == 0) Rating.Good else Rating.Hard
                setState { copy(page = SessionPage.Review(ui.copy(phase = ReviewPhase.Result(true, text, suggested)))) }
            }
            ui.attempt == 0 && text.isNotBlank() -> {
                // One more try: the amber ring and a nudge, like the prototype.
                emit(SessionEffect.PlayErrorHaptic)
                setState { copy(page = SessionPage.Review(ui.copy(attempt = 1, shake = ui.shake + 1))) }
            }
            else -> {
                audio.sfx(Sfx.Wrong)
                emit(SessionEffect.PlayErrorHaptic)
                setState { copy(page = SessionPage.Review(ui.copy(phase = ReviewPhase.Result(false, text, Rating.Again)))) }
            }
        }
        speak(ui.prompt)
    }

    private fun drawingFinished(mistakes: Int, hints: Int) = review { ui ->
        val passed = mistakes <= 2 && hints <= 1
        val suggested = when {
            !passed -> Rating.Again
            mistakes == 0 && hints == 0 -> Rating.Good
            else -> Rating.Hard
        }
        audio.sfx(if (passed) Sfx.Correct else Sfx.Wrong)
        emit(if (passed) SessionEffect.PlaySuccessHaptic else SessionEffect.PlayErrorHaptic)
        setState { copy(page = SessionPage.Review(ui.copy(phase = ReviewPhase.Result(passed, null, suggested)))) }
        speak(ui.prompt)
    }

    private fun rate(rating: Rating) = review { ui ->
        val now = clock.now()
        val card = ui.prompt.card
        val updated = FSRSScheduler.schedule(card.fsrs, rating, now)
        val elapsed = (now - shownAt).coerceIn(0, 5 * 60_000L)
        studyMs += elapsed
        reviewed++
        if (rating != Rating.Again) correct++
        viewModelScope.launch {
            study.recordReview(card, updated, rating, elapsed, now)
            // Learning steps come back within the same sitting.
            if ((updated.state == CardState.Learning || updated.state == CardState.Relearning) &&
                updated.due - now <= 20 * FSRSScheduler.MINUTE_MS
            ) {
                queue.addLast(SessionStep.Review(card.itemId))
                total++
            }
            if (reviewed % 5 == 0) flushStudyTime()
            advance()
        }
    }

    private fun completeLesson(unit: LessonUnit) {
        val now = clock.now()
        studyMs += (now - shownAt).coerceIn(0, 15 * 60_000L)
        learned += unit.title
        lastLesson = unit
        viewModelScope.launch {
            study.completeUnit(unit, now)
            flushStudyTime()
            audio.sfx(Sfx.Complete)
            emit(SessionEffect.PlaySuccessHaptic)
            if (lessonUnitId != null) {
                showSummary(lessonOnly = true, canReviewNow = unit.itemIds.size)
            } else {
                // First recall straight after the lesson: steps 5 and 6.
                queue.addAll(unit.itemIds.map { SessionStep.Review(it) })
                total += unit.itemIds.size
                advance()
            }
        }
    }

    private fun flushStudyTime() {
        val ms = studyMs
        if (ms <= 0) return
        studyMs = 0
        // Application scope: this must land even when the screen is closing.
        appScope.launch { prefs.recordActivity(clock.now(), ms) }
    }

    private suspend fun finish() {
        flushStudyTime()
        if (reviewed > 0 && lessonUnitId == null) {
            audio.sfx(Sfx.Complete)
            emit(SessionEffect.PlaySuccessHaptic)
        }
        showSummary(lessonOnly = false, canReviewNow = 0)
        WidgetUpdater.refresh(context)
    }

    private suspend fun showSummary(lessonOnly: Boolean, canReviewNow: Int) {
        val p = prefs.current()
        val today = clock.epochDay()
        val minutes = if (p.studyEpochDay == today) (p.studyMsToday / 60_000.0).roundToInt() else 0
        setState {
            copy(
                page = SessionPage.Summary(
                    SummaryUi(reviewed, correct, learned.toList(), minutes, Streak.current(p, today), lessonOnly, canReviewNow),
                ),
            )
        }
        emit(SessionEffect.Finished)
    }

    override fun onCleared() {
        flushStudyTime()
        super.onCleared()
    }
}
