package io.trtc.tuikit.chat.uikit.components.messagelist.background
import kotlin.math.max

internal data class MessageListBackgroundImageFrame(
    val imageWidth: Int,
    val imageHeight: Int,
    val scale: Float,
    val dx: Float,
    val dy: Float
)

internal object MessageListBackgroundImageFramePolicy {
    fun calculateCoverFrame(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): MessageListBackgroundImageFrame {
        require(imageWidth > 0 && imageHeight > 0) {
            "Image size must be positive."
        }
        require(viewportWidth > 0 && viewportHeight > 0) {
            "Viewport size must be positive."
        }

        val scale = max(
            viewportWidth.toFloat() / imageWidth.toFloat(),
            viewportHeight.toFloat() / imageHeight.toFloat()
        )
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        return MessageListBackgroundImageFrame(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            scale = scale,
            dx = (viewportWidth - scaledWidth) / 2f,
            dy = (viewportHeight - scaledHeight) / 2f
        )
    }

    fun shouldKeepExistingFrame(
        currentFrame: MessageListBackgroundImageFrame?,
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): Boolean {
        return currentFrame != null &&
            imageWidth > 0 &&
            imageHeight > 0 &&
            viewportWidth > 0 &&
            viewportHeight > 0 &&
            currentFrame.imageWidth == imageWidth &&
            currentFrame.imageHeight == imageHeight
    }
}
