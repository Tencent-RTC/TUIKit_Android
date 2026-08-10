package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputMode
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputSurfaceState

internal object MessageInputLongPressRecordingPolicy {
    fun shouldArmRecording(
        inputMode: InputMode,
        surface: InputSurfaceState,
        isLongPressToTalkEnabled: Boolean,
        hasInputText: Boolean,
    ): Boolean {
        return isLongPressToTalkEnabled &&
            !hasInputText &&
            inputMode == InputMode.TEXT &&
            surface == InputSurfaceState.NONE
    }
}
