package io.trtc.tuikit.chat.uikit.components.messageinput.ui

internal data class MessageInputActionVisibility(
    val showMore: Boolean,
    val showSend: Boolean,
    val showStop: Boolean
)

internal object MessageInputActionVisibilityPolicy {
    fun resolve(
        hasText: Boolean,
        isTextMode: Boolean,
        isShowMore: Boolean,
        isChatbotActive: Boolean
    ): MessageInputActionVisibility {
        if (isChatbotActive) {
            return MessageInputActionVisibility(
                showMore = false,
                showSend = false,
                showStop = true
            )
        }
        val showSend = hasText && isTextMode
        return MessageInputActionVisibility(
            showMore = !showSend && isShowMore,
            showSend = showSend,
            showStop = false
        )
    }
}
