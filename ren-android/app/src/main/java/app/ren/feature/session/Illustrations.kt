package app.ren.feature.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ren.core.designsystem.components.StrokeGlyph
import app.ren.core.designsystem.components.rememberStrokePaths
import app.ren.core.designsystem.theme.Ren
import app.ren.domain.model.Component

/**
 * The picture for step 2 (spec §6A "mnemonic anchor"). 休 has a hand-drawn line illustration; every
 * other character draws itself stroke by stroke with its radicals tinted, so the picture and the
 * shape arrive together.
 */
@Composable
fun MnemonicArt(glyph: String, strokes: List<String>, components: List<Component>, modifier: Modifier = Modifier) {
    if (glyph == "休") RestUnderTree(modifier) else StrokeStory(strokes, components, modifier)
}

@Composable
private fun StrokeStory(strokes: List<String>, components: List<Component>, modifier: Modifier) {
    val c = Ren.colors
    val paths = rememberStrokePaths(strokes)
    val still = LocalInspectionMode.current
    val progress = remember(strokes) { Animatable(if (still) strokes.size.toFloat() else 0f) }
    LaunchedEffect(strokes) {
        if (still) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(strokes.size.toFloat(), tween(durationMillis = 420 * strokes.size, easing = LinearEasing))
    }
    val palette = listOf(c.radical, c.bamboo, c.amber, c.akane)
    val colorOf: (Int) -> Color = { i ->
        val comp = components.indexOfFirst { i in it.strokes }
        if (comp >= 0) palette[comp % palette.size] else c.ink
    }
    Box(modifier.fillMaxWidth().aspectRatio(1.25f), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(0.82f).aspectRatio(1f).clip(CircleShape).background(c.soft(c.radical, 0.07f)))
        StrokeGlyph(
            paths, Modifier.fillMaxSize(0.66f).aspectRatio(1f), width = 5.2f, colorOf = colorOf,
            revealOf = { i -> (progress.value - i).coerceIn(0f, 1f) },
        )
    }
}

/** Ported from the reference prototype: a tired person leaning on a tree to rest. */
@Composable
private fun RestUnderTree(modifier: Modifier) {
    val c = Ren.colors
    val measurer = rememberTextMeasurer()
    val parse = { d: String -> PathParser().parsePathString(d).toPath() }
    val trunk = remember { parse("M198 164C200 138 200 112 198 90L230 90C228 114 228 138 232 164Z") }
    val lines = remember {
        listOf("M105 146v-6h16v6", "M188 154L156 138", "M156 138L146 164", "M146 164h-9", "M190 126L172 140L159 133").map(parse)
    }
    val eyes = remember { parse("M185 102q2.5 2.4 5 0M194 102q2.5 2.4 5 0") }
    val shirt = remember { parse("M190 124L188 152") }
    val canopy = listOf(Triple(186f, 66f, 28f), Triple(216f, 42f, 34f), Triple(248f, 66f, 28f), Triple(216f, 76f, 30f))
    val leafFill = c.bamboo.copy(alpha = 0.22f).compositeOver(c.surface)
    Canvas(modifier.fillMaxWidth().aspectRatio(196f / 168f).padding(6.dp)) {
        val k = size.width / 196f
        scale(k, k, pivot = Offset.Zero) {
            translate(-84f, -4f) {
                drawLine(c.ink3, Offset(90f, 164f), Offset(272f, 164f), 2f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(2f, 7f)))
                drawPath(trunk, c.ink.copy(alpha = 0.08f).compositeOver(c.surface))
                drawPath(trunk, c.ink, style = Stroke(3f, join = StrokeJoin.Round))
                canopy.forEach { (x, y, r) -> drawCircle(c.bamboo, r, Offset(x, y)) }
                canopy.forEach { (x, y, r) -> drawCircle(leafFill, r - 2f, Offset(x, y)) }
                drawRoundRect(c.amber.copy(alpha = 0.35f).compositeOver(c.surface), Offset(96f, 146f), Size(34f, 18f), CornerRadius(3f))
                drawRoundRect(c.ink, Offset(96f, 146f), Size(34f, 18f), CornerRadius(3f), style = Stroke(3f, join = StrokeJoin.Round))
                lines.forEach { ln(it, c.ink, 3f) }
                drawPath(shirt, c.akane, style = Stroke(10f, cap = StrokeCap.Round))
                drawCircle(c.surface, 13f, Offset(192f, 102f))
                drawCircle(c.ink, 13f, Offset(192f, 102f), style = Stroke(3f))
                ln(eyes, c.ink, 2f)
                listOf(Triple("z", 162f to 88f, 12f), Triple("z", 150f to 73f, 15f), Triple("Z", 136f to 54f, 19f)).forEach { (t, p, s) ->
                    val m = measurer.measure(t, TextStyle(fontSize = (s / k / density * k).sp, fontWeight = FontWeight.Bold, color = c.ink2))
                    drawText(m, topLeft = Offset(p.first, p.second - m.size.height * 0.8f))
                }
            }
        }
    }
}

private fun DrawScope.ln(path: androidx.compose.ui.graphics.Path, color: Color, width: Float) =
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
