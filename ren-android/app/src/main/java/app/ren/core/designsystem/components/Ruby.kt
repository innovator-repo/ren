package app.ren.core.designsystem.components

import app.ren.designsystem.pressable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ren.core.designsystem.theme.Ren
import app.ren.domain.model.Japanese
import app.ren.domain.model.RubyMarkup
import app.ren.domain.model.Segment

/** How a word token should look inside a sentence. */
data class WordLook(
    val showRuby: Boolean,
    val highlight: Color? = null,
    val underline: Color? = null,
    val color: Color? = null,
)

/**
 * One word with furigana sitting only over its kanji (spec §2 furigana: 10–11sp, centred).
 * Every token reserves the ruby line, so mixed runs stay on one baseline.
 */
@Composable
fun RubyWord(
    base: String,
    reading: String?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    look: WordLook = WordLook(showRuby = true),
    rubyStyle: TextStyle = Ren.type.furigana,
) {
    val c = Ren.colors
    // Ruby needs a line box a little taller than its glyphs, or BasicText clips the tops.
    val rubyStyle = rubyStyle.copy(lineHeight = rubyStyle.fontSize * 1.2f)
    val rubyHeight = with(LocalDensity.current) { rubyStyle.lineHeight.toDp() }
    val pairs = if (reading != null) Japanese.alignRuby(base, reading) else listOf(base to null)
    val dash = with(LocalDensity.current) { 2.dp.toPx() }
    Row(
        modifier
            .then(if (look.highlight != null) Modifier.background(look.highlight, RoundedCornerShape(4.dp)) else Modifier)
            .drawBehind {
                val u = look.underline ?: return@drawBehind
                drawLine(
                    u, Offset(0f, size.height - 1.5f), Offset(size.width, size.height - 1.5f), strokeWidth = 1.6f * density,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                )
            },
    ) {
        for ((text, ruby) in pairs) {
            RubyPair(text, if (look.showRuby) ruby else null, rubyHeight, style, rubyStyle, look.color ?: c.ink, c.ink2)
        }
    }
}

/**
 * Base text with its reading centred above. Ruby wider than its base may overhang a little,
 * as in printed Japanese, instead of pushing the sentence apart.
 */
@Composable
private fun RubyPair(text: String, ruby: String?, rubyHeight: Dp, style: TextStyle, rubyStyle: TextStyle, color: Color, rubyColor: Color) {
    Layout(
        content = {
            Box(Modifier.height(rubyHeight), contentAlignment = Alignment.BottomCenter) {
                if (ruby != null) Txt(ruby, rubyStyle, color = rubyColor, maxLines = 1)
            }
            Txt(text, style, color = color, maxLines = 1)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val top = measurables[0].measure(loose)
        val base = measurables[1].measure(loose)
        val width = maxOf(base.width, (top.width * 0.72f).toInt())
        layout(width, top.height + base.height) {
            top.place((width - top.width) / 2, 0)
            base.place((width - base.width) / 2, top.height)
        }
    }
}

private const val CLOSING = "、。，．」』）！？!?）〉》】ー"
private const val OPENING = "「『（〈《【"

/** Sentence pieces after keeping closing punctuation on the previous piece (kinsoku shori). */
private sealed interface Piece {
    data class Word(val seg: Segment.Word, val index: Int, var prefix: String = "", var suffix: String = "") : Piece
    data class Chars(var text: String) : Piece
    data object Gap : Piece
}

private fun pieces(segments: List<Segment>): List<Piece> {
    val out = mutableListOf<Piece>()
    var prefix = ""
    var wordIndex = 0
    for (seg in segments) {
        when (seg) {
            is Segment.Word -> { out += Piece.Word(seg, wordIndex++, prefix); prefix = "" }
            is Segment.Plain -> for (ch in seg.text) {
                when {
                    ch == ' ' || ch == '　' -> out += Piece.Gap
                    ch in CLOSING && out.isNotEmpty() && out.last() !is Piece.Gap -> when (val last = out.last()) {
                        is Piece.Word -> last.suffix += ch
                        is Piece.Chars -> last.text += ch
                        Piece.Gap -> Unit
                    }
                    ch in OPENING -> prefix += ch
                    else -> { out += Piece.Chars(prefix + ch); prefix = "" }
                }
            }
        }
    }
    if (prefix.isNotEmpty()) out += Piece.Chars(prefix)
    return out
}

/**
 * A sentence in ruby markup, wrapped token by token. [lookOf] decides furigana and highlight per word;
 * [onWord] makes words tappable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RubySentence(
    markup: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    lookOf: (Segment.Word) -> WordLook = { WordLook(showRuby = true) },
    plainColor: Color = Ren.colors.ink,
    onWord: ((Segment.Word, Int) -> Unit)? = null,
    tokenModifier: (Int) -> Modifier = { Modifier },
    lookByIndex: ((Int) -> WordLook)? = null,
) {
    val all = remember(markup) { pieces(RubyMarkup.parse(markup)) }
    val rubyHeight = with(LocalDensity.current) { Ren.type.furigana.lineHeight.toDp() }
    val gap = with(LocalDensity.current) { (style.fontSize.toDp() * 0.3f) }
    FlowRow(modifier, horizontalArrangement = Arrangement.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (piece in all) {
            when (piece) {
                is Piece.Word -> Row {
                    if (piece.prefix.isNotEmpty()) PlainPiece(piece.prefix, rubyHeight, style, plainColor)
                    RubyWord(
                        piece.seg.text, piece.seg.reading, style, look = lookByIndex?.invoke(piece.index) ?: lookOf(piece.seg),
                        modifier = tokenModifier(piece.index).then(
                            if (onWord != null) Modifier.pressable(scaleDown = 0.97f) { onWord(piece.seg, piece.index) } else Modifier,
                        ),
                    )
                    if (piece.suffix.isNotEmpty()) PlainPiece(piece.suffix, rubyHeight, style, plainColor)
                }
                is Piece.Chars -> PlainPiece(piece.text, rubyHeight, style, plainColor)
                Piece.Gap -> Spacer(Modifier.width(gap))
            }
        }
    }
}

@Composable
private fun PlainPiece(text: String, rubyHeight: Dp, style: TextStyle, color: Color) {
    Column {
        Spacer(Modifier.height(rubyHeight))
        Txt(text, style, color = color, maxLines = 1)
    }
}

/** Plain-text furigana label ("休日 きゅうじつ") used where a full ruby layout would be too heavy. */
@Composable
fun ReadingLine(text: String, reading: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Txt(text, Ren.type.body)
        Txt(reading, Ren.type.callout, color = Ren.colors.ink2, modifier = Modifier.padding(start = 8.dp))
    }
}
