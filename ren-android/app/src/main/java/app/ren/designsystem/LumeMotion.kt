package app.ren.designsystem

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos

/** Lume motion tokens, identical in every app of the suite. */
object LumeMotion {
    const val PRESSED_SCALE = 0.97f
    const val BREATH_MILLIS = 4000

    /** Movement, scale, sheets and card flips: a little life, no wobble. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = 380f)

    /** Colour shifts, progress fills, opacity: no overshoot, so nothing flickers. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 1500f)

    /** One half of a breath (in or out): EaseInOutSine over four seconds. */
    fun <T> breathing(): FiniteAnimationSpec<T> = tween(BREATH_MILLIS, easing = EaseInOutSine)

    val EaseInOutSine = Easing { x -> -(cos(PI.toFloat() * x) - 1f) / 2f }
}

/**
 * Press feel: a 3% shrink and slight dim on touch-down that springs back on release, with a TICK
 * the moment the finger lands.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    label: String? = null,
    role: Role = Role.Button,
    scaleDown: Float = LumeMotion.PRESSED_SCALE,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed && enabled) 1f else 0f, LumeMotion.spatial(), label = "press")
    val haptics = LocalHaptics.current
    LaunchedEffect(pressed, enabled) { if (pressed && enabled && haptic) haptics.tick() }
    return this
        .graphicsLayer {
            val s = 1f - (1f - scaleDown) * p
            scaleX = s
            scaleY = s
            alpha = 1f - 0.15f * p
        }
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
        .clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick)
}
