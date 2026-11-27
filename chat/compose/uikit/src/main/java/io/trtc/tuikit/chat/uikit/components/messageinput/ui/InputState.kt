package io.trtc.tuikit.chat.uikit.components.messageinput.ui

enum class InputState {
    NONE,
    SHOW_KEYBOARD,
    SHOW_EMOJI_PANEL
}

enum class AudioRecordingState {
    IDLE,
    RECORDING,
    READY_TO_CANCEL,
    TOO_SHORT
} 