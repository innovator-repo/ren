package app.ren.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import androidx.glance.text.TextStyle
import app.ren.R
import app.ren.data.local.StudyDao
import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.repository.PreferencesRepository
import app.ren.domain.usecase.Streak
import app.ren.work.DeepLinks
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun studyDao(): StudyDao
    fun preferences(): PreferencesRepository
}

private data class WidgetData(val due: Int, val retention: Int, val streak: Int)

/** Home-screen widget (design: "Outside the app"): retention ring, due count, streak, quick session. */
class RenWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        val ring = ringBitmap(data.retention / 100f, context.getColor(R.color.bamboo))
        provideContent { Content(data, ring, context) }
    }

    private suspend fun load(context: Context): WidgetData {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val now = System.currentTimeMillis()
        val cards = entry.studyDao().observeCards().first()
        val due = cards.count { it.dueTimestamp <= now }
        val reviewed = cards.filter { it.state == CardState.Review.value }
        val retention = if (reviewed.isEmpty()) 100 else (reviewed.map {
            FSRSScheduler.calculateRetrievability((now - it.lastReviewTimestamp).toDouble() / FSRSScheduler.DAY_MS, it.stability)
        }.average() * 100).roundToInt()
        val prefs = entry.preferences().current()
        return WidgetData(due, retention, Streak.current(prefs, LocalDate.now().toEpochDay()))
    }

    @Composable
    private fun Content(data: WidgetData, ring: Bitmap, context: Context) {
        val ink = ColorProvider(Color(0xFF111111), Color(0xFFF5F5F7))
        val ink2 = ColorProvider(Color(0xFF8E8E93), Color(0xFF8E8E93))
        val akane = ColorProvider(Color(0xFFE03E36), Color(0xFFFF453A))
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(ColorProvider(Color(0xF7FFFFFF), Color(0xF71C1C1E))).padding(16.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(60.dp), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(ring), contentDescription = "Retention ${data.retention} percent", modifier = GlanceModifier.size(60.dp))
                    Text("${data.retention}%", style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.width(14.dp))
                Column {
                    Text(if (data.due > 0) "${data.due} due" else "All caught up", style = TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    Text("🔥 ${data.streak}-day streak", style = TextStyle(color = ink2, fontSize = 13.sp))
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            Box(
                GlanceModifier.fillMaxWidth().height(42.dp).cornerRadius(12.dp).background(akane)
                    .clickable(actionStartActivity(DeepLinks.sessionIntent(context, quick = true))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (data.due > 0) "Quick ${minOf(DeepLinks.QUICK_SIZE, data.due)}-card session" else "Open Ren",
                    style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }

    private fun ringBitmap(progress: Float, color: Int): Bitmap {
        val size = 180
        val stroke = 18f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        val rect = RectF(stroke, stroke, size - stroke, size - stroke)
        paint.color = color; paint.alpha = 50
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.alpha = 255
        canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
        return bmp
    }
}

class RenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RenWidget()
}

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun refresh(context: Context) {
        scope.launch { runCatching { RenWidget().updateAll(context.applicationContext) } }
    }
}
