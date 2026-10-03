package app.ren.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.RenMotion
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet drawn inside the current screen. Drag the grabber or the body down to dismiss;
 * a quick flick dismisses even from a short drag (spec §2 velocity-tracked dismissal).
 */
@Composable
fun BoxScope.BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ren.colors
    val scope = rememberCoroutineScope()
    // 0 = fully open, 1 = fully closed.
    val closed = remember { Animatable(if (visible) 0f else 1f) }
    var height by remember { mutableFloatStateOf(1f) }
    var rendered by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            rendered = true
            closed.animateTo(0f, RenMotion.spring())
        } else if (rendered) {
            closed.animateTo(1f, tween(220))
            rendered = false
        }
    }
    if (!rendered) return

    BackHandler(enabled = visible, onBack = onDismiss)
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { alpha = 1f - closed.value }
            .background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    )
    val drag = rememberDraggableState { delta ->
        scope.launch { closed.snapTo((closed.value + delta / height).coerceAtLeast(-0.04f)) }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationY = closed.value.coerceAtLeast(0f) * height }
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(c.surface)
            .draggable(
                drag, Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (velocity > 1100f || closed.value > 0.28f) onDismiss()
                    else closed.animateTo(0f, RenMotion.spring())
                },
            )
            .clickable(remember { MutableInteractionSource() }, null) { }
            .padding(bottom = bottom + 12.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 5.dp).clip(RoundedCornerShape(50)).background(c.ink3))
        }
        content()
    }
}
