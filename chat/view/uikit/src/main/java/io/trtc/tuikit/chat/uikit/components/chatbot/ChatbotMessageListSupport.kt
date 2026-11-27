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

/**
 * 「AI 正在输入」气泡的数据源。
 *
 * 造出来的 [MessageInfo] 只活在展示列表里：不调 insertMessageToLocalStorage、不进本地库、
 * 不参与登录同步，进程一死就没了。这是它和真正插一条本地消息的根本区别——后者会在卸载重装时
 * 被同步通道重放成永远转圈的残骸，而这条不可能被重放。
 *
 * 复用 chatbotPlugin 的 FLOW 结构是为了直接落到 [ChatbotMessageRenderer] 的加载态分支上，
 * 不用再为它单独做一套 viewType；`localPlaceholder` 标记让渲染和长按菜单能把它和真实回复区分开。
 */
internal object ChatbotPlaceholderMessageFactory {
    private const val MESSAGE_ID_PREFIX = "chatbot_placeholder_"

    /** 从最近一条对端消息上取头像昵称，让占位气泡和随后真正的回复长得一样。 */
    fun resolveSender(messages: List<MessageInfo>): MessageSenderInfo? {
        return messages.firstOrNull { !it.isSentBySelf }?.from
    }

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
            conversationType = when {
                ConversationIDUtil.isC2C(conversationID) -> ConversationType.C2C
                ConversationIDUtil.isGroup(conversationID) -> ConversationType.GROUP
                else -> ConversationType.UNKNOWN
            },
            messageType = MessageType.CUSTOM,
            messagePayload = CustomMessagePayload(customData = customData),
            needReadReceipt = false
        )
    }
}
