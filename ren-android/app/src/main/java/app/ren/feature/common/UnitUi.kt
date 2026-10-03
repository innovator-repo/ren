package app.ren.feature.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import app.ren.core.designsystem.components.Chevron
import app.ren.core.designsystem.components.GlyphTile
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.Script
import app.ren.domain.model.UnitType
import app.ren.domain.repository.ContentLibrary

data class PartUi(val glyph: String, val meaning: String)

/** One lesson unit as a list row: 休 / rest / 亻 person + 木 tree. */
data class UnitUi(
    val unitId: String,
    val headlineId: String,
    val glyph: String,
    val title: String,
    val parts: List<PartUi>,
    val subtitle: String?,
    val done: Boolean,
    val type: UnitType,
)

object UnitPresenter {
    fun describe(unit: LessonUnit, lib: ContentLibrary): UnitUi {
        val head = unit.itemIds.first()
        return when (unit.type) {
            UnitType.KANJI -> {
                val k = lib.kanji[head]
                val parts = k?.radicalIds?.mapNotNull { id ->
                    val g = lib.partGlyph(id) ?: return@mapNotNull null
                    PartUi(g, lib.partMeaning(id).orEmpty())
                }.orEmpty().takeIf { it.size >= 2 } ?: emptyList()
                val words = unit.itemIds.drop(1).mapNotNull { lib.vocab[it]?.text }
                UnitUi(
                    unit.id, head, k?.character ?: unit.title, k?.meaning.orEmpty(), parts,
                    if (parts.isEmpty()) words.joinToString("・").ifEmpty { null } else null, unit.isComplete, unit.type,
                )
            }
            UnitType.RADICAL -> {
                val r = lib.radicals[head]
                val uses = lib.kanji.values.count { head in it.radicalIds }
                UnitUi(unit.id, head, r?.character ?: unit.title, r?.meaning.orEmpty(), emptyList(), "Building block · in $uses kanji", unit.isComplete, unit.type)
            }
            UnitType.KANA_GROUP -> {
                val first = lib.kana[head]
                val script = if (first?.script == Script.KATAKANA) "Katakana" else "Hiragana"
                UnitUi(unit.id, head, first?.character ?: unit.title.take(1), unit.title, emptyList(), "$script · ${unit.itemIds.size} sounds", unit.isComplete, unit.type)
            }
            UnitType.COLLOCATION -> {
                val v = lib.vocab[head]
                val col = lib.collocations[head]
                UnitUi(
                    unit.id, head, col?.particle ?: "を", v?.text ?: unit.title,
                    emptyList(), "Verb pair · ${v?.meaning.orEmpty()}", unit.isComplete, unit.type,
                )
            }
        }
    }
}

/** Row content: tile, title, the radical equation in blue, and a check or chevron. */
@Composable
fun RowScope.UnitRowContent(ui: UnitUi) {
    val c = Ren.colors
    GlyphTile(ui.glyph, size = 52.dp, color = if (ui.type == UnitType.COLLOCATION) c.akane else c.ink)
    HSpace(14.dp)
    Column(Modifier.weight(1f)) {
        Txt(ui.title, Ren.type.headline, maxLines = 1)
        if (ui.parts.isNotEmpty()) {
            val line = buildAnnotatedString {
                ui.parts.forEachIndexed { i, p ->
                    if (i > 0) append("  +  ")
                    withStyle(SpanStyle(color = c.radical)) { append(p.glyph) }
                    append(" ${p.meaning}")
                }
            }
            Txt(line, Ren.type.callout, color = c.ink2, maxLines = 1)
        } else if (ui.subtitle != null) {
            Txt(ui.subtitle, Ren.type.callout, color = c.ink2, maxLines = 1)
        }
    }
    HSpace(8.dp)
    if (ui.done) DoneCheck() else Chevron()
}

@Composable
fun DoneCheck(size: Dp = 26.dp) {
    val c = Ren.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.bamboo), contentAlignment = Alignment.Center) {
        RenIcon(RenIcons.Check, c.onAccent, size = size * 0.6f)
    }
}

@Composable
fun MasteryDot(color: Color, modifier: Modifier = Modifier) =
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
