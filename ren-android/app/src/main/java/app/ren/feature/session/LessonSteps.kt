package app.ren.feature.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ren.core.designsystem.components.Card
import app.ren.core.designsystem.components.CircleIconButton
import app.ren.core.designsystem.components.CupertinoSlider
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.PartChip
import app.ren.core.designsystem.components.Pill
import app.ren.core.designsystem.components.RubySentence
import app.ren.core.designsystem.components.SpeakerButton
import app.ren.core.designsystem.components.StrokeGlyph
import app.ren.core.designsystem.components.StrokePad
import app.ren.core.designsystem.components.Tag
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.designsystem.pressable
import app.ren.core.designsystem.components.StrokePadState
import app.ren.core.designsystem.components.rememberStrokePaths
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.jp
import app.ren.designsystem.LocalHaptics
import app.ren.domain.ink.Box as GlyphBox
import app.ren.domain.ink.GLYPH_BOX
import app.ren.domain.ink.StrokeMatcher
import app.ren.domain.ink.StrokeVerdict
import app.ren.domain.model.RubyMarkup
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Renders one lesson step. [onReady] unlocks the step's main button once its task is done. */
@Composable
fun LessonStepContent(step: LessonStep, key: Any, pad: StrokePadState, onReady: () -> Unit, onSpeak: (String) -> Unit) {
    when (step) {
        is LessonStep.PullApart -> PullApartStep(step, key)
        is LessonStep.MeetKanji -> MeetKanjiStep(step, onSpeak)
        is LessonStep.PictureIt -> PictureItStep(step, onSpeak)
        is LessonStep.WriteIt -> WriteStep(step, pad, onReady)
        is LessonStep.FindIt -> FindStep(step, key, onReady, onSpeak)
        is LessonStep.MeetKana -> MeetKanaStep(step, onSpeak)
        is LessonStep.LearnPair -> LearnPairStep(step, onSpeak)
    }
}

fun ctaLabel(step: LessonStep, next: LessonStep?, ready: Boolean): String = when {
    step is LessonStep.LearnPair -> "Add pair to my reviews"
    step is LessonStep.WriteIt && !ready -> "Clear my strokes"
    next == null -> "Finish lesson"
    next is LessonStep.PictureIt -> "Next: the story"
    next is LessonStep.WriteIt -> "Next: write it"
    next is LessonStep.FindIt -> "Next: find it"
    next is LessonStep.MeetKana -> "Next: ${next.kana.character}"
    else -> "Continue"
}

// ------------------------------------------------------------------ 1. pull it apart

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PullApartStep(step: LessonStep.PullApart, key: Any) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    var t by remember(key) { mutableFloatStateOf(0f) }
    val paths = rememberStrokePaths(step.kanji.strokes)
    val center = GLYPH_BOX / 2
    // Each part slides out along the line from the glyph's centre to its own centre.
    val directions = remember(step) {
        step.parts.mapIndexed { i, p ->
            val box = GlyphBox.of(p.strokes.map { step.kanji.strokes[it] })
            var dx = box.cx - center
            var dy = box.cy - center
            val len = hypot(dx, dy)
            if (len < 4f) { dx = if (i % 2 == 0) -1f else 1f; dy = 0f } else { dx /= len; dy /= len }
            Offset(dx, dy)
        }
    }
    val partOf = remember(step) { IntArray(step.kanji.strokes.size) { s -> step.parts.indexOfFirst { s in it.strokes } } }
    var crossed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(t > 0.5f) { if (t > 0.5f && !crossed) { crossed = true; haptics.tick() } }

    Card(padding = PaddingValues(16.dp)) {
        Box(Modifier.fillMaxWidth().height(184.dp), contentAlignment = Alignment.Center) {
            StrokeGlyph(
                paths, Modifier.size(156.dp), width = 5.2f,
                colorOf = { i -> if (partOf[i] >= 0) lerp(c.ink, c.radical, (t * 3).coerceIn(0f, 1f)) else c.ink.copy(alpha = 1f - 0.55f * t) },
                offsetOf = { i -> if (partOf[i] >= 0) directions[partOf[i]] * (24f * t) else Offset.Zero },
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().alpha(0.25f + 0.75f * (t * 3).coerceIn(0f, 1f)),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            step.parts.forEachIndexed { i, p ->
                if (i > 0) Txt("+", Ren.type.title3, color = c.ink2, modifier = Modifier.padding(horizontal = 6.dp).align(Alignment.CenterVertically))
                PartChip(p.glyph, p.meaning, color = if (p.isRadical) c.radical else c.amber)
            }
        }
        VSpace(8.dp)
        Row(Modifier.fillMaxWidth().alpha(0.25f + 0.75f * (t * 3).coerceIn(0f, 1f)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Txt("=", Ren.type.title3, color = c.ink2, modifier = Modifier.padding(end = 8.dp))
            PartChip(step.kanji.character, step.kanji.meaning.substringBefore(','), color = c.ink)
        }
    }
    VSpace(18.dp)
    CupertinoSlider(t, { t = it }, label = "Pull apart")
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Txt("Whole", Ren.type.caption.copy(fontWeight = FontWeight.Medium), color = c.ink2, modifier = Modifier.weight(1f))
        Txt("Apart", Ren.type.caption.copy(fontWeight = FontWeight.Medium), color = c.ink2)
    }
}

