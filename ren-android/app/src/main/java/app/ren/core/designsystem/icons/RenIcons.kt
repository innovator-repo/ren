package app.ren.core.designsystem.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** SF Symbols-style line icons on a 24×24 grid, ported from the reference prototype. */
object RenIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 ${-r},${r}h${-(w - 2 * r)}a$r,$r 0 0 1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}z"

    private fun icon(name: String, vararg paths: String, width: Float = 1.8f, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = if (filled) null else SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Today = icon("today", circle(12f, 12f, 9f), circle(12f, 12f, 4.6f))
    val Path = icon("path", circle(6.5f, 5.5f, 2.3f), circle(17.5f, 18.5f, 2.3f), "M6.5 7.8v2.7a3 3 0 0 0 3 3h5a3 3 0 0 1 3 3v.2")
    val Matrix = icon(
        "matrix", circle(12f, 12f, 2.4f), circle(5.5f, 6f, 2f), circle(18.5f, 7f, 2f), circle(7.5f, 18.5f, 2f),
        circle(18f, 17.5f, 2f), "M7.1 7.3l3.2 3.2M16.8 8.4l-2.9 2.3M8.7 16.8l2-2.9M16.6 16.3l-2.7-2.6",
    )
    val Reader = icon("reader", "M12 6.6C10 5 7 4.6 3.5 5v12.6c3.5-.4 6.5 0 8.5 1.6 2-1.6 5-2 8.5-1.6V5C17 4.6 14 5 12 6.6z", "M12 6.6v12.6")
    val Sliders = icon("sliders", "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    val Back = icon("back", "M15 5l-7 7 7 7", width = 2.2f)
    val Chevron = icon("chevron", "M9 5l7 7-7 7", width = 2f)
    val Flame = icon("flame", "M12 3c.8 3.4 5 5.2 5 10a5 5 0 0 1-10 0c0-2 .9-3.3 2-4.3.2 1.3.9 2 1.7 2.3C10.5 8 11 5.5 12 3z")
    val Shield = icon("shield", "M12 3l7 2.8v5.2c0 4.4-3 7.7-7 9.8-4-2.1-7-5.4-7-9.8V5.8z", "M9 12l2.2 2.2L15.5 10")
    val Lock = icon("lock", rect(5.5f, 10.5f, 13f, 9.5f, 2.5f), "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7", width = 2.4f)
    val Speaker = icon("speaker", "M4 10v4h3.6L12 18V6L7.6 10z", "M15.5 9a4 4 0 0 1 0 6M18.2 6.5a7.5 7.5 0 0 1 0 11")
    val Close = icon("close", "M6 6l12 12M18 6L6 18", width = 2.2f)
    val Undo = icon("undo", "M9 7L4 12l5 5", "M4.5 12H14a4.5 4.5 0 0 1 0 9h-3")
    val Backspace = icon("backspace", "M9.5 6H19a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 19 18H9.5L4 12z", "M12.6 9.6l4.2 4.8M16.8 9.6l-4.2 4.8")
    val Play = icon("play", "M8 5.5v13l10-6.5z", filled = true)
    val Pause = icon("pause", rect(7f, 5.5f, 3.4f, 13f, 1f), rect(13.6f, 5.5f, 3.4f, 13f, 1f), filled = true)
    val Up = icon("up", "M12 19V6M6 11.5l6-6 6 6", width = 2.4f)
    val Plus = icon("plus", "M12 6v12M6 12h12", width = 2f)
    val Minus = icon("minus", "M6 12h12", width = 2f)
    val Bell = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val Sparkle = icon("sparkle", "M12 3.5l1.9 5.1 5.1 1.9-5.1 1.9L12 17.5l-1.9-5.1L5 10.5l5.1-1.9z")
    val Brush = icon("brush", "M14.5 4.5l5 5-8.2 8.2a3 3 0 0 1-2.1.9H5.5v-3.7a3 3 0 0 1 .9-2.1z", "M12.5 6.5l5 5")
    val Keyboard = icon("keyboard", rect(3f, 6f, 18f, 12f, 2.5f), "M7 10h.01M10.5 10h.01M14 10h.01M17.5 10h.01M8 14h8")
    val Eye = icon("eye", "M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 2.8f))
    val Bookmark = icon("bookmark", "M7 4h10v16l-5-3.5L7 20z")
    val Waveform = icon("waveform", "M4 10v4M8 7v10M12 4v16M16 8v8M20 11v2")
    val Info = icon("info", circle(12f, 12f, 9f), "M12 11v5.5M12 7.6v.01")
    val Repeat = icon("repeat", "M17 4l3 3-3 3", "M4 12v-1a4 4 0 0 1 4-4h12", "M7 20l-3-3 3-3", "M20 12v1a4 4 0 0 1-4 4H4")
}

@Composable
fun RenIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Image(imageVector = icon, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}
