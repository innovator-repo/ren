package app.ren.domain.model

import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.fsrs.FsrsCard

/** fsrs_cards.itemType (spec §9). Collocations and mined sentences are VOCAB rows with a [VocabCardType]. */
enum class ItemType { KANA, RADICAL, KANJI, VOCAB }

/** vocabulary.cardType discriminator (spec §7). */
enum class VocabCardType { SINGLE_WORD, COLLOCATION, MINED_SENTENCE }

enum class Script { HIRAGANA, KATAKANA }

/** A lesson unit is what one "new card" on Today means: one kanji with its words, one radical, five kana… */
enum class UnitType { KANA_GROUP, RADICAL, KANJI, COLLOCATION }

/** How lit an item is: drives Matrix node colour, furigana fading and the New Today list. */
enum class Mastery { Locked, New, Learning, Mature }

enum class StoryCategory { STORY, DIALOGUE, REAL_LIFE }

/** A visual piece of a kanji as KanjiVG groups it, e.g. 亻 on the left of 休, strokes 0 and 1. */
data class Component(
    val element: String,
    val radicalId: String?,
    val position: String?,
    val strokes: List<Int>,
)

data class Kana(
    val id: String,
    val character: String,
    val romaji: String,
    val script: Script,
    val strokes: List<String>,
    val mnemonic: String,
    val exampleWord: String,
    val exampleMeaning: String,
)

data class Radical(
    val id: String,
    val character: String,
    val meaning: String,
    val strokeCount: Int,
    val strokes: List<String>,
    val mnemonic: String,
)

data class Kanji(
    val id: String,
    val character: String,
    val meaning: String,
    val onyomi: List<String>,
    val kunyomi: List<String>,
    val strokeCount: Int,
    val strokes: List<String>,
    val radicalIds: List<String>,
    val components: List<Component>,
    val mnemonicEquation: String,
    val mnemonicStory: String,
    val jlptLevel: Int,
)

data class Vocab(
    val id: String,
    val text: String,
    val reading: String,
    val pitchAccent: Int,
    val meaning: String,
    val kanjiIds: List<String>,
    val audioAssetPath: String,
    val exampleJp: String,
    val exampleEn: String,
    val cardType: VocabCardType,
)

data class Collocation(
    val vocabId: String,
    val noun: String,
    val nounReading: String,
    val nounGloss: String,
    val particle: String,
    val particleRole: String,
    val verb: String,
    val verbReading: String,
    val verbGloss: String,
    val note: String,
)

data class MinedSentence(
    val vocabId: String,
    val storyId: String,
    val sentenceIndex: Int,
    val audioFirst: Boolean,
    val createdAt: Long,
)

data class StorySentence(val jp: String, val en: String) {
    val segments: List<Segment> by lazy { RubyMarkup.parse(jp) }
    val plain: String by lazy { RubyMarkup.plain(jp) }
}

data class Story(
    val id: String,
    val title: String,
    val titleEn: String,
    val category: StoryCategory,
    val iconChar: String,
    val tint: String,
    val durationSec: Int,
    val audioAssetPath: String,
    val sentences: List<StorySentence>,
)

data class LessonUnit(
    val id: String,
    val type: UnitType,
    val stage: Int,
    val sequence: Int,
    val title: String,
    /** First id is the unit's headline item (the kanji, the radical…); the rest arrive with it. */
    val itemIds: List<String>,
    val completedAt: Long?,
) {
    val isComplete get() = completedAt != null
}

/** One row of fsrs_cards plus its curriculum placement. */
data class StudyCard(
    val itemId: String,
    val itemType: ItemType,
    val fsrs: FsrsCard,
    val unitId: String,
    val stage: Int,
    val sequence: Int,
) {
    /** Introduced cards have a real due date; untouched curriculum cards wait at Long.MAX_VALUE. */
    val isIntroduced get() = fsrs.due != NOT_INTRODUCED
    val mastery: Mastery
        get() = when {
            !isIntroduced -> Mastery.Locked
            fsrs.state == CardState.New -> Mastery.New
            fsrs.stability > FSRSScheduler.MATURE_STABILITY_DAYS -> Mastery.Mature
            else -> Mastery.Learning
        }

    companion object {
        const val NOT_INTRODUCED = Long.MAX_VALUE
    }
}

data class ReviewLog(
    val itemId: String,
    val rating: Int,
    val stateBefore: Int,
    val reviewedAt: Long,
    val durationMs: Long,
)

/** One of the four Path stages (spec §4). */
data class Stage(
    val index: Int,
    val title: String,
    val window: String,
    val description: String,
    val badge: String?,
)

object Curriculum {
    val stages = listOf(
        Stage(0, "Kana Sprint", "Days 1 to 10", "Hiragana, then Katakana through real words like コーヒー. Romaji off from day 4.", null),
        Stage(1, "Radical Roots", "Days 11 to 25", "The building blocks of every kanji, each with a picture story.", null),
        Stage(2, "Foundation Kanji", "Days 26 to 90", "Every kanji arrives with 2 or 3 words that use it.", "N5"),
        Stage(3, "Intermediate Mastery", "Day 91 on", "N4 and N3 kanji, verb pairs and real sentences. Opens once your N5 kanji are stable.", "N4"),
    )

    /** Share of the previous stage that must be graduated (stage 3 also needs a week of stability). */
    const val UNLOCK_SHARE = 0.8
    const val INTERMEDIATE_STABILITY_DAYS = 7.0

    /** Romaji helps only at the very start (spec §3 anti-patterns). */
    const val ROMAJI_LAST_DAY = 3
}
