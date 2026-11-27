package io.trtc.tuikit.chat.uikit.components.messagelist.ui
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

internal object MessageQuoteLocatePolicy {
    fun findLoadedTargetMessageId(
        quoteInfo: MessageQuoteInfo,
        messages: List<MessageInfo>
    ): String? {
        if (isOriginalMessageUnreachable(quoteInfo)) {
            return null
        }
        val quoteMessageId = quoteInfo.msgID.takeIf { it.isNotBlank() }
        if (quoteMessageId != null && messages.any { it.msgID == quoteMessageId }) {
            return quoteMessageId
        }
        val sequence = quoteInfo.sequence.takeIf { it > 0 } ?: return null
        return messages.firstOrNull { it.sequence == sequence }
            ?.msgID
            ?.takeIf { it.isNotBlank() }
    }

    fun shouldLoadAround(
        quoteInfo: MessageQuoteInfo,
        messages: List<MessageInfo>
    ): Boolean {
        if (isOriginalMessageUnreachable(quoteInfo)) {
            return false
        }
        if (quoteInfo.sequence <= 0) {
            return false
        }
        return findLoadedTargetMessageId(quoteInfo, messages) == null
    }

    fun isOriginalMessageUnreachable(quoteInfo: MessageQuoteInfo): Boolean {
        return quoteInfo.status == MessageStatus.DELETED || quoteInfo.status == MessageStatus.REVOKED
    }

    fun buildCursorMessage(quoteInfo: MessageQuoteInfo): MessageInfo {
        return MessageInfo(
            msgID = quoteInfo.msgID,
            timestamp = quoteInfo.timestamp,
            sequence = quoteInfo.sequence
        )
    }
}
