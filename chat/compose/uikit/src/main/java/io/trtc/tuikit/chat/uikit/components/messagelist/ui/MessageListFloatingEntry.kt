package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
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

internal sealed class MessageListFloatingEntry {
    data object BackToLatest : MessageListFloatingEntry()
    data class NewMessages(val count: Int, val firstMessage: MessageInfo) : MessageListFloatingEntry()
    data class Mention(val target: MessageListMentionTarget) : MessageListFloatingEntry()
    data class BackToQuote(val returnMessage: MessageInfo) : MessageListFloatingEntry()
}

internal object MessageListFloatingEntryPolicy {
    private const val BACK_TO_LATEST_THRESHOLD_SCREEN_MULTIPLIER = 1.5f

    fun shouldShowBackToLatest(distanceFromLatestPx: Float, viewportHeightPx: Float): Boolean {
        if (viewportHeightPx <= 0 || distanceFromLatestPx <= 0) {
            return false
        }
        return distanceFromLatestPx >= viewportHeightPx * BACK_TO_LATEST_THRESHOLD_SCREEN_MULTIPLIER
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

    fun distanceFromBottomPx(scrollRangePx: Float, scrollExtentPx: Float, scrollOffsetPx: Float): Float {
        if (scrollRangePx <= 0 || scrollExtentPx <= 0) {
            return 0f
        }
        return (scrollRangePx - scrollExtentPx - scrollOffsetPx).coerceAtLeast(0f)
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
        distanceFromLatestPx: Float,
        viewportHeightPx: Float,
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

// Floating entry card anchored at the bottom end of the message list:
// 35dp tall, 7dp corner, 8dp elevation, arrow icon + 14sp link-colored text.
@Composable
internal fun MessageListFloatingEntryCard(
    entry: MessageListFloatingEntry,
    onClick: (MessageListFloatingEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    val text = when (entry) {
        MessageListFloatingEntry.BackToLatest ->
            stringResource(R.string.message_list_floating_back_to_latest)
        is MessageListFloatingEntry.NewMessages ->
            pluralStringResource(R.plurals.message_list_floating_new_messages, entry.count, entry.count)
        is MessageListFloatingEntry.Mention ->
            when (entry.target.kind) {
                MessageListMentionKind.AT_ALL -> stringResource(R.string.message_list_floating_at_all)
                else -> stringResource(R.string.message_list_floating_someone_mentioned_me)
            }
        is MessageListFloatingEntry.BackToQuote ->
            stringResource(R.string.message_list_floating_back_to_quote)
    }

    Card(
        onClick = { onClick(entry) },
        modifier = modifier
            .widthIn(min = 94.dp)
            .height(35.dp),
        shape = RoundedCornerShape(7.dp),
        colors = CardDefaults.cardColors(containerColor = colors.floatingColorDefault),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .height(35.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.message_list_ic_floating_entry_arrow),
                contentDescription = null,
                tint = colors.textColorLink,
                modifier = Modifier
                    .size(width = 12.dp, height = 11.dp)
                    .rotate(MessageListFloatingEntryPolicy.iconRotationDegrees(entry))
            )
            Text(
                text = text,
                fontSize = 14.sp,
                color = colors.textColorLink,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
