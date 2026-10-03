package app.ren.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import app.ren.domain.ink.GLYPH_BOX

/** Parses KanjiVG stroke paths once per glyph. */
@Composable
fun rememberStrokePaths(strokes: List<String>): List<Path> = remember(strokes) {
    strokes.map { PathParser().parsePathString(it).toPath() }
}

/** Width of a brush stroke in KanjiVG units: a little heavier than the 3.0 source for a sumi feel. */
const val INK_WIDTH = 4.6f

/**
 * Draws a character from its stroke paths, each stroke with its own colour, offset and reveal
 * fraction. Used for the radical slider, the writing pad, stroke-order animation and tiles.
 */
@Composable
fun StrokeGlyph(
    paths: List<Path>,
    modifier: Modifier = Modifier,
    width: Float = INK_WIDTH,
    colorOf: (Int) -> Color,
    offsetOf: (Int) -> Offset = { Offset.Zero },
    revealOf: (Int) -> Float = { 1f },
) {
    val measure = remember { PathMeasure() }
    val partial = remember { Path() }
    Canvas(modifier) {
        val k = size.minDimension / GLYPH_BOX
        translate((size.width - GLYPH_BOX * k) / 2, (size.height - GLYPH_BOX * k) / 2) {
            scale(k, k, pivot = Offset.Zero) {
                paths.forEachIndexed { i, p ->
                    val reveal = revealOf(i)
                    if (reveal <= 0f) return@forEachIndexed
                    val o = offsetOf(i)
                    translate(o.x, o.y) {
                        if (reveal >= 1f) drawInk(p, colorOf(i), width)
                        else {
                            measure.setPath(p, false)
                            partial.reset()
                            measure.getSegment(0f, measure.length * reveal, partial, true)
                            drawInk(partial, colorOf(i), width)
                        }
                    }
                }
            }
        }
    }
}

fun DrawScope.drawInk(path: Path, color: Color, width: Float) =
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
