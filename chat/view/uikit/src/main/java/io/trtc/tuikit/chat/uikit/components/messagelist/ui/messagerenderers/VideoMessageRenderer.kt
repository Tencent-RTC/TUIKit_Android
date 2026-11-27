package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.formatSmartTime
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload

class VideoMessageRenderer : MessageRenderer {

    companion object {
        private const val BUBBLE_CORNER_RADIUS_DP = 12f
        private const val MAX_SIZE_DP = 200f
        private const val MIN_SIZE_DP = 80f
        private const val PLAY_ICON_SIZE_DP = 44f
        private const val DURATION_CHIP_RADIUS_DP = 4f
        private const val DURATION_CHIP_BG_COLOR = 0x66000000.toInt()
        private const val DURATION_HORIZONTAL_PADDING_DP = 6f
        private const val DURATION_VERTICAL_PADDING_DP = 2f
        private const val DURATION_MARGIN_DP = 8f
    }

    override val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig(showMessageMeta = true, useDefaultBubble = false)

    override fun createView(context: Context, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        val cornerRadiusPx = BUBBLE_CORNER_RADIUS_DP * density
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (MAX_SIZE_DP * density).toInt(),
                (MAX_SIZE_DP * density).toInt()
            )
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
            clipToOutline = true
        }

        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            tag = "snapshot"
        }
        container.addView(imageView)

        val playIcon = ImageView(context).apply {
            setImageResource(R.drawable.message_list_video_play_icon)
            layoutParams = FrameLayout.LayoutParams(
                (PLAY_ICON_SIZE_DP * density).toInt(),
                (PLAY_ICON_SIZE_DP * density).toInt(),
                Gravity.CENTER
            )
            tag = "play_icon"
        }
        container.addView(playIcon)

        val horizontalPadding = (DURATION_HORIZONTAL_PADDING_DP * density).toInt()
        val verticalPadding = (DURATION_VERTICAL_PADDING_DP * density).toInt()
        val durationMargin = (DURATION_MARGIN_DP * density).toInt()
        val durationView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = DURATION_CHIP_RADIUS_DP * density
                setColor(DURATION_CHIP_BG_COLOR)
            }
            tag = "duration"
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(durationMargin, durationMargin, durationMargin, durationMargin)
            }
        }
        container.addView(durationView)

        return container
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val container = view as FrameLayout
        val imageView = container.findViewWithTag<ImageView>("snapshot")
        val durationView = container.findViewWithTag<TextView>("duration")

        val density = view.context.resources.displayMetrics.density
        val payload = message.messagePayload as? VideoMessagePayload
        val snapshotWidth = payload?.videoSnapshotWidth?.takeIf { it > 0 } ?: 100
        val snapshotHeight = payload?.videoSnapshotHeight?.takeIf { it > 0 } ?: 100

        val maxSize = (MAX_SIZE_DP * density).toInt()
        val minSize = (MIN_SIZE_DP * density).toInt()
        val ratio = snapshotWidth.toFloat() / snapshotHeight.toFloat()
        val width: Int
        val height: Int
        if (ratio > 1) {
            width = maxSize
            height = (maxSize / ratio).toInt().coerceIn(minSize, maxSize)
        } else {
            height = maxSize
            width = (maxSize * ratio).toInt().coerceIn(minSize, maxSize)
        }
        container.layoutParams.width = width
        container.layoutParams.height = height

        val snapshotPath = payload?.videoSnapshotPath?.takeIf { it.isNotBlank() }
            ?: payload?.videoSnapshotURL?.takeIf { it.isNotBlank() }
        VideoSnapshotImageLoader.load(view.context, imageView, snapshotPath)

        val durationSeconds = payload?.videoDuration ?: 0
        durationView.text = formatSmartTime(durationSeconds)

        if (message.status == MessageStatus.VIOLATION) {
            view.setOnClickListener(null)
            view.isClickable = false
        } else {
            view.isClickable = true
            view.setOnClickListener {
                viewModel.showImage(view.context, message)
            }
        }
    }
}

internal object VideoSnapshotImageLoadPolicy {
    val placeholderScaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP
    val loadedScaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER
}

private object VideoSnapshotImageLoader {
    fun load(context: Context, imageView: ImageView, snapshotPath: String?) {
        imageView.scaleType = VideoSnapshotImageLoadPolicy.placeholderScaleType
        if (snapshotPath.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.message_list_image_error_image)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
            && context is Activity
            && (context.isFinishing || context.isDestroyed)
        ) {
            return
        }
        Glide.with(context.applicationContext)
            .load(snapshotPath)
            .apply(
                RequestOptions()
                    .transform(FitCenter())
                    .placeholder(R.drawable.message_list_image_error_image)
                    .error(R.drawable.message_list_image_error_image)
            )
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    imageView.scaleType = VideoSnapshotImageLoadPolicy.placeholderScaleType
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    imageView.scaleType = VideoSnapshotImageLoadPolicy.loadedScaleType
                    return false
                }
            })
            .into(imageView)
    }
}
