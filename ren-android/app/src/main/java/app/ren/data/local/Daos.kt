package app.ren.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Review scheduling and progress (spec §9 StudyDao, extended). */
@Dao
interface StudyDao {
    @Query("SELECT * FROM fsrs_cards WHERE dueTimestamp <= :currentTime ORDER BY dueTimestamp ASC")
    fun getDueCards(currentTime: Long): Flow<List<FSRSCardEntity>>

    @Query("SELECT * FROM fsrs_cards WHERE state = 0 ORDER BY stage, sequence LIMIT :limit")
    suspend fun getNewCards(limit: Int): List<FSRSCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: FSRSCardEntity)

    @Query("SELECT * FROM kanji WHERE id = :id")
    suspend fun getKanjiById(id: String): KanjiEntity?

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun getVocabById(id: String): VocabularyEntity?

    @Query("SELECT * FROM fsrs_cards")
    fun observeCards(): Flow<List<FSRSCardEntity>>

    @Query("SELECT * FROM fsrs_cards WHERE itemId = :itemId")
    suspend fun getCard(itemId: String): FSRSCardEntity?

    @Query("SELECT * FROM fsrs_cards WHERE itemId IN (:ids)")
    suspend fun getCards(ids: List<String>): List<FSRSCardEntity>

    @Query("SELECT COUNT(*) FROM fsrs_cards WHERE dueTimestamp <= :currentTime")
    suspend fun countDue(currentTime: Long): Int

    @Query("SELECT * FROM lesson_units ORDER BY stage, sequence")
    fun observeUnits(): Flow<List<LessonUnitEntity>>

    @Query("SELECT * FROM lesson_units WHERE id = :id")
    suspend fun getUnit(id: String): LessonUnitEntity?

    @Query("UPDATE lesson_units SET completedAt = :at WHERE id = :id AND completedAt IS NULL")
    suspend fun markUnitComplete(id: String, at: Long)

    /** Lessons introduce cards: they enter the queue as due-now New cards. */
    @Query("UPDATE fsrs_cards SET dueTimestamp = :at WHERE itemId IN (:ids) AND dueTimestamp = 9223372036854775807")
    suspend fun introduceCards(ids: List<String>, at: Long)

    @Insert
    suspend fun insertLog(log: ReviewLogEntity)

    @Query("SELECT * FROM review_log WHERE reviewedAt >= :since ORDER BY reviewedAt")
    fun observeLogsSince(since: Long): Flow<List<ReviewLogEntity>>

    @Transaction
    suspend fun recordReview(card: FSRSCardEntity, log: ReviewLogEntity) {
        upsertCard(card)
        insertLog(log)
    }

    @Transaction
    suspend fun completeUnit(unitId: String, itemIds: List<String>, at: Long) {
        markUnitComplete(unitId, at)
        introduceCards(itemIds, at)
    }
}

/** Read-only curriculum content, plus the learner's own mined sentences. */
@Dao
interface ContentDao {
    @Query("SELECT * FROM kana") suspend fun kana(): List<KanaEntity>
    @Query("SELECT * FROM radicals") suspend fun radicals(): List<RadicalEntity>
    @Query("SELECT * FROM kanji") suspend fun kanji(): List<KanjiEntity>
    @Query("SELECT * FROM vocabulary") suspend fun vocabulary(): List<VocabularyEntity>
    @Query("SELECT * FROM vocabulary") fun observeVocabulary(): Flow<List<VocabularyEntity>>
    @Query("SELECT * FROM collocations") suspend fun collocations(): List<CollocationEntity>
    @Query("SELECT * FROM stories ORDER BY sequence") suspend fun stories(): List<StoryEntity>
    @Query("SELECT * FROM mined_sentences") fun observeMined(): Flow<List<MinedSentenceEntity>>

    @Query("UPDATE mined_sentences SET audioFirst = :audioFirst WHERE vocabId = :id")
    suspend fun setMinedAudioFirst(id: String, audioFirst: Boolean)
}

@Dao
interface MiningDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVocab(vocab: VocabularyEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMined(mined: MinedSentenceEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCard(card: FSRSCardEntity)
    @Query("DELETE FROM vocabulary WHERE id = :id") suspend fun deleteVocab(id: String)
    @Query("DELETE FROM mined_sentences WHERE vocabId = :id") suspend fun deleteMined(id: String)
    @Query("DELETE FROM fsrs_cards WHERE itemId = :id") suspend fun deleteCard(id: String)

    @Transaction
    suspend fun save(vocab: VocabularyEntity, mined: MinedSentenceEntity, card: FSRSCardEntity) {
        insertVocab(vocab)
        insertMined(mined)
        insertCard(card)
    }

    @Transaction
    suspend fun remove(id: String) {
        deleteCard(id)
        deleteMined(id)
        deleteVocab(id)
    }
}

@Database(
    entities = [
        KanaEntity::class, RadicalEntity::class, KanjiEntity::class, VocabularyEntity::class,
        CollocationEntity::class, MinedSentenceEntity::class, StoryEntity::class,
        LessonUnitEntity::class, FSRSCardEntity::class, ReviewLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RenDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao
    abstract fun contentDao(): ContentDao
    abstract fun miningDao(): MiningDao
}
