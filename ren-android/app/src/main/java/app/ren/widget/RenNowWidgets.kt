package app.ren.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.ren.MainActivity
import app.ren.R
import app.ren.domain.model.Vocab
import app.ren.ecosystem.RenStatus
import app.ren.feature.today.DueStartModel
import app.ren.feature.today.WordFlip
import app.ren.feature.today.WordOfDay
import app.ren.work.DeepLinks
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** Neutrals that follow the system light/dark setting, and Ren's akane accent, as in the app. */
private object WidgetInk {
    val card = ColorProvider(Color(0xF7FFFFFF), Color(0xF71C1C1E))
    val ink = ColorProvider(Color(0xFF1D1D1F), Color(0xFFF5F5F7))
    val ink2 = ColorProvider(Color(0xFF6E6E73), Color(0xFFA1A1A6))
    val akane = ColorProvider(Color(0xFFBB443A), Color(0xFFF18477))
    val onAccent = ColorProvider(Color.White, Color(0xFF1C1C1E))
}

private fun widgetEntry(context: Context) =
    dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

private fun openApp(context: Context) =
    actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

private fun ringBitmap(fraction: Float, color: Int): Bitmap {
    val size = 240
    val stroke = 22f
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; this.color = color }
    val rect = RectF(stroke, stroke, size - stroke, size - stroke)
    paint.alpha = 50
    canvas.drawArc(rect, 0f, 360f, false, paint)
    paint.alpha = 255
    canvas.drawArc(rect, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
    return bmp
}

/** Due and Start: progress ring with the due count, today's plan and a Start session button (ren://session). */
class RenDueWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = load(context)
        val ring = ringBitmap(model.fraction, context.getColor(R.color.akane))
        provideContent { Content(model, ring, context) }
    }

    private suspend fun load(context: Context): DueStartModel = runCatching {
        val entry = widgetEntry(context)
        val day = LocalDate.now()
        val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dao = entry.studyDao()
        val status = RenStatus.compute(
            dao.observeCards().first(), entry.preferences().current(), System.currentTimeMillis(), day.toEpochDay(),
            units = dao.observeUnits().first(), dayStart = dayStart,
        )
        val done = dao.observeLogsSince(dayStart).first().count { it.rating > 0 }
        DueStartModel.of(status.due, status.newToday, status.minutes, done)
    }.getOrElse { DueStartModel.of(0, 0, 0, 0) }

    @Composable
    private fun Content(m: DueStartModel, ring: Bitmap, context: Context) {
        val start = if (m.hasWork) actionStartActivity(DeepLinks.sessionIntent(context, quick = false)) else openApp(context)
        Row(
            GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(WidgetInk.card).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(92.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ring), contentDescription = "${m.due} due today", modifier = GlanceModifier.size(92.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${m.due}", style = TextStyle(color = WidgetInk.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                    Text(m.caption, style = TextStyle(color = WidgetInk.ink2, fontSize = 12.sp))
                }
            }
            Spacer(GlanceModifier.width(14.dp))
            Column(GlanceModifier.fillMaxWidth()) {
                Text(m.plan, style = TextStyle(color = WidgetInk.ink2, fontSize = 14.sp))
                Spacer(GlanceModifier.height(10.dp))
                Box(
                    GlanceModifier.fillMaxWidth().height(48.dp).cornerRadius(14.dp).background(WidgetInk.akane).clickable(start),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(m.button, style = TextStyle(color = WidgetInk.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class RenDueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RenDueWidget()
}

internal val WordFlagKey = booleanPreferencesKey("word_revealed")
internal val WordDayKey = longPreferencesKey("word_day")

/** Word of the day: the Japanese word; a tap swaps in the reading and meaning in place, a second tap swaps back. */
class RenWordWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val word = loadWord(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val revealed = WordFlip.revealed(prefs[WordDayKey] ?: -1, prefs[WordFlagKey] ?: false, LocalDate.now().toEpochDay())
            Content(word, revealed, context)
        }
    }

    private suspend fun loadWord(context: Context): Vocab? = runCatching {
        val entry = widgetEntry(context)
        val met = entry.studyDao().observeCards().first().filter { it.dueTimestamp != Long.MAX_VALUE }.mapTo(HashSet()) { it.itemId }
        WordOfDay.pick(entry.content().current().vocab.values, met, LocalDate.now().toEpochDay())
    }.getOrNull()

    @Composable
    private fun Content(word: Vocab?, revealed: Boolean, context: Context) {
        val face = WordFlip.face(word, revealed)
        val tap = if (word == null) openApp(context) else actionRunCallback<FlipWordAction>()
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(WidgetInk.card).padding(16.dp).clickable(tap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("WORD OF THE DAY", style = TextStyle(color = WidgetInk.akane, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(8.dp))
            Text(
                face.headline,
                style = TextStyle(color = WidgetInk.ink, fontSize = if (word != null && !revealed) 34.sp else 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            face.detail?.let {
                Spacer(GlanceModifier.height(4.dp))
                Text(it, style = TextStyle(color = WidgetInk.ink2, fontSize = 16.sp, textAlign = TextAlign.Center))
            }
            if (word != null && !revealed) {
                Spacer(GlanceModifier.height(6.dp))
                Text("Tap for reading and meaning", style = TextStyle(color = WidgetInk.ink2, fontSize = 12.sp))
            }
        }
    }
}

/** Flips the word face. The state lives in this widget instance's own preferences. */
class FlipWordAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val today = LocalDate.now().toEpochDay()
        updateAppWidgetState(context, glanceId) { p ->
            val next = WordFlip.toggled(p[WordDayKey] ?: -1, p[WordFlagKey] ?: false, today)
            p[WordFlagKey] = next
            p[WordDayKey] = today
        }
        RenWordWidget().update(context, glanceId)
    }
}

class RenWordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RenWordWidget()
}
