package app.ren.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCopyTest {
    @Test fun lateEveningOffersTheFiveCardRoundFirst() {
        val t = ReminderCopy.build(due = 12, retention = 94, streak = 6, hour = 21)
        assertTrue(t.quickFirst)
        assertTrue(t.body.contains("94%"))
    }

    @Test fun streakCopyAppearsBeforeTheEvening() {
        val t = ReminderCopy.build(due = 12, retention = 94, streak = 6, hour = 20)
        assertEquals("6 days in a row", t.title)
        assertFalse(t.quickFirst)
        assertTrue(t.offerQuick)
    }

    @Test fun aHandfulOfCardsNeedsNoQuickAction() {
        val t = ReminderCopy.build(due = 3, retention = 100, streak = 0, hour = 18)
        assertEquals("3 cards ready in Ren", t.title)
        assertFalse(t.offerQuick)
    }
}
