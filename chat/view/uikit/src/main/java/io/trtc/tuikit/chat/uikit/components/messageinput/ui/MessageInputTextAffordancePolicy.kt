package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputSurfaceState

internal object MessageInputTextAffordancePolicy {
    fun shouldHideInputHint(
        surface: InputSurfaceState,
        keyboardHeight: Int
    ): Boolean {
        return surface != InputSurfaceState.NONE || keyboardHeight > 0
    }
}
