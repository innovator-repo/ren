package app.ren.core.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Spec §11: one StateFlow<UiState>, one onIntent dispatcher, and side effects (haptics,
 * navigation, audio) through a buffered channel collected by the screen.
 */
abstract class MviViewModel<S, I, E>(initial: S) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    abstract fun onIntent(intent: I)

    protected val current: S get() = _state.value
    protected fun setState(reducer: S.() -> S) = _state.update(reducer)
    protected fun emit(effect: E) { _effects.trySend(effect) }
}

/** Collects one-off effects only while the screen is at least STARTED. */
@Composable
fun <E> CollectEffects(effects: Flow<E>, onEffect: suspend (E) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler(it) }
        }
    }
}
