package io.trtc.tuikit.chat.uikit.components.messagelist.ui

internal enum class MessageListLatestReloadPhase {
    IDLE,
    RELOADING,
    SUCCEEDED,
    FAILED
}

internal data class MessageListLatestReloadState(
    val requestId: Long = 0L,
    val phase: MessageListLatestReloadPhase = MessageListLatestReloadPhase.IDLE,
    val retainHasMoreNewer: Boolean = false
) {
    val blocksNavigation: Boolean
        get() = phase == MessageListLatestReloadPhase.RELOADING ||
            phase == MessageListLatestReloadPhase.SUCCEEDED

    fun start(): MessageListLatestReloadState {
        if (blocksNavigation) {
            return this
        }
        return copy(
            requestId = requestId + 1,
            phase = MessageListLatestReloadPhase.RELOADING,
            retainHasMoreNewer = true
        )
    }

    fun onSuccess(completedRequestId: Long): MessageListLatestReloadState {
        val canSucceed = phase == MessageListLatestReloadPhase.RELOADING ||
            phase == MessageListLatestReloadPhase.FAILED
        if (!canSucceed || requestId != completedRequestId) {
            return this
        }
        return copy(phase = MessageListLatestReloadPhase.SUCCEEDED)
    }

    fun onFailure(completedRequestId: Long): MessageListLatestReloadState {
        if (phase != MessageListLatestReloadPhase.RELOADING || requestId != completedRequestId) {
            return this
        }
        return copy(
            phase = MessageListLatestReloadPhase.FAILED,
            retainHasMoreNewer = true
        )
    }

    fun consumeSuccess(): MessageListLatestReloadState {
        if (phase != MessageListLatestReloadPhase.SUCCEEDED) {
            return this
        }
        return copy(
            phase = MessageListLatestReloadPhase.IDLE,
            retainHasMoreNewer = false
        )
    }
}
