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
        isChatbotActive: Boolean,
        keepSendVisible: Boolean = false
    ): MessageInputActionVisibility {
        if (isChatbotActive) {
            return MessageInputActionVisibility(
                showMore = false,
                showSend = false,
                showStop = true
            )
        }
        val showSend = isTextMode && (hasText || keepSendVisible)
        return MessageInputActionVisibility(
            showMore = isShowMore && (keepSendVisible || !showSend),
            showSend = showSend,
            showStop = false
        )
    }
}
