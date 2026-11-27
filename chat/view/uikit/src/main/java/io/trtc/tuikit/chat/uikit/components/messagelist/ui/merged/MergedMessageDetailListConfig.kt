package io.trtc.tuikit.chat.uikit.components.messagelist.ui.merged

import io.trtc.tuikit.chat.uikit.components.messagelist.config.DelegatingMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol

internal class MergedMessageDetailListConfig(
    override val delegateConfig: MessageListConfigProtocol,
    baseConfig: MessageListConfigProtocol
) : MessageListConfigProtocol by baseConfig, DelegatingMessageListConfig
