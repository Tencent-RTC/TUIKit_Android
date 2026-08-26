package io.trtc.tuikit.chat.uikit.components.chatbot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil

internal object ChatbotMessageSummaryPolicy {
    fun getSummary(message: MessageInfo): String? {
        val data = ChatbotMessageProtocol.parse(message) ?: return null
        return when (data.source) {
            ChatbotMessageSource.FLOW,
            ChatbotMessageSource.ERROR -> data.displayText
            ChatbotMessageSource.INTERRUPT,
            ChatbotMessageSource.UNKNOWN -> null
        }
    }
}

internal object ChatbotPlaceholderMessageFactory {
    private const val MESSAGE_ID_PREFIX = "chatbot_placeholder_"

    fun create(
        conversationID: String,
        timestampSeconds: Long,
        sender: MessageSenderInfo? = null
    ): MessageInfo {
        val targetID = ChatbotConversationPolicy.targetID(conversationID).orEmpty()
        val resolvedSender = sender?.copy(
            userID = sender.userID.ifEmpty { targetID },
            nickname = sender.nickname?.takeIf { it.isNotBlank() } ?: targetID
        ) ?: MessageSenderInfo(
            userID = targetID,
            nickname = targetID
        )
        val conversationType = when {
            ConversationIDUtil.isC2C(conversationID) -> ConversationType.C2C
            ConversationIDUtil.isGroup(conversationID) -> ConversationType.GROUP
            else -> ConversationType.UNKNOWN
        }
        val customData = JsonObject().apply {
            addProperty("chatbotPlugin", ChatbotMessageProtocol.PLUGIN_VALUE)
            addProperty("src", ChatbotMessageProtocol.SOURCE_FLOW)
            add("chunks", JsonArray())
            addProperty("isFinished", 0)
            addProperty("localPlaceholder", true)
        }.toString()

        return MessageInfo(
            msgID = "$MESSAGE_ID_PREFIX$conversationID",
            status = MessageStatus.SEND_SUCCESS,
            timestamp = timestampSeconds,
            from = resolvedSender,
            to = targetID,
            isSentBySelf = false,
            conversationType = conversationType,
            messageType = MessageType.CUSTOM,
            messagePayload = CustomMessagePayload(customData = customData),
            needReadReceipt = false
        )
    }
}
