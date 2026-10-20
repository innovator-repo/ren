package app.ren.ecosystem

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Local-only links to the other Lume apps. Everything is an explicit broadcast guarded by a
 * signature permission, and quietly does nothing when the peer is not installed.
 */
object LumeIpc {
    const val PERMISSION = "app.lume.permission.ECOSYSTEM_SYNC"

    const val KASANE = "app.kasane"
    const val ACTION_LOG_CHECK = "app.kasane.action.LOG_CHECK"
    const val ACTION_ECO_METRICS = "app.kasane.action.ECO_METRICS"
    const val ACTION_FLOOR_MODE = "app.lume.action.FLOOR_MODE"

    const val ITEM_REN = "ren"
    const val SOURCE_REN = "ren"

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    fun kasaneInstalled(context: Context) = isInstalled(context, KASANE)

    /** Tells Kasane that today's Ren check is (or is not) done. */
    fun logCheck(context: Context, done: Boolean) = send(context, KASANE, ACTION_LOG_CHECK) {
        putExtra("item", ITEM_REN)
        putExtra("done", done)
    }

    /** Shares how well recall went, for Kasane's analysis. */
    fun ecoMetrics(context: Context, metrics: EcoMetrics) = send(context, KASANE, ACTION_ECO_METRICS) {
        putExtra("source", SOURCE_REN)
        putExtra("payload", metrics.toJson())
    }

    private inline fun send(context: Context, pkg: String, action: String, extras: Intent.() -> Unit) {
        if (!isInstalled(context, pkg)) return
        val intent = Intent(action).setPackage(pkg).apply(extras)
        runCatching { context.sendBroadcast(intent, PERMISSION) }
    }
}

/** Recall quality for one finished round. Numbers only, so the JSON needs no escaping. */
data class EcoMetrics(val recallAccuracy: Int, val meanLatencyMs: Long, val dueCount: Int) {
    fun toJson() = """{"recallAccuracy":$recallAccuracy,"meanLatencyMs":$meanLatencyMs,"dueCount":$dueCount}"""

    companion object {
        fun of(reviewed: Int, correct: Int, totalLatencyMs: Long, dueCount: Int) = EcoMetrics(
            recallAccuracy = if (reviewed == 0) 0 else correct * 100 / reviewed,
            meanLatencyMs = if (reviewed == 0) 0 else totalLatencyMs / reviewed,
            dueCount = dueCount,
        )
    }
}
