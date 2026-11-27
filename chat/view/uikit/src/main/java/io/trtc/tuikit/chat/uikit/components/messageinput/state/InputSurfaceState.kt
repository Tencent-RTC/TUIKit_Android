package io.trtc.tuikit.chat.uikit.components.messageinput.state
enum class InputSurfaceState {
    NONE,
    KEYBOARD,
    PANEL
}

enum class PanelState {
    EMOJI_PANEL,
    MORE_PANEL
}

enum class PanelTransitionState {
    IDLE,
    KEYBOARD_TO_PANEL,
    PANEL_TO_KEYBOARD
}

data class InputSurfaceSnapshot(
    val surface: InputSurfaceState = InputSurfaceState.NONE,
    val panel: PanelState? = null,
    val transition: PanelTransitionState = PanelTransitionState.IDLE
) {
    init {
        require(surface != InputSurfaceState.PANEL || panel != null)
        require(surface == InputSurfaceState.PANEL || panel == null)
    }

    companion object {
        val NONE = InputSurfaceSnapshot(InputSurfaceState.NONE, null)
        val KEYBOARD = InputSurfaceSnapshot(InputSurfaceState.KEYBOARD, null)
        val PANEL_TO_KEYBOARD = InputSurfaceSnapshot(
            InputSurfaceState.KEYBOARD,
            null,
            PanelTransitionState.PANEL_TO_KEYBOARD
        )

        fun panel(
            panel: PanelState,
            transition: PanelTransitionState = PanelTransitionState.IDLE
        ): InputSurfaceSnapshot {
            return InputSurfaceSnapshot(InputSurfaceState.PANEL, panel, transition)
        }
    }
}

enum class InputMode {
    TEXT,
    VOICE
}
