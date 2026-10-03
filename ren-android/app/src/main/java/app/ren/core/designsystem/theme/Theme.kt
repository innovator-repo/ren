package app.ren.core.designsystem.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ren.R

/** Spec §2 colour tokens. Names follow the spec; short aliases match the reference prototype. */
@Immutable
data class RenColors(
    val canvas: Color,
    val surface: Color,
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
    val isDark: Boolean,
) {
    val onAccent = Color.White
    fun soft(c: Color, alpha: Float = if (isDark) 0.22f else 0.13f) = c.copy(alpha = alpha)

    /** Story and tile tints by name, as written in the content files. */
    fun tint(name: String): Color = when (name) {
        "amber" -> amber
        "akane" -> akane
        "radical" -> radical
        "bamboo" -> bamboo
        else -> ink2
    }
}

val LightColors = RenColors(
    canvas = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF111111),
    ink2 = Color(0xFF8E8E93),
    ink3 = Color(0xFFC7C7CC),
    akane = Color(0xFFE03E36),
    bamboo = Color(0xFF34C759),
    amber = Color(0xFFFF9500),
    radical = Color(0xFF007AFF),
    hairline = Color(0x333C3C43),
    glass = Color(0xD1F9F9FB),
    fill = Color(0x1F767680),
    washi = Color(0xFFF9F8F6),
    guide = Color(0xFFE5E5EA),
    keyboard = Color(0xFFD2D5DB),
    key = Color(0xFFFFFFFF),
    keyFunction = Color(0xFFAEB3BC),
    scrim = Color(0x4D000000),
    isDark = false,
)

val DarkColors = RenColors(
    canvas = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    ink = Color(0xFFF5F5F7),
    ink2 = Color(0xFF8E8E93),
    ink3 = Color(0xFF48484A),
    akane = Color(0xFFFF453A),
    bamboo = Color(0xFF30D158),
    amber = Color(0xFFFF9F0A),
    radical = Color(0xFF0A84FF),
    hairline = Color(0x4D545458),
    glass = Color(0xCC1C1C1E),
    fill = Color(0x3D767680),
    washi = Color(0xFF2A2925),
    guide = Color(0xFF3A3A3C),
    keyboard = Color(0xFF262628),
    key = Color(0xFF4A4A4D),
    keyFunction = Color(0xFF353537),
    scrim = Color(0x80000000),
    isDark = true,
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Japanese glyph forms everywhere: without this, Han characters fall back to Chinese shapes on non-JP devices. */
private val Ja = LocaleList("ja-JP")

private fun style(size: TextUnit, weight: FontWeight, tracking: TextUnit, lineHeight: TextUnit) = TextStyle(
    fontFamily = Inter,
    fontSize = size,
    fontWeight = weight,
    letterSpacing = tracking,
    lineHeight = lineHeight,
    localeList = Ja,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    // Keep Japanese words whole (コーヒー never splits) and honour kinsoku rules.
    lineBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase),
)

/** Spec §2 type scale. */
@Immutable
data class RenTypography(
    val largeTitle: TextStyle = style(34.sp, FontWeight.Bold, (-0.4).sp, 41.sp),
    val title1: TextStyle = style(28.sp, FontWeight.Bold, (-0.2).sp, 34.sp),
    val title2: TextStyle = style(22.sp, FontWeight.SemiBold, 0.sp, 28.sp),
    val title3: TextStyle = style(20.sp, FontWeight.SemiBold, (-0.2).sp, 25.sp),
    val headline: TextStyle = style(17.sp, FontWeight.SemiBold, (-0.4).sp, 22.sp),
    val body: TextStyle = style(17.sp, FontWeight.Normal, (-0.4).sp, 22.sp),
    val callout: TextStyle = style(15.sp, FontWeight.Normal, (-0.2).sp, 20.sp),
    val subhead: TextStyle = style(15.sp, FontWeight.Medium, (-0.2).sp, 20.sp),
    val footnote: TextStyle = style(13.sp, FontWeight.Normal, (-0.1).sp, 18.sp),
    val caption: TextStyle = style(12.sp, FontWeight.Normal, 0.sp, 16.sp),
    val caption2: TextStyle = style(11.sp, FontWeight.Medium, 0.06.sp, 13.sp),
    val sectionHeader: TextStyle = style(13.sp, FontWeight.Normal, 0.4.sp, 18.sp),
    val furigana: TextStyle = style(10.5.sp, FontWeight.Medium, 0.sp, 12.sp),
)

fun TextStyle.jp(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    copy(fontSize = size, fontWeight = weight, lineHeight = size * 1.3f, letterSpacing = 0.sp)

object RenMotion {
    /** Spec §2: LowBouncy damping with MediumLow stiffness for sheets, flips and presses. */
    fun <T> spring() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    const val PRESSED_SCALE = 0.96f
}

object RenShapes {
    val card: Dp = 18.dp
    val button: Dp = 14.dp
    val tile: Dp = 14.dp
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
fun RenTheme(dark: Boolean = isSystemInDarkTheme(), glass: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalRenColors provides if (dark) DarkColors else LightColors,
        LocalRenTypography provides RenTypography(),
        LocalGlassEnabled provides glass,
        content = content,
    )
}
