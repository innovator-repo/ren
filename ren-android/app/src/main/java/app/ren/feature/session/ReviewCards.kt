package app.ren.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ren.core.designsystem.components.Card
import app.ren.core.designsystem.components.CircleIconButton
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.PartChip
import app.ren.core.designsystem.components.Pill
import app.ren.core.designsystem.components.RubySentence
import app.ren.core.designsystem.components.RubyWord
import app.ren.core.designsystem.components.SpeakerButton
import app.ren.core.designsystem.components.StrokeGlyph
import app.ren.core.designsystem.components.StrokePad
import app.ren.core.designsystem.components.Tag
import app.ren.core.designsystem.components.TextAction
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.core.designsystem.components.WordLook
import app.ren.designsystem.pressable
import app.ren.core.designsystem.components.rememberStrokePadState
import app.ren.core.designsystem.components.rememberStrokePaths
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.jp
import app.ren.domain.fsrs.Rating
import app.ren.domain.ink.StrokeMatcher
import app.ren.domain.ink.StrokeVerdict
import app.ren.domain.model.Japanese
import app.ren.domain.model.RubyMarkup
import app.ren.domain.repository.ContentLibrary

/** Eyebrow + big prompt card used by every typed review. */
@Composable
fun PromptCard(eyebrow: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, padding = PaddingValues(horizontal = 18.dp, vertical = 22.dp)) {
        Txt(eyebrow, Ren.type.caption.copy(letterSpacing = 1.sp), color = Ren.colors.ink2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        VSpace(10.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** The question half of a typed card (the keyboard lives in the session screen). */
@Composable
fun TypedPrompt(ui: ReviewUi, onSpeak: () -> Unit) {
    val c = Ren.colors
    val p = ui.prompt
    when (p.kind) {
        ReviewKind.TYPE_READING -> PromptCard("TYPE THE READING") {
            Txt(p.glyph, Ren.type.largeTitle.jp(if (p.glyph.length > 3) 38.sp else 50.sp, FontWeight.SemiBold), maxLines = 1)
            VSpace(6.dp)
            Txt(p.meaning, Ren.type.body, color = c.ink2, align = TextAlign.Center)
        }
        ReviewKind.TYPE_WORD -> PromptCard(if (Japanese.isKatakana(p.glyph.first())) "TYPE IT IN KATAKANA" else "TYPE IT IN KANA") {
            Txt(p.meaning, Ren.type.title2, align = TextAlign.Center)
            VSpace(6.dp)
            Txt("${p.glyph.length} kana", Ren.type.footnote, color = c.ink2)
        }
        ReviewKind.TYPE_PAIR -> PromptCard("COMPLETE THE PAIR") {
            Txt(p.meaning, Ren.type.title2.copy(fontWeight = FontWeight.Bold), align = TextAlign.Center)
            VSpace(14.dp)
            val pair = p.pair
            if (pair != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RubyWord(pair.noun, pair.nounReading, Ren.type.title1.jp(30.sp, FontWeight.SemiBold))
                    HSpace(6.dp)
                    Slot(44.dp); HSpace(6.dp); Slot(88.dp)
                }
                VSpace(10.dp)
                Txt("Type the particle and the verb, in kana.", Ren.type.footnote, color = c.ink2)
            }
        }
        ReviewKind.LISTEN -> PromptCard("LISTEN FIRST") {
            SpeakerButton(size = 64.dp, onClick = onSpeak)
            VSpace(12.dp)
            Txt("Type what you heard, or reveal it.", Ren.type.callout, color = c.ink2)
        }
        else -> Unit
    }
}

@Composable
private fun Slot(width: Dp) {
    val c = Ren.colors
    Box(Modifier.width(width).height(40.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, c.akane.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).background(c.soft(c.akane, 0.06f)))
}

/** Text-first mined sentence: read it, then reveal. */
@Composable
fun ReadPrompt(ui: ReviewUi) {
    PromptCard("READ IT") {
        RubySentence(ui.prompt.vocab?.exampleJp ?: ui.prompt.glyph, Ren.type.readJp.copy(fontSize = 21.sp), lookOf = { WordLook(showRuby = false) })
        VSpace(6.dp)
        Txt("Say it out loud, then reveal.", Ren.type.footnote, color = Ren.colors.ink2)
    }
}

/** Steps 5 for drawn cards: write the character from its meaning (no guide). */
@Composable
fun DrawPrompt(ui: ReviewUi, showRomaji: Boolean, onSpeak: () -> Unit, onFinished: (mistakes: Int, hints: Int) -> Unit) {
    val c = Ren.colors
    val p = ui.prompt
    val pad = rememberStrokePadState(p.card.itemId + ui.attempt, p.strokes.size)
    var hints by remember(p.card.itemId) { mutableIntStateOf(0) }
    LaunchedEffect(pad.done) { if (pad.done) onFinished(pad.mistakes, hints) }

    if (p.kind == ReviewKind.DRAW_KANA) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("WRITE WHAT YOU HEAR", Ren.type.caption.copy(letterSpacing = 1.sp), color = c.ink2)
                Txt(p.kana?.let { if (it.script.name == "KATAKANA") "Katakana" else "Hiragana" } ?: "", Ren.type.title2.copy(fontWeight = FontWeight.Bold))
            }
            if (showRomaji) Tag(p.meaning, c.radical, Modifier.padding(end = 10.dp))
            SpeakerButton(size = 40.dp, onClick = onSpeak)
        }
    } else {
        Txt(if (p.radical != null) "WRITE THE RADICAL" else "WRITE THE KANJI", Ren.type.caption.copy(letterSpacing = 1.sp), color = c.ink2)
        Txt(p.meaning, Ren.type.title2, maxLines = 2)
        p.kanji?.let { k ->
            val kun = k.kunyomi.firstOrNull()?.replace(".", "")?.trimStart('-')
            val on = k.onyomi.firstOrNull()
            Txt(listOfNotNull(kun, on).joinToString("  ·  "), Ren.type.callout, color = c.ink2)
        }
    }
    VSpace(14.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        StrokePad(p.strokes, pad, showGuide = ui.assist, lenient = true)
    }
    VSpace(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val v = pad.lastVerdict
        when {
            v == StrokeVerdict.Good -> Pill("Stroke ${pad.completed} of ${p.strokes.size}", icon = RenIcons.Check, iconTint = c.bamboo, color = c.bamboo)
            v != null -> Pill(StrokeMatcher.hint(v), icon = RenIcons.Info, iconTint = c.amber, color = c.amber)
            else -> Pill("${p.strokes.size} strokes", bold = false, color = c.ink2)
        }
        Box(Modifier.weight(1f))
        TextAction(if (pad.hintShown) "Hint shown" else "Hint", bold = false, color = if (pad.hintShown) c.ink2 else c.radical) {
            if (!pad.hintShown) { pad.hintShown = true; hints++ }
        }
        HSpace(8.dp)
        CircleIconButton(RenIcons.Undo, "Undo last stroke", size = 32.dp, iconSize = 15.dp, onClick = pad::undo)
    }
}

