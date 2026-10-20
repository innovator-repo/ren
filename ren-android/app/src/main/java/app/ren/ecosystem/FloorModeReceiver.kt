package app.ren.ecosystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ren.widget.WidgetEntryPoint
import app.ren.widget.WidgetUpdater
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Kasane's "low-energy day" switch: today a 5-card quick round is enough. Guarded by the ecosystem permission. */
class FloorModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LumeIpc.ACTION_FLOOR_MODE) return
        val on = intent.getBooleanExtra("on", false)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val prefs = EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java).preferences()
                val today = LocalDate.now().toEpochDay()
                prefs.update { it.copy(floorEpochDay = if (on) today else -1) }
                WidgetUpdater.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
