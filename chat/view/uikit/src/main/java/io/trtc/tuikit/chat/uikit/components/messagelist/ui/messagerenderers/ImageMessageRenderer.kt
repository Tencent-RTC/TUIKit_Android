package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.common.imageloader.ImageOptions
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

class ImageMessageRenderer : MessageRenderer {

    companion object {
        private const val BUBBLE_CORNER_RADIUS_DP = 12f
        private const val MAX_SIZE_DP = 200f
        private const val MIN_SIZE_DP = 80f
    }

    override val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig(showMessageMeta = true, useDefaultBubble = false)

    override fun createView(context: Context, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        val cornerRadiusPx = BUBBLE_CORNER_RADIUS_DP * density
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                (MAX_SIZE_DP * density).toInt(),
                (MAX_SIZE_DP * density).toInt()
            )
            setImageResource(R.drawable.message_list_image_error_image)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
            clipToOutline = true
        }
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val imageView = view as ImageView
        val density = view.context.resources.displayMetrics.density
        val payload = message.messagePayload as? ImageMessagePayload
        val originalWidth = payload?.originalImageWidth?.takeIf { it > 0 } ?: 100
        val originalHeight = payload?.originalImageHeight?.takeIf { it > 0 } ?: 100

        val maxSize = (MAX_SIZE_DP * density).toInt()
        val minSize = (MIN_SIZE_DP * density).toInt()
        val ratio = originalWidth.toFloat() / originalHeight.toFloat()
        val width: Int
        val height: Int
        if (ratio > 1) {
            width = maxSize
            height = (maxSize / ratio).toInt().coerceIn(minSize, maxSize)
        } else {
            height = maxSize
            width = (maxSize * ratio).toInt().coerceIn(minSize, maxSize)
        }
        imageView.layoutParams.width = width
        imageView.layoutParams.height = height

        val options = ImageOptions.Builder()
            .setPlaceImage(R.drawable.message_list_image_error_image)
            .setErrorImage(R.drawable.message_list_image_error_image)
            .build()

        val imagePath = ImageMessageRenderPolicy.resolveDisplayImagePath(payload)
        if (!imagePath.isNullOrEmpty()) {
            ImageLoader.load(view.context, imageView, imagePath, options)
        } else {
            ImageLoader.load(view.context, imageView, null, options)
        }

        if (message.status == MessageStatus.VIOLATION) {
            imageView.setOnClickListener(null)
            imageView.isClickable = false
        } else {
            imageView.isClickable = true
            imageView.setOnClickListener {
                viewModel.showImage(view.context, message)
            }
        }
    }
}

internal object ImageMessageRenderPolicy {
    fun resolveDisplayImagePath(payload: ImageMessagePayload?): String? {
        return payload?.originalImagePath.takeUnlessBlank()
            ?: payload?.largeImagePath.takeUnlessBlank()
            ?: payload?.thumbImagePath.takeUnlessBlank()
            ?: payload?.originalImageURL.takeUnlessBlank()
            ?: payload?.largeImageURL.takeUnlessBlank()
            ?: payload?.thumbImageURL.takeUnlessBlank()
    }

    private fun String?.takeUnlessBlank(): String? = this?.takeIf { it.isNotBlank() }
}
