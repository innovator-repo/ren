package app.ren.feature.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeFailureTest {
    @Test fun progressNeverGoesBackwardsWhenTheRoundGrows() {
        val s = SafeFailure()
        assertEquals(0.5f, s.advanceTo(0.5f), 0f)
        // A requeued card makes the round longer, which would shrink the raw fraction.
        assertEquals(0.5f, s.advanceTo(0.4f), 0f)
        assertEquals(0.6f, s.advanceTo(0.6f), 0f)
    }

    @Test fun aMissedCardIsRequeuedUpToTheCap() {
        val s = SafeFailure(maxRequeues = 2)
        assertTrue(s.requeue("a"))
        assertTrue(s.requeue("a"))
        assertFalse(s.requeue("a"))
        assertTrue(s.requeue("b"))
    }

    @Test fun roundEndingOnAMissGetsARescueUntilItSucceeds() {
        val s = SafeFailure(maxRescues = 2)
        s.record("a", recalled = false)
        assertEquals("a", s.rescue())
        s.record("a", recalled = false)
        assertEquals("a", s.rescue())
        // Retries spent: no more rescues (the summary is honest rather than faked).
        assertNull(s.rescue())
    }

    @Test fun roundEndingOnASuccessNeedsNoRescue() {
        val s = SafeFailure()
        s.record("a", recalled = false)
        s.record("b", recalled = true)
        assertNull(s.rescue())
        s.record("a", recalled = false)
        s.record("a", recalled = true)
        assertNull(s.rescue())
    }
}
