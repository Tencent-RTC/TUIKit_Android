package io.trtc.tuikit.chat.uikit.components.messagelist.ui
import io.trtc.tuikit.atomicxcore.api.conversation.GroupAtInfo
import io.trtc.tuikit.atomicxcore.api.conversation.GroupAtType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal enum class MessageListMentionKind {
    AT_ME,
    AT_ALL
}

internal data class MessageListMentionTarget(
    val sequence: Long,
    val kind: MessageListMentionKind
)

internal enum class MessageListMentionTargetVisibility {
    UNKNOWN,
    VISIBLE,
    HIDDEN
}

internal object MessageListFloatingEntryStyle {
    const val WIDTH_DP = 94
    const val HEIGHT_DP = 35
    const val MARGIN_END_DP = 16
    const val MARGIN_BOTTOM_DP = 16
    const val HORIZONTAL_PADDING_DP = 10
    const val CORNER_RADIUS_DP = 7
    const val ELEVATION_DP = 8
    const val STROKE_WIDTH_DP = 0
    const val ICON_WIDTH_DP = 12
    const val ICON_HEIGHT_DP = 11
    const val ICON_TEXT_SPACING_DP = 6
    const val TEXT_SIZE_SP = 14
}

internal sealed class MessageListFloatingEntry {
    data object BackToLatest : MessageListFloatingEntry()
    data class NewMessages(val count: Int, val firstMessage: MessageInfo) : MessageListFloatingEntry()
    data class Mention(val target: MessageListMentionTarget) : MessageListFloatingEntry()
    data class BackToQuote(val returnMessage: MessageInfo) : MessageListFloatingEntry()
}

internal object MessageListFloatingEntryPolicy {
    private const val BACK_TO_LATEST_THRESHOLD_SCREEN_MULTIPLIER = 1.5f

    fun shouldShowBackToLatest(distanceFromLatestPx: Int, viewportHeightPx: Int): Boolean {
        if (viewportHeightPx <= 0 || distanceFromLatestPx <= 0) {
            return false
        }
        return distanceFromLatestPx >= viewportHeightPx * BACK_TO_LATEST_THRESHOLD_SCREEN_MULTIPLIER
    }

    fun distanceFromBottomPx(scrollRangePx: Int, scrollExtentPx: Int, scrollOffsetPx: Int): Int {
        if (scrollRangePx <= 0 || scrollExtentPx <= 0) {
            return 0
        }
        return (scrollRangePx - scrollExtentPx - scrollOffsetPx).coerceAtLeast(0)
    }

    fun findOldestMentionTarget(groupAtInfoList: List<GroupAtInfo>): MessageListMentionTarget? {
        val oldestAtInfo = groupAtInfoList.minByOrNull { it.msgSeq } ?: return null
        val kind = when (oldestAtInfo.atType) {
            GroupAtType.AT_ALL,
            GroupAtType.AT_ALL_AT_ME -> MessageListMentionKind.AT_ALL
            GroupAtType.AT_ME -> MessageListMentionKind.AT_ME
            else -> MessageListMentionKind.AT_ME
        }
        return MessageListMentionTarget(sequence = oldestAtInfo.msgSeq, kind = kind)
    }

    fun iconRotationDegrees(entry: MessageListFloatingEntry): Float {
        return when (entry) {
            is MessageListFloatingEntry.Mention -> 180f
            MessageListFloatingEntry.BackToLatest,
            is MessageListFloatingEntry.BackToQuote,
            is MessageListFloatingEntry.NewMessages -> 0f
        }
    }
}

internal class MessageListFloatingEntryStateController {
    private val newMessages = mutableListOf<MessageInfo>()
    private var mentionTarget: MessageListMentionTarget? = null
    private var mentionTargetVisibility = MessageListMentionTargetVisibility.UNKNOWN
    private var shouldShowBackToLatest: Boolean = false
    private var isBeyondDisplayThreshold: Boolean = false
    private var backToQuoteReturnMessage: MessageInfo? = null
    private var showBackToQuote: Boolean = false

    fun reset() {
        newMessages.clear()
        mentionTarget = null
        mentionTargetVisibility = MessageListMentionTargetVisibility.UNKNOWN
        shouldShowBackToLatest = false
        isBeyondDisplayThreshold = false
        backToQuoteReturnMessage = null
        showBackToQuote = false
    }

