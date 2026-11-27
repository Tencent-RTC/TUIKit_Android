package io.trtc.tuikit.chat.uikit.components.messageinput.ui

internal data class MessageInputActionVisibility(
    val showMore: Boolean,
    val showSend: Boolean,
    val showStop: Boolean
)

internal object MessageInputActionVisibilityPolicy {
    /**
     * [isChatbotInterruptible] 而不是「机器人是否在回复」。
     *
     * TIMStreamElem 那条路径没有打断入口，把发送按钮换成一个点不动的停止图标只会让人以为
     * 界面卡住了。那条路径的「正在回复」由列表里的占位气泡表达，输入栏保持原样。
     */
    fun resolve(
        hasText: Boolean,
        isTextMode: Boolean,
        isShowMore: Boolean,
        isChatbotInterruptible: Boolean,
        keepSendVisible: Boolean = false
    ): MessageInputActionVisibility {
        if (isChatbotInterruptible) {
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
