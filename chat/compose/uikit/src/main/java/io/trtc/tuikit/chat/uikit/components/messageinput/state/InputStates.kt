package io.trtc.tuikit.chat.uikit.components.messageinput.state

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

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

enum class InputMode {
    TEXT,
    VOICE
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

sealed interface PanelEffect {
    data object ShowKeyboard : PanelEffect
    data object HideKeyboard : PanelEffect
    data object HideKeyboardKeepFocus : PanelEffect
    data object RequestEditTextFocus : PanelEffect
    data object ClearEditTextFocus : PanelEffect
    data class SetPanelContent(val panel: PanelState, val crossfade: Boolean = false) : PanelEffect
    data object HidePanelContent : PanelEffect
}

data class QuoteInfo(
    val messageId: String,
    val senderName: String,
    val summary: String,
    val messageInfo: MessageInfo? = null
) {
    fun toMessageInfo(): MessageInfo? {
        return messageInfo ?: messageId.takeIf { it.isNotBlank() }?.let { id ->
            MessageInfo(msgID = id)
        }
    }
}

data class OverlayState(
    val quoteMessage: QuoteInfo? = null,
    val voiceRecording: Boolean = false
)

data class InputUiState(
    val surface: InputSurfaceState = InputSurfaceState.NONE,
    val panel: PanelState? = null,
    val transition: PanelTransitionState = PanelTransitionState.IDLE,
    val inputMode: InputMode = InputMode.TEXT,
    val overlay: OverlayState = OverlayState(),
    val panelTargetHeight: Int = 0,
    val keyboardHeight: Int = 0
)
