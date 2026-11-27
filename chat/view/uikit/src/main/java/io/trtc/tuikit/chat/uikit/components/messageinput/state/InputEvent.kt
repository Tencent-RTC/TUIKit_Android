package io.trtc.tuikit.chat.uikit.components.messageinput.state
sealed interface InputEvent

sealed interface PanelEvent : InputEvent {
    data object RequestKeyboard : PanelEvent
    data object RequestEmojiPanel : PanelEvent
    data object RequestMorePanel : PanelEvent
    data object RequestCollapse : PanelEvent
    data object KeyboardShown : PanelEvent
    data object KeyboardHidden : PanelEvent
}

sealed interface InputModeEvent : InputEvent {
    data object SwitchToVoice : InputModeEvent
    data object SwitchToText : InputModeEvent
    data object SwitchToTextCollapsed : InputModeEvent
}

sealed interface OverlayEvent : InputEvent {
    data class SetQuote(val info: QuoteInfo) : OverlayEvent
    data object ClearQuote : OverlayEvent
    data class SetVoiceRecording(val active: Boolean) : OverlayEvent
}
