package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.runtime.compositionLocalOf
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.shouldShowReadReceiptIndicator
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal val LocalReadReceiptSnapshots = compositionLocalOf<Map<String, String>> { emptyMap() }

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

    fun syncVisible(
        visibleMessages: List<MessageInfo>,
        snapshotByMessageId: MutableMap<String, String>
    ): Set<String> {
        val visibleReceiptMessageIds = mutableSetOf<String>()
        val changedMessageIds = mutableSetOf<String>()
        for (message in visibleMessages) {
            val messageId = message.msgID.takeIf { it.isNotBlank() } ?: continue
            if (!message.shouldShowReadReceiptIndicator()) {
                if (snapshotByMessageId.remove(messageId) != null) {
                    changedMessageIds.add(messageId)
                }
                continue
            }
            visibleReceiptMessageIds.add(messageId)
            val latestSnapshot = build(message)
            val previousSnapshot = snapshotByMessageId[messageId]
            if (previousSnapshot != latestSnapshot) {
                snapshotByMessageId[messageId] = latestSnapshot
                changedMessageIds.add(messageId)
            }
        }
        val staleIds = snapshotByMessageId.keys.filter { it !in visibleReceiptMessageIds }
        staleIds.forEach { snapshotByMessageId.remove(it) }
        return changedMessageIds
    }
}