    fun onQuoteNavigated(returnMessage: MessageInfo) {
        if (returnMessage.msgID.isBlank()) {
            return
        }
        backToQuoteReturnMessage = returnMessage
        showBackToQuote = true
    }

    fun currentBackToQuoteReturnMessage(): MessageInfo? {
        return backToQuoteReturnMessage?.takeIf { showBackToQuote }
    }

    fun onInitialMentionTarget(
        target: MessageListMentionTarget?,
        visibility: MessageListMentionTargetVisibility = MessageListMentionTargetVisibility.HIDDEN
    ) {
        if (target == null || visibility == MessageListMentionTargetVisibility.VISIBLE) {
            mentionTarget = null
            mentionTargetVisibility = MessageListMentionTargetVisibility.UNKNOWN
            return
        }
        mentionTarget = target
        mentionTargetVisibility = visibility
    }

    fun onMentionTargetVisibilityChanged(visibility: MessageListMentionTargetVisibility) {
        if (mentionTarget == null) {
            mentionTargetVisibility = MessageListMentionTargetVisibility.UNKNOWN
            return
        }
        if (visibility == MessageListMentionTargetVisibility.VISIBLE) {
            mentionTarget = null
            mentionTargetVisibility = MessageListMentionTargetVisibility.UNKNOWN
        } else {
            mentionTargetVisibility = visibility
        }
    }

    fun currentMentionTarget(): MessageListMentionTarget? {
        return mentionTarget
    }

    fun onNewMessage(message: MessageInfo, isAtLatest: Boolean) {
        if (message.msgID.isBlank()) {
            return
        }
        if (isAtLatest) {
            clearNewMessages()
            return
        }
        if (newMessages.any { it.msgID == message.msgID }) {
            return
        }
        newMessages.add(message)
    }

    fun onMessageRecalled(msgID: String): Boolean {
        if (msgID.isBlank()) {
            return false
        }
        return newMessages.removeAll { it.msgID == msgID }
    }

    fun hasNewMessages(): Boolean {
        return newMessages.isNotEmpty()
    }

    fun onScroll(
        distanceFromLatestPx: Int,
        viewportHeightPx: Int,
        isAtLatest: Boolean,
        hasMoreNewerMessages: Boolean,
        isReturnMessageCompletelyVisible: Boolean
    ) {
        if (isReturnMessageCompletelyVisible) {
            backToQuoteReturnMessage = null
            showBackToQuote = false
        }

        if (isAtLatest) {
            clearNewMessages()
            isBeyondDisplayThreshold = false
            shouldShowBackToLatest = false
            return
        }

        if (MessageListFloatingEntryPolicy.shouldShowBackToLatest(
                distanceFromLatestPx = distanceFromLatestPx,
                viewportHeightPx = viewportHeightPx
            )
        ) {
            isBeyondDisplayThreshold = true
        }
        shouldShowBackToLatest = hasMoreNewerMessages || isBeyondDisplayThreshold
    }

    fun currentEntry(): MessageListFloatingEntry? {
        backToQuoteReturnMessage?.takeIf { showBackToQuote }?.let {
            return MessageListFloatingEntry.BackToQuote(it)
        }

        mentionTarget?.takeIf { mentionTargetVisibility != MessageListMentionTargetVisibility.VISIBLE }?.let {
            return MessageListFloatingEntry.Mention(it)
        }

        newMessages.firstOrNull()?.let {
            return MessageListFloatingEntry.NewMessages(count = newMessages.size, firstMessage = it)
        }

        return if (shouldShowBackToLatest) {
            MessageListFloatingEntry.BackToLatest
        } else {
            null
        }
    }

    fun consume(entry: MessageListFloatingEntry) {
        when (entry) {
            MessageListFloatingEntry.BackToLatest -> Unit
            is MessageListFloatingEntry.NewMessages -> clearNewMessages()
            is MessageListFloatingEntry.Mention -> mentionTarget = null
            is MessageListFloatingEntry.BackToQuote -> {
                backToQuoteReturnMessage = null
                showBackToQuote = false
            }
        }
    }

    private fun clearNewMessages() {
        newMessages.clear()
    }
}
