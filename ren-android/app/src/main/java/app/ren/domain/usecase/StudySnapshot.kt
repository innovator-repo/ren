package app.ren.domain.usecase

import app.ren.di.ApplicationScope
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.ReviewLog
import app.ren.domain.model.StudyCard
import app.ren.domain.model.UserPreferences
import app.ren.domain.repository.ContentLibrary
import app.ren.domain.repository.ContentRepository
import app.ren.domain.repository.PreferencesRepository
import app.ren.domain.repository.StudyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the tabs render from, recomputed when progress, settings or the clock move. */
data class StudySnapshot(
    val library: ContentLibrary,
    val cards: Map<String, StudyCard>,
    val units: List<LessonUnit>,
    val prefs: UserPreferences,
    val logsToday: List<ReviewLog>,
    val now: Long,
    val today: Long,
    val dayStart: Long,
) {
    val cardList: Collection<StudyCard> get() = cards.values
    val progress: List<StageProgress> by lazy { CurriculumPlanner.stageProgress(units, cards.values.toList(), prefs.unlockAllStages) }
    val summary: TodaySummary by lazy {
        TodayCalculator.summarize(now, dayStart, today, cards.values.toList(), units, logsToday, prefs, progress)
    }
    /** Day number on the path, counting the first day as day 1. */
    val pathDay: Long get() = if (prefs.startEpochDay <= 0) 1 else (today - prefs.startEpochDay + 1).coerceAtLeast(1)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class StudySnapshotSource @Inject constructor(
    content: ContentRepository,
    study: StudyRepository,
    prefs: PreferencesRepository,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Ticks every 30 s so due counts roll over without a database change. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(clock.now())
            delay(30_000)
        }
    }

    private val logsToday: Flow<Pair<Long, List<ReviewLog>>> = ticker.map { clock.epochDay(it) }.distinctUntilChanged()
        .flatMapLatest { day -> study.observeLogsSince(clock.startOfDay(day)).map { day to it } }

    val snapshot: StateFlow<StudySnapshot?> = combine(
        content.library,
        study.observeCards(),
        study.observeUnits(),
        prefs.preferences,
        combine(ticker, logsToday) { now, logs -> now to logs },
    ) { lib, cards, units, p, (now, logs) ->
        StudySnapshot(
            library = lib,
            cards = cards.associateBy { it.itemId },
            units = units,
            prefs = p,
            logsToday = logs.second,
            now = now,
            today = logs.first,
            dayStart = clock.startOfDay(logs.first),
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(15_000), null)
}
