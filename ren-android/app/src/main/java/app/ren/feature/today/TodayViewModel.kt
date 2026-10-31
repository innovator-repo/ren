package app.ren.feature.today

import androidx.lifecycle.viewModelScope
import app.ren.core.mvi.MviViewModel
import app.ren.domain.fsrs.formatInterval
import app.ren.domain.model.Curriculum
import app.ren.domain.model.ItemType
import app.ren.domain.usecase.Clock
import app.ren.domain.usecase.CurriculumPlanner
import app.ren.domain.usecase.StudySnapshot
import app.ren.domain.usecase.StudySnapshotSource
import app.ren.domain.usecase.TodaySummary
import app.ren.domain.usecase.date
import app.ren.feature.common.UnitPresenter
import app.ren.feature.common.UnitUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class TodayUiState(
    val loading: Boolean = true,
    val dateLabel: String = "",
    val summary: TodaySummary? = null,
    val newToday: List<UnitUi> = emptyList(),
    val nextDueLabel: String? = null,
    val stageName: String = "",
    val waitingOnGraduation: Boolean = false,
    val extraUnitId: String? = null,
    val pathDay: Long = 1,
    val pathLine: String = "",
    val readerLine: String = "",
    val progressLine: String = "",
    val word: WordUi? = null,
) {
    val caughtUp get() = summary != null && summary.sessionItems == 0
}

/** Today's word for the drawer row. */
data class WordUi(val id: String, val text: String, val reading: String, val meaning: String)

sealed interface TodayIntent {
    data object StartSession : TodayIntent
    data class OpenUnit(val unit: UnitUi) : TodayIntent
    data object LearnOneMore : TodayIntent
    data object OpenSettings : TodayIntent
    data object ReadAStory : TodayIntent
    data object OpenPath : TodayIntent
    data object OpenProgress : TodayIntent
    data class OpenWord(val id: String) : TodayIntent
}

sealed interface TodayEffect {
    data object Session : TodayEffect
    data class Lesson(val unitId: String) : TodayEffect
    data class Item(val itemId: String) : TodayEffect
    data object Settings : TodayEffect
    data object Reader : TodayEffect
    data object Path : TodayEffect
    data object Progress : TodayEffect
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    source: StudySnapshotSource,
    private val clock: Clock,
) : MviViewModel<TodayUiState, TodayIntent, TodayEffect>(TodayUiState()) {

    private val dateFormat = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.JAPANESE)

    init {
        viewModelScope.launch { source.snapshot.filterNotNull().collect { setState { reduce(it) } } }
    }

    private fun reduce(s: StudySnapshot): TodayUiState {
        val summary = s.summary
        val current = CurriculumPlanner.currentStage(s.progress)
        val extra = if (summary.newRemaining == 0) CurriculumPlanner.nextUnits(s.units, s.progress, 1).firstOrNull() else null
        return TodayUiState(
            loading = false,
            dateLabel = clock.date(s.now).format(dateFormat),
            summary = summary,
            newToday = summary.newToday.map { UnitPresenter.describe(it, s.library) },
            nextDueLabel = summary.nextDueAt?.let { formatInterval(s.now, it) },
            stageName = Curriculum.stages[current].title,
            waitingOnGraduation = summary.newToday.isEmpty() && s.units.any { !it.isComplete },
            extraUnitId = extra?.id,
            pathDay = s.pathDay,
            pathLine = pathLine(s, current),
            readerLine = s.library.stories.size.let { if (it == 1) "1 story" else "$it stories" },
            progressLine = progressLine(s),
            word = wordOfDay(s),
        )
    }

    private fun pathLine(s: StudySnapshot, stage: Int): String {
        val p = s.progress.getOrNull(stage) ?: return ""
        return "${p.stage.title} · ${p.unitsDone} of ${p.unitsTotal} lessons"
    }

    private fun progressLine(s: StudySnapshot): String {
        val words = s.cardList.count { it.itemType == ItemType.VOCAB && it.isIntroduced }
        val streak = s.summary.streak
        return "${if (streak == 0) "No streak yet" else "$streak-day streak"} · $words ${if (words == 1) "word" else "words"}"
    }

    private fun wordOfDay(s: StudySnapshot): WordUi? {
        val met = s.cards.values.filter { it.isIntroduced }.mapTo(HashSet()) { it.itemId }
        val w = WordOfDay.pick(s.library.vocab.values, met, s.today) ?: return null
        return WordUi(w.id, w.text, w.reading, w.meaning)
    }

    override fun onIntent(intent: TodayIntent) {
        when (intent) {
            TodayIntent.StartSession -> emit(TodayEffect.Session)
            is TodayIntent.OpenUnit ->
                emit(if (intent.unit.done) TodayEffect.Item(intent.unit.headlineId) else TodayEffect.Lesson(intent.unit.unitId))
            TodayIntent.LearnOneMore -> current.extraUnitId?.let { emit(TodayEffect.Lesson(it)) }
            TodayIntent.OpenSettings -> emit(TodayEffect.Settings)
            TodayIntent.ReadAStory -> emit(TodayEffect.Reader)
            TodayIntent.OpenPath -> emit(TodayEffect.Path)
            TodayIntent.OpenProgress -> emit(TodayEffect.Progress)
            is TodayIntent.OpenWord -> emit(TodayEffect.Item(intent.id))
        }
    }
}
