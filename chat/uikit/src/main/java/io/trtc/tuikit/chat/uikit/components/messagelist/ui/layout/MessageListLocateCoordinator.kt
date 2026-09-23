package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.components.messagelist.adapter.MessageListAdapter
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.abs

internal class MessageListLocateCoordinator(
    private val recyclerView: RecyclerView,
    private val adapterProvider: () -> MessageListAdapter?
) {
    private var pendingLocateMessageId: String? = null
    private var pendingHighlightMessageId: String? = null
    private var pendingScrollMessageId: String? = null
    private var pendingScrollToLatestMessageId: String? = null
    private var pendingScrollToLatestMessage = false

    private val highlightPendingLocateMessageRunnable = Runnable {
        highlightPendingLocateMessageIfNeeded()
    }

    val childAttachStateChangeListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            schedulePendingLocateHighlight()
        }

        override fun onChildViewDetachedFromWindow(view: View) = Unit
    }

    fun reset(locateMessageId: String?) {
        recyclerView.removeCallbacks(highlightPendingLocateMessageRunnable)
        pendingLocateMessageId = locateMessageId
        pendingHighlightMessageId = null
        pendingScrollMessageId = null
        clearPendingScrollToLatestMessage()
    }

    fun cancel() {
        recyclerView.removeCallbacks(highlightPendingLocateMessageRunnable)
    }

    fun schedulePendingLocateHighlight() {
        if (pendingHighlightMessageId.isNullOrBlank() || adapterProvider() == null) {
            return
        }
        recyclerView.removeCallbacks(highlightPendingLocateMessageRunnable)
        recyclerView.post(highlightPendingLocateMessageRunnable)
    }

    fun handlePendingLocateMessage(list: List<MessageInfo>? = null) {
        val targetMessageId = pendingLocateMessageId ?: return
        val source = list ?: adapterProvider()?.currentList ?: return
        val targetIndex = source.indexOfFirst { it.msgID == targetMessageId }
        if (targetIndex < 0) {
            return
        }
        pendingHighlightMessageId = targetMessageId
        scrollToMessageCentered(targetIndex, useMeasuredItemHeight = false)
        schedulePendingLocateHighlight()
        pendingLocateMessageId = null
    }

    fun requestLocateMessage(messageId: String?) {
        val targetMessageId = messageId?.takeIf { it.isNotBlank() } ?: return
        pendingLocateMessageId = targetMessageId
        pendingHighlightMessageId = null
        pendingScrollMessageId = null
        handlePendingLocateMessage()
    }

    fun requestScrollToMessage(messageId: String?) {
        val targetMessageId = messageId?.takeIf { it.isNotBlank() } ?: return
        recyclerView.removeCallbacks(highlightPendingLocateMessageRunnable)
        pendingLocateMessageId = null
        pendingHighlightMessageId = null
        pendingScrollMessageId = targetMessageId
        scrollToPendingMessageIfReady()
    }

    fun scrollToPendingMessageIfReady() {
        val targetMessageId = pendingScrollMessageId ?: return
        val adapter = adapterProvider() ?: return
        val targetIndex = adapter.currentList.indexOfFirst { it.msgID == targetMessageId }
        if (targetIndex < 0) {
            return
        }
        scrollToMessageCentered(targetIndex, useMeasuredItemHeight = false)
        pendingScrollMessageId = null
    }

    fun requestScrollToLatestMessage(messageId: String?) {
        pendingScrollToLatestMessage = true
        messageId?.takeIf { it.isNotBlank() }?.let {
            pendingScrollToLatestMessageId = it
        }
        scrollToLatestMessageIfReady()
    }

    fun scrollToLatestMessageIfReady() {
        val adapter = adapterProvider() ?: return
        if (!pendingScrollToLatestMessage || adapter.itemCount <= 0) {
            return
        }
        val targetMessageId = pendingScrollToLatestMessageId
        if (!targetMessageId.isNullOrBlank() && adapter.currentList.none { it.msgID == targetMessageId }) {
            return
        }

        recyclerView.scrollToPosition(0)
        clearPendingScrollToLatestMessage()
    }

    private fun clearPendingScrollToLatestMessage() {
        pendingScrollToLatestMessage = false
        pendingScrollToLatestMessageId = null
    }

    private fun highlightPendingLocateMessageIfNeeded() {
        val targetMessageId = pendingHighlightMessageId ?: return
        val adapter = adapterProvider() ?: return

        val messages = adapter.currentList
        val targetIndex = messages.indexOfFirst { it.msgID == targetMessageId }
        if (targetIndex < 0) {
            pendingHighlightMessageId = null
            return
        }

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        val isTargetVisible = targetIndex in firstVisibleItem..lastVisibleItem

        if (!isTargetVisible) {
            scrollToMessageCentered(targetIndex, useMeasuredItemHeight = false)
            schedulePendingLocateHighlight()
            return
        }

        val viewHolder = recyclerView.findViewHolderForAdapterPosition(targetIndex)
        if (viewHolder == null) {
            schedulePendingLocateHighlight()
            return
        }

        if (shouldRecenterTargetItem(viewHolder.itemView)) {
            scrollToMessageCentered(targetIndex, useMeasuredItemHeight = true)
        }

        adapter.highlightMessage(targetMessageId)
        pendingHighlightMessageId = null
    }

    private fun scrollToMessageCentered(position: Int, useMeasuredItemHeight: Boolean) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val listHeight = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        if (listHeight <= 0) {
            layoutManager.scrollToPosition(position)
            return
        }

        val measuredItemHeight = if (useMeasuredItemHeight) {
            layoutManager.findViewByPosition(position)?.height ?: 0
        } else {
            0
        }
        val offset = MessageListLocateScrollCalculator.centerOffset(
            listHeight = listHeight,
            measuredItemHeight = measuredItemHeight
        )
        layoutManager.scrollToPositionWithOffset(position, offset)
    }

    private fun shouldRecenterTargetItem(itemView: View): Boolean {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return false
        val listHeight = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        return MessageListLocateScrollCalculator.shouldRecenterTargetItem(
            listHeight = listHeight,
            itemHeight = itemView.height,
            itemTop = itemView.top,
            itemBottom = itemView.bottom,
            density = recyclerView.resources.displayMetrics.density,
            listPaddingTop = recyclerView.paddingTop,
            isContentUnderflow = isContentUnderflow(layoutManager)
        )
    }

    private fun isContentUnderflow(layoutManager: LinearLayoutManager): Boolean {
        var contentTop = Int.MAX_VALUE
        var contentBottom = Int.MIN_VALUE
        for (index in 0 until layoutManager.childCount) {
            val child = layoutManager.getChildAt(index) ?: continue
            contentTop = minOf(contentTop, layoutManager.getDecoratedTop(child))
            contentBottom = maxOf(contentBottom, layoutManager.getDecoratedBottom(child))
        }

        return MessageListUnderflowAlignment.isContentUnderflow(
            viewportHeight = recyclerView.height,
            paddingTop = recyclerView.paddingTop,
            paddingBottom = recyclerView.paddingBottom,
            itemCount = layoutManager.itemCount,
            childCount = layoutManager.childCount,
            firstVisibleItem = layoutManager.findFirstVisibleItemPosition(),
            lastVisibleItem = layoutManager.findLastVisibleItemPosition(),
            contentTop = contentTop,
            contentBottom = contentBottom
        )
    }
}

internal object MessageListLocateScrollCalculator {
    fun centerOffset(listHeight: Int, measuredItemHeight: Int): Int {
        if (listHeight <= 0) {
            return 0
        }
        return if (measuredItemHeight in 1 until listHeight) {
            (listHeight - measuredItemHeight) / 2
        } else {
            listHeight / 2
        }.coerceAtLeast(0)
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
        val tolerance = maxOf(itemHeight / 2, (8 * density).toInt())
        return abs(itemCenter - listCenter) > tolerance
    }
}
