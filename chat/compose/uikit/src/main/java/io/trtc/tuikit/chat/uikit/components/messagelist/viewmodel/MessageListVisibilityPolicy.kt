package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageProtocol
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSource
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRendererRegistry
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType

internal class MessageListVisibilityPolicy(
    private val config: MessageListConfigProtocol
) {
    fun shouldDisplay(message: MessageInfo): Boolean {
        if (io.trtc.tuikit.chat.uikit.components.messagelist.typing.TypingMessageProtocol.isTypingMessage(message)) return false
        if (!config.isShowSystemMessage &&
            (message.messageType == MessageType.TIPS || message.status == MessageStatus.REVOKED)
        ) {
            return false
        }
        if (ChatbotMessageProtocol.parse(message)?.source == ChatbotMessageSource.INTERRUPT) {
            return false
        }
        if (!config.isShowUnsupportMessage &&
            MessageRendererRegistry.isDefaultBuiltInRenderer(message, config)
        ) {
            return false
        }
        if (message.messageType == MessageType.CUSTOM &&
            CallMessageVisibilityParser.isExcludeFromHistory(message)
        ) {
            return false
        }
        if (config.messageExclusionMatchers.any { it.matches(message) }) {
            return false
        }
        return true
    }
}
