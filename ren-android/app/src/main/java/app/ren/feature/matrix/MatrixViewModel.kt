package app.ren.feature.matrix

import androidx.lifecycle.viewModelScope
import app.ren.core.mvi.MviViewModel
import app.ren.domain.model.Mastery
import app.ren.domain.usecase.GraphNode
import app.ren.domain.usecase.NodeKind
import app.ren.domain.usecase.KnowledgeGraph
import app.ren.domain.usecase.KnowledgeGraphBuilder
import app.ren.domain.usecase.StudySnapshotSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MatrixUiState(
    val loading: Boolean = true,
    val graph: KnowledgeGraph = KnowledgeGraph.Empty,
    val selectedId: String? = null,
) {
    val selected: GraphNode? get() = graph.nodes.firstOrNull { it.id == selectedId }
}

sealed interface MatrixIntent {
    data class Select(val id: String) : MatrixIntent
    data object OpenSelected : MatrixIntent
    data object OpenSettings : MatrixIntent
}

sealed interface MatrixEffect {
    data class Item(val itemId: String) : MatrixEffect
    data object Settings : MatrixEffect
}

@HiltViewModel
class MatrixViewModel @Inject constructor(source: StudySnapshotSource) :
    MviViewModel<MatrixUiState, MatrixIntent, MatrixEffect>(MatrixUiState()) {

    init {
        viewModelScope.launch {
            source.snapshot.filterNotNull()
                // Layout only changes when what is introduced or mature changes, not on every clock tick.
                .map { s -> s to s.cards.values.map { "${it.itemId}:${it.mastery}" }.hashCode() }
                .distinctUntilChanged { a, b -> a.second == b.second }
                .collect { (s, _) ->
                    val graph = withContext(Dispatchers.Default) { KnowledgeGraphBuilder.build(s.library, s.cards) }
                    setState {
                        val keep = selectedId?.takeIf { id -> graph.nodes.any { it.id == id } }
                        copy(loading = false, graph = graph, selectedId = keep ?: defaultSelection(graph))
                    }
                }
        }
    }

    /** Start on the most recently lit kanji, like the prototype's 休. */
    private fun defaultSelection(graph: KnowledgeGraph): String? =
        graph.nodes.lastOrNull { it.kind == NodeKind.KANJI && it.mastery != Mastery.Locked }?.id
            ?: graph.nodes.firstOrNull()?.id

    override fun onIntent(intent: MatrixIntent) {
        when (intent) {
            is MatrixIntent.Select -> setState { copy(selectedId = intent.id) }
            MatrixIntent.OpenSelected -> current.selectedId?.let { emit(MatrixEffect.Item(it)) }
            MatrixIntent.OpenSettings -> emit(MatrixEffect.Settings)
        }
    }
}
