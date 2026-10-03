package app.ren.domain

import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.fsrs.FsrsCard
import app.ren.domain.fsrs.Rating
import app.ren.domain.fsrs.formatInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FSRSSchedulerTest {
    private val day = FSRSScheduler.DAY_MS
    private val t0 = 1_700_000_000_000L

    @Test fun retrievabilityIsNinetyPercentAfterOneStability() {
        assertEquals(0.9, FSRSScheduler.calculateRetrievability(10.0, 10.0), 1e-9)
        assertEquals(1.0, FSRSScheduler.calculateRetrievability(0.0, 10.0), 1e-9)
        assertEquals(0.0, FSRSScheduler.calculateRetrievability(5.0, 0.0), 0.0)
    }

    @Test fun retrievabilityDecaysMonotonically() {
        val r = (0..60 step 5).map { FSRSScheduler.calculateRetrievability(it.toDouble(), 12.0) }
        assertTrue(r.zipWithNext().all { (a, b) -> b < a })
    }

    @Test fun intervalEqualsStabilityAtTargetRetention() {
        assertEquals(10, FSRSScheduler.calculateNextInterval(10.0))
        assertEquals(1, FSRSScheduler.calculateNextInterval(0.2))
        assertTrue(FSRSScheduler.calculateNextInterval(10.0, 0.8) > FSRSScheduler.calculateNextInterval(10.0, 0.95))
    }

    @Test fun newCardInitialStateUsesSpecWeights() {
        val (s, d) = FSRSScheduler.initNewCard(3)
        assertEquals(3.1262, s, 1e-9)
        assertTrue(d in 1.0..10.0)
        val (_, dEasy) = FSRSScheduler.initNewCard(4)
        val (_, dAgain) = FSRSScheduler.initNewCard(1)
        assertTrue(dEasy < d && d < dAgain)
    }

    @Test fun recallStabilityOrdersHardGoodEasy() {
        val r = FSRSScheduler.calculateRetrievability(8.0, 8.0)
        val hard = FSRSScheduler.nextStability(5.0, 8.0, r, 2)
        val good = FSRSScheduler.nextStability(5.0, 8.0, r, 3)
        val easy = FSRSScheduler.nextStability(5.0, 8.0, r, 4)
        val again = FSRSScheduler.nextStability(5.0, 8.0, r, 1)
        assertTrue(again < 8.0)
        assertTrue(8.0 < hard && hard < good && good < easy)
    }

    @Test fun difficultyMovesWithRatingAndStaysInRange() {
        assertTrue(FSRSScheduler.nextDifficulty(5.0, 1) > 5.0)
        assertTrue(FSRSScheduler.nextDifficulty(5.0, 4) < 5.0)
        assertTrue(FSRSScheduler.nextDifficulty(10.0, 1) <= 10.0)
        assertTrue(FSRSScheduler.nextDifficulty(1.0, 4) >= 1.0)
    }

    @Test fun newCardGoesThroughLearningSteps() {
        val learning = FSRSScheduler.schedule(FsrsCard(), Rating.Good, t0)
        assertEquals(CardState.Learning, learning.state)
        assertEquals(t0 + 10 * FSRSScheduler.MINUTE_MS, learning.due)
        val graduated = FSRSScheduler.schedule(learning, Rating.Good, learning.due)
        assertEquals(CardState.Review, graduated.state)
        assertTrue(graduated.scheduledDays >= 1)
        val easy = FSRSScheduler.schedule(FsrsCard(), Rating.Easy, t0)
        assertEquals(CardState.Review, easy.state)
        assertEquals(FSRSScheduler.calculateNextInterval(15.4722), easy.scheduledDays)
    }

    @Test fun reviewIntervalsAreOrderedAndLapseRelearns() {
        val card = FsrsCard(CardState.Review, stability = 6.0, difficulty = 5.0, reps = 4, lastReview = t0, due = t0 + 6 * day)
        val now = t0 + 6 * day
        val preview = FSRSScheduler.preview(card, now)
        val hard = preview.getValue(Rating.Hard).scheduledDays
        val good = preview.getValue(Rating.Good).scheduledDays
        val easy = preview.getValue(Rating.Easy).scheduledDays
        assertTrue("hard=$hard good=$good easy=$easy", hard < good && good < easy)
        val lapse = preview.getValue(Rating.Again)
        assertEquals(CardState.Relearning, lapse.state)
        assertEquals(1, lapse.lapses)
        assertTrue(lapse.stability <= card.stability)
        assertEquals(now + 10 * FSRSScheduler.MINUTE_MS, lapse.due)
    }

    @Test fun matureCardsGrowStabilityOnSuccess() {
        var card = FsrsCard()
        var now = t0
        for (rating in listOf(Rating.Good, Rating.Good, Rating.Good, Rating.Good, Rating.Good)) {
            card = FSRSScheduler.schedule(card, rating, now)
            now = card.due
        }
        assertTrue("stability ${card.stability}", card.stability > FSRSScheduler.MATURE_STABILITY_DAYS)
    }

    @Test fun intervalLabels() {
        assertEquals("10 min", formatInterval(0, 10 * FSRSScheduler.MINUTE_MS))
        assertEquals("1 day", formatInterval(0, day))
        assertEquals("6 days", formatInterval(0, 6 * day))
        assertEquals("3 mo", formatInterval(0, 90 * day))
    }
}
