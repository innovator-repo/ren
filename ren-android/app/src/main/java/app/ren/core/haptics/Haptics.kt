package app.ren.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Spec §2 haptic vocabulary, from lightest to heaviest. */
interface Haptics {
    /** Moving around: a tab, a segment, a stroke traced, a petal on the flick keyboard. */
    fun tick()
    /** An answer submitted, a card flipped, a key committed. */
    fun click()
    /** A stroke error or a wrong answer: two low taps, "no". */
    fun error()
    /** Done: a lesson finished, a sentence saved, a session complete. */
    fun success()
}

object NoHaptics : Haptics {
    override fun tick() = Unit
    override fun click() = Unit
    override fun error() = Unit
    override fun success() = Unit
}

/**
 * Plays haptics from composed vibration primitives where the actuator has them (Android 11+),
 * falling back to predefined effects, then short pulses. Uses touch usage so the system
 * "touch feedback" setting applies, and Ren's own Haptics switch on top.
 */
class VibratorHaptics(context: Context, private val enabled: () -> Boolean) : Haptics {
    private enum class Kind { TICK, CLICK, ERROR, SUCCESS }

    private val app = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val effects = HashMap<Kind, VibrationEffect?>()

    override fun tick() = play(Kind.TICK)
    override fun click() = play(Kind.CLICK)
    override fun error() = play(Kind.ERROR)
    override fun success() = play(Kind.SUCCESS)

    private fun play(kind: Kind) {
        val v = vibrator ?: return
        if (!enabled() || !v.hasVibrator() || !systemTouchFeedbackOn()) return
        val effect = effects.getOrPut(kind) { build(kind, v) } ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            else @Suppress("DEPRECATION") v.vibrate(effect)
        }
    }

    // From Android 13 the touch usage makes the system apply the setting itself.
    private fun systemTouchFeedbackOn(): Boolean = Build.VERSION.SDK_INT >= 33 ||
        Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun build(kind: Kind, v: Vibrator): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= 30) composed(kind, v)?.let { return it }
        if (Build.VERSION.SDK_INT >= 29) {
            return VibrationEffect.createPredefined(
                when (kind) {
                    Kind.TICK -> VibrationEffect.EFFECT_TICK
                    Kind.CLICK -> VibrationEffect.EFFECT_CLICK
                    Kind.ERROR, Kind.SUCCESS -> VibrationEffect.EFFECT_DOUBLE_CLICK
                },
            )
        }
        return when (kind) {
            Kind.TICK -> VibrationEffect.createOneShot(8, 90)
            Kind.CLICK -> VibrationEffect.createOneShot(14, 160)
            Kind.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 16, 80, 16), intArrayOf(0, 200, 0, 200), -1)
            Kind.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 14, 90, 18), intArrayOf(0, 170, 0, 230), -1)
        }
    }

    private fun composed(kind: Kind, v: Vibrator): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 30) return null
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val lowTick = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else tick
        // (primitive, scale, delay ms)
        val steps = when (kind) {
            Kind.TICK -> listOf(Triple(tick, 0.5f, 0))
            Kind.CLICK -> listOf(Triple(click, 0.65f, 0))
            Kind.ERROR -> listOf(Triple(lowTick, 0.9f, 0), Triple(lowTick, 0.9f, 100))
            Kind.SUCCESS -> listOf(Triple(click, 0.7f, 0), Triple(click, 1f, 110))
        }
        if (!v.areAllPrimitivesSupported(*steps.map { it.first }.distinct().toIntArray())) return null
        val c = VibrationEffect.startComposition()
        for ((p, scale, delay) in steps) c.addPrimitive(p, scale, delay)
        return c.compose()
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { NoHaptics }

@Composable
fun rememberHaptics(enabled: () -> Boolean): Haptics {
    val context = LocalContext.current
    return remember(context) { VibratorHaptics(context.applicationContext, enabled) }
}
