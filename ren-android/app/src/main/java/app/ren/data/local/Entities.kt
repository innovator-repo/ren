package app.ren.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Content tables follow spec §9 field for field. Ren adds a few columns the spec leaves implicit:
 * curriculum order (stage, sequence), KanjiVG component groups for the radical slider, and the
 * kana, collocation, mined-sentence, story, unit and review-log tables that §4, §7 and the design need.
 * Path strings are SVG path data in KanjiVG's 109×109 box, one stroke per line.
 */

@Entity(tableName = "kana")
data class KanaEntity(
    @PrimaryKey val id: String,
    val character: String,
    val romaji: String,
    val script: String,
    val strokeCount: Int,
    val strokePathsSvg: String,
    val mnemonic: String,
    val exampleWord: String,
    val exampleMeaning: String,
)

@Entity(tableName = "radicals")
data class RadicalEntity(
    @PrimaryKey val id: String,
    val character: String,
    val englishMeaning: String,
    val strokeCount: Int,
    val strokePathsSvg: String,
    val mnemonicStory: String,
    val visualAssetPath: String,
)

@Entity(tableName = "kanji")
data class KanjiEntity(
    @PrimaryKey val id: String,
    val character: String,
    val primaryMeaning: String,
    val onyomi: String,
    val kunyomi: String,
    val strokeCount: Int,
    val strokePathsSvg: String,
    val radicalIds: String,
    val mnemonicEquation: String,
    val jlptLevel: Int,
    val mnemonicStory: String,
    val componentsJson: String,
)

@Entity(tableName = "vocabulary")
data class VocabularyEntity(
    @PrimaryKey val id: String,
    val text: String,
    val reading: String,
    val pitchAccentPattern: Int,
    val englishMeaning: String,
    val kanjiIds: String,
    val audioAssetPath: String,
    val exampleSentenceJp: String,
    val exampleSentenceEn: String,
    val cardType: String = "SINGLE_WORD",
)

@Entity(tableName = "collocations")
data class CollocationEntity(
    @PrimaryKey val vocabId: String,
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

@Entity(tableName = "mined_sentences")
data class MinedSentenceEntity(
    @PrimaryKey val vocabId: String,
    val storyId: String,
    val sentenceIndex: Int,
    val audioFirst: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleEn: String,
    val category: String,
    val iconChar: String,
    val tint: String,
    val durationSec: Int,
    val audioAssetPath: String,
    val sentencesJson: String,
    val sequence: Int,
)

@Entity(tableName = "lesson_units", indices = [Index("stage", "sequence")])
data class LessonUnitEntity(
    @PrimaryKey val id: String,
    val type: String,
    val stage: Int,
    val sequence: Int,
    val title: String,
    val itemIds: String,
    val completedAt: Long?,
)

@Entity(tableName = "fsrs_cards", indices = [Index("dueTimestamp"), Index("unitId")])
data class FSRSCardEntity(
    @PrimaryKey val itemId: String,
    val itemType: String,
    val state: Int,
    val stability: Double,
    val difficulty: Double,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val reps: Int,
    val lapses: Int,
    val lastReviewTimestamp: Long,
    val dueTimestamp: Long,
    val unitId: String,
    val stage: Int,
    val sequence: Int,
)

@Entity(tableName = "review_log", indices = [Index("reviewedAt")])
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val rating: Int,
    val stateBefore: Int,
    val reviewedAt: Long,
    val durationMs: Long,
    @ColumnInfo(defaultValue = "0") val scheduledDays: Int = 0,
)
