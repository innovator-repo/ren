package app.ren.ecosystem

import app.ren.data.local.FSRSCardEntity
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
}
