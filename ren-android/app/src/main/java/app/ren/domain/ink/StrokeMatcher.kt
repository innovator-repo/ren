package app.ren.domain.ink

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** A point in KanjiVG's 109×109 box. */
data class Pt(val x: Float, val y: Float) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
    fun dist(o: Pt) = hypot(x - o.x, y - o.y)
    fun len() = hypot(x, y)
}

const val GLYPH_BOX = 109f

/**
 * Minimal SVG path flattener (M L H V C S Q T Z, absolute and relative) for KanjiVG stroke data.
 * Rendering uses Compose's own parser; this one only feeds the matcher, so it can run in unit tests.
 */
object SvgPath {
    private val token = Regex("""[MmLlHhVvCcSsQqTtZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")

    fun flatten(d: String, segments: Int = 12): List<Pt> {
        val tokens = token.findAll(d).map { it.value }.toList()
        val out = ArrayList<Pt>()
        var i = 0
        var cmd = 'M'
        var cur = Pt(0f, 0f)
        var start = cur
        var lastCtrl: Pt? = null
        fun num(): Float = tokens[i++].toFloat()
        fun isNum(): Boolean = i < tokens.size && tokens[i][0].let { it.isDigit() || it == '-' || it == '.' }

        while (i < tokens.size) {
            if (!isNum()) cmd = tokens[i++][0]
            val rel = cmd.isLowerCase()
            fun p(): Pt { val x = num(); val y = num(); return if (rel) Pt(cur.x + x, cur.y + y) else Pt(x, y) }
            when (cmd.uppercaseChar()) {
                'M' -> { cur = p(); start = cur; out += cur; cmd = if (rel) 'l' else 'L'; lastCtrl = null }
                'L' -> { cur = p(); out += cur; lastCtrl = null }
                'H' -> { val x = num(); cur = Pt(if (rel) cur.x + x else x, cur.y); out += cur; lastCtrl = null }
                'V' -> { val y = num(); cur = Pt(cur.x, if (rel) cur.y + y else y); out += cur; lastCtrl = null }
                'C' -> { val c1 = p(); val c2 = p(); val e = p(); cubic(out, cur, c1, c2, e, segments); lastCtrl = c2; cur = e }
                'S' -> {
                    val c1 = lastCtrl?.let { cur * 2f - it } ?: cur
                    val c2 = p(); val e = p(); cubic(out, cur, c1, c2, e, segments); lastCtrl = c2; cur = e
                }
                'Q' -> { val c = p(); val e = p(); quad(out, cur, c, e, segments); lastCtrl = c; cur = e }
                'T' -> { val c = lastCtrl?.let { cur * 2f - it } ?: cur; val e = p(); quad(out, cur, c, e, segments); lastCtrl = c; cur = e }
                'Z' -> { cur = start; out += cur; lastCtrl = null }
                else -> i++
            }
        }
        return out
    }

    private fun cubic(out: MutableList<Pt>, p0: Pt, p1: Pt, p2: Pt, p3: Pt, n: Int) {
        for (k in 1..n) {
            val t = k / n.toFloat(); val u = 1 - t
            out += p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
        }
    }

    private fun quad(out: MutableList<Pt>, p0: Pt, p1: Pt, p2: Pt, n: Int) {
        for (k in 1..n) {
            val t = k / n.toFloat(); val u = 1 - t
            out += p0 * (u * u) + p1 * (2 * u * t) + p2 * (t * t)
        }
    }
}

fun polylineLength(pts: List<Pt>): Float = pts.zipWithNext { a, b -> a.dist(b) }.sum()

/** Resamples a polyline to [n] points spaced evenly along its length. */
fun resample(pts: List<Pt>, n: Int): List<Pt> {
    if (pts.isEmpty()) return emptyList()
    if (pts.size == 1) return List(n) { pts[0] }
    val total = polylineLength(pts)
    if (total == 0f) return List(n) { pts[0] }
    val step = total / (n - 1)
    val out = ArrayList<Pt>(n).apply { add(pts[0]) }
    var acc = 0f
    var prev = pts[0]
    var idx = 1
    while (idx < pts.size && out.size < n) {
        val next = pts[idx]
        val seg = prev.dist(next)
        if (acc + seg >= step && seg > 0f) {
            val t = (step - acc) / seg
            val q = prev + (next - prev) * t
            out += q
            prev = q
            acc = 0f
        } else {
            acc += seg
            prev = next
            idx++
        }
    }
    while (out.size < n) out += pts.last()
    return out
}

enum class StrokeVerdict { Good, WrongStart, WrongDirection, WrongShape, TooShort }

/**
 * Tolerance-band stroke matching (spec §6B): order is enforced by the caller, and each stroke is
 * checked for start point, overall direction and mean distance after resampling.
 */
object StrokeMatcher {
    private const val SAMPLES = 24

    fun match(user: List<Pt>, referencePath: String, lenient: Boolean = false): StrokeVerdict =
        match(user, SvgPath.flatten(referencePath), lenient)

    fun match(user: List<Pt>, reference: List<Pt>, lenient: Boolean): StrokeVerdict {
        val k = if (lenient) 1.25f else 1f
        val refLen = polylineLength(reference)
        val userLen = polylineLength(user)
        // Dots (丶) are short; anything else must be drawn, not tapped.
        if (userLen < max(4f, refLen * 0.3f)) return StrokeVerdict.TooShort

        val u = resample(user, SAMPLES)
        val r = resample(reference, SAMPLES)
        val refVec = r.last() - r.first()
        val userVec = u.last() - u.first()
        if (refVec.len() > 12f && userVec.len() > 0f) {
            val cos = (refVec.x * userVec.x + refVec.y * userVec.y) / (refVec.len() * userVec.len())
            if (cos < 0.35f) return StrokeVerdict.WrongDirection
        }
        if (u.first().dist(r.first()) > 24f * k && u.last().dist(r.first()) < u.first().dist(r.first())) {
            return StrokeVerdict.WrongDirection
        }
        if (u.first().dist(r.first()) > 26f * k) return StrokeVerdict.WrongStart

        val mean = u.indices.sumOf { u[it].dist(r[it]).toDouble() } / SAMPLES
        val scale = sqrt(max(1f, refLen / 40f)).toDouble()
        return if (mean <= 15.0 * k * scale) StrokeVerdict.Good else StrokeVerdict.WrongShape
    }

    fun hint(verdict: StrokeVerdict): String = when (verdict) {
        StrokeVerdict.Good -> "Good stroke"
        StrokeVerdict.WrongStart -> "Start at the red dot"
        StrokeVerdict.WrongDirection -> "Other way round"
        StrokeVerdict.WrongShape -> "Not quite. Follow the grey line"
        StrokeVerdict.TooShort -> "Draw the whole stroke"
    }
}

/** Bounding box of a set of strokes, used to place hit regions and split animations. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2

    companion object {
        fun of(paths: List<String>): Box {
            val pts = paths.flatMap { SvgPath.flatten(it, 6) }
            if (pts.isEmpty()) return Box(0f, 0f, GLYPH_BOX, GLYPH_BOX)
            return Box(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
        }
    }
}
