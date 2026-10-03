package app.ren.feature.session

import app.ren.domain.model.Japanese
import app.ren.domain.model.Kanji
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.StudyCard
import app.ren.domain.model.UnitType
import app.ren.domain.model.Vocab
import app.ren.domain.model.VocabCardType
import app.ren.domain.repository.ContentLibrary
import app.ren.domain.usecase.AnswerChecker

/** Turns a lesson unit into concrete steps (spec §6A), and cards into production prompts. */
object LessonPlanner {

    fun steps(unit: LessonUnit, lib: ContentLibrary, showRomaji: Boolean): List<LessonStep> = when (unit.type) {
        UnitType.KANJI -> kanjiSteps(unit, lib)
        UnitType.RADICAL -> radicalSteps(unit, lib)
        UnitType.KANA_GROUP -> unit.itemIds.mapNotNull { lib.kana[it] }.flatMap { k ->
            listOf(LessonStep.MeetKana(k, showRomaji), LessonStep.WriteIt(k.character, k.strokes, null, emptyList()))
        }
        UnitType.COLLOCATION -> {
            val v = lib.vocab[unit.itemIds.first()]
            val p = lib.collocations[unit.itemIds.first()]
            if (v != null && p != null) listOf(LessonStep.LearnPair(v, p)) else emptyList()
        }
    }

    private fun kanjiSteps(unit: LessonUnit, lib: ContentLibrary): List<LessonStep> {
        val k = lib.kanji[unit.itemIds.first()] ?: return emptyList()
        val words = unit.itemIds.drop(1).mapNotNull { lib.vocab[it] }
        val parts = k.components.filter { it.radicalId != null }.map { c ->
            Part(lib.partGlyph(c.radicalId) ?: c.element, lib.partMeaning(c.radicalId).orEmpty(), c.strokes, c.radicalId in lib.radicals)
        }
        val steps = mutableListOf<LessonStep>()
        val selfRadical = k.radicalIds.size == 1 && lib.radicals[k.radicalIds.first()]?.character == k.character
        if (parts.size >= 2 || (parts.size == 1 && !selfRadical)) {
            val names = parts.joinToString(" and ") { it.meaning }
            steps += LessonStep.PullApart(
                k, parts,
                if (parts.size == 2) "${k.character} is built from two pieces you already know: $names."
                else "${k.character} is built from ${parts.size} pieces: $names.",
            )
            steps += LessonStep.PictureIt(
                k.character, k.strokes, k.components, k.mnemonicStory,
                parts.map { Keyword(it.meaning, false) } + Keyword(k.meaning.substringBefore(',').trim(), true),
                audioText = words.firstOrNull()?.reading ?: k.kunyomi.firstOrNull()?.replace(".", "") ?: k.character,
            )
        } else {
            steps += LessonStep.MeetKanji(
                k,
                if (selfRadical) "You know ${k.character} as a building block. On its own it is a word too."
                else "A picture that became a character.",
            )
        }
        steps += LessonStep.WriteIt(k.character, k.strokes, strokeTip(k, lib), parts.map { it.glyph })
        words.firstOrNull { it.text.contains(k.character) }?.let { anchor ->
            steps += findStep(anchor, k, lib, words - anchor)
        }
        return steps
    }

    private fun findStep(anchor: Vocab, k: Kanji, lib: ContentLibrary, others: List<Vocab>): LessonStep.FindIt {
        val index = anchor.text.indexOf(k.character)
        val target = k.components.indexOfFirst { it.radicalId != null && it.radicalId in lib.radicals }
            .takeIf { it >= 0 && k.components.size >= 2 }
        val name = target?.let { lib.partMeaning(k.components[it].radicalId) } ?: k.meaning.substringBefore(',')
        return LessonStep.FindIt(
            word = anchor,
            kanji = k,
            targetKanjiIndex = index,
            targetComponent = target,
            targetName = name,
            otherWords = others,
            glyphStrokes = anchor.text.map { ch ->
                lib.kanjiByChar[ch.toString()]?.strokes ?: lib.kana.values.firstOrNull { it.character == ch.toString() }?.strokes ?: emptyList()
            },
            subtitle = if (target != null) "Listen, then tap the part that means $name."
            else "Listen, then tap ${k.character} inside the word.",
        )
    }

    private fun radicalSteps(unit: LessonUnit, lib: ContentLibrary): List<LessonStep> {
        val r = lib.radicals[unit.itemIds.first()] ?: return emptyList()
        val steps = mutableListOf<LessonStep>(
            LessonStep.PictureIt(
                r.character, r.strokes, emptyList(), r.mnemonic,
                listOf(Keyword(r.meaning, true)), audioText = r.character,
                subtitle = "A building block: you will meet it inside many kanji.",
            ),
            LessonStep.WriteIt(r.character, r.strokes, null, emptyList()),
        )
        return steps
    }

    /** "Person first, then tree. Left to right, top to bottom." */
    private fun strokeTip(k: Kanji, lib: ContentLibrary): String? {
        val parts = k.components.filter { it.radicalId != null }.sortedBy { it.strokes.firstOrNull() ?: 0 }
        if (parts.size < 2) return "${k.strokeCount} strokes. Top to bottom, left to right."
        val names = parts.map { lib.partMeaning(it.radicalId).orEmpty() }
        return "${names.first().replaceFirstChar { it.uppercase() }} first, then ${names.drop(1).joinToString(", then ")}. " +
            "${k.strokeCount} strokes in all."
    }

    // ------------------------------------------------------------------ review prompts

    fun prompt(card: StudyCard, lib: ContentLibrary): ReviewPrompt? {
        lib.kanji[card.itemId]?.let { k ->
            return ReviewPrompt(card, ReviewKind.DRAW, k.character, k.meaning, k.strokes, kanji = k)
        }
        lib.radicals[card.itemId]?.let { r ->
            return ReviewPrompt(card, ReviewKind.DRAW, r.character, r.meaning, r.strokes, radical = r)
        }
        lib.kana[card.itemId]?.let { k ->
            return ReviewPrompt(card, ReviewKind.DRAW_KANA, k.character, k.romaji, k.strokes, kana = k)
        }
        val v = lib.vocab[card.itemId] ?: return null
        return when (v.cardType) {
            VocabCardType.SINGLE_WORD -> {
                val kanaOnly = !Japanese.hasKanji(v.text)
                ReviewPrompt(
                    card, if (kanaOnly) ReviewKind.TYPE_WORD else ReviewKind.TYPE_READING, v.text, v.meaning,
                    vocab = v, answers = listOf(Japanese.toHiragana(v.reading)),
                )
            }
            VocabCardType.COLLOCATION -> {
                val p = lib.collocations[v.id] ?: return null
                val verb = Japanese.toHiragana(p.verbReading)
                val answers = (listOf(verb) + AnswerChecker.politeForms(verb)).flatMap { listOf(p.particle + it, p.nounReading + p.particle + it) }
                ReviewPrompt(card, ReviewKind.TYPE_PAIR, v.text, v.meaning, vocab = v, pair = p, answers = answers)
            }
            VocabCardType.MINED_SENTENCE -> {
                val m = lib.mined[v.id]
                ReviewPrompt(
                    card, if (m?.audioFirst != false) ReviewKind.LISTEN else ReviewKind.READ, v.text, v.meaning,
                    vocab = v, mined = m, answers = listOf(Japanese.toHiragana(v.reading)),
                )
            }
        }
    }
}
