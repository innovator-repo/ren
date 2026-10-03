package app.ren.domain.repository

import app.ren.domain.fsrs.FsrsCard
import app.ren.domain.fsrs.Rating
import app.ren.domain.model.Collocation
import app.ren.domain.model.Kana
import app.ren.domain.model.Kanji
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.MinedSentence
import app.ren.domain.model.Radical
import app.ren.domain.model.ReviewLog
import app.ren.domain.model.Story
import app.ren.domain.model.StudyCard
import app.ren.domain.model.UserPreferences
import app.ren.domain.model.Vocab
import kotlinx.coroutines.flow.Flow

/** Everything the learner can study, indexed for fast lookups. Small enough to keep in memory. */
data class ContentLibrary(
    val kana: Map<String, Kana>,
    val radicals: Map<String, Radical>,
    val kanji: Map<String, Kanji>,
    val vocab: Map<String, Vocab>,
    val collocations: Map<String, Collocation>,
    val mined: Map<String, MinedSentence>,
    val stories: List<Story>,
) {
    val kanjiByChar: Map<String, Kanji> = kanji.values.associateBy { it.character }
    val radicalByChar: Map<String, Radical> = radicals.values.associateBy { it.character }
    val vocabByText: Map<String, Vocab> = vocab.values.associateBy { it.text }

    /** The glyph that represents an item on tiles and graph nodes. */
    fun glyph(itemId: String): String = kana[itemId]?.character ?: radicals[itemId]?.character
        ?: kanji[itemId]?.character ?: vocab[itemId]?.text ?: "?"

    fun meaning(itemId: String): String = kana[itemId]?.romaji ?: radicals[itemId]?.meaning
        ?: kanji[itemId]?.meaning ?: vocab[itemId]?.meaning ?: ""

    /** Meaning of a kanji component, which may be a radical or an earlier kanji. */
    fun partMeaning(id: String?): String? = id?.let { radicals[it]?.meaning ?: kanji[it]?.meaning }
    fun partGlyph(id: String?): String? = id?.let { radicals[it]?.character ?: kanji[it]?.character }

    companion object {
        val Empty = ContentLibrary(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList())
    }
}

interface ContentRepository {
    /** Emits again when the learner mines a new sentence. */
    val library: Flow<ContentLibrary>
    suspend fun current(): ContentLibrary
}

interface StudyRepository {
    fun observeCards(): Flow<List<StudyCard>>
    fun observeUnits(): Flow<List<LessonUnit>>
    fun observeLogsSince(since: Long): Flow<List<ReviewLog>>
    suspend fun cards(ids: List<String>): List<StudyCard>
    suspend fun unit(id: String): LessonUnit?
    suspend fun countDue(now: Long): Int
    suspend fun recordReview(card: StudyCard, updated: FsrsCard, rating: Rating, durationMs: Long, now: Long)
    suspend fun completeUnit(unit: LessonUnit, now: Long)
    /** Puts a single reader word straight into the queue (the word sheet's "Add to my reviews"). */
    suspend fun introduce(itemId: String, now: Long)
}

interface MiningRepository {
    /** Saves a sentence fragment from a story as its own audio-first card; returns the new vocab id. */
    suspend fun mine(storyId: String, sentenceIndex: Int, fragmentMarkup: String, english: String, audioFirst: Boolean, now: Long): String
    suspend fun remove(vocabId: String)
    suspend fun setAudioFirst(vocabId: String, audioFirst: Boolean)
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun current(): UserPreferences
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
    /** Rolls the streak forward and adds study time for today. */
    suspend fun recordActivity(now: Long, studyMs: Long)
}
