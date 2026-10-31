package app.ren.ecosystem

import app.ren.data.local.FSRSCardEntity
import app.ren.data.local.LessonUnitEntity
import app.ren.feature.today.NowFirst
import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.model.UserPreferences
import app.ren.domain.usecase.Streak
import kotlin.math.roundToInt

/** What the widget, and other Lume apps through the status provider, can see of today. */
data class RenStatus(
    val due: Int,
    val retention: Int,
    val streak: Int,
    val doneToday: Boolean,
    /** New lesson units still waiting today. */
    val newToday: Int = 0,
    /** Estimated minutes for what is waiting. */
    val minutes: Int = 0,
) {
    /** One provider row, in [COLUMNS] order. */
    fun toRow(): Array<Any> = arrayOf(due, retention, streak, if (doneToday) 1 else 0, newToday, minutes)

    companion object {
        val COLUMNS = arrayOf("due", "retention", "streak", "doneToday", "newToday", "minutes")

        /** Units still to start today: the daily allowance minus those finished since [dayStart], limited to what is left. */
        fun newToday(units: List<LessonUnitEntity>, prefs: UserPreferences, dayStart: Long): Int {
            val doneToday = units.count { (it.completedAt ?: -1) >= dayStart }
            val open = units.count { it.completedAt == null }
            return (prefs.newUnitsPerDay - doneToday).coerceAtLeast(0).coerceAtMost(open)
        }

        fun compute(
            cards: List<FSRSCardEntity>,
            prefs: UserPreferences,
            now: Long,
            today: Long,
            units: List<LessonUnitEntity> = emptyList(),
            dayStart: Long = 0,
        ): RenStatus {
            val due = cards.count { it.dueTimestamp <= now }
            val reviewed = cards.filter { it.state == CardState.Review.value }
            val retention = if (reviewed.isEmpty()) 100 else (reviewed.map {
                FSRSScheduler.calculateRetrievability((now - it.lastReviewTimestamp).toDouble() / FSRSScheduler.DAY_MS, it.stability)
            }.average() * 100).roundToInt()
            val fresh = newToday(units, prefs, dayStart)
            return RenStatus(
                due, retention.coerceIn(0, 100), Streak.current(prefs, today), prefs.renDoneEpochDay == today,
                newToday = fresh, minutes = NowFirst.estimateMinutes(due, fresh),
            )
        }
    }
}
