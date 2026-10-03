package app.ren.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.ren.core.designsystem.theme.Ren
import app.ren.core.haptics.LocalHaptics
import app.ren.domain.ink.GLYPH_BOX
import app.ren.domain.ink.Pt
import app.ren.domain.ink.StrokeMatcher
import app.ren.domain.ink.StrokeVerdict
import app.ren.domain.ink.SvgPath
import kotlinx.coroutines.launch

/** Progress through one character on the pad. Hoisted so screens can undo, clear and react. */
@Stable
class StrokePadState(val strokeCount: Int) {
    var completed by mutableIntStateOf(0)
        private set
    var lastVerdict by mutableStateOf<StrokeVerdict?>(null)
        internal set
    var mistakes by mutableIntStateOf(0)
        internal set
    /** Recall mode: reveal the next stroke's guide after asking for a hint. */
    var hintShown by mutableStateOf(false)
    val done get() = completed >= strokeCount

    internal fun accept() { completed++; hintShown = false }
    fun undo() { if (completed > 0) completed--; lastVerdict = null }
    fun clear() { completed = 0; lastVerdict = null; hintShown = false }
}

@Composable
fun rememberStrokePadState(key: Any, strokeCount: Int) = remember(key) { StrokePadState(strokeCount) }

/**
 * Spec §6B: a washi-paper card with dashed crosshair guides. The learner traces stroke by stroke;
 * each stroke is checked in order. Correct strokes snap into place in sumi ink with a tick;
 * wrong ones flash red, shake and buzz twice.
 */
@Composable
fun StrokePad(
    strokes: List<String>,
    state: StrokePadState,
    modifier: Modifier = Modifier,
    showGuide: Boolean = true,
    lenient: Boolean = false,
    onStroke: (StrokeVerdict) -> Unit = {},
) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val paths = rememberStrokePaths(strokes)
    val reference = remember(strokes) { strokes.map { SvgPath.flatten(it) } }
    val live = remember { mutableStateListOf<Offset>() }
    var failed by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val failAlpha = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val snap = remember { Animatable(1f) }
    val callback by rememberUpdatedState(onStroke)
    val measurer = rememberTextMeasurer()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "ring")

    Box(
        modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { translationX = shake.value }
            .clip(RoundedCornerShape(16.dp))
            .background(c.washi)
            .border(0.5.dp, c.hairline, RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Writing pad. Draw stroke ${state.completed + 1} of ${state.strokeCount}." }
            .pointerInput(strokes, state) {
                val k = size.width / GLYPH_BOX
                detectDragGestures(
                    onDragStart = { live.clear(); live.add(it) },
                    onDrag = { change, _ -> live.add(change.position) },
                    onDragCancel = { live.clear() },
                    onDragEnd = {
                        if (state.done) { live.clear(); return@detectDragGestures }
                        val pts = live.map { Pt(it.x / k, it.y / k) }
                        val verdict = StrokeMatcher.match(pts, reference[state.completed], lenient)
                        state.lastVerdict = verdict
                        if (verdict == StrokeVerdict.Good) {
                            haptics.tick()
                            state.accept()
                            scope.launch { snap.snapTo(0f); snap.animateTo(1f, tween(160)) }
                        } else {
                            haptics.error()
                            state.mistakes++
                            failed = live.toList()
                            scope.launch {
                                failAlpha.snapTo(1f)
                                failAlpha.animateTo(0f, tween(650))
                            }
                            scope.launch {
                                shake.animateTo(0f, keyframes {
                                    durationMillis = 350
                                    -14f at 50; 12f at 110; -8f at 170; 5f at 230; 0f at 350
                                })
                            }
                        }
                        live.clear()
                        callback(verdict)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / GLYPH_BOX
            // Dashed crosshair guides.
            val dash = PathEffect.dashPathEffect(floatArrayOf(2f * k, 2.5f * k))
            drawLine(c.guide, Offset(size.width / 2, 3 * k), Offset(size.width / 2, size.height - 3 * k), 0.6f * k, pathEffect = dash)
            drawLine(c.guide, Offset(3 * k, size.height / 2), Offset(size.width - 3 * k, size.height / 2), 0.6f * k, pathEffect = dash)

            scale(k, k, pivot = Offset.Zero) {
                paths.forEachIndexed { i, p ->
                    when {
                        i < state.completed -> {
                            val justDone = i == state.completed - 1
                            drawInk(p, c.ink.copy(alpha = if (justDone) 0.4f + 0.6f * snap.value else 1f), INK_WIDTH)
                        }
                        showGuide || (state.hintShown && i == state.completed) ->
                            drawInk(p, c.ink3.copy(alpha = 0.7f), 6f)
                    }
                }
            }
            if (failed.isNotEmpty() && failAlpha.value > 0f) drawPath(smooth(failed), c.akane.copy(alpha = failAlpha.value), style = Stroke(INK_WIDTH * k, cap = StrokeCap.Round))
            if (live.size > 1) drawPath(smooth(live), c.akane, style = Stroke(INK_WIDTH * k, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Start dot for the next stroke: follow the red dot.
            if (!state.done && (showGuide || state.hintShown)) {
                val start = reference[state.completed].first()
                val center = Offset(start.x * k, start.y * k)
                drawCircle(c.akane.copy(alpha = 0.5f * (1 - ring)), radius = (3.6f + 6f * ring) * k, center = center, style = Stroke(0.8f * k))
                drawCircle(c.akane, radius = 3.8f * k, center = center)
                val label = measurer.measure("${state.completed + 1}", TextStyle(color = c.onAccent, fontSize = (5.2f * k).toSp(), fontWeight = FontWeight.Bold))
                translate(center.x - label.size.width / 2f, center.y - label.size.height / 2f) { drawText(label) }
            }
        }
    }
}

/** Quadratic smoothing through midpoints, so a finger trace reads as a brush stroke. */
private fun smooth(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    if (points.size < 3) { points.drop(1).forEach { lineTo(it.x, it.y) }; return@apply }
    for (i in 1 until points.size - 1) {
        val mid = (points[i] + points[i + 1]) / 2f
        quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
    }
    lineTo(points.last().x, points.last().y)
}
