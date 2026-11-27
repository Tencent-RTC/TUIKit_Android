package io.trtc.tuikit.chat.uikit.components.common
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

object EventBus {
    private val _events = MutableSharedFlow<Any>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun post(event: Any): Boolean {
        return _events.tryEmit(event)
    }

    inline fun post(crossinline block: () -> Any): Boolean {
        return post(block())
    }

    suspend fun emit(event: Any) {
        _events.emit(event)
    }

    suspend inline fun emit(crossinline block: () -> Any) {
        emit(block())
    }
}

inline fun <reified T> EventBus.observeOn(
    lifecycleOwner: LifecycleOwner,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline action: (T) -> Unit
): Job {
    return lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(state) {
            runCatching {
                events.filterIsInstance<T>().collect { action(it) }
            }
        }
    }
}

inline fun <reified T> EventBus.observeOn(
    scope: CoroutineScope,
    crossinline action: (T) -> Unit
): Job {
    return scope.launch {
        events.filterIsInstance<T>().collect { action(it) }
    }
}

inline fun <reified T> CoroutineScope.onEvent(
    crossinline action: (T) -> Unit
) = EventBus.observeOn<T>(this, action = action)

inline fun <reified T> LifecycleOwner.onEvent(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline action: (T) -> Unit
) = EventBus.observeOn<T>(this, state = state, action = action)
