package io.trtc.tuikit.chat.uikit.components.messageinput.state
sealed interface PanelEffect {
    data object ShowKeyboard : PanelEffect
    data object HideKeyboard : PanelEffect
    data object HideKeyboardKeepFocus : PanelEffect
    data object RequestEditTextFocus : PanelEffect
    data object ClearEditTextFocus : PanelEffect
    data class SetPanelContent(val panel: PanelState, val crossfade: Boolean = false) : PanelEffect
    data object HidePanelContent : PanelEffect
}