// ------------------------------------------------------------------ meet (pictograph kanji)

@Composable
private fun MeetKanjiStep(step: LessonStep.MeetKanji, onSpeak: (String) -> Unit) {
    val c = Ren.colors
    val k = step.kanji
    Card(padding = PaddingValues(20.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StrokeGlyph(rememberStrokePaths(k.strokes), Modifier.size(140.dp), width = 5.2f, colorOf = { c.ink })
        }
        VSpace(10.dp)
        Txt(k.meaning, Ren.type.title2.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        VSpace(14.dp)
        ReadingsRow(k.onyomi, k.kunyomi)
        VSpace(14.dp)
        Txt(k.mnemonicStory, Ren.type.body, color = c.ink2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
    VSpace(14.dp)
    val say = k.kunyomi.firstOrNull()?.substringBefore('.')?.trimStart('-') ?: k.onyomi.firstOrNull() ?: k.character
    SpeakPill(say) { onSpeak(say) }
}

@Composable
fun ReadingsRow(on: List<String>, kun: List<String>) {
    val c = Ren.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (kun.isNotEmpty()) ReadingBox("Kun", kun.take(2).joinToString("、") { it.replace(".", "") }, Modifier.weight(1f), c.radical)
        if (on.isNotEmpty()) ReadingBox("On", on.take(2).joinToString("、"), Modifier.weight(1f), c.amber)
    }
}

@Composable
private fun ReadingBox(label: String, value: String, modifier: Modifier, color: Color) {
    val c = Ren.colors
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.soft(color, 0.1f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Txt(label.uppercase(), Ren.type.caption2, color = color)
        Txt(value, Ren.type.body.jp(15.sp, FontWeight.Medium), maxLines = 2)
    }
}

// ------------------------------------------------------------------ 2. picture it

@Composable
private fun PictureItStep(step: LessonStep.PictureIt, onSpeak: (String) -> Unit) {
    val c = Ren.colors
    Card(padding = PaddingValues(10.dp)) { MnemonicArt(step.glyph, step.strokes, step.components) }
    VSpace(18.dp)
    val story = buildAnnotatedString {
        var i = 0
        val text = step.story
        val words = step.keywords.filter { it.word.isNotBlank() }
        while (i < text.length) {
            val hit = words.firstOrNull { text.regionMatches(i, it.word, 0, it.word.length, ignoreCase = true) }
            if (hit != null) {
                withStyle(SpanStyle(color = if (hit.isTarget) c.akane else c.radical)) { append(text.substring(i, i + hit.word.length)) }
                i += hit.word.length
            } else {
                append(text[i]); i++
            }
        }
    }
    Txt(story, Ren.type.read.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold))
    VSpace(16.dp)
    SpeakPill(step.audioText) { onSpeak(step.audioText) }
}

@Composable
private fun SpeakPill(text: String, onClick: () -> Unit) {
    val c = Ren.colors
    Pill(text, Modifier.pressable(onClick = onClick), icon = RenIcons.Speaker, iconTint = c.radical, background = c.group, bold = false)
}

// ------------------------------------------------------------------ 3. write it

@Composable
private fun WriteStep(step: LessonStep.WriteIt, pad: StrokePadState, onReady: () -> Unit) {
    val c = Ren.colors
    LaunchedEffect(pad.done) { if (pad.done) onReady() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        StrokePad(step.strokes, pad)
    }
    VSpace(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val v = pad.lastVerdict
        when {
            pad.done -> Pill("All ${step.strokes.size} strokes", icon = RenIcons.Check, iconTint = c.bamboo, color = c.bamboo)
            v == StrokeVerdict.Good -> Pill(StrokeMatcher.hint(v), icon = RenIcons.Check, iconTint = c.bamboo, color = c.bamboo)
            v != null -> Pill(StrokeMatcher.hint(v), icon = RenIcons.Info, iconTint = c.amber, color = c.amber)
            else -> Pill("Stroke 1 of ${step.strokes.size}", bold = false, color = c.ink2)
        }
        Box(Modifier.weight(1f))
        CircleIconButton(RenIcons.Undo, "Undo last stroke", size = 32.dp, iconSize = 15.dp, onClick = pad::undo)
    }
    if (step.tip != null) {
        VSpace(12.dp)
        Card(padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step.tipGlyphs.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        step.tipGlyphs.forEachIndexed { i, g ->
                            if (i > 0) Txt("↓", Ren.type.caption, color = c.radical)
                            Txt(g, Ren.type.title2.copy(fontWeight = FontWeight.Normal), color = c.radical)
                        }
                    }
                    HSpace(16.dp)
                }
                Txt(step.tip, Ren.type.callout, color = c.ink2)
            }
        }
    }
}

