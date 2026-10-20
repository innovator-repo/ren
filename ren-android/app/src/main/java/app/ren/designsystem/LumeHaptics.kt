package app.ren.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition as C
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** The Lume haptic vocabulary shared by every app, from lightest to heaviest. */
enum class HapticToken {
    /** Moving around: a tab, a segment, a stroke traced, a petal on the flick keyboard. */
    TICK,
    /** An answer submitted, a card flipped, a key committed. */
    CLICK,
    /** Done: a task checked off, a lesson finished, a session complete. */
    CHECK_COMPLETE,
    /** A latch sliding shut: an irreversible commitment. */
    SEAL_LOCK,
    /** A calm unveiling: a flipped card, an unlocked stage. */
    REVEAL_SWELL,
    /** One slow breath: rise, hold, fall. */
    BREATH_WAVE,
    /** Non-punitive course correction ("safe failure"): two low taps. */
    GENTLE_NUDGE,
}

interface LumeHaptics {
    fun play(token: HapticToken)

    fun tick() = play(HapticToken.TICK)
    fun click() = play(HapticToken.CLICK)
    fun checkComplete() = play(HapticToken.CHECK_COMPLETE)
    fun sealLock() = play(HapticToken.SEAL_LOCK)
    fun revealSwell() = play(HapticToken.REVEAL_SWELL)
    fun breathWave() = play(HapticToken.BREATH_WAVE)
    fun gentleNudge() = play(HapticToken.GENTLE_NUDGE)
}

object NoHaptics : LumeHaptics {
    override fun play(token: HapticToken) = Unit
}

/**
 * Plays haptics from composed vibration primitives where the actuator has them (Android 11+),
 * falling back to predefined effects, then short pulses. Uses touch usage so the system
 * "touch feedback" setting applies, and the app's own Haptics switch on top.
 */
class VibratorHaptics(context: Context, private val enabled: () -> Boolean) : LumeHaptics {
    private val app = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val effects = HashMap<HapticToken, VibrationEffect?>()

    override fun play(token: HapticToken) {
        val v = vibrator ?: return
        if (!enabled() || !v.hasVibrator() || !systemTouchFeedbackOn()) return
        val effect = effects.getOrPut(token) { build(token, v) } ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            else @Suppress("DEPRECATION") v.vibrate(effect)
        }
    }

    // From Android 13 the touch usage makes the system apply the setting itself.
    private fun systemTouchFeedbackOn(): Boolean = Build.VERSION.SDK_INT >= 33 ||
        Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun build(token: HapticToken, v: Vibrator): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= 30) composed(token, v)?.let { return it }
        if (Build.VERSION.SDK_INT >= 29) {
            return VibrationEffect.createPredefined(
                when (token) {
                    HapticToken.TICK, HapticToken.REVEAL_SWELL -> VibrationEffect.EFFECT_TICK
                    HapticToken.CLICK, HapticToken.BREATH_WAVE -> VibrationEffect.EFFECT_CLICK
                    HapticToken.CHECK_COMPLETE, HapticToken.SEAL_LOCK, HapticToken.GENTLE_NUDGE -> VibrationEffect.EFFECT_DOUBLE_CLICK
                },
            )
        }
        return when (token) {
            HapticToken.TICK, HapticToken.REVEAL_SWELL -> VibrationEffect.createOneShot(8, 90)
            HapticToken.CLICK, HapticToken.BREATH_WAVE -> VibrationEffect.createOneShot(14, 160)
            HapticToken.GENTLE_NUDGE -> VibrationEffect.createWaveform(longArrayOf(0, 16, 80, 16), intArrayOf(0, 200, 0, 200), -1)
            HapticToken.CHECK_COMPLETE -> VibrationEffect.createWaveform(longArrayOf(0, 14, 40, 8), intArrayOf(0, 230, 0, 90), -1)
            HapticToken.SEAL_LOCK -> VibrationEffect.createWaveform(longArrayOf(0, 12, 20, 18, 30, 8), intArrayOf(0, 140, 0, 255, 0, 100), -1)
        }
    }

    /** One primitive of a composition: (primitive, scale, delay in ms before it). */
    private data class Step(val primitive: Int, val scale: Float, val delayMs: Int)

    private fun composed(token: HapticToken, v: Vibrator): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 30) return null
        
        val lowTick = if (Build.VERSION.SDK_INT >= 31) C.PRIMITIVE_LOW_TICK else C.PRIMITIVE_TICK
        val steps = when (token) {
            HapticToken.TICK -> listOf(Step(C.PRIMITIVE_TICK, 0.5f, 0))
            HapticToken.CLICK -> listOf(Step(C.PRIMITIVE_CLICK, 0.65f, 0))
            HapticToken.CHECK_COMPLETE -> listOf(Step(C.PRIMITIVE_CLICK, 0.9f, 0), Step(C.PRIMITIVE_TICK, 0.35f, 50))
            HapticToken.SEAL_LOCK -> listOf(Step(C.PRIMITIVE_QUICK_RISE, 0.55f, 0), Step(C.PRIMITIVE_CLICK, 1f, 30), Step(C.PRIMITIVE_TICK, 0.4f, 70))
            HapticToken.REVEAL_SWELL -> listOf(Step(C.PRIMITIVE_SLOW_RISE, 0.6f, 0), Step(C.PRIMITIVE_TICK, 0.5f, 20))
            HapticToken.BREATH_WAVE -> listOf(Step(C.PRIMITIVE_SLOW_RISE, 0.7f, 0), Step(C.PRIMITIVE_TICK, 0.4f, 400), Step(C.PRIMITIVE_QUICK_FALL, 0.7f, 400))
            HapticToken.GENTLE_NUDGE -> listOf(Step(lowTick, 0.75f, 0), Step(lowTick, 0.75f, 100))
        }
        if (!v.areAllPrimitivesSupported(*steps.map { it.primitive }.distinct().toIntArray())) return null
        val composition = VibrationEffect.startComposition()
        for (s in steps) composition.addPrimitive(s.primitive, s.scale, s.delayMs)
        return composition.compose()
    }
}

val LocalHaptics = staticCompositionLocalOf<LumeHaptics> { NoHaptics }

@Composable
fun rememberHaptics(enabled: () -> Boolean): LumeHaptics {
    val context = LocalContext.current
    return remember(context) { VibratorHaptics(context.applicationContext, enabled) }
}
