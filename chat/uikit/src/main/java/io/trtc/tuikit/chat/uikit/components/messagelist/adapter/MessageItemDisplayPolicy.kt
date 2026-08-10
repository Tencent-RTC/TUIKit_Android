package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment

internal object MessageItemDisplayPolicy {
    private const val BUBBLE_SCREEN_WIDTH_RATIO = 0.72f

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

    fun resolveMaxRowWidth(
        screenWidth: Int,
        horizontalPaddingDp: Int,
        avatarSpacingDp: Int,
        density: Float
    ): Int {
        val horizontalPad = (horizontalPaddingDp * density).toInt() * 2
        val avatarArea = ((MESSAGE_AVATAR_SIZE_DP + avatarSpacingDp) * density).toInt()
        return (screenWidth - horizontalPad - avatarArea).coerceAtLeast(0)
    }

    fun resolvePreferredBubbleMaxWidth(
        screenWidth: Int,
        maxRowWidth: Int
    ): Int {
        val upperBound = (screenWidth * BUBBLE_SCREEN_WIDTH_RATIO).toInt()
        return minOf(maxRowWidth, upperBound).coerceAtLeast(0)
    }

    fun resolveMaxWidthsForMode(
        maxRowWidth: Int,
        preferredBubbleMaxWidth: Int,
        checkBoxVisible: Boolean,
        statusReserveWidth: Int,
        density: Float,
        inlineTimeReserveWidth: Int = 0
    ): MessageItemMaxWidths {
        val offset = (if (checkBoxVisible) {
            resolveMultiSelectCheckBoxArea(density)
        } else {
            0
        }) + inlineTimeReserveWidth.coerceAtLeast(0)
        val rowWidth = (maxRowWidth - offset).coerceAtLeast(0)
        val preferredBubbleWidth = (preferredBubbleMaxWidth - offset).coerceAtLeast(0)
        val bubbleWidth = MessageStatusLayoutPolicy.resolveBubbleMaxWidth(
            rowMaxWidth = rowWidth,
            preferredBubbleMaxWidth = preferredBubbleWidth,
            statusReserveWidth = statusReserveWidth
        )
        return MessageItemMaxWidths(
            rowMaxWidth = rowWidth,
            bubbleMaxWidth = bubbleWidth
        )
    }

    fun resolveAuxiliaryTextMaxWidth(
        maxRowWidth: Int,
        preferredBubbleMaxWidth: Int,
        checkBoxVisible: Boolean,
        density: Float,
        inlineTimeReserveWidth: Int = 0
    ): Int {
        val offset = (if (checkBoxVisible) {
            resolveMultiSelectCheckBoxArea(density)
        } else {
            0
        }) + inlineTimeReserveWidth.coerceAtLeast(0)
        val rowWidth = (maxRowWidth - offset).coerceAtLeast(0)
        val preferredBubbleWidth = (preferredBubbleMaxWidth - offset).coerceAtLeast(0)
        return minOf(rowWidth, preferredBubbleWidth).coerceAtLeast(0)
    }

    fun resolveMultiSelectCheckBoxArea(density: Float): Int {
        return ((MULTI_SELECT_CHECKBOX_SIZE_DP + MULTI_SELECT_CHECKBOX_MARGIN_END_DP) * density).toInt()
    }
}

internal data class MessageItemMaxWidths(
    val rowMaxWidth: Int,
    val bubbleMaxWidth: Int
)
