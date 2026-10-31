package app.ren.feature.today

import app.ren.domain.model.Vocab
import app.ren.domain.model.VocabCardType
import kotlin.math.abs

/** Pure helpers behind the Now-first main screen and the two home-screen widgets. */
object NowFirst {
    /** Share of today's reviews already done: the arc of the hero ring. A caught-up day is a full ring. */
    fun ringFraction(done: Int, due: Int): Float {
        val total = done.coerceAtLeast(0) + due.coerceAtLeast(0)
        return if (total == 0 || due <= 0) 1f else done.coerceAtLeast(0).toFloat() / total
    }

    /** "5 new · about 9 min"; just the time when nothing is new; a calm line when nothing is left. */
    fun planLine(newCount: Int, minutes: Int): String = when {
        newCount <= 0 && minutes <= 0 -> "Nothing left for today"
        newCount <= 0 -> "About $minutes min"
        minutes <= 0 -> "$newCount new"
        else -> "$newCount new · about $minutes min"
    }

    fun headerContext(pathDay: Long): String = "DAY ${pathDay.coerceAtLeast(1)}"

    /** Label of the hero button; with nothing to study it opens the app instead of an empty session. */
    fun startLabel(due: Int, newCount: Int): String = if (due + newCount > 0) "Start session" else "Open Ren"

    /** Minutes for a session: 25 s per review, 2 min per new unit, at least 1 when there is anything to do. */
    fun estimateMinutes(due: Int, newUnits: Int): Int {
        val work = due + newUnits
        if (work <= 0) return 0
        return Math.round((due * 25 + newUnits * 120) / 60.0).toInt().coerceAtLeast(1)
    }

    /** Where the drawer settles after a drag: a clear fling wins, otherwise the nearer end. */
    fun drawerTarget(fraction: Float, velocityPxPerSec: Float, flingThreshold: Float = 700f): Float = when {
        velocityPxPerSec <= -flingThreshold -> 1f
        velocityPxPerSec >= flingThreshold -> 0f
        fraction >= 0.5f -> 1f
        else -> 0f
    }
}

/** The word shown in the drawer and on the Word of the day widget. */
object WordOfDay {
    /**
     * One word per day, the same on every device and in the widget. Words the learner has met come first;
     * a learner who has met none yet still gets a word from the library. Null only for an empty library.
     */
    fun pick(vocab: Collection<Vocab>, introducedIds: Set<String>, epochDay: Long): Vocab? {
        val words = vocab.filter { it.cardType == VocabCardType.SINGLE_WORD && it.text.isNotBlank() }
        val pool = words.filter { it.id in introducedIds }.ifEmpty { words }.sortedBy { it.id }
        if (pool.isEmpty()) return null
        return pool[(abs(epochDay) % pool.size).toInt()]
    }
}

/** What the Word of the day widget shows; a tap flips [revealed], a second tap flips it back. */
data class WordFace(val headline: String, val detail: String?)

object WordFlip {
    /** The flip state belongs to one day, so tomorrow's word starts face up again. */
    fun revealed(storedDay: Long, flag: Boolean, today: Long): Boolean = flag && storedDay == today

    fun toggled(storedDay: Long, flag: Boolean, today: Long): Boolean = !revealed(storedDay, flag, today)

    fun face(word: Vocab?, revealed: Boolean): WordFace = when {
        word == null -> WordFace("No word yet", "Open Ren to start learning")
        revealed -> WordFace(word.reading.ifBlank { word.text }, word.meaning)
        else -> WordFace(word.text, null)
    }
}

/** What the Due and Start widget shows; every field has a sensible value on an empty deck. */
data class DueStartModel(val due: Int, val newToday: Int, val minutes: Int, val fraction: Float) {
    val plan: String get() = NowFirst.planLine(newToday, minutes)
    val button: String get() = NowFirst.startLabel(due, newToday)
    val hasWork: Boolean get() = due + newToday > 0
    val caption: String get() = if (due > 0) "due today" else "all caught up"

    companion object {
        fun of(due: Int, newToday: Int, minutes: Int, reviewsDone: Int) =
            DueStartModel(due.coerceAtLeast(0), newToday.coerceAtLeast(0), minutes.coerceAtLeast(0), NowFirst.ringFraction(reviewsDone, due))
    }
}
