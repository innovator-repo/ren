package app.ren.core.designsystem.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ren.R
import app.ren.designsystem.CircadianPhase
import app.ren.designsystem.Geist
import app.ren.designsystem.JaLocale
import app.ren.designsystem.KinsokuLineBreak
import app.ren.designsystem.LumeColors
import app.ren.designsystem.LumeMotion
import app.ren.designsystem.Newsreader
import app.ren.designsystem.NotoSerifJp

/**
 * Ren's palette: calm neutral pages (paper white, or graphite in dark) with soft grouped fills
 * instead of stacked cards, Akane as the one accent, and the spec's semantic colours (§2) for
 * radicals, correct answers and pending reviews.
 */
@Immutable
data class RenColors(
    /** Page background. */
    val canvas: Color,
    /** Raised surfaces: sheets, the one hero card on a screen, keyboard keys. */
    val surface: Color,
    /** Soft grouped background for related rows (an inset group). */
    val group: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val akane: Color,
    val bamboo: Color,
    val amber: Color,
    val radical: Color,
    val hairline: Color,
    val glass: Color,
    val fill: Color,
    val washi: Color,
    val guide: Color,
    val keyboard: Color,
    val key: Color,
    val keyFunction: Color,
    val scrim: Color,
    val shadow: Color,
    val isDark: Boolean,
) {
    val onAccent = Color.White
    fun soft(c: Color, alpha: Float = if (isDark) 0.2f else 0.12f) = c.copy(alpha = alpha)

    /** Story and tile tints by name, as written in the content files. */
    fun tint(name: String): Color = when (name) {
        "amber" -> amber
        "akane" -> akane
        "radical" -> radical
        "bamboo" -> bamboo
        else -> ink2
    }
}

/** Applies the phase's page, group and raised-surface fills; [CircadianPhase.DAY] leaves the palette untouched. */
fun RenColors.circadian(phase: CircadianPhase): RenColors {
    val s = LumeColors.surfaces(phase, isDark)
    return copy(canvas = s.canvas, surface = s.surface, group = s.group)
}

val LightColors = RenColors(
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    group = Color(0xFFF4F4F6),
    ink = Color(0xFF1D1D1F),
    ink2 = Color(0xFF6E6E73),
    ink3 = Color(0xFFAEAEB2),
    akane = Color(0xFFE03E36),
    bamboo = Color(0xFF34C759),
    amber = Color(0xFFFF9500),
    radical = Color(0xFF007AFF),
    hairline = Color(0x243C3C43),
    glass = Color(0xE0FFFFFF),
    fill = Color(0x1F787880),
    washi = Color(0xFFFAF9F6),
    guide = Color(0xFFE3E3E8),
    keyboard = Color(0xFFD5D8DE),
    key = Color(0xFFFFFFFF),
    keyFunction = Color(0xFFB2B7C0),
    scrim = Color(0x33000000),
    shadow = Color(0x24000000),
    isDark = false,
)

val DarkColors = RenColors(
    canvas = Color(0xFF1C1C1E),
    surface = Color(0xFF2C2C2E),
    group = Color(0xFF252527),
    ink = Color(0xFFF5F5F7),
    ink2 = Color(0xFFA1A1A6),
    ink3 = Color(0xFF636366),
    akane = Color(0xFFFF453A),
    bamboo = Color(0xFF30D158),
    amber = Color(0xFFFF9F0A),
    radical = Color(0xFF0A84FF),
    hairline = Color(0x1FFFFFFF),
    glass = Color(0xD91C1C1E),
    fill = Color(0x42787880),
    washi = Color(0xFF262522),
    guide = Color(0xFF3A3A3C),
    keyboard = Color(0xFF2A2A2C),
    key = Color(0xFF545458),
    keyFunction = Color(0xFF3A3A3C),
    scrim = Color(0x73000000),
    shadow = Color(0x66000000),
    isDark = true,
)

private fun style(size: TextUnit, weight: FontWeight, lineHeight: TextUnit, tracking: TextUnit = 0.sp, family: FontFamily = Geist) = TextStyle(
    fontFamily = family,
    fontSize = size,
    fontWeight = weight,
    letterSpacing = tracking,
    lineHeight = lineHeight,
    localeList = JaLocale,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    lineBreak = KinsokuLineBreak,
)

