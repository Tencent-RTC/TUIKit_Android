package io.trtc.tuikit.chat.demo.common

import kotlin.math.min
import kotlin.math.roundToInt

internal data class BadgePosition(
    val left: Int,
    val top: Int
)

internal data class BadgeDragOffset(
    val x: Int,
    val y: Int
)

internal object BadgeDragPolicy {
    fun isInBadgeTouchArea(
        itemWidth: Int,
        itemHeight: Int,
        touchX: Float,
        touchY: Float,
        isRtl: Boolean
    ): Boolean {
        if (itemWidth <= 0 || itemHeight <= 0) {
            return false
        }
        val touchAreaSize = min(itemWidth, itemHeight) / 2f
        val inTopArea = touchY in 0f..touchAreaSize
        val inEndArea = if (isRtl) {
            touchX in 0f..touchAreaSize
        } else {
            touchX in (itemWidth - touchAreaSize)..itemWidth.toFloat()
        }
        return inTopArea && inEndArea
    }

    fun shouldClearUnread(startRawY: Float, currentRawY: Float, thresholdPx: Int): Boolean {
        return startRawY - currentRawY >= thresholdPx
    }

    fun badgeVerticalOffset(startRawY: Float, currentRawY: Float, thresholdPx: Int): Int {
        val dragUpDistance = (startRawY - currentRawY).coerceIn(0f, thresholdPx.toFloat())
        return -dragUpDistance.roundToInt()
    }

    fun badgeDragOffset(
        startRawX: Float,
        startRawY: Float,
        currentRawX: Float,
        currentRawY: Float
    ): BadgeDragOffset {
        val dragUpDistance = (startRawY - currentRawY).coerceAtLeast(0f)
        return BadgeDragOffset(
            x = (currentRawX - startRawX).roundToInt(),
            y = -dragUpDistance.roundToInt()
        )
    }

    fun badgeTopEndPosition(
        anchorLeft: Int,
        anchorTop: Int,
        anchorWidth: Int,
        badgeWidth: Int,
        badgeHeight: Int,
        horizontalOffsetPx: Int,
        verticalOffsetPx: Int,
        isRtl: Boolean
    ): BadgePosition {
        val badgeCenterX = if (isRtl) {
            anchorLeft - horizontalOffsetPx
        } else {
            anchorLeft + anchorWidth + horizontalOffsetPx
        }
        val badgeCenterY = anchorTop + verticalOffsetPx
        return BadgePosition(
            left = badgeCenterX - badgeWidth / 2,
            top = badgeCenterY - badgeHeight / 2
        )
    }
}
