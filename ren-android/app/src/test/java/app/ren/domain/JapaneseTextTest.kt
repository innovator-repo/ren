package app.ren.domain

import app.ren.domain.model.Japanese
import app.ren.domain.model.RubyMarkup
import app.ren.domain.model.Segment
import app.ren.domain.usecase.AnswerChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseTextTest {
    @Test fun parsesRubyMarkup() {
        val segs = RubyMarkup.parse("{日曜日|にちようび}は{休日|きゅうじつ}です。")
        assertEquals(4, segs.size)
        assertEquals(Segment.Word("日曜日", "にちようび", "日曜日"), segs[0])
        assertEquals(Segment.Plain("は"), segs[1])
        assertEquals("日曜日は休日です。", RubyMarkup.plain("{日曜日|にちようび}は{休日|きゅうじつ}です。"))
        assertEquals("にちようびはきゅうじつです。", RubyMarkup.reading("{日曜日|にちようび}は{休日|きゅうじつ}です。"))
    }

    @Test fun parsesLemmaAndKanaWords() {
        val segs = RubyMarkup.parse("{食べます|たべます|食べる}と{パン}")
        assertEquals(Segment.Word("食べます", "たべます", "食べる"), segs[0])
        assertEquals(Segment.Word("パン", null, "パン"), segs[2])
    }

    @Test fun alignsFuriganaOverKanjiOnly() {
        assertEquals(listOf("食" to "た", "べる" to null), Japanese.alignRuby("食べる", "たべる"))
        assertEquals(listOf("お" to null, "母" to "かあ", "さん" to null), Japanese.alignRuby("お母さん", "おかあさん"))
        assertEquals(listOf("休日" to "きゅうじつ"), Japanese.alignRuby("休日", "きゅうじつ"))
        assertEquals(listOf("パン" to null), Japanese.alignRuby("パン", "パン"))
    }

    @Test fun moraeJoinSmallKana() {
        assertEquals(listOf("きゅ", "う", "じ", "つ"), Japanese.morae("きゅうじつ"))
    }

    @Test fun pitchContours() {
        // 休日 [0]: low, high, high, high, particle high
        assertEquals(listOf(false, true, true, true, true), Japanese.pitchContour(4, 0))
        // 先生 [3]: low, high, high, then particle low
        assertEquals(listOf(false, true, true, false, false), Japanese.pitchContour(4, 3))
        // 今日 [1]: high, low
        assertEquals(listOf(true, false, false), Japanese.pitchContour(2, 1))
    }

    @Test fun answersAreScriptInsensitive() {
        assertTrue(AnswerChecker.matches("きゅうじつ", "きゅうじつ"))
        assertTrue(AnswerChecker.matches("キュウジツ", "きゅうじつ"))
        assertTrue(AnswerChecker.matches(" にのる ", "にのる"))
        assertFalse(AnswerChecker.matches("きゅじつ", "きゅうじつ"))
        assertFalse(AnswerChecker.matches("", "きゅうじつ"))
    }

    @Test fun politeFormsOfVerbs() {
        assertTrue("のります" in AnswerChecker.politeForms("のる"))
        assertTrue("たべます" in AnswerChecker.politeForms("たべる"))
        assertTrue("のみます" in AnswerChecker.politeForms("のむ"))
        assertTrue("いきます" in AnswerChecker.politeForms("いく"))
    }
}
