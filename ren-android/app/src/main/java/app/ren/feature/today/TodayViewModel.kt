package app.ren.feature.today

import androidx.lifecycle.viewModelScope
import app.ren.core.mvi.MviViewModel
import app.ren.domain.fsrs.formatInterval
import app.ren.domain.model.Curriculum
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
) {
    val caughtUp get() = summary != null && summary.sessionItems == 0
}

sealed interface TodayIntent {
    data object StartSession : TodayIntent
    data class OpenUnit(val unit: UnitUi) : TodayIntent
    data object LearnOneMore : TodayIntent
    data object OpenSettings : TodayIntent
    data object ReadAStory : TodayIntent
}

sealed interface TodayEffect {
    data object Session : TodayEffect
    data class Lesson(val unitId: String) : TodayEffect
    data class Item(val itemId: String) : TodayEffect
    data object Settings : TodayEffect
    data object Reader : TodayEffect
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
        )
    }

    override fun onIntent(intent: TodayIntent) {
        when (intent) {
            TodayIntent.StartSession -> emit(TodayEffect.Session)
            is TodayIntent.OpenUnit ->
                emit(if (intent.unit.done) TodayEffect.Item(intent.unit.headlineId) else TodayEffect.Lesson(intent.unit.unitId))
            TodayIntent.LearnOneMore -> current.extraUnitId?.let { emit(TodayEffect.Lesson(it)) }
            TodayIntent.OpenSettings -> emit(TodayEffect.Settings)
            TodayIntent.ReadAStory -> emit(TodayEffect.Reader)
        }
    }
}
