package app.ren.ecosystem

import app.ren.data.local.FSRSCardEntity
import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.model.UserPreferences
import app.ren.domain.usecase.Streak
import kotlin.math.roundToInt

/** What the widget, and other Lume apps through the status provider, can see of today. */
data class RenStatus(val due: Int, val retention: Int, val streak: Int, val doneToday: Boolean) {
    companion object {
        fun compute(cards: List<FSRSCardEntity>, prefs: UserPreferences, now: Long, today: Long): RenStatus {
            val due = cards.count { it.dueTimestamp <= now }
            val reviewed = cards.filter { it.state == CardState.Review.value }
            val retention = if (reviewed.isEmpty()) 100 else (reviewed.map {
                FSRSScheduler.calculateRetrievability((now - it.lastReviewTimestamp).toDouble() / FSRSScheduler.DAY_MS, it.stability)
            }.average() * 100).roundToInt()
            return RenStatus(due, retention.coerceIn(0, 100), Streak.current(prefs, today), prefs.renDoneEpochDay == today)
        }
    }
}
