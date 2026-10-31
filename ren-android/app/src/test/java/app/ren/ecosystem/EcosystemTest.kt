package app.ren.ecosystem

import app.ren.data.local.FSRSCardEntity
import app.ren.data.local.LessonUnitEntity
import app.ren.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcosystemTest {
    @Test fun metricsPayloadIsPlainJson() {
        val m = EcoMetrics.of(reviewed = 8, correct = 6, totalLatencyMs = 24_000, dueCount = 12)
        assertEquals(75, m.recallAccuracy)
        assertEquals(3000L, m.meanLatencyMs)
        assertEquals("""{"recallAccuracy":75,"meanLatencyMs":3000,"dueCount":12}""", m.toJson())
    }

    @Test fun emptyRoundDoesNotDivideByZero() {
        assertEquals(EcoMetrics(0, 0, 4), EcoMetrics.of(0, 0, 0, 4))
    }

    private fun card(id: String, due: Long) = FSRSCardEntity(
        itemId = id, itemType = "kanji", state = 0, stability = 0.0, difficulty = 0.0, elapsedDays = 0, scheduledDays = 0,
        reps = 0, lapses = 0, lastReviewTimestamp = 0, dueTimestamp = due, unitId = "u", stage = 1, sequence = 1,
    )

    @Test fun statusCountsDueAndReadsTheDoneFlag() {
        val cards = listOf(card("a", due = 100), card("b", due = 5_000))
        val open = RenStatus.compute(cards, UserPreferences(), now = 1_000, today = 20_000)
        assertEquals(1, open.due)
        assertFalse(open.doneToday)
        val done = RenStatus.compute(cards, UserPreferences(renDoneEpochDay = 20_000), now = 1_000, today = 20_000)
        assertTrue(done.doneToday)
        assertFalse(RenStatus.compute(cards, UserPreferences(renDoneEpochDay = 19_999), now = 1_000, today = 20_000).doneToday)
    }

    private fun unit(id: String, done: Long?) = LessonUnitEntity(id, "KANJI", 2, 1, id, "a", done)

    @Test fun statusRowFollowsTheColumnOrder() {
        val row = RenStatus(due = 12, retention = 91, streak = 41, doneToday = true, newToday = 5, minutes = 9).toRow()
        assertEquals(listOf("due", "retention", "streak", "doneToday", "newToday", "minutes"), RenStatus.COLUMNS.toList())
        assertEquals(listOf<Any>(12, 91, 41, 1, 5, 9), row.toList())
        assertEquals(listOf<Any>(0, 100, 0, 0, 0, 0), RenStatus(0, 100, 0, false).toRow().toList())
    }

    @Test fun newTodayIsTheAllowanceLeftAndNeverMoreThanWhatIsOpen() {
        val prefs = UserPreferences(newUnitsPerDay = 3)
        val units = listOf(unit("a", 5_000), unit("b", null), unit("c", null), unit("d", null), unit("e", null))
        assertEquals(2, RenStatus.newToday(units, prefs, dayStart = 1_000))
        assertEquals(3, RenStatus.newToday(units, prefs, dayStart = 9_000))
        assertEquals(1, RenStatus.newToday(listOf(unit("b", null)), prefs, 0))
        assertEquals(0, RenStatus.newToday(emptyList(), prefs, 0))
    }

    @Test fun statusFillsNewAndMinutesAndStaysSafeOnAnEmptyDeck() {
        val cards = listOf(card("a", due = 100), card("b", due = 200))
        val s = RenStatus.compute(
            cards, UserPreferences(newUnitsPerDay = 2), now = 1_000, today = 20_000,
            units = listOf(unit("u1", null), unit("u2", null), unit("u3", null)), dayStart = 0,
        )
        assertEquals(2, s.due)
        assertEquals(2, s.newToday)
        assertEquals(5, s.minutes) // 2 reviews + 2 new = 290 s
        val empty = RenStatus.compute(emptyList(), UserPreferences(), now = 1_000, today = 20_000)
        assertEquals(RenStatus(0, 100, 0, false), empty)
    }
}
