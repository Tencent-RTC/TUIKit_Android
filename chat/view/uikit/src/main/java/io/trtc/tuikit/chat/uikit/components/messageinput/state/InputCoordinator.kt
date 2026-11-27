package io.trtc.tuikit.chat.uikit.components.messageinput.state
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.KeyboardBridge
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

class InputCoordinator(
    private val keyboardBridge: KeyboardBridge,
    private val panelHeightProvider: PanelHeightProvider
) : KeyboardBridge.Listener {

    private val _state = MutableStateFlow(InputUiState())
    val state: StateFlow<InputUiState> = _state.asStateFlow()

    private val _effects = Channel<PanelEffect>(capacity = Channel.UNLIMITED)
    val effects: Flow<PanelEffect> = _effects.receiveAsFlow()

    private val surfaceReducer = InputSurfaceReducer()
    private val overlayReducer = OverlayStateReducer()

    fun dispatch(event: InputEvent) {
        Log.d(TAG, "dispatch: $event | currentSurfaceState=${surfaceReducer.currentState}")
        when (event) {
            is PanelEvent -> handlePanelEvent(event)
            is InputModeEvent -> handleInputModeEvent(event)
            is OverlayEvent -> handleOverlayEvent(event)
        }
    }

    override fun onKeyboardHeightChanged(height: Int, isVisible: Boolean) {
        Log.d(TAG, "onKeyboardHeightChanged: height=$height visible=$isVisible surfaceState=${surfaceReducer.currentState}")

        updateState { copy(keyboardHeight = if (isVisible) height else 0) }

        when {
            !isVisible -> {
                if (surfaceReducer.currentState.transition == PanelTransitionState.KEYBOARD_TO_PANEL) {
                    Log.d(TAG, "  → keyboard hidden after panel request, settling panel transition")
                    handlePanelEvent(PanelEvent.KeyboardHidden)
                }
                if (surfaceReducer.currentState.transition == PanelTransitionState.PANEL_TO_KEYBOARD &&
                    surfaceReducer.currentState.surface == InputSurfaceState.KEYBOARD
                ) {
                    Log.d(TAG, "  → ignore stale visible=false while switching panel→keyboard")
                    return
                }
                if (surfaceReducer.currentState.surface == InputSurfaceState.KEYBOARD) {
                    Log.d(TAG, "  → keyboard hidden while surface=KEYBOARD, dispatching KeyboardHidden")
                    handlePanelEvent(PanelEvent.KeyboardHidden)
                }
            }
            isVisible && surfaceReducer.currentState.surface != InputSurfaceState.KEYBOARD -> {
                if (surfaceReducer.currentState.transition == PanelTransitionState.KEYBOARD_TO_PANEL) {
                    Log.d(TAG, "  → ignore stale visible=true while switching keyboard→panel")
                    return
                }
                Log.d(TAG, "  → keyboard shown while surfaceState=${surfaceReducer.currentState}, following to KEYBOARD")
                handlePanelEvent(PanelEvent.RequestKeyboard)
            }
        }
        if (isVisible) {
            if (surfaceReducer.currentState.transition == PanelTransitionState.PANEL_TO_KEYBOARD) {
                Log.d(TAG, "  → keyboard shown after keyboard request, settling keyboard transition")
                handlePanelEvent(PanelEvent.KeyboardShown)
            }
        }

        if (isVisible && surfaceReducer.currentState.surface == InputSurfaceState.KEYBOARD && _state.value.panelTargetHeight > 0) {
            Log.d(TAG, "  → keyboard settled, clearing residual panelTargetHeight=${_state.value.panelTargetHeight}")
            updateState { copy(panelTargetHeight = 0) }
        }
    }

    override fun onKeyboardHeightChanging(currentHeight: Int) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onKeyboardHeightChanging: $currentHeight")
        }
        val shouldClearResidualPanelTarget = shouldClearResidualPanelTargetForKeyboardAnimation(currentHeight)
        updateState {
            if (shouldClearResidualPanelTarget) {
                Log.d(TAG, "  → keyboard changing, clearing residual panelTargetHeight=$panelTargetHeight")
                copy(keyboardHeight = currentHeight, panelTargetHeight = 0)
            } else {
                copy(keyboardHeight = currentHeight)
            }
        }
    }

    var density: Float = 2.75f

    private val minPanelHeightPx: Int get() = (MIN_PANEL_HEIGHT_DP * density).toInt()
    private val emojiPanelDefaultPx: Int get() = (EMOJI_PANEL_DEFAULT_HEIGHT_DP * density).toInt()
    private val morePanelDefaultPx: Int get() = (MORE_PANEL_DEFAULT_HEIGHT_DP * density).toInt()

    fun resolveTargetPanelHeight(panel: PanelState): Int {
        val preferred = panelHeightProvider.getPreferredHeight(panel)
        val fallback = when (panel) {
            PanelState.MORE_PANEL -> morePanelDefaultPx
            else -> emojiPanelDefaultPx
        }
        val maxForPanel = when (panel) {
            PanelState.MORE_PANEL -> morePanelDefaultPx
            else -> Int.MAX_VALUE
        }
        if (preferred > 0) {
            return preferred.coerceAtLeast(minPanelHeightPx).coerceAtMost(maxForPanel)
        }
        if (keyboardBridge.hasRecordedKeyboardHeight()) {
            val savedKbHeight = keyboardBridge.getKeyboardHeight()
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
        if (!keyboardBridge.hasRecordedKeyboardHeight()) {
            return false
        }
        val recordedKeyboardHeight = keyboardBridge.getKeyboardHeight()
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
        } else 0

        Log.d(TAG, "handlePanelEvent: $event targetHeight=$targetHeight")

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
            else -> 0
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
            Log.d(TAG, "  → exiting VOICE mode due to $event")
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
        overlayReducer.handleEvent(event)
        updateState { copy(overlay = overlayReducer.state.value) }
    }

    private fun executeEffect(effect: PanelEffect) {
        Log.d(TAG, "executeEffect: $effect")
        val result = _effects.trySend(effect)
        if (result.isFailure) {
            Log.w(TAG, "drop effect because channel is closed: $effect", result.exceptionOrNull())
        }
    }

    private inline fun updateState(transform: InputUiState.() -> InputUiState) {
        val old = _state.value
        _state.update { it.transform() }
        val new = _state.value
        if (old != new) {
            Log.d(
                TAG,
                "stateUpdate: panel=${old.panel}→${new.panel} " +
                    "surface=${old.surface}→${new.surface} " +
                    "transition=${old.transition}→${new.transition} " +
                    "mode=${old.inputMode}→${new.inputMode} " +
                    "panelTarget=${old.panelTargetHeight}→${new.panelTargetHeight} " +
                    "kbH=${old.keyboardHeight}→${new.keyboardHeight}"
            )
        }
    }

    companion object {
        private const val TAG = "MsgInput.Coord"
        const val MIN_PANEL_HEIGHT_DP = 180
        const val EMOJI_PANEL_DEFAULT_HEIGHT_DP = 280
        const val MORE_PANEL_DEFAULT_HEIGHT_DP = 220
    }
}

fun interface PanelHeightProvider {
    fun getPreferredHeight(panel: PanelState): Int
}
