package app.ren.feature.today

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ren.core.designsystem.components.Hairline
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.PrimaryButton
import app.ren.core.designsystem.components.ToolButton
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.core.designsystem.components.rowHighlight
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.mvi.CollectEffects
import app.ren.designsystem.LumeMotion
import app.ren.designsystem.pressable
import app.ren.domain.usecase.TodaySummary
import app.ren.navigation.LocalNavigator
import kotlinx.coroutines.launch

/** The drawer peek: handle, title and the three shortcut chips, fully visible on a 360x780 dp screen. */
private val DrawerPeek = 170.dp

/** The open drawer stops this far below the top, so a sliver of the hero still shows. */
private val DrawerTopGap = 120.dp

@Composable
fun TodayRoute(vm: TodayViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    CollectEffects(vm.effects) { e ->
        when (e) {
            TodayEffect.Session -> nav.session()
            is TodayEffect.Lesson -> nav.lesson(e.unitId)
            is TodayEffect.Item -> nav.item(e.itemId)
            TodayEffect.Settings -> nav.settings()
            TodayEffect.Reader -> nav.reader()
            TodayEffect.Path -> nav.path()
            TodayEffect.Progress -> nav.progress()
        }
    }
    TodayScreen(state, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), vm::onIntent)
}

/** Now-first main screen: one hero (what is due), a pull-up drawer for everything else, no tab bar. */
@Composable
fun TodayScreen(state: TodayUiState, bottomInset: Dp, onIntent: (TodayIntent) -> Unit) {
    val c = Ren.colors
    val summary = state.summary
    BoxWithConstraints(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(state.pathDay) { onIntent(TodayIntent.OpenSettings) }
            if (summary != null) {
                Hero(
                    state, summary,
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = DrawerPeek + bottomInset),
                    onIntent,
                )
            }
        }
        if (summary != null) Drawer(state, bottomInset, maxHeight - DrawerTopGap, Modifier.align(Alignment.BottomCenter), onIntent)
    }
}

@Composable
private fun Header(pathDay: Long, onSettings: () -> Unit) {
    val c = Ren.colors
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(c.akane), contentAlignment = Alignment.Center) {
            Txt("練", Ren.type.headline.copy(fontSize = 16.sp), color = c.onAccent)
        }
        HSpace(10.dp)
        Txt("REN · ${NowFirst.headerContext(pathDay)}", Ren.type.kicker, color = c.ink2, modifier = Modifier.weight(1f), maxLines = 1)
        ToolButton(RenIcons.Sliders, "Settings", onClick = onSettings)
    }
}

/** The single thing that matters now: the ring with today's due count, the plan, and the one button. */
@Composable
private fun Hero(state: TodayUiState, s: TodaySummary, modifier: Modifier, onIntent: (TodayIntent) -> Unit) {
    val c = Ren.colors
    val busy = s.sessionItems > 0
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        val fraction = NowFirst.ringFraction(s.reviewsDone, s.dueReviews)
        val track = c.fill
        val arc = c.akane
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 14.dp.toPx()
                val inset = w / 2
                val box = Size(size.width - w, size.height - w)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(w))
                drawArc(arc, -90f, 360f * fraction, false, Offset(inset, inset), box, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("${s.dueReviews}", Ren.type.largeTitle.copy(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold))
                Txt(if (s.dueReviews > 0 || busy) "due today" else "all caught up", Ren.type.footnote, color = c.ink2)
            }
        }
        VSpace(16.dp)
        Txt(
            if (!busy && state.nextDueLabel != null) "Next review in ${state.nextDueLabel}"
            else NowFirst.planLine(s.newRemaining, s.estimatedMinutes),
            Ren.type.callout, color = c.ink2, align = TextAlign.Center,
        )
        VSpace(20.dp)
        PrimaryButton(
            if (busy) "Start session" else "Read a story",
            Modifier.padding(horizontal = 20.dp),
        ) { onIntent(if (busy) TodayIntent.StartSession else TodayIntent.ReadAStory) }
    }
}

/** Bottom sheet: closed it shows a title, a summary and the shortcut chips; pulled up it lists everything else. */
@Composable
private fun Drawer(state: TodayUiState, bottomInset: Dp, fullHeight: Dp, modifier: Modifier, onIntent: (TodayIntent) -> Unit) {
    val c = Ren.colors
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val peek = DrawerPeek + bottomInset
    val fraction = remember { Animatable(0f) }
    val rangePx = with(density) { (fullHeight - peek).toPx() }.coerceAtLeast(1f)
    val dragState = rememberDraggableState { delta ->
        scope.launch { fraction.snapTo((fraction.value - delta / rangePx).coerceIn(0f, 1f)) }
    }
    fun settle(to: Float) { scope.launch { fraction.animateTo(to, LumeMotion.spatial()) } }
    BackHandler(enabled = fraction.value > 0.5f) { settle(0f) }

    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier.fillMaxWidth().height(lerp(peek, fullHeight, fraction.value))
            .shadow(12.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape).background(c.group),
    ) {
        Column(
            Modifier.fillMaxWidth().draggable(
                dragState, Orientation.Vertical,
                onDragStopped = { v -> settle(NowFirst.drawerTarget(fraction.value, v)) },
            ),
        ) {
            Box(
                Modifier.fillMaxWidth().height(24.dp)
                    .pressable(label = "More to learn", scaleDown = 0.98f, haptic = false) { settle(if (fraction.value > 0.5f) 0f else 1f) },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.ink3)) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("More to learn", Ren.type.title3)
                HSpace(12.dp)
                Txt(state.stageName, Ren.type.footnote, color = c.ink2, modifier = Modifier.weight(1f), align = TextAlign.End, maxLines = 1)
            }
            VSpace(10.dp)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Path", RenIcons.Path) { onIntent(TodayIntent.OpenPath) }
                Chip("Reader", RenIcons.Reader) { onIntent(TodayIntent.ReadAStory) }
                Chip("Progress", RenIcons.Matrix) { onIntent(TodayIntent.OpenProgress) }
            }
            VSpace(10.dp)
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).alpha(((fraction.value - 0.15f) / 0.6f).coerceIn(0f, 1f))
                .verticalScroll(rememberScrollState(), enabled = fraction.value > 0.9f)
                .padding(horizontal = 16.dp).padding(bottom = bottomInset + 16.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)) {
                DrawerRow("Path", state.pathLine) { onIntent(TodayIntent.OpenPath) }
                Hairline(Modifier.padding(start = 16.dp))
                DrawerRow("Reader", state.readerLine) { onIntent(TodayIntent.ReadAStory) }
                Hairline(Modifier.padding(start = 16.dp))
                DrawerRow("Progress", state.progressLine) { onIntent(TodayIntent.OpenProgress) }
                state.word?.let { w ->
                    Hairline(Modifier.padding(start = 16.dp))
                    DrawerRow("Word of the day", "${w.text} · ${w.meaning}") { onIntent(TodayIntent.OpenWord(w.id)) }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, icon: ImageVector, onClick: () -> Unit) {
    val c = Ren.colors
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(c.surface)
            .pressable(label = label, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RenIcon(icon, c.akane, size = 16.dp)
        HSpace(7.dp)
        Txt(label, Ren.type.subhead.copy(fontSize = 14.sp), maxLines = 1)
    }
}

@Composable
private fun DrawerRow(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).rowHighlight(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Txt(title, Ren.type.headline.copy(fontSize = 16.sp))
        if (detail.isNotBlank()) Txt(detail, Ren.type.callout, color = Ren.colors.ink2, maxLines = 1)
    }
}
