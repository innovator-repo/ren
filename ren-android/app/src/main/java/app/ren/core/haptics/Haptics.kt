package app.ren.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Spec §2 haptic vocabulary. */
interface Haptics {
    /** A stroke traced correctly, a key flicked into a new direction. */
    fun tick()
    /** A card flip or an answer submitted. */
    fun click()
    /** A stroke error or a wrong answer: two heavy clicks. */
    fun error()
    /** Session complete. */
    fun success()
}

object NoHaptics : Haptics {
    override fun tick() = Unit
    override fun click() = Unit
    override fun error() = Unit
    override fun success() = Unit
}

class VibratorHaptics(context: Context, private val enabled: () -> Boolean) : Haptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val primitives: Boolean = Build.VERSION.SDK_INT >= 30 && vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
    ) == true

    private fun play(effect: () -> VibrationEffect) {
        val v = vibrator ?: return
        if (!enabled() || !v.hasVibrator()) return
        runCatching { v.vibrate(effect()) }
    }

    override fun tick() = play {
        if (primitives && Build.VERSION.SDK_INT >= 30) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f).compose()
        } else if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        else VibrationEffect.createOneShot(8, 60)
    }

    override fun click() = play {
        if (primitives && Build.VERSION.SDK_INT >= 30) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f).compose()
        } else if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        else VibrationEffect.createOneShot(14, 120)
    }

    override fun error() = play {
        if (primitives && Build.VERSION.SDK_INT >= 30) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 90)
                .compose()
        } else VibrationEffect.createWaveform(longArrayOf(0, 22, 90, 22), intArrayOf(0, 255, 0, 255), -1)
    }

    override fun success() = play {
        if (primitives && Build.VERSION.SDK_INT >= 30) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 60)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 60)
                .compose()
        } else VibrationEffect.createWaveform(longArrayOf(0, 10, 60, 10, 60, 20), -1)
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { NoHaptics }

@Composable
fun rememberHaptics(enabled: () -> Boolean): Haptics {
    val context = LocalContext.current
    return remember(context) { VibratorHaptics(context.applicationContext, enabled) }
}