/** A compact scale, close to Sigil's: text that reads easily without shouting. */
@Immutable
data class RenTypography(
    val largeTitle: TextStyle = style(30.sp, FontWeight.Bold, 36.sp, (-0.01).em),
    val title1: TextStyle = style(24.sp, FontWeight.Bold, 29.sp, (-0.015).em),
    val title2: TextStyle = style(20.sp, FontWeight.Bold, 25.sp, (-0.01).em),
    val title3: TextStyle = style(17.sp, FontWeight.SemiBold, 22.sp),
    /** Row and card titles. */
    val headline: TextStyle = style(15.sp, FontWeight.SemiBold, 20.sp),
    val body: TextStyle = style(15.sp, FontWeight.Normal, 21.sp),
    val callout: TextStyle = style(14.sp, FontWeight.Normal, 20.sp),
    val subhead: TextStyle = style(13.sp, FontWeight.Medium, 18.sp),
    val footnote: TextStyle = style(13.sp, FontWeight.Normal, 18.sp),
    val caption: TextStyle = style(12.sp, FontWeight.Normal, 16.sp),
    val caption2: TextStyle = style(10.sp, FontWeight.Medium, 12.sp),
    val button: TextStyle = style(15.sp, FontWeight.SemiBold, 20.sp),
    /** Group headings in lists: sentence case, quiet. */
    val sectionHeader: TextStyle = style(12.sp, FontWeight.SemiBold, 16.sp),
    /** Small caps-style labels over a card ("UP NEXT"). */
    val kicker: TextStyle = style(11.sp, FontWeight.SemiBold, 14.sp, 0.06.em),
    val furigana: TextStyle = style(9.5.sp, FontWeight.Medium, 11.sp),
    /** English prose to be read, not scanned. */
    val read: TextStyle = style(17.sp, FontWeight.Normal, 25.sp, family = Newsreader),
    /** Japanese prose in the Reader and example sentences. */
    val readJp: TextStyle = style(19.sp, FontWeight.Normal, 30.sp, family = NotoSerifJp),
)

/** Japanese text to be read (kanji, vocabulary, stories): the Noto Serif JP voice. */
fun TextStyle.jp(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    copy(fontSize = size, fontWeight = weight, lineHeight = size * 1.3f, letterSpacing = 0.sp, fontFamily = NotoSerifJp)

/** Japanese kana in interface chrome (keyboard keys, the answer field): the sans face with Japanese forms. */
fun TextStyle.jpUi(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    copy(fontSize = size, fontWeight = weight, lineHeight = size * 1.3f, letterSpacing = 0.sp)

object RenMotion {
    fun <T> spring() = LumeMotion.spatial<T>()
    fun <T> snappy() = LumeMotion.effects<T>()
    fun <T> gentle() = LumeMotion.effects<T>()
    const val PRESSED_SCALE = LumeMotion.PRESSED_SCALE
}

object RenShapes {
    val card: Dp = 14.dp
    val button: Dp = 12.dp
    val tile: Dp = 9.dp
    val gutter: Dp = 16.dp
}

val LocalRenColors = staticCompositionLocalOf { LightColors }
val LocalRenTypography = staticCompositionLocalOf { RenTypography() }

/** Lets previews and screenshot tests switch off backdrop blur, which layoutlib cannot render. */
val LocalGlassEnabled = staticCompositionLocalOf { true }

object Ren {
    val colors: RenColors @Composable @ReadOnlyComposable get() = LocalRenColors.current
    val type: RenTypography @Composable @ReadOnlyComposable get() = LocalRenTypography.current
}

@Composable
fun RenTheme(
    dark: Boolean = isSystemInDarkTheme(),
    glass: Boolean = true,
    phase: CircadianPhase = CircadianPhase.DAY,
    content: @Composable () -> Unit,
) {
    val colors = (if (dark) DarkColors else LightColors).circadian(phase)
    CompositionLocalProvider(
        LocalRenColors provides colors,
        LocalRenTypography provides RenTypography(),
        LocalGlassEnabled provides glass,
        LocalTextSelectionColors provides TextSelectionColors(colors.akane, colors.akane.copy(alpha = 0.28f)),
        content = content,
    )
}
