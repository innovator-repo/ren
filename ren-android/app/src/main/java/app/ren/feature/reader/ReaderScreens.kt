package app.ren.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ren.core.designsystem.components.BottomSheet
import app.ren.core.designsystem.components.Card
import app.ren.core.designsystem.components.Chevron
import app.ren.core.designsystem.components.CircleIconButton
import app.ren.core.designsystem.components.CupertinoSwitch
import app.ren.core.designsystem.components.GlyphTile
import app.ren.core.designsystem.components.GroupedList
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.LargeTitleScaffold
import app.ren.core.designsystem.components.NavBar
import app.ren.core.designsystem.components.NavBarHeight
import app.ren.core.designsystem.components.PartChip
import app.ren.core.designsystem.components.PrimaryButton
import app.ren.core.designsystem.components.RubySentence
import app.ren.core.designsystem.components.RubyWord
import app.ren.core.designsystem.components.SectionHeader
import app.ren.core.designsystem.components.SegmentedControl
import app.ren.core.designsystem.components.SpeakerButton
import app.ren.core.designsystem.components.Tag
import app.ren.core.designsystem.components.TextAction
import app.ren.core.designsystem.components.ToolButton
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.core.designsystem.components.WordLook
import app.ren.core.designsystem.components.glassSource
import app.ren.core.designsystem.components.pressable
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.jp
import app.ren.core.haptics.LocalHaptics
import app.ren.core.mvi.CollectEffects
import app.ren.domain.model.RubyMarkup
import app.ren.domain.usecase.StoryInsight
import app.ren.navigation.LocalNavigator
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------------ library

@Composable
fun ReaderRoute(bottomInset: Dp, vm: LibraryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    CollectEffects(vm.effects) { e ->
        when (e) {
            is LibraryEffect.Story -> nav.story(e.id)
            LibraryEffect.Settings -> nav.settings()
        }
    }
    LibraryScreen(state, bottomInset, vm::onIntent)
}

