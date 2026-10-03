package app.ren.domain.usecase

import app.ren.domain.model.UserPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface Clock {
    fun now(): Long
    val zone: ZoneId
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override val zone: ZoneId get() = ZoneId.systemDefault()
}

fun Clock.epochDay(millis: Long = now()): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
fun Clock.startOfDay(epochDay: Long = epochDay()): Long =
    LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
fun Clock.date(millis: Long = now()): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/**
 * Streak anchoring with a burnout shield (spec §3): a freeze is earned every 7 days, up to 2,
 * and spent automatically to cover a missed day.
 */
object Streak {
    const val FREEZE_EVERY = 7
    const val MAX_FREEZES = 2

    fun onActivity(p: UserPreferences, today: Long): UserPreferences {
        val last = p.lastActiveEpochDay
        if (last == today) return p
        var freezes = p.streakFreezes
        val streak = when {
            last < 0 -> 1
            last == today - 1 -> p.streakDays + 1
            else -> {
                val missed = (today - last - 1).toInt()
                if (missed in 1..freezes) {
                    freezes -= missed
                    p.streakDays + 1
                } else 1
            }
        }
        if (streak % FREEZE_EVERY == 0) freezes = (freezes + 1).coerceAtMost(MAX_FREEZES)
        return p.copy(
            streakDays = streak,
            lastActiveEpochDay = today,
            streakFreezes = freezes,
            longestStreak = maxOf(p.longestStreak, streak),
        )
    }

    /** The streak as it stands today, before any activity: broken streaks read as 0. */
    fun current(p: UserPreferences, today: Long): Int {
        val last = p.lastActiveEpochDay
        if (last < 0) return 0
        val missed = (today - last - 1).toInt()
        return if (missed <= p.streakFreezes) p.streakDays else 0
    }
}
