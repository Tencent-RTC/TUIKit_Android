package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout

internal object MessageListAutoScrollPolicy {
    fun isAtLatestPosition(
        hasItems: Boolean,
        hasMoreNewerMessages: Boolean,
        canScrollTowardLatest: Boolean
    ): Boolean {
        if (!hasItems) {
            return !hasMoreNewerMessages
        }
        return !hasMoreNewerMessages && !canScrollTowardLatest
    }
}
