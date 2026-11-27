package io.trtc.tuikit.chat.uikit.components.messageinput.state
data class InputUiState(
    val surface: InputSurfaceState = InputSurfaceState.NONE,
    val panel: PanelState? = null,
    val transition: PanelTransitionState = PanelTransitionState.IDLE,
    val inputMode: InputMode = InputMode.TEXT,
    val overlay: OverlayState = OverlayState(),
    val panelTargetHeight: Int = 0,
    val keyboardHeight: Int = 0
)
