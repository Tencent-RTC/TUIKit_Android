package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.config.messageRenderRules
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRendererResolver
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageModel
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageProtocol
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSource
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.StreamMessagePayload

internal class MessageListVisibilityPolicy(
    private val config: MessageListConfigProtocol,
    private val parseCallMessage: (MessageInfo) -> CallMessageModel? = CallMessageParser::parse
) {
    private val rendererResolver = MessageRendererResolver(
        customRules = config.messageRenderRules()
    )

    fun shouldDisplay(message: MessageInfo): Boolean {
        if (!config.isShowSystemMessage &&
            (message.messageType == MessageType.TIPS || message.status == MessageStatus.REVOKED)
        ) {
            return false
        }
        if (ChatbotMessageProtocol.parse(message)?.source == ChatbotMessageSource.INTERRUPT) {
            return false
        }
        if (isEmptyStreamMessage(message)) {
            return false
        }
        if (!config.isShowUnsupportMessage &&
            rendererResolver.isDefaultBuiltInRenderer(message)
        ) {
            return false
        }
        if (message.messageType == MessageType.CUSTOM) {
            val callModel = parseCallMessage(message)
            if (callModel?.isExcludeFromHistory == true) {
                return false
            }
        }
        if (config.messageExclusionMatchers.any { it.matches(message) }) {
            return false
        }
        return true
    }

    /**
     * 正文还没到的 TIMStreamElem 流式消息。
     *
     * 这条路径不显示三点加载动画，空正文渲染出来就只是一个空气泡，等第一段正文到了
     * 再让消息出现。顺带也挡掉了那些正文补丁丢失、永远填不上的残骸消息。
     */
    private fun isEmptyStreamMessage(message: MessageInfo): Boolean {
        if (message.messageType != MessageType.STREAM) {
            return false
        }
        val payload = message.messagePayload as? StreamMessagePayload ?: return false
        return payload.markdown.isEmpty()
    }
}
