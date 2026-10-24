package app.ren.designsystem

import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.core.designsystem.components.Hairline
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.LocalGlassEnabled
import app.ren.core.designsystem.theme.Ren
import app.ren.designsystem.LocalHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Spec §2 glassmorphism: a translucent fill over a 20dp backdrop blur on API 31+,
 * falling back to a near-opaque fill where RenderEffect is unavailable.
 */
@Composable
fun Modifier.frostedGlass(state: HazeState?): Modifier {
    val c = Ren.colors
    return if (state != null && LocalGlassEnabled.current) {
        hazeEffect(
            state,
            HazeStyle(
                backgroundColor = c.canvas,
                tint = HazeTint(c.glass),
                blurRadius = 20.dp,
                noiseFactor = 0f,
                fallbackTint = HazeTint(c.glass.copy(alpha = 0.92f)),
            ),
        )
    } else {
        background(c.glass.copy(alpha = 0.95f))
    }
}

@Composable
fun Modifier.glassSource(state: HazeState): Modifier = if (LocalGlassEnabled.current) hazeSource(state) else this

val NavBarHeight = 44.dp
val TabBarHeight = 50.dp

/**
 * iOS large-title screen: the big title scrolls with content; once it passes under the bar,
 * a frosted bar fades in with a small centred title.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottomInset: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val c = Ren.colors
    val haze = remember { HazeState() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val titleGone by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > with(density) { 64.dp.toPx() }
        }
    }
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4 }
    }
    val barAlpha by animateFloatAsState(if (scrolled) 1f else 0f, app.ren.designsystem.LumeMotion.effects(), label = "bar")
    val titleAlpha by animateFloatAsState(if (titleGone) 1f else 0f, app.ren.designsystem.LumeMotion.effects(), label = "title")

    Box(modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(haze),
            contentPadding = PaddingValues(top = top + 6.dp, bottom = bottomInset + 28.dp),
        ) {
            item(key = "large-title") {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp)) {
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(subtitle.orEmpty(), Ren.type.footnote, color = c.ink2, modifier = Modifier.weight(1f), maxLines = 1)
                        trailing?.invoke()
                    }
                    Txt(title, Ren.type.largeTitle, modifier = Modifier.padding(bottom = 6.dp), maxLines = 1)
                }
            }
            content()
        }
        Box(Modifier.fillMaxWidth().alpha(barAlpha)) {
            Column(Modifier.fillMaxWidth().frostedGlass(haze)) {
                Box(Modifier.fillMaxWidth().padding(top = top).height(NavBarHeight), contentAlignment = Alignment.Center) {
                    Txt(title, Ren.type.headline, modifier = Modifier.alpha(titleAlpha), maxLines = 1)
                }
                Hairline(Modifier.alpha(titleAlpha))
            }
        }
    }
}

/** Navigation bar for pushed screens: "‹ Library" on the left, title centred. */
@Composable
fun NavBar(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    haze: HazeState? = null,
    showHairline: Boolean = true,
    titleContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Ren.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier.fillMaxWidth().frostedGlass(haze)) {
        Box(Modifier.fillMaxWidth().padding(top = top).height(NavBarHeight).padding(horizontal = 8.dp)) {
            if (onBack != null) {
                Row(
                    Modifier.align(Alignment.CenterStart).pressable(label = "Back", scaleDown = 0.94f, onClick = onBack)
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RenIcon(RenIcons.Back, c.akane, size = 20.dp)
                    if (backLabel != null) Txt(backLabel, Ren.type.body.copy(fontSize = 16.sp), color = c.akane, maxLines = 1)
                }
            }
            Box(Modifier.align(Alignment.Center).widthIn(max = 220.dp)) {
                if (titleContent != null) titleContent() else Txt(title, Ren.type.headline, maxLines = 1)
            }
            Box(Modifier.align(Alignment.CenterEnd)) { trailing?.invoke() }
        }
        if (showHairline) Hairline()
    }
}

data class TabItem(val label: String, val icon: ImageVector)

/** Spec §5: four tabs on a frosted bar with a hairline top border. */
@Composable
fun TabBar(items: List<TabItem>, selected: Int, haze: HazeState?, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier.fillMaxWidth().frostedGlass(haze)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().height(TabBarHeight).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val active = i == selected
                val tint by animateColorAsState(if (active) c.akane else c.ink2, app.ren.designsystem.LumeMotion.effects(), label = "tab")
                Column(
                    Modifier
                        .weight(1f)
                        .pressable(label = item.label, scaleDown = 0.9f) {
                            if (!active) haptics.tick()
                            onSelect(i)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RenIcon(item.icon, tint, size = 22.dp)
                    VSpace(2.dp)
                    Txt(
                        item.label,
                        Ren.type.caption2.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(bottom))
    }
}
