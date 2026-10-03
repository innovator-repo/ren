package app.ren.domain

import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FsrsCard
import app.ren.domain.model.ItemType
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.StudyCard
import app.ren.domain.model.UnitType
import app.ren.domain.model.UserPreferences
import app.ren.domain.usecase.CurriculumPlanner
import app.ren.domain.usecase.SessionPlanner
import app.ren.domain.usecase.SessionStep
import app.ren.domain.usecase.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    private fun unit(id: String, stage: Int, seq: Int, done: Boolean) =
        LessonUnit(id, UnitType.RADICAL, stage, seq, id, listOf("item_$id"), if (done) 1L else null)

    private fun card(id: String, stage: Int, state: CardState, due: Long, stability: Double = 1.0) =
        StudyCard(id, if (stage == 0) ItemType.KANA else ItemType.RADICAL,
            FsrsCard(state = state, stability = stability, due = due, lastReview = 0), "u_$id", stage, 0)

    @Test fun laterStagesStayLockedUntilEarlierOnesGraduate() {
        val units = listOf(unit("a", 0, 0, true), unit("b", 0, 1, true), unit("c", 1, 2, false))
        val learning = listOf(card("item_a", 0, CardState.Learning, 5), card("item_b", 0, CardState.Review, 5))
        val p1 = CurriculumPlanner.stageProgress(units, learning, unlockAll = false)
        assertTrue(p1[0].unlocked)
        assertFalse(p1[1].unlocked)
        assertEquals(0, CurriculumPlanner.nextUnits(units, p1, 3).size)

        val graduated = learning.map { it.copy(fsrs = it.fsrs.copy(state = CardState.Review)) }
        val p2 = CurriculumPlanner.stageProgress(units, graduated, unlockAll = false)
        assertTrue(p2[1].unlocked)
        assertEquals(listOf("c"), CurriculumPlanner.nextUnits(units, p2, 3).map { it.id })
        assertTrue(CurriculumPlanner.stageProgress(units, learning, unlockAll = true)[3].unlocked)
    }

    @Test fun sessionPutsForgottenReviewsFirstThenLessons() {
        val now = 100L * 86_400_000L
        val cards = listOf(
            card("fresh", 1, CardState.Review, now - 1, stability = 50.0),
            card("shaky", 1, CardState.Review, now - 1, stability = 0.5),
            card("future", 1, CardState.Review, now + 1),
            card("locked", 1, CardState.New, StudyCard.NOT_INTRODUCED),
        )
        val steps = SessionPlanner.daily(cards, listOf(unit("u", 1, 0, false)), now, cap = 10)
        assertEquals(listOf<SessionStep>(SessionStep.Review("shaky"), SessionStep.Review("fresh"), SessionStep.Lesson("u")), steps)
    }

    @Test fun streakGrowsUsesFreezesAndResets() {
        var p = UserPreferences()
        p = Streak.onActivity(p, 10)
        assertEquals(1, p.streakDays)
        for (d in 11L..16L) p = Streak.onActivity(p, d)
        assertEquals(7, p.streakDays)
        assertEquals(1, p.streakFreezes)
        p = Streak.onActivity(p, 18) // missed day 17: freeze spent
        assertEquals(8, p.streakDays)
        assertEquals(0, p.streakFreezes)
        assertEquals(8, Streak.current(p, 19))
        assertEquals(0, Streak.current(p, 21))
        p = Streak.onActivity(p, 21)
        assertEquals(1, p.streakDays)
        assertEquals(8, p.longestStreak)
    }
}
