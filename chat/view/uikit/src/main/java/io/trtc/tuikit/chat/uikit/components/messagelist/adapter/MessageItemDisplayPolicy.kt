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

    fun resolveAvatarAreaPx(avatarSpacingDp: Int, density: Float): Int {
        return ((MESSAGE_AVATAR_SIZE_DP + avatarSpacingDp) * density).toInt()
    }

    fun resolveMaxRowWidth(
        screenWidth: Int,
        horizontalPaddingDp: Int,
        avatarSpacingDp: Int,
        density: Float,
        isAvatarVisible: Boolean = true
    ): Int {
        val horizontalPad = (horizontalPaddingDp * density).toInt() * 2
        val avatarArea = if (isAvatarVisible) {
            resolveAvatarAreaPx(avatarSpacingDp, density)
        } else {
            0
        }
        return (screenWidth - horizontalPad - avatarArea).coerceAtLeast(0)
    }

    fun resolvePreferredBubbleMaxWidth(
        screenWidth: Int,
        maxRowWidth: Int,
        extraWidth: Int = 0,
        fillAvailableWidth: Boolean = false
    ): Int {
        if (fillAvailableWidth) {
            return maxRowWidth.coerceAtLeast(0)
        }
        // 关掉头像时把原先留给头像的宽度加回气泡上限，否则仍被 72% 屏幕宽卡住
        val upperBound = (screenWidth * BUBBLE_SCREEN_WIDTH_RATIO).toInt() + extraWidth.coerceAtLeast(0)
        return minOf(maxRowWidth, upperBound).coerceAtLeast(0)
    }

    fun resolveMaxWidthsForMode(
        maxRowWidth: Int,
        preferredBubbleMaxWidth: Int,
        checkBoxVisible: Boolean,
        statusReserveWidth: Int,
        density: Float,
        fillAvailableWidth: Boolean = false
    ): MessageItemMaxWidths {
        val offset = if (checkBoxVisible) {
            resolveMultiSelectCheckBoxArea(density)
        } else {
            0
        }
        val rowWidth = (maxRowWidth - offset).coerceAtLeast(0)
        val preferredBubbleWidth = (preferredBubbleMaxWidth - offset).coerceAtLeast(0)
        val bubbleWidth = MessageStatusLayoutPolicy.resolveBubbleMaxWidth(
            rowMaxWidth = rowWidth,
            preferredBubbleMaxWidth = preferredBubbleWidth,
            statusReserveWidth = statusReserveWidth
        )
        val contentWidth = if (fillAvailableWidth) {
            bubbleWidth
        } else {
            val worstCaseContentWidth =
                (rowWidth - (STATUS_AREA_WORST_CASE_DP * density).toInt()).coerceAtLeast(0)
            when {
                bubbleWidth > 0 && worstCaseContentWidth > 0 -> {
                    minOf(bubbleWidth, worstCaseContentWidth)
                }
                else -> maxOf(bubbleWidth, worstCaseContentWidth)
            }
        }
        return MessageItemMaxWidths(
            rowMaxWidth = rowWidth,
            bubbleMaxWidth = bubbleWidth,
            contentMaxWidth = contentWidth
        )
    }

    fun resolveAuxiliaryTextMaxWidth(
        maxRowWidth: Int,
        preferredBubbleMaxWidth: Int,
        checkBoxVisible: Boolean,
        density: Float
    ): Int {
        val offset = if (checkBoxVisible) {
            resolveMultiSelectCheckBoxArea(density)
        } else {
            0
        }
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
    val bubbleMaxWidth: Int,
    val contentMaxWidth: Int
)
