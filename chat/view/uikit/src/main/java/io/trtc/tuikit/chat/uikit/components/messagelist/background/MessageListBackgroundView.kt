package io.trtc.tuikit.chat.uikit.components.messagelist.background
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListBackground

class MessageListBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    @ColorInt
    private var defaultColor: Int = 0
    private var messageListBackground: MessageListBackground? = null
    private var gradientDrawable: GradientDrawable? = null
    private var imageDrawable: Drawable? = null
    private var imageTarget: CustomTarget<Drawable>? = null
    private var imageFrame: MessageListBackgroundImageFrame? = null

    fun setDefaultBackgroundColor(@ColorInt color: Int) {
        if (defaultColor == color) {
            return
        }
        defaultColor = color
        invalidate()
    }

    fun setMessageListBackground(background: MessageListBackground?) {
        if (messageListBackground == background) {
            return
        }
        messageListBackground = background
        gradientDrawable = null
        imageDrawable = null
        imageFrame = null
        clearImageTarget()

        when (background) {
            is MessageListBackground.Gradient -> {
                gradientDrawable = GradientDrawable(
                    background.direction.toGradientOrientation(),
                    background.colors.toIntArray()
                )
            }

            is MessageListBackground.Image -> {
                loadImage(background.uri)
            }

            else -> Unit
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (val currentBackground = messageListBackground) {
            is MessageListBackground.Color -> canvas.drawColor(currentBackground.color)
            is MessageListBackground.Gradient -> drawGradient(canvas)
            is MessageListBackground.Image -> drawImage(canvas)
            null -> canvas.drawColor(defaultColor)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val imageBackground = messageListBackground as? MessageListBackground.Image
        if (imageBackground != null && imageDrawable == null && imageTarget == null) {
            loadImage(imageBackground.uri)
        }
    }

    override fun onDetachedFromWindow() {
        clearImageTarget()
        super.onDetachedFromWindow()
    }

    private fun drawGradient(canvas: Canvas) {
        val drawable = gradientDrawable
        if (drawable == null) {
            canvas.drawColor(defaultColor)
            return
        }
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
    }

    private fun drawImage(canvas: Canvas) {
        val drawable = imageDrawable
        val drawableWidth = drawable?.intrinsicWidth ?: 0
        val drawableHeight = drawable?.intrinsicHeight ?: 0
        if (drawable == null || drawableWidth <= 0 || drawableHeight <= 0 || width <= 0 || height <= 0) {
            canvas.drawColor(defaultColor)
            return
        }

        if (!MessageListBackgroundImageFramePolicy.shouldKeepExistingFrame(
                currentFrame = imageFrame,
                imageWidth = drawableWidth,
                imageHeight = drawableHeight,
                viewportWidth = width,
                viewportHeight = height
            )
        ) {
            imageFrame = MessageListBackgroundImageFramePolicy.calculateCoverFrame(
                imageWidth = drawableWidth,
                imageHeight = drawableHeight,
                viewportWidth = width,
                viewportHeight = height
            )
        }

        val frame = imageFrame ?: return
        val saveCount = canvas.save()
        canvas.translate(frame.dx, frame.dy)
        canvas.scale(frame.scale, frame.scale)
        drawable.setBounds(0, 0, drawableWidth, drawableHeight)
        drawable.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun loadImage(uri: Any) {
        val target = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                imageDrawable = resource
                imageFrame = null
                invalidate()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageDrawable = null
                imageFrame = null
                invalidate()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageDrawable = null
                imageFrame = null
                invalidate()
            }
        }
        imageTarget = target
        Glide.with(context.applicationContext)
            .load(uri)
            .into(target)
    }

    private fun clearImageTarget() {
        imageTarget?.let {
            Glide.with(context.applicationContext).clear(it)
        }
        imageTarget = null
    }

    private fun MessageListBackground.GradientDirection.toGradientOrientation(): GradientDrawable.Orientation {
        return when (this) {
            MessageListBackground.GradientDirection.TOP_BOTTOM -> GradientDrawable.Orientation.TOP_BOTTOM
            MessageListBackground.GradientDirection.BOTTOM_TOP -> GradientDrawable.Orientation.BOTTOM_TOP
            MessageListBackground.GradientDirection.LEFT_RIGHT -> GradientDrawable.Orientation.LEFT_RIGHT
            MessageListBackground.GradientDirection.RIGHT_LEFT -> GradientDrawable.Orientation.RIGHT_LEFT
            MessageListBackground.GradientDirection.TOP_LEFT_BOTTOM_RIGHT -> GradientDrawable.Orientation.TL_BR
            MessageListBackground.GradientDirection.BOTTOM_RIGHT_TOP_LEFT -> GradientDrawable.Orientation.BR_TL
            else -> GradientDrawable.Orientation.TOP_BOTTOM
        }
    }
}
