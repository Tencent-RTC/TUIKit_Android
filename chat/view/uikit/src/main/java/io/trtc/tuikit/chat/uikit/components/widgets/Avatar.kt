package io.trtc.tuikit.chat.uikit.components.widgets
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.config.GlobalAvatarShape
import io.trtc.tuikit.atomicx.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

private val supportedAvatarImageUrlSchemes = setOf("http", "https", "content", "file", "android.resource")

internal fun normalizeAvatarImageUrlKey(url: Any?): String? {
    return when (url) {
        is String -> {
            val trimmedUrl = url.trim()
            trimmedUrl.takeIf { it.isNotEmpty() && hasSupportedAvatarImageUrlScheme(it) }
        }
        null -> null
        else -> url.toString().takeIf { it.isNotEmpty() }
    }
}

private fun hasSupportedAvatarImageUrlScheme(url: String): Boolean {
    val colonIndex = url.indexOf(':')
    if (colonIndex <= 0 || url.length <= colonIndex + 2) return false
    if (url[colonIndex + 1] != '/' || url[colonIndex + 2] != '/') return false
    val scheme = url.substring(0, colonIndex).lowercase(Locale.US)
    return scheme in supportedAvatarImageUrlSchemes
}

class Avatar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        internal const val STATUS_DOT_SIZE_DP = 8f
        internal const val STATUS_DOT_BORDER_DP = 1f
        private const val STATUS_DOT_OFFSET_DP = 2f
        private const val GRAY_LIGHT_7 = 0xFFA5A9B0.toInt()
        private const val ICON_SCALE_FACTOR = 0.5f
        private const val INVALID_IMAGE_URL_CACHE_MAX_SIZE = 100

        private val invalidImageUrlCache = LinkedHashSet<String>()
    }

    sealed class AvatarContent {
        data class Image(val url: Any?, val fallbackName: String = "") : AvatarContent()
        data class Text(val name: String) : AvatarContent()
        data class Icon(val drawable: Drawable) : AvatarContent()
        data class Default(val isGroup: Boolean = false) : AvatarContent()
    }

    sealed class AvatarBadge {
        object None : AvatarBadge()
        object Dot : AvatarBadge()
        data class Text(val text: String) : AvatarBadge()
        data class Count(val count: Int) : AvatarBadge()
    }

    enum class AvatarSize(
        val sizeDp: Float,
        val textSizeSp: Float,
        val borderRadiusDp: Float
    ) {
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

    enum class AvatarStatus {
        None,
        Online,
        Offline
    }

    private val themeStore = ThemeStore.shared(context)
    private val colors get() = themeStore.themeState.value.currentTheme.tokens.color

    private val avatarContainer = FrameLayout(context).apply {
        clipChildren = true
    }

    private val imageView: ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = GONE
    }

    private val iconView: ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = GONE
    }

    private val textView: TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        visibility = GONE
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private var badgeView: AvatarBadgeView? = null
    private var statusDotView: StatusDotView? = null

    private var avatarSize: AvatarSize = AvatarSize.M
    private var avatarShape: AvatarShape? = null
    private var avatarStatus: AvatarStatus = AvatarStatus.None
    private var avatarBadge: AvatarBadge = AvatarBadge.None
    private var avatarContent: AvatarContent? = null
    private var onAvatarClickListener: (() -> Unit)? = null
    private var actualAvatarSizePx: Int = 0
    private var viewScope: CoroutineScope? = null
    private var currentImageRequestId: Long = 0L

    init {
        clipChildren = false
        clipToPadding = false

        avatarContainer.addView(
            imageView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        avatarContainer.addView(
            iconView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        avatarContainer.addView(
            textView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        addView(avatarContainer)

        setSize(avatarSize)
        applyShape()
        applyBackgroundColor()
    }

    fun setContent(content: AvatarContent) {
        avatarContent = content
        currentImageRequestId += 1
        val requestId = currentImageRequestId
        when (content) {
            is AvatarContent.Image -> setImageContent(content.url, content.fallbackName, requestId)
            is AvatarContent.Text -> setTextContent(content.name)
            is AvatarContent.Icon -> setIconContent(content.drawable)
            is AvatarContent.Default -> setDefaultContent(content.isGroup)
        }
    }

    fun setSize(size: AvatarSize) {
        avatarSize = size
        applyTextStyle()
        applyShape()
        requestLayout()
        invalidate()
    }

    fun setShape(shape: AvatarShape?) {
        avatarShape = shape
        applyShape()
    }

    fun setStatus(status: AvatarStatus) {
        avatarStatus = status

        statusDotView?.let { removeView(it) }
        statusDotView = null

        if (status == AvatarStatus.Online || status == AvatarStatus.Offline) {
            val dotColor = when (status) {
                AvatarStatus.Offline -> GRAY_LIGHT_7
                AvatarStatus.Online -> colors.textColorSuccess
                else -> 0
            }
            statusDotView = StatusDotView(context).apply {
                setDotColor(dotColor)
                setBorderColor(colors.bgColorDefault)
            }
            addView(statusDotView)
        }

        requestLayout()
    }

    fun setBadge(badge: AvatarBadge) {
        avatarBadge = badge

        badgeView?.let { removeView(it) }
        badgeView = null

        if (badge !is AvatarBadge.None) {
            val badgeText = when (badge) {
                is AvatarBadge.Text -> badge.text
                is AvatarBadge.Count -> badge.count.toString()
                else -> ""
            }
            badgeView = AvatarBadgeView(context).apply {
                if (badge is AvatarBadge.Dot || badgeText.isEmpty()) {
                    setType(AvatarBadgeView.BadgeType.Dot)
                } else {
                    setText(badgeText)
                    setType(AvatarBadgeView.BadgeType.Text)
                }
            }
            addView(badgeView)
        }

        requestLayout()
    }

    fun setOnAvatarClickListener(listener: (() -> Unit)?) {
        onAvatarClickListener = listener
        if (listener != null) {
            setOnClickListener { onAvatarClickListener?.invoke() }
        } else {
            setOnClickListener(null)
            isClickable = false
        }
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

            widthMode == MeasureSpec.EXACTLY -> widthSize
            heightMode == MeasureSpec.EXACTLY -> heightSize
            else -> dp2px(avatarSize.sizeDp, context.resources.displayMetrics).toInt()
        }

        if (actualAvatarSizePx != newAvatarSizePx) {
            actualAvatarSizePx = newAvatarSizePx
            applyShape()
            updateIconSize()
        }

        val exactSpec = MeasureSpec.makeMeasureSpec(actualAvatarSizePx, MeasureSpec.EXACTLY)
        avatarContainer.measure(exactSpec, exactSpec)

        statusDotView?.let { dot ->
            dot.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
        }

        badgeView?.let { badge ->
            badge.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
        }

        setMeasuredDimension(
            resolveSize(actualAvatarSizePx, widthMeasureSpec),
            resolveSize(actualAvatarSizePx, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        avatarContainer.layout(0, 0, actualAvatarSizePx, actualAvatarSizePx)
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL

        statusDotView?.let { dot ->
            val dotW = dot.measuredWidth
            val dotH = dot.measuredHeight
            val offsetPx = dp2px(STATUS_DOT_OFFSET_DP, context.resources.displayMetrics).toInt()
            val dotLeft = if (isRtl) {
                -offsetPx
            } else {
                actualAvatarSizePx - dotW + offsetPx
            }
            val dotTop = actualAvatarSizePx - dotH + offsetPx
            dot.layout(dotLeft, dotTop, dotLeft + dotW, dotTop + dotH)
        }

        badgeView?.let { badge ->
            val badgeW = badge.measuredWidth
            val badgeH = badge.measuredHeight
            val badgeLeft = if (isRtl) {
                -(badgeW / 2)
            } else {
                actualAvatarSizePx - badgeW / 2
            }
            val badgeTop = -(badgeH / 2)
            badge.layout(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            themeStore.themeState.collectLatest {
                applyThemeColors()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun setImageContent(url: Any?, fallbackName: String, requestId: Long) {
        val urlKey = normalizeAvatarImageUrlKey(url)
        if (urlKey == null) {
            Glide.with(context.applicationContext).clear(imageView)
            showFallbackText(fallbackName)
            return
        }
        if (isInvalidImageUrlCached(urlKey)) {
            Glide.with(context.applicationContext).clear(imageView)
            showFallbackText(fallbackName)
            return
        }

        showImageView()
        Glide.with(context.applicationContext)
            .load(url)
            .centerCrop()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    cacheInvalidImageUrl(urlKey)
                    post {
                        if (requestId == currentImageRequestId) {
                            Glide.with(context.applicationContext).clear(imageView)
                            showFallbackText(fallbackName)
                        }
                    }
                    return true
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }
            })
            .into(imageView)
    }

    private fun setTextContent(name: String) {
        val title = if (name.isEmpty()) "" else name.first().uppercase()
        hideAllContentViews()
        textView.visibility = VISIBLE
        textView.text = title
    }

    private fun setIconContent(drawable: Drawable) {
        hideAllContentViews()
        iconView.visibility = VISIBLE
        iconView.setImageDrawable(drawable)
        iconView.setColorFilter(colors.textColorPrimary)
        updateIconSize()
    }

    private fun setDefaultContent(isGroup: Boolean) {
        val resId = if (isGroup) {
            getDefaultGroupAvatarRes()
        } else {
            getDefaultUserAvatarRes()
        }
        showImageView()
        Glide.with(context.applicationContext)
            .load(resId)
            .centerCrop()
            .into(imageView)
    }

    private fun showFallbackText(fallbackName: String) {
        hideAllContentViews()
        textView.visibility = VISIBLE
        textView.text = if (fallbackName.isEmpty()) {
            ""
        } else {
            fallbackName.first().uppercase()
        }
    }

    private fun showImageView() {
        hideAllContentViews()
        imageView.visibility = VISIBLE
    }

    private fun hideAllContentViews() {
        imageView.visibility = GONE
        iconView.visibility = GONE
        textView.visibility = GONE
    }

    private fun isInvalidImageUrlCached(urlKey: String): Boolean {
        synchronized(invalidImageUrlCache) {
            return invalidImageUrlCache.contains(urlKey)
        }
    }

    private fun cacheInvalidImageUrl(urlKey: String) {
        synchronized(invalidImageUrlCache) {
            if (invalidImageUrlCache.contains(urlKey)) {
                return
            }
            if (invalidImageUrlCache.size >= INVALID_IMAGE_URL_CACHE_MAX_SIZE) {
                val oldestUrlKey = invalidImageUrlCache.firstOrNull()
                if (oldestUrlKey != null) {
                    invalidImageUrlCache.remove(oldestUrlKey)
                }
            }
            invalidImageUrlCache.add(urlKey)
        }
    }

    private fun resolveEffectiveShape(): AvatarShape {
        avatarShape?.let { return it }
        return when (AppBuilderConfig.avatarShape) {
            GlobalAvatarShape.CIRCULAR -> AvatarShape.Round
            GlobalAvatarShape.ROUNDED -> AvatarShape.RoundRectangle
            GlobalAvatarShape.SQUARE -> AvatarShape.Rectangle
            else -> AvatarShape.RoundRectangle
        }
    }

    private fun applyShape() {
        val effectiveShape = resolveEffectiveShape()
        val sizePx = if (actualAvatarSizePx > 0) {
            actualAvatarSizePx.toFloat()
        } else {
            dp2px(avatarSize.sizeDp, context.resources.displayMetrics)
        }

        val clipProvider = when (effectiveShape) {
            AvatarShape.Round -> createClipProvider(sizePx / 2)
            AvatarShape.RoundRectangle -> createClipProvider(
                dp2px(avatarSize.borderRadiusDp, context.resources.displayMetrics)
            )
            AvatarShape.Rectangle -> null
            else -> createClipProvider(
                dp2px(avatarSize.borderRadiusDp, context.resources.displayMetrics)
            )
        }

        avatarContainer.clipToOutline = clipProvider != null
        avatarContainer.outlineProvider = clipProvider
    }

    private fun applyBackgroundColor() {
        avatarContainer.setBackgroundColor(colors.bgColorAvatar)
    }

    private fun applyThemeColors() {
        applyBackgroundColor()
        applyTextStyle()
        if (iconView.visibility == VISIBLE) {
            iconView.setColorFilter(colors.textColorPrimary)
        }
        statusDotView?.setBorderColor(colors.bgColorDefault)
        when (avatarStatus) {
            AvatarStatus.Offline -> statusDotView?.setDotColor(GRAY_LIGHT_7)
            AvatarStatus.Online -> statusDotView?.setDotColor(colors.textColorSuccess)
            AvatarStatus.None -> Unit
            else -> Unit
        }
        badgeView?.updateColors()
        invalidate()
    }

    private fun applyTextStyle() {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, avatarSize.textSizeSp)
        textView.setTextColor(colors.textColorPrimary)
        textView.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textView.paint.isFakeBoldText = true
    }

    private fun updateIconSize() {
        if (actualAvatarSizePx > 0 && iconView.visibility == VISIBLE) {
            val iconSizePx = (actualAvatarSizePx * ICON_SCALE_FACTOR).toInt()
            val lp = iconView.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(iconSizePx, iconSizePx)
            lp.width = iconSizePx
            lp.height = iconSizePx
            lp.gravity = Gravity.CENTER
            iconView.layoutParams = lp
        }
    }

    private fun createClipProvider(radius: Float): ViewOutlineProvider {
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val effectiveShape = resolveEffectiveShape()
                val actualRadius = when (effectiveShape) {
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

    @DrawableRes
    private fun getDefaultUserAvatarRes(): Int {
        return try {
            val field = R.drawable::class.java
                .getField("base_component_avatar_user_default_icon")
            field.getInt(null)
        } catch (e: Exception) {
            android.R.drawable.sym_def_app_icon
        }
    }

    @DrawableRes
    private fun getDefaultGroupAvatarRes(): Int {
        return try {
            val field = R.drawable::class.java
                .getField("base_component_avatar_group_default_icon")
            field.getInt(null)
        } catch (e: Exception) {
            android.R.drawable.sym_def_app_icon
        }
    }

}

private class StatusDotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotSizePx = dp2px(Avatar.STATUS_DOT_SIZE_DP, context.resources.displayMetrics).toInt()
    private val borderWidthPx = dp2px(Avatar.STATUS_DOT_BORDER_DP, context.resources.displayMetrics)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun setDotColor(color: Int) {
        dotPaint.color = color
        invalidate()
    }

    fun setBorderColor(color: Int) {
        borderPaint.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dotSizePx, dotSizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val outerRadius = width / 2f
        val innerRadius = outerRadius - borderWidthPx

        canvas.drawCircle(cx, cy, outerRadius, borderPaint)
        canvas.drawCircle(cx, cy, innerRadius, dotPaint)
    }
}

class AvatarBadgeView @JvmOverloads constructor(
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

            else -> {
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

            else -> {
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

    fun updateColors() {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        backgroundColor = colors.textColorError
        textColor = colors.textColorButton
        invalidate()
    }
}
