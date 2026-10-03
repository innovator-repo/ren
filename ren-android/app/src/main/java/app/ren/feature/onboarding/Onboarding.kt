package app.ren.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.PrimaryButton
import app.ren.core.designsystem.components.StrokeGlyph
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.core.designsystem.components.rememberStrokePaths
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.feature.settings.SettingsIntent
import app.ren.feature.settings.SettingsViewModel

/** 連 from KanjiVG (CC BY-SA 3.0), the app's mark. */
private val REN_STROKES = listOf(
    "M44.79,24.29c1.96,0.59,3.74,0.31,5.47,0.16c8.42-0.77,23.24-2.57,31-2.96c1.79-0.09,3.44-0.29,5.21,0.16",
    "M44.3,34.99c0.79,0.79,1.4,1.52,1.6,2.45c0.93,4.19,2.76,13.02,4.06,20.8c0.23,1.4,0.45,2.76,0.64,4.06",
    "M46.56,35.94c10.77-1.22,28.9-3.5,36.82-3.93c3.68-0.2,5.22,0.53,4.71,4.37c-0.58,4.41-1.92,12.26-3.57,19.06c-0.31,1.26-0.62,2.49-0.95,3.65",
    "M49.4,47.67c9.85-0.92,23.48-2.8,36.2-3.51",
    "M51.4,59.64c8.69-0.94,20.1-2.14,31.42-2.74",
    "M41.75,71.47c2.32,0.62,4.8,0.45,7.14,0.21c11.18-1.12,26.08-2.74,38.61-3.32c2.41-0.11,4.75-0.12,7,0.34",
    "M64.41,10.38c1.29,1.29,2.03,3.12,2.03,4.79c0,0.94-0.06,45.9-0.15,64.45c-0.02,3.55-0.04,6.14-0.06,7.26",
    "M20.38,19c3.84,1.83,9.92,7.53,10.88,10.38",
    "M13,50.75c2.25,0.62,4.16,0.22,6.3-0.48c2.8-0.92,8.29-2.82,9.45-3.27c2.25-0.88,3.4,0.45,2.25,2.75c-7.38,14.75-7.62,8.75,0.25,19c1.02,1.33,0.68,2.67-0.75,4C28.62,74.5,23.12,79,20.25,81.5",
    "M15.5,83.5c3-0.5,9-1.75,13.5-0.75s30.32,7.71,34.75,9c12,3.5,19.12,4.5,28,4",
)

private data class Feature(val icon: ImageVector, val tint: Color, val title: String, val body: String)

@Composable
fun OnboardingRoute(onDone: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    OnboardingScreen {
        vm.onIntent(SettingsIntent.CompleteOnboarding)
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else onDone()
    }
}

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    val c = Ren.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val paths = rememberStrokePaths(REN_STROKES)
    val still = LocalInspectionMode.current
    val draw = remember { Animatable(if (still) REN_STROKES.size.toFloat() else 0f) }
    val fade = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        draw.animateTo(REN_STROKES.size.toFloat(), tween(2200, easing = LinearEasing))
        fade.animateTo(1f, tween(500))
    }
    val features = listOf(
        Feature(RenIcons.Brush, c.akane, "Draw, don't pick", "Every answer is written on the pad or typed on a flick keyboard. No multiple choice."),
        Feature(RenIcons.Matrix, c.radical, "Radicals first", "Kanji arrive as small equations of pieces you already know: 亻 + 木 = 休."),
        Feature(RenIcons.Today, c.bamboo, "Ten minutes a day", "A memory model brings each card back just before you would forget it."),
        Feature(RenIcons.Reader, c.amber, "Read from week one", "Short stories at your level, with furigana that fades as kanji stick."),
    )
    Column(Modifier.fillMaxSize().background(c.canvas).padding(top = top, bottom = bottom)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
            VSpace(36.dp)
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(26.dp)).background(c.akane), Alignment.Center) {
                StrokeGlyph(paths, Modifier.size(80.dp), width = 6.4f, colorOf = { Color.White }, revealOf = { i -> (draw.value - i).coerceIn(0f, 1f) })
            }
            VSpace(26.dp)
            Txt("Welcome to Ren", Ren.type.largeTitle)
            Txt("連 means to link. Japanese, one link at a time, from kana to N3.", Ren.type.body, color = c.ink2)
            VSpace(30.dp)
            Column(Modifier.alpha(fade.value).graphicsLayer { translationY = (1 - fade.value) * 24f }, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                features.forEach { f ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.soft(f.tint, 0.16f)), Alignment.Center) {
                            RenIcon(f.icon, f.tint, size = 24.dp)
                        }
                        HSpace(16.dp)
                        Column(Modifier.weight(1f)) {
                            Txt(f.title, Ren.type.headline)
                            Txt(f.body, Ren.type.callout, color = c.ink2)
                        }
                    }
                }
            }
            VSpace(24.dp)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Txt(
                "Day 1 starts with five hiragana. We'll send one reminder a day when reviews are waiting.",
                Ren.type.footnote, color = c.ink2, modifier = Modifier.padding(bottom = 14.dp),
            )
            PrimaryButton("Get started", onClick = onStart)
        }
    }
}