/** Step 6: what it was, and four honest answers that set the next review (spec §8). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(ui: ReviewUi, lib: ContentLibrary, onSpeak: () -> Unit) {
    val c = Ren.colors
    val p = ui.prompt
    val phase = ui.phase
    val correct = (phase as? ReviewPhase.Result)?.correct ?: true
    val revealed = phase is ReviewPhase.Revealed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (revealed) c.radical else if (correct) c.bamboo else c.amber), Alignment.Center) {
            RenIcon(if (revealed) RenIcons.Eye else if (correct) RenIcons.Check else RenIcons.Repeat, c.onAccent, size = 18.dp)
        }
        HSpace(10.dp)
        Txt(
            if (revealed) "Here it is" else if (correct) "Correct" else "Not quite",
            Ren.type.title2.copy(fontWeight = FontWeight.Bold), color = if (revealed) c.radical else if (correct) c.bamboo else c.amber,
        )
    }
    VSpace(14.dp)
    Card(padding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val v = p.vocab
            when {
                v != null && (p.kind == ReviewKind.LISTEN || p.kind == ReviewKind.READ) -> {
                    RubySentence(v.exampleJp, Ren.type.readJp.copy(fontSize = 21.sp))
                    VSpace(8.dp)
                    Txt(v.meaning, Ren.type.body, color = c.ink2, align = TextAlign.Center)
                }
                v != null -> {
                    Txt(v.text, Ren.type.largeTitle.jp(if (v.text.length > 3) 36.sp else 46.sp, FontWeight.SemiBold), maxLines = 1)
                    Txt(v.reading, Ren.type.title3.jp(18.sp), color = c.ink)
                    Txt(v.meaning, Ren.type.body, color = c.ink2, align = TextAlign.Center)
                }
                else -> {
                    StrokeGlyph(rememberStrokePaths(p.strokes), Modifier.size(96.dp), colorOf = { c.ink })
                    Txt(p.meaning, Ren.type.title3.copy(fontWeight = FontWeight.Bold))
                    p.kanji?.let { k ->
                        VSpace(4.dp)
                        Txt(
                            (k.kunyomi.take(2).map { it.replace(".", "") } + k.onyomi.take(2)).joinToString("  ·  "),
                            Ren.type.callout, color = c.ink2,
                        )
                    }
                    p.kana?.let { Txt(it.exampleWord + "  " + it.exampleMeaning, Ren.type.callout, color = c.ink2) }
                }
            }
            val typed = (phase as? ReviewPhase.Result)?.typed ?: (phase as? ReviewPhase.Revealed)?.typed
            if (!typed.isNullOrBlank() && !correct) {
                VSpace(6.dp)
                Txt("You typed $typed", Ren.type.footnote.copy(fontWeight = FontWeight.Medium), color = c.amber)
            }
            val parts = partsFor(p, lib)
            if (parts.isNotEmpty()) {
                VSpace(14.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { (g, m) -> PartChip(g, m) }
                }
            }
        }
    }
    val v = p.vocab
    val example = when {
        v != null && v.exampleJp.isNotBlank() && p.kind != ReviewKind.LISTEN && p.kind != ReviewKind.READ -> v.exampleJp to v.exampleEn
        p.kanji != null -> lib.vocab.values.firstOrNull { it.kanjiIds.contains(p.kanji.id) && it.exampleJp.isNotBlank() }?.let { it.exampleJp to it.exampleEn }
        else -> null
    }
    if (example != null) {
        VSpace(12.dp)
        Card(Modifier.pressable(onClick = onSpeak), padding = PaddingValues(16.dp)) {
            RubySentence(example.first, Ren.type.readJp.copy(fontSize = 17.sp))
            VSpace(4.dp)
            Txt(example.second, Ren.type.footnote, color = c.ink2)
        }
    }
}

private fun partsFor(p: ReviewPrompt, lib: ContentLibrary): List<Pair<String, String>> {
    val kanjiIds = p.kanji?.let { listOf(it.id) } ?: p.vocab?.kanjiIds.orEmpty()
    return kanjiIds.flatMap { id ->
        val k = lib.kanji[id] ?: return@flatMap emptyList()
        if (k.radicalIds.size >= 2) k.radicalIds.mapNotNull { r -> lib.partGlyph(r)?.let { it to lib.partMeaning(r).orEmpty() } }
        else listOf(k.character to k.meaning.substringBefore(','))
    }.distinct().take(5)
}

/** The four honest answers, each with the interval it would set. */
@Composable
fun RatingBar(intervals: Map<Rating, String>, suggested: Rating?, onRate: (Rating) -> Unit) {
    val c = Ren.colors
    Column(Modifier.fillMaxWidth()) {
        Txt("How did that feel?", Ren.type.headline, color = c.ink2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        VSpace(12.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Rating.Again to c.akane, Rating.Hard to c.amber, Rating.Good to c.bamboo, Rating.Easy to c.radical).forEach { (r, color) ->
                RateButton(r, intervals[r].orEmpty(), color, r == suggested, Modifier.weight(1f)) { onRate(r) }
            }
        }
    }
}

@Composable
private fun RateButton(rating: Rating, interval: String, color: Color, suggested: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ren.colors
    Column(
        modifier
            .height(62.dp)
            .pressable(label = "${rating.name}, next review in $interval", scaleDown = 0.94f, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(c.group)
            .border(if (suggested) 2.dp else 0.dp, if (suggested) color.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(22.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        VSpace(8.dp)
        Txt(rating.name, Ren.type.subhead.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        Txt(interval, Ren.type.caption.copy(fontWeight = FontWeight.Medium), color = c.ink2, maxLines = 1)
    }
}

fun ReviewPrompt.speakText(): String = vocab?.let { RubyMarkup.reading(it.exampleJp).ifBlank { it.reading } } ?: glyph
