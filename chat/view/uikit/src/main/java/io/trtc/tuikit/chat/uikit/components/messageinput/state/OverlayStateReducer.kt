package io.trtc.tuikit.chat.uikit.components.messageinput.state
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class OverlayStateReducer {

    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    fun handleEvent(event: OverlayEvent) {
        when (event) {
            is OverlayEvent.SetQuote -> _state.update { it.copy(quoteMessage = event.info) }
            OverlayEvent.ClearQuote -> _state.update { it.copy(quoteMessage = null) }
            is OverlayEvent.SetVoiceRecording -> _state.update { it.copy(voiceRecording = event.active) }
        }
    }

    fun reset() {
        _state.value = OverlayState()
    }
}
