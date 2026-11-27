package io.trtc.tuikit.chat.uikit.components.messageinput.state

import android.os.Handler
import android.os.Looper
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.KeyboardBridge
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Port of the View version's InputCoordinator. Heights are panel-spacer pixels
 * (IME inset minus navigation bar), matching the View implementation.
 */
class InputCoordinator(
    private val recordedKeyboardHeightProvider: RecordedKeyboardHeightProvider
) : KeyboardBridge.Listener {
    interface RecordedKeyboardHeightProvider {
        fun hasRecordedKeyboardHeight(): Boolean
        fun getRecordedKeyboardHeight(): Int
    }

    // Synchronous forwarding of the IME animation running state, deliberately NOT routed
    // through StateFlow, so observers (e.g. the message list) can react in the same frame
    // as the insets animation callback.
    var keyboardAnimationRunningCallback: ((Boolean) -> Unit)? = null

    // The animation running callback alone leaves gaps: snap dispatches when the system skips
    // the IME animation, and the window between an interrupted animation's onEnd and the next
    // onStart, change the keyboard height while no animation is reported as running. Raising
    // the same flag synchronously on every height change (and lowering it shortly after the
    // changes settle) keeps observers suppressing placement animations for exactly the period
    // in which the bottom input surface height is moving.
    private val keyboardHeightSettleHandler = Handler(Looper.getMainLooper())
    private var keyboardHeightSettleEndRunnable: Runnable? = null
    private var keyboardHeightChangingNotified = false

    private fun markKeyboardHeightChanging() {
        if (!keyboardHeightChangingNotified) {
            keyboardHeightChangingNotified = true
            keyboardAnimationRunningCallback?.invoke(true)
        }
        keyboardHeightSettleEndRunnable?.let { keyboardHeightSettleHandler.removeCallbacks(it) }
        val runnable = Runnable {
            keyboardHeightSettleEndRunnable = null
            keyboardHeightChangingNotified = false
            keyboardAnimationRunningCallback?.invoke(false)
        }
        keyboardHeightSettleEndRunnable = runnable
        keyboardHeightSettleHandler.postDelayed(runnable, KEYBOARD_HEIGHT_SETTLE_TIMEOUT_MS)
    }

    private val _state = MutableStateFlow(InputUiState())
    val state: StateFlow<InputUiState> = _state.asStateFlow()

    private val _effects = Channel<PanelEffect>(capacity = Channel.UNLIMITED)
    val effects: Flow<PanelEffect> = _effects.receiveAsFlow()

    private val surfaceReducer = InputSurfaceReducer()
    private val _overlayState = MutableStateFlow(OverlayState())

    var density: Float = 2.75f

    private val minPanelHeightPx: Int get() = (MIN_PANEL_HEIGHT_DP * density).toInt()
    private val emojiPanelDefaultPx: Int get() = (EMOJI_PANEL_DEFAULT_HEIGHT_DP * density).toInt()
    private val morePanelDefaultPx: Int get() = (MORE_PANEL_DEFAULT_HEIGHT_DP * density).toInt()

    fun dispatch(event: InputEvent) {
        when (event) {
            is PanelEvent -> handlePanelEvent(event)
            is InputModeEvent -> handleInputModeEvent(event)
            is OverlayEvent -> handleOverlayEvent(event)
        }
    }

    override fun onKeyboardHeightChanged(height: Int, isVisible: Boolean) {
        if (_state.value.keyboardHeight != height) {
            markKeyboardHeightChanging()
        }
        // The bridge already reports the effective height (0 when hidden), including the
        // intermediate heights of an interrupted animation; keep the surface tracking it
        // exactly instead of snapping to 0 while the IME is still settling.
        updateState { copy(keyboardHeight = height) }

        when {
            !isVisible -> {
                if (surfaceReducer.currentState.transition == PanelTransitionState.KEYBOARD_TO_PANEL) {
                    handlePanelEvent(PanelEvent.KeyboardHidden)
                }
                if (surfaceReducer.currentState.transition == PanelTransitionState.PANEL_TO_KEYBOARD &&
                    surfaceReducer.currentState.surface == InputSurfaceState.KEYBOARD
                ) {
                    return
                }
                if (surfaceReducer.currentState.surface == InputSurfaceState.KEYBOARD) {
                    handlePanelEvent(PanelEvent.KeyboardHidden)
                }
            }

            isVisible && surfaceReducer.currentState.surface != InputSurfaceState.KEYBOARD -> {
                if (surfaceReducer.currentState.transition == PanelTransitionState.KEYBOARD_TO_PANEL) {
                    return
                }
                handlePanelEvent(PanelEvent.RequestKeyboard)
            }
        }
        if (isVisible) {
            if (surfaceReducer.currentState.transition == PanelTransitionState.PANEL_TO_KEYBOARD) {
                handlePanelEvent(PanelEvent.KeyboardShown)
            }
        }

        if (isVisible && surfaceReducer.currentState.surface == InputSurfaceState.KEYBOARD &&
            _state.value.panelTargetHeight > 0
        ) {
            updateState { copy(panelTargetHeight = 0) }
        }
    }

    override fun onKeyboardAnimationRunningChanged(isRunning: Boolean) {
        keyboardAnimationRunningCallback?.invoke(isRunning)
    }

    override fun onKeyboardHeightChanging(currentHeight: Int) {
        if (_state.value.keyboardHeight != currentHeight) {
            markKeyboardHeightChanging()
        }
        val shouldClearResidualPanelTarget = shouldClearResidualPanelTargetForKeyboardAnimation(currentHeight)
        updateState {
            if (shouldClearResidualPanelTarget) {
                copy(keyboardHeight = currentHeight, panelTargetHeight = 0)
            } else {
                copy(keyboardHeight = currentHeight)
            }
        }
    }

    fun resolveTargetPanelHeight(panel: PanelState): Int {
        val fallback = when (panel) {
            PanelState.MORE_PANEL -> morePanelDefaultPx
            else -> emojiPanelDefaultPx
        }
        val maxForPanel = when (panel) {
            PanelState.MORE_PANEL -> morePanelDefaultPx
            else -> Int.MAX_VALUE
        }
        if (recordedKeyboardHeightProvider.hasRecordedKeyboardHeight()) {
            val savedKbHeight = recordedKeyboardHeightProvider.getRecordedKeyboardHeight()
            if (savedKbHeight > 0) {
                return maxOf(savedKbHeight, fallback).coerceAtLeast(minPanelHeightPx).coerceAtMost(maxForPanel)
            }
        }
        return fallback.coerceAtMost(maxForPanel)
    }

    private fun shouldClearResidualPanelTargetForKeyboardAnimation(currentHeight: Int): Boolean {
        val panelTargetHeight = _state.value.panelTargetHeight
        if (currentHeight <= 0 ||
            panelTargetHeight <= 0 ||
            surfaceReducer.currentState.surface != InputSurfaceState.KEYBOARD
        ) {
            return false
        }
        if (surfaceReducer.currentState.transition == PanelTransitionState.PANEL_TO_KEYBOARD) {
            return true
        }
        if (!recordedKeyboardHeightProvider.hasRecordedKeyboardHeight()) {
            return false
        }
        val recordedKeyboardHeight = recordedKeyboardHeightProvider.getRecordedKeyboardHeight()
        return recordedKeyboardHeight in 1 until minPanelHeightPx &&
            currentHeight <= recordedKeyboardHeight
    }

    private fun handlePanelEvent(event: PanelEvent) {
        val panelForHeight = when (event) {
            PanelEvent.RequestEmojiPanel -> PanelState.EMOJI_PANEL
            PanelEvent.RequestMorePanel -> PanelState.MORE_PANEL
            else -> null
        }
        val targetHeight = if (panelForHeight != null) {
            resolveTargetPanelHeight(panelForHeight)
        } else {
            0
        }

        maybeExitVoiceMode(event)

        val priorState = _state.value

        val result = surfaceReducer.transition(event)

        val newPanelTargetHeight = when (result.newState.surface) {
            InputSurfaceState.PANEL -> if (panelForHeight != null) {
                targetHeight
            } else {
                _state.value.panelTargetHeight
            }

            InputSurfaceState.NONE -> 0
            InputSurfaceState.KEYBOARD -> {
                if (priorState.surface == InputSurfaceState.PANEL) {
                    _state.value.panelTargetHeight
                } else {
                    0
                }
            }
        }
        updateState {
            copy(
                surface = result.newState.surface,
                panel = result.newState.panel,
                transition = result.newState.transition,
                panelTargetHeight = newPanelTargetHeight
            )
        }

        for (effect in result.effects) {
            executeEffect(effect)
        }
    }

    private fun maybeExitVoiceMode(event: PanelEvent) {
        val requiresTextMode = event == PanelEvent.RequestKeyboard ||
            event == PanelEvent.RequestEmojiPanel ||
            event == PanelEvent.RequestMorePanel
        if (requiresTextMode && _state.value.inputMode == InputMode.VOICE) {
            updateState { copy(inputMode = InputMode.TEXT) }
        }
    }

    private fun handleInputModeEvent(event: InputModeEvent) {
        when (event) {
            InputModeEvent.SwitchToVoice -> {
                dispatch(PanelEvent.RequestCollapse)
                updateState { copy(inputMode = InputMode.VOICE) }
            }

            InputModeEvent.SwitchToText -> {
                updateState { copy(inputMode = InputMode.TEXT) }
                dispatch(PanelEvent.RequestKeyboard)
            }

            InputModeEvent.SwitchToTextCollapsed -> {
                updateState { copy(inputMode = InputMode.TEXT) }
                dispatch(PanelEvent.RequestCollapse)
            }
        }
    }

    private fun handleOverlayEvent(event: OverlayEvent) {
        when (event) {
            is OverlayEvent.SetQuote -> _overlayState.update { it.copy(quoteMessage = event.info) }
            OverlayEvent.ClearQuote -> _overlayState.update { it.copy(quoteMessage = null) }
            is OverlayEvent.SetVoiceRecording -> _overlayState.update { it.copy(voiceRecording = event.active) }
        }
        updateState { copy(overlay = _overlayState.value) }
    }

    private fun executeEffect(effect: PanelEffect) {
        _effects.trySend(effect)
    }

    private inline fun updateState(transform: InputUiState.() -> InputUiState) {
        _state.update { it.transform() }
    }

    companion object {
        const val MIN_PANEL_HEIGHT_DP = 180
        const val EMOJI_PANEL_DEFAULT_HEIGHT_DP = 280
        const val MORE_PANEL_DEFAULT_HEIGHT_DP = 220
        private const val KEYBOARD_HEIGHT_SETTLE_TIMEOUT_MS = 100L
    }
}
