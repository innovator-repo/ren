package app.ren.screens

import app.ren.data.local.CollocationEntity
import app.ren.data.local.FSRSCardEntity
import app.ren.data.local.KanaEntity
import app.ren.data.local.KanjiEntity
import app.ren.data.local.LessonUnitEntity
import app.ren.data.local.RadicalEntity
import app.ren.data.local.StoryEntity
import app.ren.data.local.VocabularyEntity
import app.ren.data.repository.toDomain
import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.StudyCard
import app.ren.domain.repository.ContentLibrary
import java.io.File
import java.sql.DriverManager
import java.sql.ResultSet

/** Reads the real bundled seed database over JDBC so screenshots show genuine curriculum content. */
object SampleData {
    const val NOW = 1_759_300_000_000L
    private const val DAY = FSRSScheduler.DAY_MS

    private val dbFile: File = listOf("src/main/assets/database/ren_core.db", "app/src/main/assets/database/ren_core.db")
        .map(::File).first { it.exists() }

    private fun <T> query(sql: String, map: (ResultSet) -> T): List<T> =
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery(sql)
                buildList { while (rs.next()) add(map(rs)) }
            }
        }

    private fun ResultSet.s(c: String) = getString(c) ?: ""
    private fun ResultSet.i(c: String) = getInt(c)

    val library: ContentLibrary by lazy {
        ContentLibrary(
            kana = query("SELECT * FROM kana") {
                KanaEntity(it.s("id"), it.s("character"), it.s("romaji"), it.s("script"), it.i("strokeCount"), it.s("strokePathsSvg"), it.s("mnemonic"), it.s("exampleWord"), it.s("exampleMeaning")).toDomain()
            }.associateBy { it.id },
            radicals = query("SELECT * FROM radicals") {
                RadicalEntity(it.s("id"), it.s("character"), it.s("englishMeaning"), it.i("strokeCount"), it.s("strokePathsSvg"), it.s("mnemonicStory"), it.s("visualAssetPath")).toDomain()
            }.associateBy { it.id },
            kanji = query("SELECT * FROM kanji") {
                KanjiEntity(it.s("id"), it.s("character"), it.s("primaryMeaning"), it.s("onyomi"), it.s("kunyomi"), it.i("strokeCount"), it.s("strokePathsSvg"), it.s("radicalIds"), it.s("mnemonicEquation"), it.i("jlptLevel"), it.s("mnemonicStory"), it.s("componentsJson")).toDomain()
            }.associateBy { it.id },
            vocab = query("SELECT * FROM vocabulary") {
                VocabularyEntity(it.s("id"), it.s("text"), it.s("reading"), it.i("pitchAccentPattern"), it.s("englishMeaning"), it.s("kanjiIds"), it.s("audioAssetPath"), it.s("exampleSentenceJp"), it.s("exampleSentenceEn"), it.s("cardType")).toDomain()
            }.associateBy { it.id },
            collocations = query("SELECT * FROM collocations") {
                CollocationEntity(it.s("vocabId"), it.s("noun"), it.s("nounReading"), it.s("nounGloss"), it.s("particle"), it.s("particleRole"), it.s("verb"), it.s("verbReading"), it.s("verbGloss"), it.s("note")).toDomain()
            }.associateBy { it.vocabId },
            mined = emptyMap(),
            stories = query("SELECT * FROM stories ORDER BY sequence") {
                StoryEntity(it.s("id"), it.s("title"), it.s("titleEn"), it.s("category"), it.s("iconChar"), it.s("tint"), it.i("durationSec"), it.s("audioAssetPath"), it.s("sentencesJson"), it.i("sequence")).toDomain()
            },
        )
    }

    val units: List<LessonUnit> by lazy {
        query("SELECT * FROM lesson_units ORDER BY stage, sequence") {
            LessonUnitEntity(it.s("id"), it.s("type"), it.i("stage"), it.i("sequence"), it.s("title"), it.s("itemIds"), null).toDomain()
        }
    }

    private val freshCards: List<StudyCard> by lazy {
        query("SELECT * FROM fsrs_cards") {
            FSRSCardEntity(it.s("itemId"), it.s("itemType"), it.i("state"), it.getDouble("stability"), it.getDouble("difficulty"), it.i("elapsedDays"), it.i("scheduledDays"), it.i("reps"), it.i("lapses"), it.getLong("lastReviewTimestamp"), it.getLong("dueTimestamp"), it.s("unitId"), it.i("stage"), it.i("sequence")).toDomain()
        }
    }

    fun unitFor(char: String): LessonUnit = units.first { it.title == char && it.type.name == "KANJI" }

    /**
     * Day 34 of the path, like the prototype: kana and radicals done, 36 kanji in, 休 mature at 34 days,
     * 好 and 明 still to learn today.
     */
    val day34: Map<String, StudyCard> by lazy {
        val stage2 = units.filter { it.stage == 2 }
        val stop = stage2.indexOfFirst { it.title == "好" }
        val doneUnits = units.filter { it.stage < 2 }.map { it.id }.toSet() + stage2.take(stop).map { it.id } +
            stage2.filter { it.title in setOf("林", "森", "体") }.map { it.id }
        val learning = setOf("森", "体", "今", "何")
        freshCards.associate { c ->
            val introduced = c.unitId in doneUnits
            val glyph = library.glyph(c.itemId)
            val stability = when {
                !introduced -> 0.0
                glyph == "休" -> 34.0
                glyph in learning -> 6.0
                c.stage < 2 -> 40.0 + (c.sequence % 20)
                else -> 10.0 + (c.sequence % 30)
            }
            c.itemId to if (!introduced) c else c.copy(
                fsrs = c.fsrs.copy(
                    state = CardState.Review, stability = stability, difficulty = 5.0, reps = 6,
                    lastReview = NOW - DAY * 3, due = if (c.sequence % 9 == 0) NOW - 1 else NOW + DAY * 4,
                ),
            )
        }
    }

    /** Day 70: every Foundation kanji is in, Intermediate not yet. */
    val day70: Map<String, StudyCard> by lazy {
        freshCards.associate { c ->
            c.itemId to if (c.unitId.isEmpty() || c.stage > 2) c else c.copy(
                fsrs = c.fsrs.copy(state = CardState.Review, stability = 12.0 + c.sequence % 25, difficulty = 5.0, reps = 5, lastReview = NOW - DAY, due = NOW + DAY),
            )
        }
    }

    val completedUnits: List<LessonUnit> by lazy {
        val introduced = day34.values.filter { it.isIntroduced }.map { it.unitId }.toSet()
        units.map { if (it.id in introduced) it.copy(completedAt = NOW - DAY) else it }
    }
}
