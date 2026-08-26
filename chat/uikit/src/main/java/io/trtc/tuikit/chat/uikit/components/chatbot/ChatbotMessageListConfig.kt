package io.trtc.tuikit.chat.uikit.components.chatbot

import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment
import io.trtc.tuikit.chat.uikit.components.messagelist.config.DelegatingMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageActionCustomizer
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageBubbleAppearance
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListBackground
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageMatcher

internal class ChatbotMessageListConfig(
    override val delegateConfig: MessageListConfigProtocol
) : MessageListConfigProtocol, DelegatingMessageListConfig {
    override val alignment: MessageAlignment
        get() = delegateConfig.alignment
    override val background: MessageListBackground?
        get() = delegateConfig.background
    override val defaultBubbleAppearance: MessageBubbleAppearance?
        get() = delegateConfig.defaultBubbleAppearance
    override val ownBubbleAppearance: MessageBubbleAppearance?
        get() = delegateConfig.ownBubbleAppearance
    override val incomingBubbleAppearance: MessageBubbleAppearance?
        get() = delegateConfig.incomingBubbleAppearance
    override val leftBubbleAppearance: MessageBubbleAppearance?
        get() = delegateConfig.leftBubbleAppearance
    override val rightBubbleAppearance: MessageBubbleAppearance?
        get() = delegateConfig.rightBubbleAppearance
    override val isShowTimeMessage: Boolean
        get() = delegateConfig.isShowTimeMessage
    override val isShowLeftAvatar: Boolean
        get() = delegateConfig.isShowLeftAvatar
    override val isShowLeftNickname: Boolean
        get() = delegateConfig.isShowLeftNickname
    override val isShowRightAvatar: Boolean
        get() = delegateConfig.isShowRightAvatar
    override val isShowRightNickname: Boolean
        get() = delegateConfig.isShowRightNickname
    override val cellSpacing: Int
        get() = delegateConfig.cellSpacing
    override val isShowSystemMessage: Boolean
        get() = delegateConfig.isShowSystemMessage
    override val isShowUnsupportMessage: Boolean
        get() = delegateConfig.isShowUnsupportMessage
    override val horizontalPadding: Int
        get() = delegateConfig.horizontalPadding
    override val avatarSpacing: Int
        get() = delegateConfig.avatarSpacing
    override val isShowReadReceipt: Boolean = false
    override val isSupportCopy: Boolean
        get() = delegateConfig.isSupportCopy
    override val isSupportDelete: Boolean
        get() = delegateConfig.isSupportDelete
    override val isSupportRecall: Boolean = false
    override val isSupportMultiSelect: Boolean = false
    override val isSupportForward: Boolean
        get() = delegateConfig.isSupportForward
    override val isSupportReaction: Boolean = false
    override val isSupportQuote: Boolean = false
    override val isSupportConvertToText: Boolean = false
    override val isSupportTranslate: Boolean = false
    override val isSupportListenFromHere: Boolean = false
    override val enableTyping: Boolean = false
    override val messageExclusionMatchers: List<MessageMatcher>
        get() = delegateConfig.messageExclusionMatchers
    override val actionCustomizer: MessageActionCustomizer? = null
}
