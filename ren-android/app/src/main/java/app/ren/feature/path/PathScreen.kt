package app.ren.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ren.core.designsystem.components.Card
import app.ren.core.designsystem.components.CircleIconButton
import app.ren.core.designsystem.components.GroupedList
import app.ren.core.designsystem.components.LargeTitleScaffold
import app.ren.core.designsystem.components.NavBar
import app.ren.core.designsystem.components.PrimaryButton
import app.ren.core.designsystem.components.ProgressBar
import app.ren.core.designsystem.components.SectionHeader
import app.ren.core.designsystem.components.Tag
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.core.designsystem.components.NavBarHeight
import app.ren.core.designsystem.components.glassSource
import app.ren.core.designsystem.components.pressable
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.mvi.CollectEffects
import app.ren.domain.model.Mastery
import app.ren.feature.common.UnitRowContent
import app.ren.navigation.LocalNavigator
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.layout.statusBars

@Composable
fun PathRoute(bottomInset: Dp, vm: PathViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    CollectEffects(vm.effects) { e ->
        when (e) {
            is PathUiEffect.Stage -> nav.stage(e.index)
            is PathUiEffect.Lesson -> nav.lesson(e.unitId)
            PathUiEffect.Settings -> nav.settings()
        }
    }
    PathScreen(state, bottomInset, vm::onIntent)
}

@Composable
fun PathScreen(state: PathUiState, bottomInset: Dp, onIntent: (PathIntent) -> Unit) {
    val c = Ren.colors
    LargeTitleScaffold(
        title = "Path",
        subtitle = "Day ${state.day} of your path",
        bottomInset = bottomInset,
        trailing = {
            CircleIconButton(RenIcons.Sliders, "Settings", background = c.surface, tint = c.akane, size = 36.dp, iconSize = 20.dp) {
                onIntent(PathIntent.OpenSettings)
            }
        },
    ) {
        state.stages.forEachIndexed { i, stage ->
            item(key = "stage-${stage.stage.index}") {
                StageRow(
                    stage,
                    isFirst = i == 0,
                    isLast = i == state.stages.lastIndex,
                    prevDone = i > 0 && state.stages[i - 1].status == StageStatus.Done,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun StageRow(stage: StageUi, isFirst: Boolean, isLast: Boolean, prevDone: Boolean, onIntent: (PathIntent) -> Unit) {
    val c = Ren.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        // Timeline rail: line above the node, the node, then the line down to the next stage.
        Box(Modifier.width(48.dp).fillMaxHeight()) {
            val doneLine = stage.status == StageStatus.Done
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val w = 3.dp.toPx()
                if (!isFirst) drawLine(if (prevDone) c.bamboo else c.ink3, Offset(x, 0f), Offset(x, 24.dp.toPx()), w,
                    pathEffect = if (prevDone) null else PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                if (!isLast) drawLine(
                    if (doneLine) c.bamboo else c.ink3, Offset(x, 56.dp.toPx()), Offset(x, size.height), w,
                    pathEffect = if (doneLine) null else PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
                )
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 22.dp)) { StageNode(stage) }
        }
        val locked = stage.status == StageStatus.Locked
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 10.dp, bottom = 10.dp)
                .pressable(enabled = !locked) { onIntent(PathIntent.OpenStage(stage.stage.index)) }
                .clip(RoundedCornerShape(18.dp))
                .then(if (locked) Modifier.border(0.5.dp, c.hairline, RoundedCornerShape(18.dp)) else Modifier.background(c.surface))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Txt(stage.stage.title, Ren.type.title3.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                stage.stage.badge?.let { Tag(it, if (locked) c.ink2 else c.bamboo, Modifier.padding(start = 8.dp)) }
            }
            VSpace(6.dp)
            Txt(
                if (locked) stage.stage.description else "${stage.stage.window}. ${stage.stage.description}",
                Ren.type.callout.copy(fontSize = 16.sp, lineHeight = 22.sp), color = c.ink2,
            )
            val p = stage.progress
            if (stage.status == StageStatus.Current || stage.status == StageStatus.Open) {
                VSpace(14.dp)
                Counter(stage.primaryLabel, p.primaryDone, p.primaryTotal)
                VSpace(6.dp)
                ProgressBar(p.primaryDone / p.primaryTotal.coerceAtLeast(1).toFloat(), c.akane)
                if (stage.secondaryLabel != null && p.secondaryTotal > 0) {
                    VSpace(12.dp)
                    Counter(stage.secondaryLabel, p.secondaryDone, p.secondaryTotal)
                    VSpace(6.dp)
                    ProgressBar(p.secondaryDone / p.secondaryTotal.coerceAtLeast(1).toFloat(), c.radical)
                }
                stage.nextUnitId?.let { next ->
                    VSpace(16.dp)
                    PrimaryButton(if (p.unitsDone == 0) "Begin" else "Continue") { onIntent(PathIntent.Continue(next)) }
                }
            } else if (stage.status == StageStatus.Done) {
                VSpace(10.dp)
                Txt("${p.primaryTotal} ${stage.primaryLabel.lowercase()} learned", Ren.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.bamboo)
            }
        }
    }
}

@Composable
private fun StageNode(stage: StageUi) {
    val c = Ren.colors
    when (stage.status) {
        StageStatus.Done -> Box(Modifier.size(34.dp).clip(CircleShape).background(c.bamboo), Alignment.Center) {
            RenIcon(RenIcons.Check, c.onAccent, size = 18.dp)
        }
        StageStatus.Current -> Box(
            Modifier.size(44.dp).clip(CircleShape).background(c.soft(c.akane, 0.2f)), Alignment.Center,
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(c.akane), Alignment.Center) {
                Txt("${stage.stage.index + 1}", Ren.type.subhead.copy(fontWeight = FontWeight.Bold), color = c.onAccent)
            }
        }
        StageStatus.Open -> Box(Modifier.size(34.dp).clip(CircleShape).background(c.surface).border(2.dp, c.akane, CircleShape), Alignment.Center) {
            Txt("${stage.stage.index + 1}", Ren.type.subhead.copy(fontWeight = FontWeight.Bold), color = c.akane)
        }
        StageStatus.Locked -> Box(Modifier.size(34.dp).clip(CircleShape).background(c.fill), Alignment.Center) {
            RenIcon(RenIcons.Lock, c.ink2, size = 17.dp)
        }
    }
}

