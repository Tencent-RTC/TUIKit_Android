package io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.components.messagelist.adapter.MessageListAdapter
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListAdapterUpdatePolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.shouldShowReadReceiptIndicator
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal class MessageListReadReceiptController(
    private val recyclerView: RecyclerView,
    private val adapterProvider: () -> MessageListAdapter?,
    private val messagesProvider: () -> List<MessageInfo>,
    private val isAttachedToWindowProvider: () -> Boolean,
    private val onVisibleMessagesRead: (List<MessageInfo>) -> Unit
) {
    private val readReceiptSnapshotByMessageId = mutableMapOf<String, String>()
    private var isInScrollCallback = false
    private var isReadReceiptIndicatorRefreshPosted = false

    private val postedReadReceiptIndicatorRefreshRunnable = Runnable {
        isReadReceiptIndicatorRefreshPosted = false
        refreshVisibleReadReceiptIndicators()
    }
    private val syncVisibleReadReceiptsRunnable = Runnable {
        syncVisibleReadReceipts()
    }

    private val refreshReadReceiptRunnable = object : Runnable {
        override fun run() {
            refreshVisibleReadReceiptIndicators()
            if (isAttachedToWindowProvider()) {
                recyclerView.postDelayed(this, READ_RECEIPT_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun scheduleRefresh() {
        if (adapterProvider() == null) {
            return
        }
        recyclerView.removeCallbacks(refreshReadReceiptRunnable)
        recyclerView.post(refreshReadReceiptRunnable)
    }

    fun postSyncVisibleReadReceipts() {
        recyclerView.removeCallbacks(syncVisibleReadReceiptsRunnable)
        recyclerView.post(syncVisibleReadReceiptsRunnable)
    }

    fun cancel() {
        recyclerView.removeCallbacks(refreshReadReceiptRunnable)
        recyclerView.removeCallbacks(postedReadReceiptIndicatorRefreshRunnable)
        recyclerView.removeCallbacks(syncVisibleReadReceiptsRunnable)
        isReadReceiptIndicatorRefreshPosted = false
        readReceiptSnapshotByMessageId.clear()
        isInScrollCallback = false
    }

    fun syncVisibleReadReceipts() {
        refreshVisibleReadReceiptIndicators()
        val visibleMessages = collectVisibleMessages()
        if (visibleMessages.isNotEmpty()) {
            onVisibleMessagesRead(visibleMessages)
        }
    }

    fun runInScrollCallback(block: () -> Unit) {
        isInScrollCallback = true
        try {
            block()
        } finally {
            isInScrollCallback = false
        }
    }

    private fun refreshVisibleReadReceiptIndicators() {
        val adapter = adapterProvider() ?: return
        if (
            MessageListAdapterUpdatePolicy.shouldPostponeAdapterNotify(
                isInScrollCallback = isInScrollCallback,
                isComputingLayout = recyclerView.isComputingLayout
            )
        ) {
            postVisibleReadReceiptIndicatorRefresh()
            return
        }

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        if (firstVisibleItem == RecyclerView.NO_POSITION || lastVisibleItem == RecyclerView.NO_POSITION) {
            return
        }

        val messages = messagesProvider()
        val visibleReceiptMessageIds = mutableSetOf<String>()
        for (index in firstVisibleItem..lastVisibleItem) {
            val message = messages.getOrNull(index) ?: continue
            val messageId = message.msgID ?: continue
            if (!message.shouldShowReadReceiptIndicator()) {
                readReceiptSnapshotByMessageId.remove(messageId)
                continue
            }

            visibleReceiptMessageIds.add(messageId)
            val latestSnapshot = MessageListReadReceiptSnapshot.build(message)
            val previousSnapshot = readReceiptSnapshotByMessageId[messageId]
            if (previousSnapshot != latestSnapshot) {
                readReceiptSnapshotByMessageId[messageId] = latestSnapshot
                adapter.notifyItemChanged(index)
            }
        }

        readReceiptSnapshotByMessageId.keys.retainAll(visibleReceiptMessageIds)
    }

    private fun postVisibleReadReceiptIndicatorRefresh() {
        if (isReadReceiptIndicatorRefreshPosted) {
            return
        }
        isReadReceiptIndicatorRefreshPosted = true
        recyclerView.post(postedReadReceiptIndicatorRefreshRunnable)
    }

    private fun collectVisibleMessages(): List<MessageInfo> {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return emptyList()
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        if (firstVisibleItem == RecyclerView.NO_POSITION || lastVisibleItem == RecyclerView.NO_POSITION) {
            return emptyList()
        }
        val messages = messagesProvider()
        return (firstVisibleItem..lastVisibleItem).mapNotNull { index ->
            messages.getOrNull(index)
        }
    }

    private companion object {
        const val READ_RECEIPT_REFRESH_INTERVAL_MS = 1000L
    }
}

internal object MessageListReadReceiptSnapshot {
    fun build(message: MessageInfo): String {
        val receipt = message.readReceiptInfo
        return listOf(
            message.status.name,
            receipt?.isPeerRead.toString(),
            (receipt?.readCount ?: 0).toString(),
            (receipt?.unreadCount ?: 0).toString()
        ).joinToString("#")
    }
}
