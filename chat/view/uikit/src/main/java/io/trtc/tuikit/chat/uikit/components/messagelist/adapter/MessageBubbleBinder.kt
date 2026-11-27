package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageBubbleAppearance
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.BubbleStyle

object MessageBubbleBinder {
    fun resolveBubbleStyle(
        bubbleStyle: BubbleStyle,
        config: MessageListConfigProtocol? = null,
        isLeftAligned: Boolean = true,
        isSelf: Boolean = false
    ): MessageBubbleStyleBinding {
        val effectiveStyle = bubbleStyle
        return MessageBubbleStyleBinding(
            effectiveStyle = effectiveStyle,
            appearance = resolveAppearance(
                effectiveStyle = effectiveStyle,
                config = config,
                isLeftAligned = isLeftAligned,
                isSelf = isSelf
            )
        )
    }

    private fun resolveAppearance(
        effectiveStyle: BubbleStyle,
        config: MessageListConfigProtocol?,
        isLeftAligned: Boolean,
        isSelf: Boolean
    ): MessageBubbleAppearance? {
        if (effectiveStyle != BubbleStyle.DEFAULT || config == null) {
            return null
        }
        val positionAppearance = if (isLeftAligned) {
            config.leftBubbleAppearance
        } else {
            config.rightBubbleAppearance
        }
        val senderAppearance = if (isSelf) {
            config.ownBubbleAppearance
        } else {
            config.incomingBubbleAppearance
        }
        val base = config.defaultBubbleAppearance ?: MessageBubbleAppearance()
        val resolved = base
            .mergeWith(positionAppearance)
            .mergeWith(senderAppearance)
        return resolved.takeUnless { it.isEmpty }
    }
}

data class MessageBubbleStyleBinding(
    val effectiveStyle: BubbleStyle,
    val appearance: MessageBubbleAppearance? = null
)
