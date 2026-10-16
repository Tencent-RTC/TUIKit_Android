package com.trtc.uikit.livekit.common.theme.avatar

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.imageloader.ImageLoader
import com.trtc.uikit.livekit.common.basic.imageloader.ImageOptions
import com.trtc.uikit.livekit.common.basic.ScreenUtil.dp2px
import com.trtc.uikit.livekit.common.theme.ThemeStore
import kotlin.math.ceil
import kotlin.math.min

class AtomicAvatar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        private const val SQRT2_OVER_2 = 0.70710678f
        private const val BADGE_EXTRA_PADDING_DP = 2f
    }

    sealed class AvatarContent {
        data class URL(val url: String, @DrawableRes val placeImage: Int) : AvatarContent()
        data class Text(val name: String) : AvatarContent()
        data class Icon(val drawable: Drawable) : AvatarContent()
    }

    sealed class AvatarBadge {
        object None : AvatarBadge()
        object Dot : AvatarBadge()
        data class Text(val text: String) : AvatarBadge()
    }

    enum class AvatarSize(
        val sizeDp: Float,
        val textSizeSp: Float,
        val borderRadiusDp: Float
    ) {
        XXS(16f, 10f, 4f),
        XS(24f, 12f, 4f),
        S(32f, 14f, 4f),
        M(40f, 16f, 4f),
        L(48f, 18f, 8f),
        XL(64f, 28f, 12f),
        XXL(96f, 36f, 12f)
    }

    enum class AvatarShape {
        Round,
        RoundRectangle,
        Rectangle
    }

    private val themeStore = ThemeStore.shared(context)
    private val colors get() = themeStore.themeState.value.currentTheme.tokens.color

    private val avatarContainer = ClippingFrameLayout(context).apply {
        clipChildren = true
    }

    private val imageView: ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.TRANSPARENT)
        visibility = GONE
    }

    private val textView: TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        visibility = GONE
        setTextColor(colors.textColorPrimary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private var badgeView: Badge? = null

    private var avatarSize: AvatarSize = AvatarSize.M
    private var avatarShape: AvatarShape = AvatarShape.Round
    private var avatarBadge: AvatarBadge = AvatarBadge.None

    private var actualAvatarSizePx: Int = 0

    init {
        avatarContainer.addView(
            imageView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        avatarContainer.addView(
            textView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        addView(avatarContainer)

        attrs?.let { parseAttributes(it) }

        setSize(avatarSize)
        applyShape()
        applyBackgroundColor()
    }

    private fun parseAttributes(attrs: AttributeSet) {
        val typedArray: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.AtomicAvatar)

        try {
            val sizeOrdinal =
                typedArray.getInt(R.styleable.AtomicAvatar_avatarSize, AvatarSize.M.ordinal)
            avatarSize = AvatarSize.values()[sizeOrdinal]

            val shapeOrdinal =
                typedArray.getInt(R.styleable.AtomicAvatar_avatarShape, AvatarShape.Round.ordinal)
            avatarShape = AvatarShape.values()[shapeOrdinal]

        } finally {
            typedArray.recycle()
        }
    }

    fun setContent(content: AvatarContent) {
        when (content) {
            is AvatarContent.URL -> setImageContent(content.url, content.placeImage)
            is AvatarContent.Text -> setTextContent(content.name)
            is AvatarContent.Icon -> setIconContent(content.drawable)
        }
    }

    fun setSize(size: AvatarSize) {
        avatarSize = size
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.textSizeSp.toFloat())

        applyShape()
        requestLayout()
        invalidate()
    }

    fun setShape(shape: AvatarShape) {
        avatarShape = shape
        applyShape()
    }

    fun setBadge(badge: AvatarBadge) {
        avatarBadge = badge

        badgeView?.let { removeView(it) }
        badgeView = null

        if (badge !is AvatarBadge.None) {
            badgeView = Badge(context).apply {
                when (badge) {
                    is AvatarBadge.Dot -> setType(Badge.BadgeType.Dot)
                    is AvatarBadge.Text -> {
                        setText(badge.text)
                        setType(Badge.BadgeType.Text)
                    }

                    else -> {}
                }
            }
            addView(badgeView)
        }

        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val newAvatarSizePx = when {
            widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY -> {
                min(widthSize, heightSize)
            }

            widthMode == MeasureSpec.EXACTLY -> {
                widthSize
            }

            heightMode == MeasureSpec.EXACTLY -> {
                heightSize
            }

            else -> {
                dp2px(avatarSize.sizeDp, context.resources.displayMetrics).toInt()
            }
        }

        if (actualAvatarSizePx != newAvatarSizePx) {
            actualAvatarSizePx = newAvatarSizePx
            applyShape()
        }

        val exactSpec = MeasureSpec.makeMeasureSpec(actualAvatarSizePx, MeasureSpec.EXACTLY)
        avatarContainer.measure(exactSpec, exactSpec)

        var containerWidth = actualAvatarSizePx
        var containerHeight = actualAvatarSizePx

        badgeView?.let { badge ->
            badge.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            val badgeW = badge.measuredWidth
            val badgeH = badge.measuredHeight

            val (centerX, centerY) = calculateBadgeCenter(actualAvatarSizePx)

            val badgeLeft = (centerX - badgeW / 2).toInt()
            val badgeTop = (centerY - badgeH / 2).toInt()

            val badgeActualRight = badgeLeft + badgeW
            if (badgeActualRight > actualAvatarSizePx) {
                containerWidth = badgeActualRight
            }

            val topOverflow = if (badgeTop < 0) -badgeTop else 0
            containerHeight = actualAvatarSizePx + topOverflow
        }

        setMeasuredDimension(
            resolveSize(containerWidth, widthMeasureSpec),
            resolveSize(containerHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val sizePx = actualAvatarSizePx
        val (centerX, centerY) = calculateBadgeCenter(sizePx)

        var topOffset = 0

        badgeView?.let { badge ->
            val badgeH = badge.measuredHeight
            val rawBadgeTop = (centerY - badgeH / 2).toInt()
            if (rawBadgeTop < 0) {
                topOffset = -rawBadgeTop
            }
        }

        avatarContainer.layout(0, topOffset, sizePx, topOffset + sizePx)

        badgeView?.let { badge ->
            val badgeW = badge.measuredWidth
            val badgeH = badge.measuredHeight

            val badgeLeft = (centerX - badgeW / 2).toInt()
            val rawBadgeTop = (centerY - badgeH / 2).toInt()
            val badgeTop = rawBadgeTop + topOffset

            badge.layout(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH)
        }
    }

    private fun calculateBadgeCenter(avatarSizePx: Int): Pair<Float, Float> {
        return when (avatarShape) {
            AvatarShape.Round -> {
                val radius = avatarSizePx / 2f
                val extraPadding = dp2px(BADGE_EXTRA_PADDING_DP, context.resources.displayMetrics)
                val offset = (radius + extraPadding) * SQRT2_OVER_2
                (radius + offset) to (radius - offset)
            }

            AvatarShape.RoundRectangle -> {
                val borderRadiusPx =
                    dp2px(avatarSize.borderRadiusDp, context.resources.displayMetrics)
                if (avatarBadge is AvatarBadge.Dot) {
                    val offset = borderRadiusPx * (1 - SQRT2_OVER_2)
                    (avatarSizePx - offset) to offset
                } else {
                    avatarSizePx.toFloat() to 0f
                }
            }

            AvatarShape.Rectangle -> {
                avatarSizePx.toFloat() to 0f
            }
        }
    }

    private fun setImageContent(url: String, @DrawableRes placeImage: Int) {
        imageView.visibility = VISIBLE
        textView.visibility = GONE
        avatarContainer.setBackgroundColor(Color.TRANSPARENT)
        val normalizedURL = url.trim()
        ImageLoader.clear(context, imageView)
        if (normalizedURL.isEmpty()) {
            if (placeImage == 0) {
                imageView.setImageDrawable(null)
            } else {
                imageView.setImageResource(placeImage)
            }
            return
        }
        val options = ImageOptions.Builder()
            .setPlaceImage(placeImage)
            .setErrorImage(placeImage)
            .build()
        ImageLoader.load(context, imageView, normalizedURL, options)
    }

    private fun setTextContent(name: String) {
        imageView.visibility = GONE
        textView.visibility = VISIBLE
        textView.text = name
        applyBackgroundColor()
    }

    private fun setIconContent(drawable: Drawable) {
        imageView.visibility = VISIBLE
        textView.visibility = GONE
        avatarContainer.setBackgroundColor(Color.TRANSPARENT)
        imageView.setImageDrawable(drawable)
    }

    private fun applyShape() {
        val sizePx = if (actualAvatarSizePx > 0) {
            actualAvatarSizePx.toFloat()
        } else {
            dp2px(avatarSize.sizeDp, context.resources.displayMetrics)
        }

        val clipProvider = when (avatarShape) {
            AvatarShape.Round -> createRoundClipDrawable(sizePx / 2)
            AvatarShape.RoundRectangle -> createRoundClipDrawable(
                dp2px(avatarSize.borderRadiusDp, context.resources.displayMetrics)
            )

            AvatarShape.Rectangle -> null
        }

        avatarContainer.clipToOutline = clipProvider != null
        avatarContainer.outlineProvider = clipProvider

        val cornerRadius = when (avatarShape) {
            AvatarShape.Round -> sizePx / 2f
            AvatarShape.RoundRectangle -> dp2px(
                avatarSize.borderRadiusDp,
                context.resources.displayMetrics
            )
            AvatarShape.Rectangle -> 0f
        }
        avatarContainer.setCornerRadius(cornerRadius)
    }

    private fun applyBackgroundColor() {
        avatarContainer.setBackgroundColor(colors.bgColorAvatar)
    }

    private fun createRoundClipDrawable(radius: Float): ViewOutlineProvider {
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val actualRadius = when (avatarShape) {
                    AvatarShape.Round -> view.width / 2f
                    AvatarShape.RoundRectangle -> dp2px(
                        avatarSize.borderRadiusDp,
                        context.resources.displayMetrics
                    )

                    else -> radius
                }
                outline.setRoundRect(0, 0, view.width, view.height, actualRadius)
            }
        }
    }

    private class ClippingFrameLayout(context: Context) : FrameLayout(context) {
        private val clipPath = Path()
        private val clipRect = RectF()
        private var cornerRadius: Float = 0f

        fun setCornerRadius(radius: Float) {
            if (cornerRadius != radius) {
                cornerRadius = radius
                rebuildPath()
                invalidate()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildPath()
        }

        private fun rebuildPath() {
            clipPath.reset()
            if (width <= 0 || height <= 0) return
            clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
            if (cornerRadius > 0f) {
                clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, Path.Direction.CW)
            } else {
                clipPath.addRect(clipRect, Path.Direction.CW)
            }
        }

        override fun draw(canvas: Canvas) {
            if (cornerRadius > 0f && !clipPath.isEmpty) {
                val save = canvas.save()
                canvas.clipPath(clipPath)
                super.draw(canvas)
                canvas.restoreToCount(save)
            } else {
                super.draw(canvas)
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (cornerRadius > 0f && !clipPath.isEmpty) {
                val save = canvas.save()
                canvas.clipPath(clipPath)
                super.dispatchDraw(canvas)
                canvas.restoreToCount(save)
            } else {
                super.dispatchDraw(canvas)
            }
        }
    }
}

private class Badge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DOT_SIZE_DP = 8f
        private const val TEXT_HEIGHT_DP = 16f
        private const val TEXT_HORIZONTAL_PADDING_DP = 5f
        private const val TEXT_CORNER_RADIUS_DP = 8f
        private const val TEXT_SIZE_SP = 12f
    }

    enum class BadgeType {
        Dot,
        Text
    }

    private val themeStore = ThemeStore.shared(context)

    private var backgroundColor: Int = 0
    private var textColor: Int = 0

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            TEXT_SIZE_SP,
            resources.displayMetrics
        )
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var badgeType: BadgeType = BadgeType.Text
    private var badgeText: String = ""
    private val rectF = RectF()

    private var cachedDotSize: Int = 0
    private var cachedTextHeight: Int = 0
    private var cachedTextPadding: Int = 0
    private var cachedCornerRadius: Float = 0f

    init {
        cachedDotSize = dp2px(DOT_SIZE_DP, context.resources.displayMetrics).toInt()
        cachedTextHeight = dp2px(TEXT_HEIGHT_DP, context.resources.displayMetrics).toInt()
        cachedTextPadding =
            dp2px(TEXT_HORIZONTAL_PADDING_DP * 2, context.resources.displayMetrics).toInt()
        cachedCornerRadius = dp2px(TEXT_CORNER_RADIUS_DP, context.resources.displayMetrics)

        updateColors()
    }

    fun setType(type: BadgeType) {
        if (badgeType != type) {
            badgeType = type
            requestLayout()
            invalidate()
        }
    }

    fun setText(text: String) {
        if (badgeText != text) {
            badgeText = text
            requestLayout()
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        when (badgeType) {
            BadgeType.Dot -> {
                setMeasuredDimension(cachedDotSize, cachedDotSize)
            }

            BadgeType.Text -> {
                if (badgeText.isEmpty()) {
                    setMeasuredDimension(0, 0)
                } else {
                    val textWidth = textPaint.measureText(badgeText)
                    val width = (ceil(textWidth) + cachedTextPadding).toInt()
                    setMeasuredDimension(width, cachedTextHeight)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (badgeType) {
            BadgeType.Dot -> drawDot(canvas)
            BadgeType.Text -> {
                if (badgeText.isNotEmpty()) {
                    drawTextBadge(canvas)
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateColors()
    }

    private fun drawDot(canvas: Canvas) {
        backgroundPaint.color = backgroundColor
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 2f
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
    }

    private fun drawTextBadge(canvas: Canvas) {
        backgroundPaint.color = backgroundColor
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, cachedCornerRadius, cachedCornerRadius, backgroundPaint)

        textPaint.color = textColor
        val centerX = width / 2f
        val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(badgeText, centerX, textY, textPaint)
    }

    private fun updateColors() {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        backgroundColor = colors.textColorError
        textColor = colors.textColorButton
    }
}