@Composable
private fun Counter(label: String, done: Int, total: Int) {
    val c = Ren.colors
    Row(Modifier.fillMaxWidth()) {
        Txt(label, Ren.type.callout, color = c.ink2, modifier = Modifier.weight(1f))
        Txt(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)) { append("$done") }
                withStyle(SpanStyle(color = c.ink2)) { append(" / $total") }
            },
            Ren.type.callout,
        )
    }
}

// ------------------------------------------------------------------ stage detail

@Composable
fun StageDetailRoute(vm: StageDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    CollectEffects(vm.effects) { e ->
        when (e) {
            is StageDetailEffect.Item -> nav.item(e.itemId)
            is StageDetailEffect.Lesson -> nav.lesson(e.unitId)
        }
    }
    StageDetailScreen(state, nav::back, vm::onIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageDetailScreen(state: StageDetailUiState, onBack: () -> Unit, onIntent: (StageDetailIntent) -> Unit) {
    val c = Ren.colors
    val haze = remember { HazeState() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val stage = state.stage ?: return
    val p = state.progress ?: return
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(haze),
            contentPadding = PaddingValues(top = top + NavBarHeight + 12.dp, bottom = bottom + 110.dp),
        ) {
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    Txt(stage.title, Ren.type.title2.copy(fontWeight = FontWeight.Bold))
                    VSpace(4.dp)
                    Txt("${stage.window}. ${stage.description}", Ren.type.callout, color = c.ink2)
                    VSpace(14.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag("${p.unitsDone} of ${p.unitsTotal} lessons", c.akane)
                        Tag("${(p.graduatedShare * 100).toInt()}% graduated", c.bamboo)
                    }
                }
            }
            item { SectionHeader(primaryLabel(stage.index), Modifier.padding(horizontal = 16.dp)) }
            item {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.tiles.forEach { t ->
                        val (bg, fg) = when (t.mastery) {
                            Mastery.Locked -> c.surface to c.ink3
                            Mastery.New, Mastery.Learning -> c.soft(c.amber) to c.ink
                            Mastery.Mature -> c.soft(c.bamboo) to c.ink
                        }
                        Box(
                            Modifier.size(52.dp).pressable { onIntent(StageDetailIntent.OpenItem(t.itemId)) }
                                .clip(RoundedCornerShape(13.dp)).background(bg),
                            Alignment.Center,
                        ) { Txt(t.glyph, Ren.type.title2.copy(fontWeight = FontWeight.Normal, fontSize = 26.sp), color = fg, maxLines = 1) }
                    }
                }
            }
            if (state.pairs.isNotEmpty()) {
                item { SectionHeader("Verb pairs", Modifier.padding(horizontal = 16.dp)) }
                item {
                    GroupedList(Modifier.padding(horizontal = 16.dp)) {
                        state.pairs.forEach { u -> row(inset = 82.dp, onClick = { onIntent(StageDetailIntent.OpenUnit(u)) }) { UnitRowContent(u) } }
                    }
                }
            }
        }
        NavBar(stage.title, backLabel = "Path", onBack = onBack, haze = haze)
        if (state.nextUnitId != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = bottom + 16.dp)) {
                PrimaryButton(if (p.unitsDone == 0) "Begin this stage" else "Continue") { onIntent(StageDetailIntent.Continue) }
            }
        }
    }
}
