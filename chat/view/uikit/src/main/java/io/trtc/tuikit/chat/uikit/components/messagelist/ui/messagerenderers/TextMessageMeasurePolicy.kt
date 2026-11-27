package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
internal object TextMessageMeasurePolicy {
    fun resolveCompactWidth(
        measuredWidth: Int,
        maxLineWidth: Int,
        horizontalPadding: Int,
        maxWidth: Int
    ): Int {
        val contentWidth = (maxLineWidth + horizontalPadding).coerceAtLeast(0)
        val cappedWidth = if (maxWidth > 0) minOf(contentWidth, maxWidth) else contentWidth
        return minOf(measuredWidth, cappedWidth).coerceAtLeast(0)
    }
}
