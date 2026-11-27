package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import io.trtc.tuikit.chat.uikit.components.messageinput.state.QuoteInfo
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal object MessageInputQuoteEventPolicy {
    fun resolveQuoteInfo(
        event: Map<*, *>,
        currentConversationID: String?
    ): QuoteInfo? {
        val eventConversationID = event[KEY_CONVERSATION_ID] as? String
        if (!eventConversationID.isNullOrBlank() && eventConversationID != currentConversationID) {
            return null
        }
        val message = event[KEY_MESSAGE] as? MessageInfo ?: return null
        val messageId = message.msgID?.takeIf { it.isNotBlank() } ?: return null
        val summary = (event[KEY_SUMMARY] as? String)?.takeIf { it.isNotBlank() }.orEmpty()
        return QuoteInfo(
            messageId = messageId,
            senderName = message.senderDisplayName,
            summary = summary,
            messageInfo = message
        )
    }

    private const val KEY_CONVERSATION_ID = "conversationID"
    private const val KEY_MESSAGE = "message"
    private const val KEY_SUMMARY = "summary"
}
