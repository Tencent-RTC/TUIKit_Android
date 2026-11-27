package io.trtc.tuikit.chat.uikit.components.messagelist.ui

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

    fun shouldScrollAfterLatestChanged(
        previousLatestMessageId: String?,
        newLatestMessageId: String?,
        wasAtLatestPosition: Boolean,
        suppressLatestAutoScroll: Boolean
    ): Boolean {
        if (suppressLatestAutoScroll ||
            previousLatestMessageId.isNullOrBlank() ||
            newLatestMessageId.isNullOrBlank() ||
            previousLatestMessageId == newLatestMessageId
        ) {
            return false
        }
        return wasAtLatestPosition
    }

    fun isItemCompletelyVisible(
        itemOffset: Int,
        itemSize: Int,
        viewportStartOffset: Int,
        viewportEndOffset: Int
    ): Boolean {
        return itemOffset >= viewportStartOffset &&
            itemOffset + itemSize <= viewportEndOffset
    }
}
