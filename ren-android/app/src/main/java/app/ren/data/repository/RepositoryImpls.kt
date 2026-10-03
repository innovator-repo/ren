package app.ren.data.repository

import androidx.datastore.core.DataStore
import app.ren.data.local.ContentDao
import app.ren.data.local.FSRSCardEntity
import app.ren.data.local.MinedSentenceEntity
import app.ren.data.local.MiningDao
import app.ren.data.local.ReviewLogEntity
import app.ren.data.local.StudyDao
import app.ren.di.ApplicationScope
import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FsrsCard
import app.ren.domain.fsrs.Rating
import app.ren.domain.model.Japanese
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.ReviewLog
import app.ren.domain.model.RubyMarkup
import app.ren.domain.model.StudyCard
import app.ren.domain.model.UserPreferences
import app.ren.domain.model.Vocab
import app.ren.domain.model.VocabCardType
import app.ren.domain.repository.ContentLibrary
import app.ren.domain.repository.ContentRepository
import app.ren.domain.repository.MiningRepository
import app.ren.domain.repository.PreferencesRepository
import app.ren.domain.repository.StudyRepository
import app.ren.domain.usecase.Clock
import app.ren.domain.usecase.Streak
import app.ren.domain.usecase.epochDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val dao: ContentDao,
    @ApplicationScope scope: CoroutineScope,
) : ContentRepository {

    private val static = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        ContentLibrary.Empty.copy(
            kana = dao.kana().map { it.toDomain() }.associateBy { it.id },
            radicals = dao.radicals().map { it.toDomain() }.associateBy { it.id },
            kanji = dao.kanji().map { it.toDomain() }.associateBy { it.id },
            collocations = dao.collocations().map { it.toDomain() }.associateBy { it.vocabId },
            stories = dao.stories().map { it.toDomain() },
        )
    }

    override val library: Flow<ContentLibrary> = flow {
        val base = static.await()
        emitAll(
            combine(dao.observeVocabulary(), dao.observeMined()) { vocab, mined ->
                base.copy(
                    vocab = vocab.map { it.toDomain() }.associateBy { it.id },
                    mined = mined.map { it.toDomain() }.associateBy { it.vocabId },
                )
            },
        )
    }.shareIn(scope, SharingStarted.WhileSubscribed(10_000), replay = 1)

    override suspend fun current(): ContentLibrary = library.first()
}

@Singleton
class StudyRepositoryImpl @Inject constructor(private val dao: StudyDao) : StudyRepository {
    override fun observeCards(): Flow<List<StudyCard>> = dao.observeCards().map { list -> list.map { it.toDomain() } }
    override fun observeUnits(): Flow<List<LessonUnit>> = dao.observeUnits().map { list -> list.map { it.toDomain() } }
    override fun observeLogsSince(since: Long): Flow<List<ReviewLog>> =
        dao.observeLogsSince(since).map { list -> list.map { it.toDomain() } }

    override suspend fun cards(ids: List<String>): List<StudyCard> {
        val byId = dao.getCards(ids).associateBy { it.itemId }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    override suspend fun unit(id: String): LessonUnit? = dao.getUnit(id)?.toDomain()
    override suspend fun countDue(now: Long): Int = dao.countDue(now)

    override suspend fun recordReview(card: StudyCard, updated: FsrsCard, rating: Rating, durationMs: Long, now: Long) {
        dao.recordReview(
            card.toEntity(updated),
            ReviewLogEntity(
                itemId = card.itemId,
                rating = rating.value,
                stateBefore = card.fsrs.state.value,
                reviewedAt = now,
                durationMs = durationMs,
                scheduledDays = updated.scheduledDays,
            ),
        )
    }

    override suspend fun completeUnit(unit: LessonUnit, now: Long) = dao.completeUnit(unit.id, unit.itemIds, now)

    override suspend fun introduce(itemId: String, now: Long) = dao.introduceCards(listOf(itemId), now)
}

@Singleton
class MiningRepositoryImpl @Inject constructor(
    private val dao: MiningDao,
    private val content: ContentDao,
    private val library: ContentRepository,
) : MiningRepository {
    override suspend fun mine(
        storyId: String,
        sentenceIndex: Int,
        fragmentMarkup: String,
        english: String,
        audioFirst: Boolean,
        now: Long,
    ): String {
        val lib = library.current()
        val id = "mined_" + UUID.randomUUID().toString().take(12)
        val text = RubyMarkup.plain(fragmentMarkup).trim()
        val kanjiIds = Japanese.kanjiIn(text).mapNotNull { lib.kanjiByChar[it]?.id }
        val vocab = Vocab(
            id = id,
            text = text,
            reading = RubyMarkup.reading(fragmentMarkup).trim(),
            pitchAccent = -1,
            meaning = english,
            kanjiIds = kanjiIds,
            audioAssetPath = "",
            exampleJp = fragmentMarkup.trim(),
            exampleEn = english,
            cardType = VocabCardType.MINED_SENTENCE,
        )
        dao.save(
            vocab.toEntity(),
            MinedSentenceEntity(id, storyId, sentenceIndex, audioFirst, now),
            FSRSCardEntity(
                itemId = id, itemType = "VOCAB", state = CardState.New.value, stability = 0.0, difficulty = 0.0,
                elapsedDays = 0, scheduledDays = 0, reps = 0, lapses = 0, lastReviewTimestamp = 0,
                dueTimestamp = now, unitId = "", stage = 3, sequence = Int.MAX_VALUE,
            ),
        )
        return id
    }

    override suspend fun remove(vocabId: String) = dao.remove(vocabId)
    override suspend fun setAudioFirst(vocabId: String, audioFirst: Boolean) = content.setMinedAudioFirst(vocabId, audioFirst)
}

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val store: DataStore<UserPreferences>,
    private val clock: Clock,
) : PreferencesRepository {
    override val preferences: Flow<UserPreferences> = store.data
    override suspend fun current(): UserPreferences = store.data.first()
    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        store.updateData(transform)
    }

    override suspend fun recordActivity(now: Long, studyMs: Long) {
        val today = clock.epochDay(now)
        store.updateData { p ->
            val withStreak = Streak.onActivity(p, today)
            val sameDay = p.studyEpochDay == today
            withStreak.copy(studyEpochDay = today, studyMsToday = (if (sameDay) p.studyMsToday else 0L) + studyMs)
        }
    }
}
