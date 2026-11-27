package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions.MessageReactionPanelPolicy
import kotlin.math.roundToInt

internal object LongPressDimens {
    const val COLUMNS = 5
    const val MAX_ROWS = 2
    const val PAGE_SIZE = COLUMNS * MAX_ROWS

    const val SWITCH_ANIMATION_DURATION = 220L

    const val PAGE_INDICATOR_INACTIVE_ALPHA = 64

    fun screenMargin(density: Float): Int = 8.dp(density)

    fun visualGap(density: Float): Int = 4.dp(density)

    fun pageIndicatorDotSize(density: Float): Int = 5.dp(density)

    fun pageIndicatorDotSpacing(density: Float): Int = 4.dp(density)

    fun pageIndicatorVerticalPadding(density: Float): Int = 6.dp(density)

    fun pageIndicatorAreaHeight(density: Float): Int {
        return pageIndicatorDotSize(density) + pageIndicatorVerticalPadding(density) * 2
    }

    fun popupItemCellWidth(density: Float): Int = 44.dp(density)

    fun popupItemCellHeight(density: Float): Int = 56.dp(density)

    fun popupPageVerticalPadding(density: Float): Int = 4.dp(density) * 2

    fun popupDividerHeight(): Int = 1

    fun popupPageHeight(rowCount: Int, density: Float): Int {
        val safeRowCount = rowCount.coerceAtLeast(1)
        return popupPageVerticalPadding(density) +
            popupItemCellHeight(density) * safeRowCount +
            popupDividerHeight() * (safeRowCount - 1)
    }

    fun popupPagerVerticalPadding(): Int = 0

    fun resolveSinglePageColumnCount(itemCount: Int): Int {
        return itemCount.coerceIn(1, COLUMNS)
    }

    fun popupCardWidth(columnCount: Int, density: Float): Int {
        return popupItemCellWidth(density) * columnCount + 4.dp(density) * 2
    }

    fun cardWidthForColumns(columnCount: Int, density: Float): Int {
        return popupCardWidth(columnCount, density)
    }

    fun emojiPanelHorizontalPadding(density: Float): Int = 12.dp(density)

    fun emojiPanelTopPadding(density: Float): Int = 12.dp(density)

    fun emojiPanelHeaderHeight(density: Float): Int = 14.dp(density)

    fun emojiPanelHeaderGap(density: Float): Int = 8.dp(density)

    fun emojiPanelBottomPadding(density: Float): Int = 8.dp(density)

    fun emojiPanelCollapseIconSize(density: Float): Int = 14.dp(density)

    fun emojiRowDividerHeight(density: Float): Int = 1 + 3.dp(density) * 2

    fun emojiCellSize(density: Float): Int = 34.dp(density)

    fun emojiPanelPageHeight(density: Float): Int {
        return emojiCellSize(density) * MessageReactionPanelPolicy.ROWS +
            emojiRowDividerHeight(density)
    }

    fun emojiPanelContentWidth(density: Float): Int {
        return emojiPanelHorizontalPadding(density) * 2 +
            emojiCellSize(density) * MessageReactionPanelPolicy.COLUMNS
    }

    private fun Int.dp(density: Float): Int = (this * density).roundToInt()
}
