package app.ren.feature.today

import app.ren.domain.model.Vocab
import app.ren.domain.model.VocabCardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowFirstTest {
    private fun word(id: String, text: String = id, type: VocabCardType = VocabCardType.SINGLE_WORD) =
        Vocab(id, text, "よみ-$id", 0, "meaning-$id", emptyList(), "", "", "", type)

    @Test fun ringShowsShareDoneAndIsFullWhenNothingIsDue() {
        assertEquals(0.6f, NowFirst.ringFraction(done = 18, due = 12), 0.001f)
        assertEquals(0f, NowFirst.ringFraction(done = 0, due = 12), 0f)
        assertEquals(1f, NowFirst.ringFraction(done = 0, due = 0), 0f)
        assertEquals(1f, NowFirst.ringFraction(done = 5, due = 0), 0f)
        assertEquals(0f, NowFirst.ringFraction(done = -3, due = 4), 0f)
    }

    @Test fun planLineReadsLikeTheDesign() {
        assertEquals("5 new · about 9 min", NowFirst.planLine(5, 9))
        assertEquals("About 4 min", NowFirst.planLine(0, 4))
        assertEquals("2 new", NowFirst.planLine(2, 0))
        assertEquals("Nothing left for today", NowFirst.planLine(0, 0))
    }

    @Test fun startLabelFallsBackToOpenWhenNothingToStudy() {
        assertEquals("Start session", NowFirst.startLabel(due = 3, newCount = 0))
        assertEquals("Start session", NowFirst.startLabel(due = 0, newCount = 2))
        assertEquals("Open Ren", NowFirst.startLabel(0, 0))
    }

    @Test fun minutesEstimateMatchesTheTodayPlanner() {
        assertEquals(0, NowFirst.estimateMinutes(0, 0))
        assertEquals(1, NowFirst.estimateMinutes(1, 0))
        assertEquals(5, NowFirst.estimateMinutes(12, 0))   // 300 s
        assertEquals(11, NowFirst.estimateMinutes(12, 3))  // 660 s
    }

    @Test fun headerContextCountsFromDayOne() {
        assertEquals("DAY 41", NowFirst.headerContext(41))
        assertEquals("DAY 1", NowFirst.headerContext(0))
    }

    @Test fun drawerSettlesOnTheNearerEndUnlessFlung() {
        assertEquals(1f, NowFirst.drawerTarget(0.2f, -1500f), 0f)
        assertEquals(0f, NowFirst.drawerTarget(0.9f, 1500f), 0f)
        assertEquals(1f, NowFirst.drawerTarget(0.6f, 0f), 0f)
        assertEquals(0f, NowFirst.drawerTarget(0.4f, 100f), 0f)
    }

    @Test fun dueStartModelWorksWithNoData() {
        val empty = DueStartModel.of(0, 0, 0, 0)
        assertFalse(empty.hasWork)
        assertEquals("Open Ren", empty.button)
        assertEquals("all caught up", empty.caption)
        assertEquals(1f, empty.fraction, 0f)
        val busy = DueStartModel.of(12, 5, 9, 18)
        assertTrue(busy.hasWork)
        assertEquals("Start session", busy.button)
        assertEquals("5 new · about 9 min", busy.plan)
        assertEquals("due today", busy.caption)
    }

    @Test fun wordOfTheDayIsStablePerDayAndPrefersMetWords() {
        val all = listOf(word("a"), word("b"), word("c"), word("d"))
        assertEquals(WordOfDay.pick(all, emptySet(), 100), WordOfDay.pick(all.reversed(), emptySet(), 100))
        assertEquals("b", WordOfDay.pick(all, emptySet(), 101)?.id)
        assertEquals("d", WordOfDay.pick(all, setOf("d"), 7)?.id)
        assertNotEqualsForNextDay(all)
    }

    private fun assertNotEqualsForNextDay(all: List<Vocab>) =
        assertTrue(WordOfDay.pick(all, emptySet(), 10)?.id != WordOfDay.pick(all, emptySet(), 11)?.id)

    @Test fun wordOfTheDayIgnoresSentencesAndEmptyLibraries() {
        assertNull(WordOfDay.pick(emptyList(), emptySet(), 5))
        assertNull(WordOfDay.pick(listOf(word("s", type = VocabCardType.MINED_SENTENCE), word("blank", text = " ")), emptySet(), 5))
        assertEquals("w", WordOfDay.pick(listOf(word("s", type = VocabCardType.COLLOCATION), word("w")), emptySet(), 5)?.id)
    }

    @Test fun flipSwapsAndSwapsBack() {
        val w = word("taberu", "食べる")
        assertEquals(WordFace("食べる", null), WordFlip.face(w, revealed = false))
        assertEquals(WordFace("よみ-taberu", "meaning-taberu"), WordFlip.face(w, revealed = true))
        val first = WordFlip.toggled(storedDay = -1, flag = false, today = 20)
        assertTrue(first)
        assertFalse(WordFlip.toggled(storedDay = 20, flag = first, today = 20))
    }

    @Test fun flipResetsOnANewDayAndHandlesNoWord() {
        assertFalse(WordFlip.revealed(storedDay = 19, flag = true, today = 20))
        assertTrue(WordFlip.toggled(storedDay = 19, flag = true, today = 20))
        assertEquals("No word yet", WordFlip.face(null, revealed = true).headline)
        assertEquals("よみ", WordFlip.face(word("x", "x").copy(reading = "よみ"), true).headline)
    }
}