// ------------------------------------------------------------------ 4. find it

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindStep(step: LessonStep.FindIt, key: Any, onReady: () -> Unit, onSpeak: (String) -> Unit) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var solved by remember(key) { mutableStateOf(false) }
    var wrong by remember(key) { mutableIntStateOf(-1) }
    var message by remember(key) { mutableStateOf("Tap a part of ${step.kanji?.character ?: ""}.") }
    val shake = remember(key) { Animatable(0f) }
    val k = step.kanji
    val glyphSize = 76.dp

    Card(padding = PaddingValues(top = 22.dp, bottom = 22.dp, start = 16.dp, end = 16.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SpeakerButton(size = 44.dp) { onSpeak(step.word.reading) }
        }
        VSpace(18.dp)
        Row(Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value }, horizontalArrangement = Arrangement.Center) {
            val boxes = remember(step) { hitBoxes(step) }
            step.word.text.forEachIndexed { i, ch ->
                val isTarget = i == step.targetKanjiIndex
                val strokes = step.glyphStrokes.getOrNull(i).orEmpty()
                Box(Modifier.size(glyphSize)) {
                    if (strokes.isNotEmpty()) {
                        StrokeGlyph(rememberStrokePaths(strokes), Modifier.fillMaxSize(), width = 4.8f, colorOf = { c.ink })
                    } else {
                        Txt(ch.toString(), Ren.type.body.jp(64.sp, FontWeight.Normal), modifier = Modifier.align(Alignment.Center), color = c.ink, maxLines = 1)
                    }
                    if (isTarget && k != null && step.targetComponent != null) {
                        // Hit areas over each radical, from KanjiVG stroke geometry.
                        boxes.forEach { (ci, b) ->
                            HitBox(
                                b, glyphSize,
                                state = when {
                                    solved && ci == step.targetComponent -> HitState.Right
                                    wrong == ci -> HitState.Wrong
                                    else -> HitState.Idle
                                },
                            ) {
                                if (solved) return@HitBox
                                val name = k.components[ci].element
                                if (ci == step.targetComponent) {
                                    solved = true; haptics.click(); onReady()
                                    message = "Yes. $name is the ${step.targetName}."
                                } else {
                                    wrong = ci; haptics.gentleNudge()
                                    message = "That part is $name. Try another piece."
                                    scope.launch { shake.animateTo(0f, keyframes { durationMillis = 350; -10f at 60; 8f at 140; -4f at 220 }) }
                                }
                            }
                        }
                    } else if (step.targetComponent == null) {
                        HitBox(GlyphBox(6f, 6f, GLYPH_BOX - 6, GLYPH_BOX - 6), glyphSize, state = when {
                            solved && isTarget -> HitState.Right
                            wrong == i + 100 -> HitState.Wrong
                            else -> HitState.Idle
                        }) {
                            if (solved) return@HitBox
                            if (isTarget) { solved = true; haptics.click(); onReady(); message = "Yes, that is ${k?.character}." }
                            else {
                                wrong = i + 100; haptics.gentleNudge(); message = "Not that one. Listen again."
                                scope.launch { shake.animateTo(0f, keyframes { durationMillis = 350; -10f at 60; 8f at 140; -4f at 220 }) }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(solved, enter = fadeIn() + expandVertically()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                VSpace(6.dp)
                Txt(step.word.reading, Ren.type.title3.jp(18.sp, FontWeight.Medium), color = c.ink2)
                Txt(step.word.meaning, Ren.type.callout, color = c.ink2)
            }
        }
    }
    VSpace(16.dp)
    Txt(
        message, Ren.type.callout.copy(fontWeight = FontWeight.Medium),
        color = if (solved) c.bamboo else if (wrong >= 0) c.amber else c.ink2,
        modifier = Modifier.fillMaxWidth(), align = TextAlign.Center,
    )
    if (solved && step.otherWords.isNotEmpty()) {
        VSpace(16.dp)
        Txt("ALSO WITH ${step.kanji?.character}", Ren.type.caption2.copy(letterSpacing = 0.6.sp), color = c.ink2)
        VSpace(8.dp)
        Card(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            step.otherWords.forEach { w ->
                Row(Modifier.fillMaxWidth().pressable { onSpeak(w.reading) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(w.text, Ren.type.body.jp(17.sp, FontWeight.Medium))
                    HSpace(10.dp)
                    Txt(w.reading, Ren.type.callout, color = c.ink2)
                    Box(Modifier.weight(1f))
                    Txt(w.meaning, Ren.type.footnote, color = c.ink2, maxLines = 1)
                }
            }
        }
    }
}

private enum class HitState { Idle, Right, Wrong }

/** One padded box per radical; two side-by-side boxes meet at their midpoint instead of overlapping. */
private fun hitBoxes(step: LessonStep.FindIt): List<Pair<Int, GlyphBox>> {
    val k = step.kanji ?: return emptyList()
    val pad = 5f
    val raw = k.components.mapIndexedNotNull { ci, comp ->
        if (comp.radicalId == null) return@mapIndexedNotNull null
        val b = GlyphBox.of(comp.strokes.map { k.strokes[it] })
        ci to GlyphBox(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad)
    }
    if (raw.size != 2) return raw
    val (a, b) = raw
    val (first, second) = if (a.second.cx <= b.second.cx) a to b else b to a
    return if (kotlin.math.abs(first.second.cx - second.second.cx) >= kotlin.math.abs(first.second.cy - second.second.cy)) {
        val mid = (first.second.right + second.second.left) / 2
        listOf(first.first to first.second.copy(right = minOf(first.second.right, mid)), second.first to second.second.copy(left = maxOf(second.second.left, mid)))
    } else {
        val (top, bottom) = if (a.second.cy <= b.second.cy) a to b else b to a
        val mid = (top.second.bottom + bottom.second.top) / 2
        listOf(top.first to top.second.copy(bottom = minOf(top.second.bottom, mid)), bottom.first to bottom.second.copy(top = maxOf(bottom.second.top, mid)))
    }
}

@Composable
private fun HitBox(b: GlyphBox, size: Dp, state: HitState, onTap: () -> Unit) {
    val c = Ren.colors
    val k = size / GLYPH_BOX
    val border by animateColorAsState(
        when (state) { HitState.Right -> c.radical; HitState.Wrong -> c.amber; HitState.Idle -> c.ink3 }, label = "hit",
    )
    val fill = when (state) { HitState.Right -> c.soft(c.radical, 0.16f); else -> Color.Transparent }
    Box(
        Modifier
            .offset(x = k * b.left, y = k * b.top)
            .size(width = k * (b.right - b.left), height = k * (b.bottom - b.top))
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .border(if (state == HitState.Idle) 1.5.dp else 2.dp, border, RoundedCornerShape(10.dp))
            .pressable(scaleDown = 0.97f, onClick = onTap),
    )
}

// ------------------------------------------------------------------ kana

@Composable
private fun MeetKanaStep(step: LessonStep.MeetKana, onSpeak: (String) -> Unit) {
    val c = Ren.colors
    val kana = step.kana
    Card(padding = PaddingValues(20.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StrokeGlyph(rememberStrokePaths(kana.strokes), Modifier.size(132.dp), width = 5.4f, colorOf = { c.ink })
            if (step.showRomaji) Tag(kana.romaji, c.radical, Modifier.align(Alignment.TopEnd))
        }
        VSpace(12.dp)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SpeakerButton(size = 40.dp) { onSpeak(kana.character) } }
        VSpace(14.dp)
        Txt(kana.mnemonic, Ren.type.body, color = c.ink2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
    VSpace(14.dp)
    Card(Modifier.pressable { onSpeak(kana.exampleWord) }, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(kana.exampleWord, Ren.type.title3.jp(20.sp, FontWeight.Medium))
            HSpace(12.dp)
            Txt(kana.exampleMeaning, Ren.type.callout, color = c.ink2, modifier = Modifier.weight(1f))
            CircleIconButton(RenIcons.Speaker, "Hear ${kana.exampleWord}", background = c.soft(c.radical), tint = c.radical, size = 36.dp) {
                onSpeak(kana.exampleWord)
            }
        }
    }
}

// ------------------------------------------------------------------ §7 verb pair

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearnPairStep(step: LessonStep.LearnPair, onSpeak: (String) -> Unit) {
    val c = Ren.colors
    val p = step.pair
    Card(padding = PaddingValues(vertical = 22.dp, horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().pressable { onSpeak(step.vocab.reading) }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Txt(p.noun, Ren.type.largeTitle.jp(36.sp, FontWeight.Bold))
            Txt(
                p.particle, Ren.type.largeTitle.jp(36.sp, FontWeight.Bold), color = c.akane,
                modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp)).background(c.soft(c.akane, 0.14f)).padding(horizontal = 6.dp),
            )
            Txt(p.verb, Ren.type.largeTitle.jp(36.sp, FontWeight.Bold))
        }
        VSpace(10.dp)
        Txt(step.vocab.meaning, Ren.type.title3.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        VSpace(16.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            PairChip(p.noun, p.nounGloss, c.ink)
            PairChip(p.particle, p.particleRole, c.akane)
            PairChip(p.verb, p.verbGloss, c.ink)
        }
        VSpace(14.dp)
        Txt(p.note, Ren.type.footnote, color = c.ink2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
    VSpace(14.dp)
    Card(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RubySentence(step.vocab.exampleJp, Ren.type.readJp.copy(fontSize = 17.sp))
                VSpace(4.dp)
                Txt(step.vocab.exampleEn, Ren.type.footnote, color = c.ink2)
            }
            SpeakerButton { onSpeak(RubyMarkup.reading(step.vocab.exampleJp)) }
        }
    }
}

@Composable
private fun PairChip(text: String, gloss: String, color: Color) {
    val c = Ren.colors
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(10.dp)).background(if (color == c.akane) c.soft(c.akane, 0.12f) else c.fill)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(text, Ren.type.body.jp(16.sp, FontWeight.Medium), color = color, maxLines = 1)
        Txt(gloss, Ren.type.caption2, color = if (color == c.akane) c.akane else c.ink2, maxLines = 1)
    }
}