@Composable
fun LibraryScreen(state: LibraryUiState, bottomInset: Dp, onIntent: (LibraryIntent) -> Unit) {
    val c = Ren.colors
    LargeTitleScaffold(
        title = "Reader",
        subtitle = "Read at your level",
        bottomInset = bottomInset,
        trailing = {
            ToolButton(RenIcons.Sliders, "Settings") { onIntent(LibraryIntent.OpenSettings) }
        },
    ) {
        item(key = "seg") {
            SegmentedControl(listOf("Stories", "Dialogues", "Real life"), state.category, Modifier.padding(horizontal = 16.dp)) {
                onIntent(LibraryIntent.SelectCategory(it))
            }
        }
        val visible = state.visible
        if (visible.isNotEmpty()) {
            item(key = "header") { SectionHeader("Matched to you", Modifier.padding(horizontal = 16.dp)) }
            item(key = "list") {
                GroupedList(Modifier.padding(horizontal = 16.dp), plain = true) {
                    visible.forEach { insight ->
                        row(inset = 56.dp, enabled = !insight.locked, onClick = { onIntent(LibraryIntent.Open(insight.story.id)) }) {
                            StoryRow(insight)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StoryRow(insight: StoryInsight) {
    val c = Ren.colors
    val tint = c.tint(insight.story.tint)
    val locked = insight.locked
    GlyphTile(
        insight.story.iconChar, size = 40.dp,
        background = if (locked) c.fill else c.soft(tint), color = if (locked) c.ink2 else tint,
    )
    HSpace(14.dp)
    Column(Modifier.weight(1f).alpha(if (locked) 0.55f else 1f)) {
        RubySentence(
            insight.story.title, Ren.type.headline.jp(16.sp, FontWeight.SemiBold),
            lookOf = { w -> WordLook(showRuby = w.text.any { ch -> ch.toString() in insight.missingKanji }) },
        )
        Txt(insight.story.titleEn, Ren.type.callout, color = c.ink2, maxLines = 1)
        if (locked) {
            VSpace(4.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RenIcon(RenIcons.Lock, c.ink2, size = 15.dp)
                HSpace(5.dp)
                Txt(insight.lockReason, Ren.type.footnote, color = c.ink2)
            }
        } else {
            Txt("${insight.story.durationSec} sec · ${(insight.knownShare * 100).roundToInt()}% known", Ren.type.footnote, color = c.ink2)
            VSpace(6.dp)
            when {
                insight.missingKanji.size == 1 -> Tag("1 new kanji", c.amber)
                insight.newWords.size == 1 -> Tag("1 new word", c.amber)
                insight.newWords.size > 1 -> Tag("${insight.newWords.size} new words", c.amber)
                else -> Tag("All words known", c.bamboo)
            }
        }
    }
    if (!locked) { HSpace(8.dp); Chevron() }
}

// ------------------------------------------------------------------ story

@Composable
fun StoryRoute(vm: StoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val haptics = LocalHaptics.current
    CollectEffects(vm.effects) { e ->
        when (e) {
            is StoryEffect.Item -> nav.item(e.itemId)
            StoryEffect.Saved -> haptics.success()
        }
    }
    StoryScreen(state, nav::back, vm::onIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryScreen(state: StoryUiState, onBack: () -> Unit, onIntent: (StoryIntent) -> Unit) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    val haze = remember { HazeState() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val story = state.story ?: return
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(haze),
            contentPadding = PaddingValues(top = top + NavBarHeight + 12.dp, bottom = bottom + if (state.mining != null) 280.dp else 48.dp),
        ) {
            item(key = "player") { Player(state, Modifier.padding(horizontal = 16.dp)) { onIntent(StoryIntent.TogglePlay) } }
            item(key = "hint") {
                Txt(
                    if (state.mining != null) "Tap more words to grow the fragment."
                    else "Tap a word to look it up. Press and hold to save a sentence.",
                    Ren.type.footnote, color = c.ink2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            itemsIndexed(state.sentences, key = { _, s -> "s${s.index}" }) { _, s ->
                val active = state.playback.playing && state.playback.sentence == s.index
                val glow by animateFloatAsState(if (active) 1f else 0f, label = "active")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.soft(c.akane, 0.08f * glow))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    RubySentence(
                        s.jp, Ren.type.readJp,
                        tokenModifier = { i ->
                            Modifier.pointerInput(s.index, i) {
                                detectTapGestures(
                                    onTap = { onIntent(StoryIntent.TapWord(s.index, i)) },
                                    onLongPress = { haptics.click(); onIntent(StoryIntent.LongPressWord(s.index, i)) },
                                )
                            }
                        },
                        lookByIndex = { i ->
                            val w = s.words.getOrNull(i)
                            WordLook(
                                showRuby = w?.showRuby == true,
                                highlight = if (w?.selected == true) c.soft(c.radical, 0.28f) else null,
                                underline = if (w?.isNew == true && w.selected.not()) c.amber else null,
                            )
                        },
                    )
                    VSpace(4.dp)
                    Txt(s.en, Ren.type.footnote, color = c.ink2)
                }
            }
        }
        NavBar(
            RubyMarkup.plain(story.title), backLabel = "Library", onBack = onBack, haze = haze,
            trailing = {
                if (state.savedCount > 0) Tag("${state.savedCount} saved", c.bamboo, Modifier.padding(end = 8.dp))
            },
        )

        AnimatedVisibility(
            state.mining != null,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            state.mining?.let { MiningTray(it, Modifier.padding(start = 12.dp, end = 12.dp, bottom = bottom + 16.dp), onIntent) }
        }

        BottomSheet(visible = state.sheet != null, onDismiss = { onIntent(StoryIntent.DismissSheet) }) {
            state.sheet?.let { WordSheet(it, onIntent) }
        }
    }
}

@Composable
private fun Player(state: StoryUiState, modifier: Modifier, onToggle: () -> Unit) {
    val c = Ren.colors
    val p = state.playback
    Card(modifier, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).pressable(label = if (p.playing) "Pause" else "Play", scaleDown = 0.9f, onClick = onToggle)
                    .clip(CircleShape).background(c.akane),
                Alignment.Center,
            ) { RenIcon(if (p.playing) RenIcons.Pause else RenIcons.Play, c.onAccent, size = 20.dp) }
            HSpace(12.dp)
            Waveform(state.story?.id.orEmpty(), p.progress, Modifier.weight(1f).height(30.dp))
            HSpace(12.dp)
            Txt("${clock(p.elapsedSec)} / ${clock(p.totalSec)}", Ren.type.footnote.copy(fontWeight = FontWeight.Medium), color = c.ink2)
        }
    }
}

private fun clock(sec: Float): String {
    val s = sec.roundToInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** A deterministic waveform for each story; played bars turn Akane. */
@Composable
private fun Waveform(seed: String, progress: Float, modifier: Modifier) {
    val c = Ren.colors
    val bars = 34
    val heights = remember(seed) {
        val h = seed.hashCode()
        List(bars) { i -> 0.28f + 0.72f * abs(sin(i * 0.55 + h % 7) * 0.6 + sin(i * 1.7 + h % 11) * 0.4).toFloat().coerceIn(0f, 1f) }
    }
    Canvas(modifier) {
        val w = size.width / bars
        heights.forEachIndexed { i, hFrac ->
            val x = i * w + w / 2
            val h = size.height * hFrac
            drawLine(
                if (i / bars.toFloat() < progress) c.akane else c.ink3,
                Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                strokeWidth = w * 0.55f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordSheet(sheet: WordSheetUi, onIntent: (StoryIntent) -> Unit) {
    val c = Ren.colors
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RubyWord(sheet.vocab.text, sheet.vocab.reading, Ren.type.title1.jp(28.sp, FontWeight.SemiBold), rubyStyle = Ren.type.furigana.copy(fontSize = 11.sp))
                Txt(sheet.vocab.meaning, Ren.type.body, color = c.ink2)
            }
            SpeakerButton(size = 38.dp) { onIntent(StoryIntent.SpeakWord) }
        }
        if (sheet.parts.isNotEmpty()) {
            SheetLabel("Built from")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sheet.parts.forEach { PartChip(it.glyph, it.meaning) }
            }
        }
        sheet.pitch?.let { pitch ->
            SheetLabel("Pitch accent")
            Row(verticalAlignment = Alignment.CenterVertically) {
                PitchGraph(pitch, Modifier.width(196.dp).height(64.dp))
                HSpace(12.dp)
                Txt(pitch.description, Ren.type.caption, color = c.ink2, modifier = Modifier.weight(1f))
            }
        }
        VSpace(18.dp)
        if (sheet.inReviews) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(sheet.status, Ren.type.subhead, color = c.bamboo, modifier = Modifier.weight(1f))
                TextAction("Details") { onIntent(StoryIntent.OpenWordDetails) }
            }
        } else {
            PrimaryButton("Add to my reviews") { onIntent(StoryIntent.AddToReviews) }
        }
        VSpace(4.dp)
    }
}

@Composable
private fun SheetLabel(text: String) {
    VSpace(16.dp)
    Txt(text.uppercase(), Ren.type.caption.copy(letterSpacing = 0.6.sp), color = Ren.colors.ink2)
    VSpace(8.dp)
}

/** Tokyo pitch as dots joined by a line; the hollow last dot is the following particle. */
@Composable
fun PitchGraph(pitch: PitchUi, modifier: Modifier) {
    val c = Ren.colors
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val n = pitch.contour.size
        val step = size.width / (n + 0.2f)
        val hi = size.height * 0.12f
        val lo = size.height * 0.52f
        val pts = pitch.contour.mapIndexed { i, high -> Offset(step * (i + 0.5f), if (high) hi else lo) }
        for (i in 0 until pts.size - 1) drawLine(c.radical, pts[i], pts[i + 1], 2.dp.toPx())
        pts.forEachIndexed { i, p ->
            if (i == pts.lastIndex) {
                drawCircle(c.canvas, 4.dp.toPx(), p)
                drawCircle(c.radical, 4.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            } else drawCircle(c.radical, 4.dp.toPx(), p)
            val label = pitch.morae.getOrNull(i) ?: "は"
            val t = measurer.measure(label, TextStyle(fontSize = 12.sp, color = if (i == pts.lastIndex) c.ink2 else c.ink))
            drawText(t, topLeft = Offset(p.x - t.size.width / 2f, size.height - t.size.height))
        }
    }
}

@Composable
private fun MiningTray(m: MiningUi, modifier: Modifier, onIntent: (StoryIntent) -> Unit) {
    val c = Ren.colors
    Column(
        modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("SAVE THIS FRAGMENT", Ren.type.caption2.copy(letterSpacing = 0.6.sp), color = c.ink2)
                Txt(m.plain, Ren.type.title3.jp(19.sp, FontWeight.SemiBold), maxLines = 2)
                Txt(m.english, Ren.type.footnote, color = c.ink2, maxLines = 2)
            }
            SpeakerButton(size = 34.dp) { onIntent(StoryIntent.SpeakFragment) }
            HSpace(6.dp)
            CircleIconButton(RenIcons.Close, "Cancel", size = 30.dp, iconSize = 14.dp) { onIntent(StoryIntent.CancelMining) }
        }
        VSpace(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(if (m.audioFirst) "Audio on the front of the card" else "Text on the front of the card", Ren.type.callout, modifier = Modifier.weight(1f))
            CupertinoSwitch(m.audioFirst) { onIntent(StoryIntent.ToggleAudioFirst) }
        }
        VSpace(12.dp)
        PrimaryButton(if (m.savedId != null) "Saved. First review today" else "Save to reviews", color = if (m.savedId != null) c.bamboo else c.akane) {
            onIntent(StoryIntent.SaveMined)
        }
        Txt(
            if (m.savedId != null) "Tap again to undo." else "Joins today's queue as 1 new card.",
            Ren.type.footnote, color = c.ink2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
