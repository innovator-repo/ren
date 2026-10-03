package app.ren.feature.path

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ren.core.mvi.MviViewModel
import app.ren.domain.model.Curriculum
import app.ren.domain.model.ItemType
import app.ren.domain.model.Mastery
import app.ren.domain.model.Stage
import app.ren.domain.model.UnitType
import app.ren.domain.usecase.CurriculumPlanner
import app.ren.domain.usecase.StageProgress
import app.ren.domain.usecase.StudySnapshot
import app.ren.domain.usecase.StudySnapshotSource
import app.ren.feature.common.UnitPresenter
import app.ren.feature.common.UnitUi
import app.ren.navigation.StageRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StageStatus { Done, Current, Open, Locked }

data class StageUi(
    val stage: Stage,
    val status: StageStatus,
    val progress: StageProgress,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val nextUnitId: String?,
)

data class PathUiState(val loading: Boolean = true, val day: Long = 1, val stages: List<StageUi> = emptyList())

sealed interface PathIntent {
    data class OpenStage(val index: Int) : PathIntent
    data class Continue(val unitId: String) : PathIntent
    data object OpenSettings : PathIntent
}

sealed interface PathUiEffect {
    data class Stage(val index: Int) : PathUiEffect
    data class Lesson(val unitId: String) : PathUiEffect
    data object Settings : PathUiEffect
}

internal fun primaryLabel(index: Int) = when (index) {
    0 -> "Kana"
    1 -> "Radicals"
    else -> "Kanji"
}

@HiltViewModel
class PathViewModel @Inject constructor(source: StudySnapshotSource) :
    MviViewModel<PathUiState, PathIntent, PathUiEffect>(PathUiState()) {

    init {
        viewModelScope.launch { source.snapshot.filterNotNull().collect { s -> setState { reduce(s) } } }
    }

    private fun reduce(s: StudySnapshot): PathUiState {
        val current = CurriculumPlanner.currentStage(s.progress)
        val stages = s.progress.map { p ->
            val i = p.stage.index
            val status = when {
                p.complete && p.graduatedShare >= Curriculum.UNLOCK_SHARE -> StageStatus.Done
                i == current -> StageStatus.Current
                p.unlocked -> StageStatus.Open
                else -> StageStatus.Locked
            }
            StageUi(
                stage = p.stage,
                status = status,
                progress = p,
                primaryLabel = primaryLabel(i),
                secondaryLabel = if (p.secondaryTotal > 0) "Words" else null,
                nextUnitId = if (p.unlocked) CurriculumPlanner.nextUnitInStage(s.units, i)?.id else null,
            )
        }
        return PathUiState(loading = false, day = s.pathDay, stages = stages)
    }

    override fun onIntent(intent: PathIntent) = when (intent) {
        is PathIntent.OpenStage -> emit(PathUiEffect.Stage(intent.index))
        is PathIntent.Continue -> emit(PathUiEffect.Lesson(intent.unitId))
        PathIntent.OpenSettings -> emit(PathUiEffect.Settings)
    }
}

data class StageTile(val itemId: String, val glyph: String, val meaning: String, val mastery: Mastery)

data class StageDetailUiState(
    val loading: Boolean = true,
    val stage: Stage? = null,
    val progress: StageProgress? = null,
    val tiles: List<StageTile> = emptyList(),
    val pairs: List<UnitUi> = emptyList(),
    val nextUnitId: String? = null,
    val unlocked: Boolean = false,
)

sealed interface StageDetailIntent {
    data class OpenItem(val itemId: String) : StageDetailIntent
    data class OpenUnit(val unit: UnitUi) : StageDetailIntent
    data object Continue : StageDetailIntent
}

sealed interface StageDetailEffect {
    data class Item(val itemId: String) : StageDetailEffect
    data class Lesson(val unitId: String) : StageDetailEffect
}

@HiltViewModel
class StageDetailViewModel @Inject constructor(
    source: StudySnapshotSource,
    handle: SavedStateHandle,
) : MviViewModel<StageDetailUiState, StageDetailIntent, StageDetailEffect>(StageDetailUiState()) {
    private val index = handle.toRoute<StageRoute>().index

    init {
        viewModelScope.launch {
            source.snapshot.filterNotNull().collect { s ->
                val p = s.progress[index]
                val primaryType = when (index) { 0 -> ItemType.KANA; 1 -> ItemType.RADICAL; else -> ItemType.KANJI }
                val tiles = s.cards.values.filter { it.stage == index && it.itemType == primaryType && it.unitId.isNotEmpty() }
                    .sortedBy { it.sequence }
                    .map { StageTile(it.itemId, s.library.glyph(it.itemId), s.library.meaning(it.itemId), it.mastery) }
                val pairs = s.units.filter { it.stage == index && it.type == UnitType.COLLOCATION }
                    .map { UnitPresenter.describe(it, s.library) }
                setState {
                    StageDetailUiState(
                        loading = false, stage = p.stage, progress = p, tiles = tiles, pairs = pairs,
                        nextUnitId = if (p.unlocked) CurriculumPlanner.nextUnitInStage(s.units, index)?.id else null,
                        unlocked = p.unlocked,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: StageDetailIntent) {
        when (intent) {
            is StageDetailIntent.OpenItem -> emit(StageDetailEffect.Item(intent.itemId))
            is StageDetailIntent.OpenUnit -> emit(
                if (intent.unit.done) StageDetailEffect.Item(intent.unit.headlineId) else StageDetailEffect.Lesson(intent.unit.unitId),
            )
            StageDetailIntent.Continue -> current.nextUnitId?.let { emit(StageDetailEffect.Lesson(it)) }
        }
    }
}
