package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

internal object MediaBubbleSizePolicy {
    const val CORNER_RADIUS_DP = 12f
    const val MAX_SIZE_DP = 200f
    const val MIN_SIZE_DP = 80f

    fun calculateSize(originalWidth: Int, originalHeight: Int): DpSize {
        val width = originalWidth.takeIf { it > 0 } ?: 100
        val height = originalHeight.takeIf { it > 0 } ?: 100
        val ratio = width.toFloat() / height.toFloat()
        return if (ratio > 1f) {
            DpSize(
                width = MAX_SIZE_DP.dp,
                height = (MAX_SIZE_DP / ratio).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP).dp
            )
        } else {
            DpSize(
                width = (MAX_SIZE_DP * ratio).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP).dp,
                height = MAX_SIZE_DP.dp
            )
        }
    }
}

internal object ImageMessageRenderPolicy {
    fun resolveDisplayImagePath(
        originalImagePath: String?,
        largeImagePath: String?,
        thumbImagePath: String?,
        originalImageURL: String?,
        largeImageURL: String?,
        thumbImageURL: String?,
    ): String? {
        return originalImagePath.takeUnlessBlank()
            ?: largeImagePath.takeUnlessBlank()
            ?: thumbImagePath.takeUnlessBlank()
            ?: originalImageURL.takeUnlessBlank()
            ?: largeImageURL.takeUnlessBlank()
            ?: thumbImageURL.takeUnlessBlank()
    }

    private fun String?.takeUnlessBlank(): String? = this?.takeIf { it.isNotBlank() }
}
