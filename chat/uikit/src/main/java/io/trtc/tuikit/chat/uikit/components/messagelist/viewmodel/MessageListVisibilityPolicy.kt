package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRendererResolver
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageModel
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType

internal class MessageListVisibilityPolicy(
    private val config: MessageListConfigProtocol,
    private val parseCallMessage: (MessageInfo) -> CallMessageModel? = CallMessageParser::parse
) {
    private val rendererResolver = MessageRendererResolver(
        customRules = (config as? ChatMessageListConfig)?.customRenderRules.orEmpty()
    )

    fun shouldDisplay(message: MessageInfo): Boolean {
        if (!config.isShowSystemMessage &&
            (message.messageType == MessageType.TIPS || message.status == MessageStatus.REVOKED)
        ) {
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
}
