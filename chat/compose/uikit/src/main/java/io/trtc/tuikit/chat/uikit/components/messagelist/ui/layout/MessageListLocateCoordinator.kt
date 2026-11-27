package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout

import androidx.compose.foundation.lazy.LazyListState
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageListAutoScrollPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.HighlightManager
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.abs
import kotlinx.coroutines.delay

internal class MessageListLocateCoordinator(
    private val highlightManager: HighlightManager
) {
    private var pendingLocateMessageId: String? = null
    private var pendingHighlightMessageId: String? = null
    private var pendingScrollMessageId: String? = null

    fun reset(locateMessageId: String?) {
        pendingLocateMessageId = locateMessageId
        pendingHighlightMessageId = null
        pendingScrollMessageId = null
    }

    fun requestLocateMessage(messageId: String?) {
        val targetMessageId = messageId?.takeIf { it.isNotBlank() } ?: return
        pendingLocateMessageId = targetMessageId
        pendingHighlightMessageId = null
        pendingScrollMessageId = null
    }

    fun requestScrollToMessage(messageId: String?) {
        val targetMessageId = messageId?.takeIf { it.isNotBlank() } ?: return
        pendingLocateMessageId = null
        pendingHighlightMessageId = null
        pendingScrollMessageId = targetMessageId
    }

    fun willApplyPendingTargetNavigation(messages: List<MessageInfo>): Boolean {
        val locateMessageId = pendingLocateMessageId
        val scrollMessageId = pendingScrollMessageId
        return messages.any { message ->
            (locateMessageId != null && message.msgID == locateMessageId) ||
                (scrollMessageId != null && message.msgID == scrollMessageId)
        }
    }

    suspend fun applyPending(messages: List<MessageInfo>, listState: LazyListState) {
        handlePendingLocate(messages, listState)
        handlePendingScroll(messages, listState)
    }

    private suspend fun handlePendingLocate(messages: List<MessageInfo>, listState: LazyListState) {
        val targetMessageId = pendingLocateMessageId ?: return
        val targetIndex = messages.indexOfFirst { it.msgID == targetMessageId }
        if (targetIndex < 0) {
            return
        }
        pendingHighlightMessageId = targetMessageId
        scrollToIndex(listState, targetIndex, measuredItemHeight = 0)
        waitUntilVisibleThenHighlight(listState, targetIndex, targetMessageId)
        if (pendingLocateMessageId == targetMessageId) {
            pendingLocateMessageId = null
        }
    }

    private suspend fun handlePendingScroll(messages: List<MessageInfo>, listState: LazyListState) {
        val targetMessageId = pendingScrollMessageId ?: return
        val targetIndex = messages.indexOfFirst { it.msgID == targetMessageId }
        if (targetIndex < 0) {
            return
        }
        scrollToIndex(listState, targetIndex, measuredItemHeight = 0)
        waitUntilVisibleThenRecenter(listState, targetIndex)
        pendingScrollMessageId = null
    }

    fun isMessageCompletelyVisible(messageId: String, listState: LazyListState): Boolean {
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == messageId } ?: return false
        return isItemCompletelyVisible(visible.offset, visible.size, listState)
    }

    fun mentionTargetVisibility(
        sequence: Long,
        messages: List<MessageInfo>,
        listState: LazyListState
    ): Boolean {
        val targetIndex = messages.indexOfFirst { it.sequence == sequence }
        if (targetIndex < 0) {
            return false
        }
        val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: return false
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
        return targetIndex in firstVisible..lastVisible
    }

    fun distanceFromLatestPx(listState: LazyListState): Float {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
        if (first.index == 0) {
            return first.offset.coerceAtLeast(0).toFloat()
        }
        return (first.index * first.size + first.offset).coerceAtLeast(0).toFloat()
    }

    private fun isItemCompletelyVisible(
        itemOffset: Int,
        itemSize: Int,
        listState: LazyListState
    ): Boolean {
        return MessageListAutoScrollPolicy.isItemCompletelyVisible(
            itemOffset = itemOffset,
            itemSize = itemSize,
            viewportStartOffset = listState.layoutInfo.viewportStartOffset,
            viewportEndOffset = listState.layoutInfo.viewportEndOffset
        )
    }

    private suspend fun waitUntilVisibleThenHighlight(
        listState: LazyListState,
        targetIndex: Int,
        targetMessageId: String
    ) {
        repeat(LOCATE_LAYOUT_RETRY_COUNT) {
            delay(LOCATE_LAYOUT_RETRY_DELAY_MS)
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            if (visible != null) {
                recenterIfNeeded(listState, targetIndex, visible.size, visible.offset)
                highlightManager.addHighlight(targetMessageId)
                pendingHighlightMessageId = null
                return
            }
            scrollToIndex(listState, targetIndex, measuredItemHeight = 0)
        }
        highlightManager.addHighlight(targetMessageId)
        pendingHighlightMessageId = null
    }

    private suspend fun waitUntilVisibleThenRecenter(listState: LazyListState, targetIndex: Int) {
        repeat(LOCATE_LAYOUT_RETRY_COUNT) {
            delay(LOCATE_LAYOUT_RETRY_DELAY_MS)
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            if (visible != null) {
                recenterIfNeeded(listState, targetIndex, visible.size, visible.offset)
                return
            }
            scrollToIndex(listState, targetIndex, measuredItemHeight = 0)
        }
    }

    private suspend fun recenterIfNeeded(
        listState: LazyListState,
        targetIndex: Int,
        itemHeight: Int,
        itemOffset: Int
    ) {
        val viewportHeight = viewportHeight(listState)
        if (!MessageListLocateScrollCalculator.shouldRecenterTargetItem(
                listHeight = viewportHeight,
                itemHeight = itemHeight,
                itemTop = itemOffset,
                itemBottom = itemOffset + itemHeight,
                density = 1f,
                isContentUnderflow = isContentUnderflow(listState)
            )
        ) {
            return
        }
        scrollToIndex(listState, targetIndex, measuredItemHeight = itemHeight)
        delay(LOCATE_LAYOUT_RETRY_DELAY_MS)
    }

    private suspend fun scrollToIndex(
        listState: LazyListState,
        index: Int,
        measuredItemHeight: Int
    ) {
        val offset = MessageListLocateScrollCalculator.centerOffset(
            listHeight = viewportHeight(listState),
            measuredItemHeight = measuredItemHeight
        )
        listState.scrollToItem(index, offset)
    }

    private fun viewportHeight(listState: LazyListState): Int {
        return (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
            .coerceAtLeast(0)
    }

    private fun isContentUnderflow(listState: LazyListState): Boolean {
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty() || info.totalItemsCount <= 0) {
            return false
        }
        val contentTop = visible.minOf { it.offset }
        val contentBottom = visible.maxOf { it.offset + it.size }
        return MessageListUnderflowAlignment.isContentUnderflow(
            viewportHeight = viewportHeight(listState),
            paddingTop = 0,
            paddingBottom = 0,
            itemCount = info.totalItemsCount,
            childCount = visible.size,
            firstVisibleItem = visible.minOf { it.index },
            lastVisibleItem = visible.maxOf { it.index },
            contentTop = contentTop,
            contentBottom = contentBottom
        )
    }

    private companion object {
        const val LOCATE_LAYOUT_RETRY_COUNT = 10
        const val LOCATE_LAYOUT_RETRY_DELAY_MS = 16L
    }
}

internal object MessageListLocateScrollCalculator {
    fun centerOffset(listHeight: Int, measuredItemHeight: Int): Int {
        if (listHeight <= 0) {
            return 0
        }
        val distanceToCenter = if (measuredItemHeight in 1 until listHeight) {
            (listHeight - measuredItemHeight) / 2
        } else {
            listHeight / 2
        }.coerceAtLeast(0)
        return -distanceToCenter
    }

    fun shouldRecenterTargetItem(
        listHeight: Int,
        itemHeight: Int,
        itemTop: Int,
        itemBottom: Int,
        density: Float,
        listPaddingTop: Int = 0,
        isContentUnderflow: Boolean = false
    ): Boolean {
        if (isContentUnderflow) return false
        if (listHeight <= 0 || itemHeight <= 0) return false
        if (itemHeight >= listHeight) return false
        val itemCenter = (itemTop + itemBottom) / 2
        val listCenter = listPaddingTop + listHeight / 2
        val tolerance = maxOf(itemHeight / 2, (8 * density).toInt().coerceAtLeast(1))
        return abs(itemCenter - listCenter) > tolerance
    }
}
