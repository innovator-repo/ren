package app.ren.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ren.data.local.RenDatabase
import app.ren.data.repository.StudyRepositoryImpl
import app.ren.data.repository.toDomain
import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.fsrs.Rating
import app.ren.domain.model.ItemType
import app.ren.domain.model.UnitType
import app.ren.domain.model.VocabCardType
import app.ren.domain.repository.ContentLibrary
import app.ren.domain.usecase.CurriculumPlanner
import app.ren.domain.usecase.KnowledgeGraphBuilder
import app.ren.domain.usecase.StoryAnalyzer
import app.ren.feature.session.LessonPlanner
import app.ren.feature.session.LessonStep
import app.ren.feature.session.ReviewKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Opens the real bundled ren_core.db through Room exactly as the app does (createFromAsset),
 * so a schema drift between Room and tools/seed/build_seed_db.py fails here, not on a phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SeedDatabaseTest {
    private lateinit var db: RenDatabase
    private val now = 1_759_000_000_000L

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("ren_test.db")
        db = Room.databaseBuilder(context, RenDatabase::class.java, "ren_test.db")
            .createFromAsset("database/ren_core.db")
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    private suspend fun library(): ContentLibrary {
        val c = db.contentDao()
        return ContentLibrary(
            kana = c.kana().map { it.toDomain() }.associateBy { it.id },
            radicals = c.radicals().map { it.toDomain() }.associateBy { it.id },
            kanji = c.kanji().map { it.toDomain() }.associateBy { it.id },
            vocab = c.vocabulary().map { it.toDomain() }.associateBy { it.id },
            collocations = c.collocations().map { it.toDomain() }.associateBy { it.vocabId },
            mined = emptyMap(),
            stories = c.stories().map { it.toDomain() },
        )
    }

    @Test fun seedPassesRoomValidationAndHasTheCurriculum() = runTest {
        val lib = library()
        assertEquals(92, lib.kana.size)
        assertTrue(lib.radicals.size >= 50)
        assertTrue(lib.kanji.size >= 120)
        assertTrue(lib.vocab.size >= 200)
        assertEquals(10, lib.stories.size)
        val units = db.studyDao().observeUnits().first()
        val cards = db.studyDao().observeCards().first().map { it.toDomain() }
        assertEquals(setOf(0, 1, 2, 3), units.map { it.stage }.toSet())
        assertTrue("fresh install has nothing introduced", cards.none { it.isIntroduced })
        // Every unit's items have a card.
        val cardIds = cards.map { it.itemId }.toSet()
        units.forEach { u -> u.toDomain().itemIds.forEach { assertTrue("$it", it in cardIds) } }
    }

    @Test fun kyuuHasRadicalComponentsAndAFullLesson() = runTest {
        val lib = library()
        val kyuu = lib.kanjiByChar.getValue("休")
        assertEquals(listOf("亻", "木"), kyuu.components.map { it.element })
        assertEquals(6, kyuu.strokes.size)
        val unit = db.studyDao().getUnit("unit_${kyuu.id}")!!.toDomain()
        val steps = LessonPlanner.steps(unit, lib, showRomaji = false)
        assertEquals(
            listOf(LessonStep.PullApart::class, LessonStep.PictureIt::class, LessonStep.WriteIt::class, LessonStep.FindIt::class),
            steps.map { it::class },
        )
        val find = steps.last() as LessonStep.FindIt
        assertEquals("person", find.targetName)
        assertEquals("休日", find.word.text)
    }

    @Test fun everyUnitPlansAndEveryCardHasAPrompt() = runTest {
        val lib = library()
        val units = db.studyDao().observeUnits().first().map { it.toDomain() }
        for (u in units) assertTrue("no steps for ${u.title}", LessonPlanner.steps(u, lib, false).isNotEmpty())
        val cards = db.studyDao().observeCards().first().map { it.toDomain() }
        for (c in cards) assertNotNull("no prompt for ${c.itemId}", LessonPlanner.prompt(c, lib))
        val pair = cards.first { lib.vocab[it.itemId]?.cardType == VocabCardType.COLLOCATION && lib.vocab[it.itemId]?.text == "電車に乗る" }
        val prompt = LessonPlanner.prompt(pair, lib)!!
        assertEquals(ReviewKind.TYPE_PAIR, prompt.kind)
        assertTrue("にのる" in prompt.answers && "にのります" in prompt.answers)
    }

    @Test fun firstLessonThenReviewsGraduateAndUnlockNothingEarly() = runTest {
        val study = StudyRepositoryImpl(db.studyDao())
        val units = study.observeUnits().first()
        val first = units.first()
        assertEquals(UnitType.KANA_GROUP, first.type)
        study.completeUnit(first, now)

        var cards = study.observeCards().first()
        val due = cards.filter { it.isIntroduced && it.fsrs.due <= now }
        assertEquals(5, due.size)
        assertTrue(due.all { it.itemType == ItemType.KANA })

        // Two good answers move each card from New through Learning into Review.
        var t = now
        for (card in due) {
            val learning = FSRSScheduler.schedule(card.fsrs, Rating.Good, t)
            study.recordReview(card, learning, Rating.Good, 4_000, t)
            val fresh = study.cards(listOf(card.itemId)).first()
            val graduated = FSRSScheduler.schedule(fresh.fsrs, Rating.Good, learning.due)
            study.recordReview(fresh, graduated, Rating.Good, 3_000, learning.due)
            t += 1_000
        }
        cards = study.observeCards().first()
        assertTrue(cards.filter { it.unitId == first.id }.all { it.fsrs.state == CardState.Review })
        val logs = study.observeLogsSince(now - 1).first()
        assertEquals(10, logs.size)

        val progress = CurriculumPlanner.stageProgress(study.observeUnits().first(), cards, unlockAll = false)
        assertFalse("radicals stay locked until all kana are done", progress[1].unlocked)
        val next = CurriculumPlanner.nextUnits(study.observeUnits().first(), progress, 3)
        assertEquals(3, next.size)
        assertTrue(next.all { it.stage == 0 })
    }

    @Test fun readerGatesStoriesOnWhatIsKnown() = runTest {
        val lib = library()
        val cards = db.studyDao().observeCards().first().map { it.toDomain() }.associateBy { it.itemId }
        val hello = lib.stories.first { it.id == "hajimemashite" }
        assertTrue(StoryAnalyzer.analyze(hello, lib, cards).locked)
        val konbini = StoryAnalyzer.analyze(lib.stories.first { it.id == "konbini" }, lib, cards)
        assertTrue(konbini.missingKanji.size > 1)
        // The knowledge graph previews locked kanji for a brand-new learner.
        val graph = KnowledgeGraphBuilder.build(lib, cards)
        assertTrue(graph.nodes.isNotEmpty())
        assertEquals(0, graph.litKanji)
    }
}
