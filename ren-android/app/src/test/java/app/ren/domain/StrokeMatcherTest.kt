package app.ren.domain

import app.ren.domain.ink.Pt
import app.ren.domain.ink.StrokeMatcher
import app.ren.domain.ink.StrokeVerdict
import app.ren.domain.ink.SvgPath
import app.ren.domain.ink.polylineLength
import app.ren.domain.ink.resample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeMatcherTest {
    // 休, stroke 3 (the horizontal of 木) and stroke 4 (its vertical), from KanjiVG.
    private val horizontal = "M37.65,38.83c2.45,0.97,5.18,0.75,7.73,0.54c11.76-0.97,24.94-3.35,37.49-4.01c2.65-0.14,5.39-0.22,7.99,0.39"
    private val vertical = "M61.43,14c0.82,0.75,1.87,2.12,1.87,3.7c0,8.8,0.05,53.72-0.12,72.05c-0.03,2.88-0.06,4.91-0.08,5.75"

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 20) =
        List(n) { i -> val t = i / (n - 1f); Pt(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t) }

    @Test fun flattensKanjiVgPaths() {
        val pts = SvgPath.flatten(horizontal)
        assertEquals(37.65f, pts.first().x, 0.01f)
        assertEquals(90.84f, pts.last().x, 0.05f)
        assertTrue(polylineLength(pts) in 52f..56f)
    }

    @Test fun resamplesEvenly() {
        val r = resample(line(0f, 0f, 100f, 0f, 3), 11)
        assertEquals(11, r.size)
        assertEquals(50f, r[5].x, 0.01f)
    }

    @Test fun acceptsAReasonableTrace() {
        assertEquals(StrokeVerdict.Good, StrokeMatcher.match(line(39f, 40f, 89f, 35f), horizontal))
        assertEquals(StrokeVerdict.Good, StrokeMatcher.match(line(61f, 16f, 64f, 93f), vertical))
    }

    @Test fun rejectsBackwardsStroke() {
        assertEquals(StrokeVerdict.WrongDirection, StrokeMatcher.match(line(89f, 35f, 39f, 40f), horizontal))
    }

    @Test fun rejectsWrongStrokeAndTaps() {
        val verdict = StrokeMatcher.match(line(39f, 40f, 89f, 35f), vertical)
        assertTrue(verdict != StrokeVerdict.Good)
        assertEquals(StrokeVerdict.TooShort, StrokeMatcher.match(listOf(Pt(38f, 39f), Pt(39f, 39f)), horizontal))
    }
}
