package io.trtc.tuikit.chat.uikit.components.messageinput.state
import android.util.Log

class InputSurfaceReducer {

    data class TransitionResult(
        val newState: InputSurfaceSnapshot,
        val effects: List<PanelEffect>
    )

    var currentState: InputSurfaceSnapshot = InputSurfaceSnapshot.NONE
        private set

    fun transition(event: PanelEvent): TransitionResult {
        val oldState = currentState
        val (newState, effects) = resolve(currentState, event)
        currentState = newState
        Log.d(TAG, "FSM: $oldState + $event → $newState | effects=$effects")
        return TransitionResult(newState, effects)
    }

    companion object {
        private const val TAG = "MsgInput.SurfaceReducer"
    }

    fun reset() {
        currentState = InputSurfaceSnapshot.NONE
    }

    private fun resolve(
        current: InputSurfaceSnapshot,
        event: PanelEvent
    ): Pair<InputSurfaceSnapshot, List<PanelEffect>> {
        return when (current.surface) {
            InputSurfaceState.NONE -> resolveFromNone(event)
            InputSurfaceState.KEYBOARD -> resolveFromKeyboard(current, event)
            InputSurfaceState.PANEL -> resolveFromPanel(current, event)
            else -> InputSurfaceSnapshot.NONE to emptyList()
        }
    }

    private fun resolveFromNone(
        event: PanelEvent
    ): Pair<InputSurfaceSnapshot, List<PanelEffect>> = when (event) {
        PanelEvent.RequestKeyboard -> InputSurfaceSnapshot.KEYBOARD to listOf(
            PanelEffect.RequestEditTextFocus,
            PanelEffect.ShowKeyboard
        )
        PanelEvent.RequestEmojiPanel -> InputSurfaceSnapshot.panel(PanelState.EMOJI_PANEL) to listOf(
            PanelEffect.SetPanelContent(PanelState.EMOJI_PANEL, crossfade = false)
        )
        PanelEvent.RequestMorePanel -> InputSurfaceSnapshot.panel(PanelState.MORE_PANEL) to listOf(
            PanelEffect.ClearEditTextFocus,
            PanelEffect.SetPanelContent(PanelState.MORE_PANEL, crossfade = false)
        )
        else -> InputSurfaceSnapshot.NONE to emptyList()
    }

    private fun resolveFromKeyboard(
        current: InputSurfaceSnapshot,
        event: PanelEvent
    ): Pair<InputSurfaceSnapshot, List<PanelEffect>> = when (event) {
        PanelEvent.RequestEmojiPanel -> InputSurfaceSnapshot.panel(
            PanelState.EMOJI_PANEL,
            PanelTransitionState.KEYBOARD_TO_PANEL
        ) to listOf(
            PanelEffect.HideKeyboardKeepFocus,
            PanelEffect.SetPanelContent(PanelState.EMOJI_PANEL, crossfade = false)
        )
        PanelEvent.RequestMorePanel -> InputSurfaceSnapshot.panel(
            PanelState.MORE_PANEL,
            PanelTransitionState.KEYBOARD_TO_PANEL
        ) to listOf(
            PanelEffect.HideKeyboardKeepFocus,
            PanelEffect.ClearEditTextFocus,
            PanelEffect.SetPanelContent(PanelState.MORE_PANEL, crossfade = false)
        )
        PanelEvent.RequestCollapse -> InputSurfaceSnapshot.NONE to listOf(PanelEffect.HideKeyboard)
        PanelEvent.KeyboardHidden -> if (current.transition == PanelTransitionState.PANEL_TO_KEYBOARD) {
            current to emptyList()
        } else {
            InputSurfaceSnapshot.NONE to emptyList()
        }
        PanelEvent.KeyboardShown -> InputSurfaceSnapshot.KEYBOARD to emptyList()
        else -> current to emptyList()
    }

    private fun resolveFromPanel(
        current: InputSurfaceSnapshot,
        event: PanelEvent
    ): Pair<InputSurfaceSnapshot, List<PanelEffect>> {
        if (event == PanelEvent.KeyboardHidden &&
            current.transition == PanelTransitionState.KEYBOARD_TO_PANEL &&
            current.panel != null
        ) {
            return InputSurfaceSnapshot.panel(current.panel) to emptyList()
        }
        return when (current.panel) {
            PanelState.EMOJI_PANEL -> resolveFromEmojiPanel(event)
            PanelState.MORE_PANEL -> resolveFromMorePanel(event)
            null -> InputSurfaceSnapshot.NONE to emptyList()
            else -> InputSurfaceSnapshot.NONE to emptyList()
        }
    }

    private fun resolveFromEmojiPanel(
        event: PanelEvent
    ): Pair<InputSurfaceSnapshot, List<PanelEffect>> = when (event) {
        PanelEvent.RequestKeyboard -> InputSurfaceSnapshot.PANEL_TO_KEYBOARD to listOf(
            PanelEffect.HidePanelContent,
            PanelEffect.RequestEditTextFocus,
            PanelEffect.ShowKeyboard
        )
        PanelEvent.RequestEmojiPanel -> InputSurfaceSnapshot.NONE to listOf(PanelEffect.HidePanelContent)
        PanelEvent.RequestMorePanel -> InputSurfaceSnapshot.panel(PanelState.MORE_PANEL) to listOf(
            PanelEffect.ClearEditTextFocus,
            PanelEffect.SetPanelContent(PanelState.MORE_PANEL, crossfade = true)
        )
        PanelEvent.RequestCollapse -> InputSurfaceSnapshot.NONE to listOf(PanelEffect.HidePanelContent)
        else -> InputSurfaceSnapshot.panel(PanelState.EMOJI_PANEL) to emptyList()
    }

    private fun resolveFromMorePanel(
        event: PanelEvent
    ): Pair<InputSurfaceSnapshot, List<PanelEffect>> = when (event) {
        PanelEvent.RequestKeyboard -> InputSurfaceSnapshot.PANEL_TO_KEYBOARD to listOf(
            PanelEffect.HidePanelContent,
            PanelEffect.RequestEditTextFocus,
            PanelEffect.ShowKeyboard
        )
        PanelEvent.RequestEmojiPanel -> InputSurfaceSnapshot.panel(PanelState.EMOJI_PANEL) to listOf(
            PanelEffect.SetPanelContent(PanelState.EMOJI_PANEL, crossfade = true)
        )
        PanelEvent.RequestMorePanel -> InputSurfaceSnapshot.PANEL_TO_KEYBOARD to listOf(
            PanelEffect.HidePanelContent,
            PanelEffect.RequestEditTextFocus,
            PanelEffect.ShowKeyboard
        )
        PanelEvent.RequestCollapse -> InputSurfaceSnapshot.NONE to listOf(PanelEffect.HidePanelContent)
        else -> InputSurfaceSnapshot.panel(PanelState.MORE_PANEL) to emptyList()
    }
}
