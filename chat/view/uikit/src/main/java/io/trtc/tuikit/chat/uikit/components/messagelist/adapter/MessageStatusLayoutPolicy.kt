package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment

object MessageStatusLayoutPolicy {
    private const val STATUS_GAP_DP = 8

    fun isLeftAligned(
        alignment: MessageAlignment,
        isSelf: Boolean,
        isRtl: Boolean
    ): Boolean {
        return when (alignment) {
            MessageAlignment.LEFT -> true
            MessageAlignment.RIGHT -> false
            MessageAlignment.TWO_SIDED -> if (isRtl) isSelf else !isSelf
            else -> if (isRtl) isSelf else !isSelf
        }
    }

    fun resolve(isLeftAligned: Boolean): MessageStatusLayout {
        return if (isLeftAligned) {
            MessageStatusLayout(
                statusBeforeBubble = false,
                marginStartDp = STATUS_GAP_DP,
                marginEndDp = 0
            )
        } else {
            MessageStatusLayout(
                statusBeforeBubble = true,
                marginStartDp = 0,
                marginEndDp = STATUS_GAP_DP
            )
        }
    }

    fun resolveBubbleMaxWidth(
        rowMaxWidth: Int,
        preferredBubbleMaxWidth: Int,
        statusReserveWidth: Int
    ): Int {
        val availableWidth = (rowMaxWidth - statusReserveWidth).coerceAtLeast(0)
        return minOf(preferredBubbleMaxWidth, availableWidth)
    }

    fun resolveStableStatusReserveWidth(
        currentStatusContentWidth: Int,
        potentialReadReceiptContentWidth: Int,
        marginStartPx: Int,
        marginEndPx: Int
    ): Int {
        val contentWidth = maxOf(currentStatusContentWidth, potentialReadReceiptContentWidth)
        if (contentWidth <= 0) {
            return 0
        }
        return contentWidth + marginStartPx + marginEndPx
    }

    fun resolveStableGroupReadReceiptCount(readCount: Int): Int {
        return maxOf(readCount, STABLE_GROUP_READ_RECEIPT_COUNT)
    }

    private const val STABLE_GROUP_READ_RECEIPT_COUNT = 500
}

data class MessageStatusLayout(
    val statusBeforeBubble: Boolean,
    val marginStartDp: Int,
    val marginEndDp: Int
)
