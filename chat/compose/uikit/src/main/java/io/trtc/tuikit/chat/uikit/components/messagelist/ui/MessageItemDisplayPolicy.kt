package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment

internal object MessageItemDisplayPolicy {
    private const val BUBBLE_SCREEN_WIDTH_RATIO = 0.72f
    const val AVATAR_SIZE_DP = 40
    const val MULTI_SELECT_CHECKBOX_SIZE_DP = 22
    const val MULTI_SELECT_CHECKBOX_MARGIN_END_DP = 12
    const val DEFAULT_BUBBLE_MIN_SIZE_DP = 40
    const val STATUS_GAP_DP = 8
    const val STATUS_AREA_WORST_CASE_DP = 60
    const val FAIL_ICON_SIZE_DP = 14
    const val SENDING_INDICATOR_SIZE_DP = 12
    const val CALL_UNREAD_DOT_SIZE_DP = 6
    const val CALL_UNREAD_DOT_MARGIN_DP = 6
    const val INLINE_MESSAGE_TIME_MARGIN_START_DP = 8
    const val READ_RECEIPT_TEXT_SIZE_SP = 12
    private const val STABLE_GROUP_READ_RECEIPT_COUNT = 500

    fun shouldShowNickname(
        alignment: MessageAlignment,
        isSelf: Boolean,
        isGroupChat: Boolean
    ): Boolean {
        return when {
            isSelf -> alignment != MessageAlignment.TWO_SIDED
            else -> isGroupChat
        }
    }

    fun resolveIsLeftAligned(
        alignment: MessageAlignment,
        isSelf: Boolean,
        isRtl: Boolean
    ): Boolean {
        return when (alignment) {
            MessageAlignment.LEFT -> true
            MessageAlignment.RIGHT -> false
            MessageAlignment.TWO_SIDED -> if (isRtl) isSelf else !isSelf
        }
    }

    fun resolveStatusLayout(
        isLeftAligned: Boolean,
        gap: Dp = STATUS_GAP_DP.dp
    ): MessageStatusLayout {
        return if (isLeftAligned) {
            MessageStatusLayout(
                statusBeforeBubble = false,
                marginStart = gap,
                marginEnd = 0.dp
            )
        } else {
            MessageStatusLayout(
                statusBeforeBubble = true,
                marginStart = 0.dp,
                marginEnd = gap
            )
        }
    }

    fun resolveStableStatusReserveWidth(
        currentStatusContentWidth: Dp,
        potentialReadReceiptContentWidth: Dp,
        layout: MessageStatusLayout
    ): Dp {
        val contentWidth = maxOf(currentStatusContentWidth, potentialReadReceiptContentWidth)
        if (contentWidth <= 0.dp) {
            return 0.dp
        }
        return contentWidth + layout.marginStart + layout.marginEnd
    }

    fun resolveStableGroupReadReceiptCount(readCount: Int): Int {
        return maxOf(readCount, STABLE_GROUP_READ_RECEIPT_COUNT)
    }

    fun resolveMaxRowWidth(
        screenWidth: Dp,
        horizontalPadding: Dp,
        avatarSpacing: Dp,
        showAvatar: Boolean
    ): Dp {
        val avatarArea = if (showAvatar) AVATAR_SIZE_DP.dp + avatarSpacing else 0.dp
        return (screenWidth - horizontalPadding * 2 - avatarArea).coerceAtLeast(0.dp)
    }

    fun resolvePreferredBubbleMaxWidth(
        screenWidth: Dp,
        maxRowWidth: Dp,
        fillAvailableWidth: Boolean = false
    ): Dp {
        if (fillAvailableWidth) {
            return maxRowWidth.coerceAtLeast(0.dp)
        }
        val upperBound = screenWidth * BUBBLE_SCREEN_WIDTH_RATIO
        return minOf(maxRowWidth, upperBound).coerceAtLeast(0.dp)
    }

    fun resolveMaxWidthsForMode(
        maxRowWidth: Dp,
        preferredBubbleMaxWidth: Dp,
        checkBoxVisible: Boolean,
        statusReserve: Dp,
        fillAvailableWidth: Boolean = false
    ): MessageItemMaxWidths {
        val offset = if (checkBoxVisible) {
            (MULTI_SELECT_CHECKBOX_SIZE_DP + MULTI_SELECT_CHECKBOX_MARGIN_END_DP).dp
        } else {
            0.dp
        }
        val rowWidth = (maxRowWidth - offset).coerceAtLeast(0.dp)
        val preferred = (preferredBubbleMaxWidth - offset).coerceAtLeast(0.dp)
        val bubbleWidth = minOf(
            preferred,
            (rowWidth - statusReserve).coerceAtLeast(0.dp)
        )
        val contentWidth = if (fillAvailableWidth) {
            bubbleWidth
        } else {
            val worstCaseContentWidth =
                (rowWidth - STATUS_AREA_WORST_CASE_DP.dp).coerceAtLeast(0.dp)
            when {
                bubbleWidth > 0.dp && worstCaseContentWidth > 0.dp -> {
                    minOf(bubbleWidth, worstCaseContentWidth)
                }
                else -> maxOf(bubbleWidth, worstCaseContentWidth)
            }
        }
        return MessageItemMaxWidths(
            rowMaxWidth = rowWidth,
            bubbleMaxWidth = bubbleWidth,
            contentMaxWidth = contentWidth,
            auxiliaryMaxWidth = minOf(rowWidth, preferred).coerceAtLeast(0.dp)
        )
    }

}

internal data class MessageStatusLayout(
    val statusBeforeBubble: Boolean,
    val marginStart: Dp,
    val marginEnd: Dp
)

internal data class MessageItemMaxWidths(
    val rowMaxWidth: Dp,
    val bubbleMaxWidth: Dp,
    val contentMaxWidth: Dp,
    val auxiliaryMaxWidth: Dp
)
