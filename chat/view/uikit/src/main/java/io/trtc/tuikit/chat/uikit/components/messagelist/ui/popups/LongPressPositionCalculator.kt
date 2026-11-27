package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
internal object LongPressPositionCalculator {

    data class AnchorBounds(
        val screenX: Int,
        val screenY: Int,
        val width: Int,
        val height: Int
    ) {
        val centerX: Float
            get() = screenX + width / 2f
    }

    data class ListBounds(
        val top: Int,
        val bottom: Int
    )

    data class Chrome(
        val shadowPadH: Int,
        val shadowPadTop: Int,
        val shadowPadBottom: Int
    )

    data class Position(
        val popupX: Int,
        val popupY: Int,
        val popupWidth: Int,
        val showAbove: Boolean,
        val arrowCenterX: Float
    )

    fun calculate(
        anchor: AnchorBounds,
        list: ListBounds,
        screenWidth: Int,
        contentWidth: Int,
        maxBubbleHeight: Int,
        screenMargin: Int,
        visualGap: Int,
        chrome: Chrome
    ): Position {
        val popupWidth = contentWidth + chrome.shadowPadH * 2
        val preferredX = (anchor.centerX - popupWidth / 2f).toInt()
        val maxX = screenWidth - popupWidth - screenMargin
        val popupX = preferredX.coerceIn(screenMargin, maxX)

        val abovePopupY = anchor.screenY - maxBubbleHeight - visualGap + chrome.shadowPadBottom
        val belowPopupY = anchor.screenY + anchor.height + visualGap - chrome.shadowPadTop
        val showAbove = abovePopupY >= list.top - chrome.shadowPadTop
        val popupY = if (showAbove) {
            abovePopupY.coerceAtLeast(list.top - chrome.shadowPadTop)
        } else {
            val maxY = list.bottom - maxBubbleHeight + chrome.shadowPadBottom
            belowPopupY.coerceAtMost(maxY)
        }

        return Position(
            popupX = popupX,
            popupY = popupY,
            popupWidth = popupWidth,
            showAbove = showAbove,
            arrowCenterX = anchor.centerX - popupX - chrome.shadowPadH
        )
    }
}
