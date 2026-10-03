package app.ren.feature.matrix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ren.core.designsystem.components.Card
import app.ren.core.designsystem.components.Chevron
import app.ren.core.designsystem.components.CircleIconButton
import app.ren.core.designsystem.components.GlyphTile
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.LargeTitleScaffold
import app.ren.core.designsystem.components.Tag
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.pressable
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.RenColors
import app.ren.core.haptics.LocalHaptics
import app.ren.core.mvi.CollectEffects
import app.ren.domain.model.Mastery
import app.ren.domain.usecase.GraphNode
import app.ren.domain.usecase.KnowledgeGraph
import app.ren.domain.usecase.NodeKind
import app.ren.navigation.LocalNavigator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun MatrixRoute(bottomInset: Dp, vm: MatrixViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    CollectEffects(vm.effects) { e ->
        when (e) {
            is MatrixEffect.Item -> nav.item(e.itemId)
            MatrixEffect.Settings -> nav.settings()
        }
    }
    MatrixScreen(state, bottomInset, vm::onIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatrixScreen(state: MatrixUiState, bottomInset: Dp, onIntent: (MatrixIntent) -> Unit) {
    val c = Ren.colors
    val lit = state.graph.litKanji
    LargeTitleScaffold(
        title = "Matrix",
        subtitle = if (lit == 0) "Nothing lit yet" else "$lit kanji lit",
        bottomInset = bottomInset,
        trailing = {
            CircleIconButton(RenIcons.Sliders, "Settings", background = c.surface, tint = c.akane, size = 36.dp, iconSize = 20.dp) {
                onIntent(MatrixIntent.OpenSettings)
            }
        },
    ) {
        item(key = "graph") {
            Card(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                GraphCanvas(state.graph, state.selectedId, Modifier.fillMaxWidth().height(380.dp)) { onIntent(MatrixIntent.Select(it)) }
            }
        }
        item(key = "legend") {
            FlowRow(
                Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendDot("Radical", c.radical)
                LegendDot("Mature", c.bamboo)
                LegendDot("Learning", c.amber)
                LegendDot("Locked", c.ink3)
            }
        }
        state.selected?.let { node ->
            item(key = "info") { InfoCard(node, Modifier.padding(horizontal = 16.dp)) { onIntent(MatrixIntent.OpenSelected) } }
        }
        if (lit == 0 && !state.loading) {
            item(key = "hint") {
                Txt(
                    "Kanji light up here as you learn them. Each one hangs off the radicals it is built from.",
                    Ren.type.footnote, color = c.ink2, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        HSpace(6.dp)
        Txt(label, Ren.type.footnote, color = Ren.colors.ink2)
    }
}

@Composable
private fun InfoCard(node: GraphNode, modifier: Modifier, onOpen: () -> Unit) {
    val c = Ren.colors
    Card(modifier.pressable(onClick = onOpen), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphTile(node.glyph, size = 56.dp, color = if (node.kind == NodeKind.RADICAL) c.radical else c.ink)
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Txt(node.meaning, Ren.type.headline.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Txt(node.equation, Ren.type.callout, color = c.ink2, maxLines = 1)
                if (node.words.isNotEmpty()) {
                    Txt(node.words.joinToString("・") { it.text }, Ren.type.footnote, color = c.ink2, maxLines = 1)
                }
            }
            HSpace(8.dp)
            Tag(node.status, statusColor(node, c))
            HSpace(6.dp)
            Chevron()
        }
    }
}

private fun statusColor(node: GraphNode, c: RenColors) = when {
    node.kind == NodeKind.RADICAL && node.mastery != Mastery.Locked -> c.radical
    node.mastery == Mastery.Mature -> c.bamboo
    node.mastery == Mastery.Learning || node.mastery == Mastery.New -> c.amber
    else -> c.ink2
}

private fun nodeColor(node: GraphNode, c: RenColors): Color? = when {
    node.mastery == Mastery.Locked -> null
    node.kind == NodeKind.RADICAL -> c.radical
    node.mastery == Mastery.Mature -> c.bamboo
    else -> c.amber
}

/**
 * Spec §5 radial knowledge graph. Pinch to zoom, drag to pan, tap a node to inspect it.
 * Lit nodes glow in their system colour; locked ones stay translucent grey with a dashed rim.
 */
@Composable
private fun GraphCanvas(graph: KnowledgeGraph, selectedId: String?, modifier: Modifier, onSelect: (String) -> Unit) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val unit = with(density) { 1.dp.toPx() }
        val fit = min(viewW / (graph.width * unit), viewH / (graph.height * unit))
        val selected = graph.nodes.firstOrNull { it.id == selectedId }
        var scale by remember(graph) { mutableFloatStateOf(fit.coerceAtLeast(0.8f)) }
        // Start centred on the selection when only part of the graph fits.
        var pan by remember(graph) {
            mutableStateOf(
                if (selected != null && scale > fit) Offset((graph.width / 2 - selected.x) * unit * scale, (graph.height / 2 - selected.y) * unit * scale)
                else Offset.Zero,
            )
        }

        fun toScreen(x: Float, y: Float) = Offset(
            viewW / 2 + (x - graph.width / 2) * unit * scale + pan.x,
            viewH / 2 + (y - graph.height / 2) * unit * scale + pan.y,
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(graph) {
                    detectTransformGestures { _, panChange, zoom, _ ->
                        scale = (scale * zoom).coerceIn(fit * 0.8f, 2.6f)
                        pan += panChange
                    }
                }
                .pointerInput(graph, scale, pan) {
                    detectTapGestures { tap ->
                        val hit = graph.nodes.minByOrNull { n -> (toScreen(n.x, n.y) - tap).getDistance() }
                        if (hit != null && (toScreen(hit.x, hit.y) - tap).getDistance() < 34 * unit * scale.coerceAtLeast(0.8f)) {
                            haptics.tick()
                            onSelect(hit.id)
                        }
                    }
                },
        ) {
            val s = unit * scale
            for (e in graph.edges) {
                val a = graph.nodes[e.from]
                val b = graph.nodes[e.to]
                drawLine(
                    if (e.lit) c.radical.copy(alpha = 0.45f) else c.ink3,
                    toScreen(a.x, a.y), toScreen(b.x, b.y), strokeWidth = 1.5f * s,
                    pathEffect = if (e.lit) null else PathEffect.dashPathEffect(floatArrayOf(3f * s, 4f * s)),
                )
            }
            // Words orbit the selected kanji.
            if (selected != null && selected.words.isNotEmpty()) {
                val center = toScreen(selected.x, selected.y)
                selected.words.forEachIndexed { i, w ->
                    val angle = Math.toRadians(110.0 + i * 40.0)
                    val p = center + Offset((cos(angle) * 52 * s).toFloat(), (sin(angle) * 52 * s).toFloat())
                    drawLine(c.radical.copy(alpha = 0.45f), center, p, 1.5f * s)
                    drawCircle(c.ink2, 5f * s, p)
                    val label = measurer.measure(w.text, TextStyle(fontSize = (10 * scale).sp, color = c.ink2))
                    drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y + 7f * s))
                }
            }
            for (n in graph.nodes) {
                val p = toScreen(n.x, n.y)
                val r = (if (n.kind == NodeKind.RADICAL) 24f else 20f) * s
                val color = nodeColor(n, c)
                if (color != null) {
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.4f), Color.Transparent), p, r * 1.7f), r * 1.7f, p)
                    drawCircle(color, r, p)
                } else {
                    drawCircle(c.fill, r, p)
                    drawCircle(c.ink3, r, p, style = Stroke(1.5f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s))))
                }
                if (n.id == selectedId) drawCircle(c.ink, r + 5f * s, p, style = Stroke(2f * s))
                val text = measurer.measure(
                    n.glyph,
                    TextStyle(fontSize = ((if (n.kind == NodeKind.RADICAL) 24 else 20) * scale).sp, color = if (color != null) Color.White else c.ink2, fontWeight = FontWeight.Medium),
                )
                drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height / 2f))
            }
        }
    }
}
