package app.ren.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ren.MainActivity
import app.ren.R
import app.ren.data.local.StudyDao
import app.ren.domain.repository.PreferencesRepository
import app.ren.ecosystem.LumeIpc
import app.ren.ecosystem.RenStatus
import app.ren.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** What a reminder says; varies with the day's state so it never reads like the same alarm twice. */
data class ReminderText(val title: String, val body: String, val offerQuick: Boolean, val quickFirst: Boolean)

object ReminderCopy {
    /** From this hour a five-card round is offered first: small enough to never skip a day. */
    const val LATE_HOUR = 21

    fun build(due: Int, retention: Int, streak: Int, hour: Int): ReminderText {
        val late = hour >= LATE_HOUR
        val cards = if (due == 1) "1 card" else "$due cards"
        val title = when {
            late -> "Five cards save today"
            streak >= 3 -> "$streak days in a row"
            else -> "$cards ready in Ren"
        }
        val body = when {
            late -> "$cards due at $retention% retention. A 2-minute round of ${DeepLinks.QUICK_SIZE} keeps the habit alive."
            streak >= 3 -> "$cards due at $retention% retention. A short session keeps your streak going."
            else -> "$cards due at $retention% retention. About ${(due / 6).coerceIn(2, 10)} minutes."
        }
        return ReminderText(title, body, offerQuick = late || due > DeepLinks.QUICK_SIZE, quickFirst = late)
    }
}

/**
 * Spec §10: once a day, if anything is due, one gentle notification that opens the review queue.
 * Stays quiet when Kasane (which orchestrates reminders for the suite) is installed, or when
 * today's session is already done.
 */
@HiltWorker
class ReviewReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val studyDao: StudyDao,
    private val prefs: PreferencesRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val status = RenStatus.compute(studyDao.observeCards().first(), prefs.current(), now, today)
        val quiet = status.doneToday || LumeIpc.kasaneInstalled(applicationContext)
        if (!quiet && status.due > 0) {
            val text = ReminderCopy.build(status.due, status.retention, status.streak, LocalTime.now().hour)
            NotificationHelper.showReviewReminder(applicationContext, text)
        }
        WidgetUpdater.refresh(applicationContext)
        return Result.success()
    }
}

/** Refreshes the widget so its late-evening amber tint appears at 21:00 and clears at midnight. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refresh(applicationContext)
        return Result.success()
    }
}

object NotificationHelper {
    const val CHANNEL = "daily_reminder"
    private const val ID = 7

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showReviewReminder(context: Context, text: ReminderText) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 1, DeepLinks.sessionIntent(context, quick = false), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val quick = PendingIntent.getActivity(
            context, 3, DeepLinks.sessionIntent(context, quick = true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val later = PendingIntent.getBroadcast(
            context, 2, Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.ACTION_LATER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.akane))
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(if (text.quickFirst) quick else open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (text.quickFirst) {
            builder.addAction(0, "Quick round (${DeepLinks.QUICK_SIZE})", quick).addAction(0, "Full session", open)
        } else {
            builder.addAction(0, "Start session", open)
            if (text.offerQuick) builder.addAction(0, "Quick round (${DeepLinks.QUICK_SIZE})", quick)
        }
        val notification = builder.addAction(0, "Later", later).build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}

/** Handles the notification's "Later" action: dismiss now, ask again in an hour. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LATER) return
        NotificationHelper.cancel(context)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-snooze", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReviewReminderWorker>().setInitialDelay(1, TimeUnit.HOURS).build(),
        )
    }

    companion object {
        const val ACTION_LATER = "app.ren.REMINDER_LATER"
    }
}

object ReminderScheduler {
    private const val WORK = "daily-review-reminder"

    /** Schedules the daily reminder at the learner's chosen minute of the day (default 8:00 PM). */
    fun schedule(context: Context, enabled: Boolean, minuteOfDay: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Daily widget refreshes at 21:00 (amber tint if undone) and 00:00 (clear it). */
    fun scheduleWidgetTint(context: Context) {
        val wm = WorkManager.getInstance(context)
        for ((name, hour) in listOf("widget-tint-on" to 21, "widget-tint-off" to 0)) {
            val now = LocalDateTime.now()
            var next = now.with(LocalTime.of(hour, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** ren://session opens the daily queue; ren://session/quick opens a five-card round (widget). */
object DeepLinks {
    const val QUICK_SIZE = 5

    fun sessionIntent(context: Context, quick: Boolean): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(if (quick) "ren://session/quick" else "ren://session"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Returns null for intents that are not session links, true for a quick session, false for the daily one. */
    fun parse(intent: Intent?): Boolean? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "ren" || uri.host != "session") return null
        return uri.pathSegments.firstOrNull() == "quick"
    }
}
