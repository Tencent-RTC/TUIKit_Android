package io.trtc.tuikit.chat.uikit.components.widgets
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class SwitchSize(
    val widthDp: Float,
    val heightDp: Float,
    val thumbSizeDp: Float,
    val paddingDp: Float,
    val textSizeSp: Float
) {
    S(26f, 16f, 12f, 2f, 10f),
    M(32f, 20f, 15f, 2.5f, 12f),
    L(48f, 28f, 24f, 2f, 14f)
}

sealed class SwitchType {
    data object Basic : SwitchType()
    data object WithText : SwitchType()
    data object WithIcon : SwitchType()
}

class Switch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val ANIMATION_DURATION_MS = 300L
        private const val DISABLED_ALPHA = 0.6f
        private const val LOADING_STROKE_WIDTH_DP = 1f
        private const val LOADING_SIZE_FACTOR = 0.6f
        private const val SHADOW_ELEVATION_S_DP = 1.6f
        private const val SHADOW_ELEVATION_M_DP = 2f
        private const val SHADOW_ELEVATION_L_DP = 2.4f
        private const val LOADING_SWEEP_ANGLE = 270f
        private const val LOADING_ANIMATION_DURATION_MS = 1000L
    }

    private var switchSize: SwitchSize = SwitchSize.L
    private var switchType: SwitchType = SwitchType.Basic
    private var isChecked: Boolean = false
    private var isLoading: Boolean = false
    private var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    private var thumbOffset: Float = 0f
    private var thumbAnimator: ValueAnimator? = null
    private var loadingAngle: Float = 0f
    private var loadingAnimator: ValueAnimator? = null
    private var viewScope: CoroutineScope? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(dp2px(2f, resources.displayMetrics), 0f, dp2px(1f, resources.displayMetrics), 0x20000000)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val trackRect = RectF()
    private val loadingRect = RectF()
    private val checkPath = Path()
    private val closePath = Path()

    init {
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        setOnClickListener {
            if (isEnabled && !isLoading) {
                setChecked(!isChecked, animate = true)
                onCheckedChangeListener?.invoke(isChecked)
            }
        }
    }

    fun setChecked(checked: Boolean, animate: Boolean = false) {
        if (isChecked == checked) return
        isChecked = checked
        if (animate) {
            animateThumb(if (checked) 1f else 0f)
        } else {
            thumbAnimator?.cancel()
            thumbOffset = if (checked) 1f else 0f
            invalidate()
        }
    }

    fun getChecked(): Boolean = isChecked

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        invalidate()
    }

    fun setLoading(loading: Boolean) {
        isLoading = loading
        if (loading) {
            startLoadingAnimation()
        } else {
            stopLoadingAnimation()
        }
        invalidate()
    }

    fun getLoading(): Boolean = isLoading

    fun setSize(size: SwitchSize) {
        switchSize = size
        requestLayout()
        invalidate()
    }

    fun getSize(): SwitchSize = switchSize

    fun setType(type: SwitchType) {
        switchType = type
        requestLayout()
        invalidate()
    }

    fun getType(): SwitchType = switchType

    fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        onCheckedChangeListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dm = resources.displayMetrics
        val effectiveWidth = when (switchType) {
            SwitchType.Basic -> switchSize.widthDp
            SwitchType.WithIcon, SwitchType.WithText -> switchSize.heightDp * 2
        }
        val widthPx = dp2px(effectiveWidth, dm).toInt()
        val heightPx = dp2px(switchSize.heightDp, dm).toInt()
        setMeasuredDimension(
            resolveSize(widthPx, widthMeasureSpec),
            resolveSize(heightPx, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dm = resources.displayMetrics
        val colors = getColors()

        val w = width.toFloat()
        val h = height.toFloat()
        val thumbSizePx = dp2px(switchSize.thumbSizeDp, dm)
        val paddingPx = dp2px(switchSize.paddingDp, dm)

        var trackColor = if (isChecked) colors.switchColorOn else colors.switchColorOff
        var currentThumbColor = colors.switchColorButton
        if (!isEnabled) {
            trackColor = applyAlpha(trackColor, DISABLED_ALPHA)
            currentThumbColor = applyAlpha(currentThumbColor, DISABLED_ALPHA)
        }

        trackPaint.color = trackColor
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, h / 2f, h / 2f, trackPaint)

        if (switchType == SwitchType.WithText || switchType == SwitchType.WithIcon) {
            drawTrackContent(canvas, colors, w, h, thumbSizePx, paddingPx)
        }

        val maxOffset = w - thumbSizePx - paddingPx * 2
        val visualThumbOffset = getVisualThumbOffset()
        val thumbX = paddingPx + maxOffset * visualThumbOffset
        val thumbCenterX = thumbX + thumbSizePx / 2
        val thumbCenterY = h / 2

        val elevationDp = when (switchSize) {
            SwitchSize.S -> SHADOW_ELEVATION_S_DP
            SwitchSize.M -> SHADOW_ELEVATION_M_DP
            SwitchSize.L -> SHADOW_ELEVATION_L_DP
        }
        shadowPaint.color = currentThumbColor
        shadowPaint.setShadowLayer(
            dp2px(elevationDp, dm),
            0f,
            dp2px(elevationDp / 2, dm),
            0x30000000
        )
        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbSizePx / 2, shadowPaint)

        thumbPaint.color = currentThumbColor
        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbSizePx / 2, thumbPaint)

        if (isLoading) {
            val loadingSize = thumbSizePx * LOADING_SIZE_FACTOR
            loadingPaint.strokeWidth = dp2px(LOADING_STROKE_WIDTH_DP, dm)
            loadingPaint.color = colors.switchColorOn
            loadingRect.set(
                thumbCenterX - loadingSize / 2,
                thumbCenterY - loadingSize / 2,
                thumbCenterX + loadingSize / 2,
                thumbCenterY + loadingSize / 2
            )
            canvas.drawArc(loadingRect, loadingAngle, LOADING_SWEEP_ANGLE, false, loadingPaint)
        }
    }

    private fun drawTrackContent(
        canvas: Canvas,
        colors: ColorTokens,
        w: Float,
        h: Float,
        thumbSizePx: Float,
        paddingPx: Float
    ) {
        val dm = resources.displayMetrics
        val contentColor = colors.textColorButton
        val contentWidth = w - thumbSizePx - paddingPx * 2
        val showContentOnLeft = if (isLayoutRtl()) !isChecked else isChecked
        val contentCenterX = if (showContentOnLeft) {
            contentWidth / 2 + paddingPx
        } else {
            w - contentWidth / 2 - paddingPx
        }
        val contentCenterY = h / 2

        when (switchType) {
            SwitchType.WithText -> {
                val text = if (isChecked) {
                    context.getString(R.string.uikit_switch_open)
                } else {
                    context.getString(R.string.uikit_switch_close)
                }
                textPaint.color = contentColor
                textPaint.textSize = dp2px(switchSize.textSizeSp * 0.8f, dm)
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val textY = contentCenterY - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(text, contentCenterX, textY, textPaint)
            }

            SwitchType.WithIcon -> {
                if (isChecked) {
                    drawCheckIcon(
                        canvas,
                        contentCenterX,
                        contentCenterY,
                        thumbSizePx * 0.45f,
                        contentColor
                    )
                } else {
                    drawCloseIcon(
                        canvas,
                        contentCenterX,
                        contentCenterY,
                        thumbSizePx * 0.35f,
                        contentColor
                    )
                }
            }

            else -> {}
        }
    }

    private fun drawCheckIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = size * 0.2f
        checkPath.reset()
        checkPath.moveTo(cx - size * 0.4f, cy)
        checkPath.lineTo(cx - size * 0.1f, cy + size * 0.3f)
        checkPath.lineTo(cx + size * 0.4f, cy - size * 0.3f)
        canvas.drawPath(checkPath, iconPaint)
    }

    private fun drawCloseIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = size * 0.2f
        closePath.reset()
        closePath.moveTo(cx - size, cy - size)
        closePath.lineTo(cx + size, cy + size)
        closePath.moveTo(cx + size, cy - size)
        closePath.lineTo(cx - size, cy + size)
        canvas.drawPath(closePath, iconPaint)
    }

    private fun getVisualThumbOffset(): Float {
        return if (isLayoutRtl()) 1f - thumbOffset else thumbOffset
    }

    private fun isLayoutRtl(): Boolean {
        return layoutDirection == LAYOUT_DIRECTION_RTL
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        invalidate()
    }

    private fun animateThumb(targetValue: Float) {
        thumbAnimator?.cancel()
        thumbAnimator = ValueAnimator.ofFloat(thumbOffset, targetValue).apply {
            duration = ANIMATION_DURATION_MS
            addUpdateListener { animation ->
                thumbOffset = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startLoadingAnimation() {
        loadingAnimator?.cancel()
        loadingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = LOADING_ANIMATION_DURATION_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                loadingAngle = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopLoadingAnimation() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        loadingAngle = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        thumbOffset = if (isChecked) 1f else 0f
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
        thumbAnimator?.cancel()
        stopLoadingAnimation()
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = ((color ushr 24) * alpha).toInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}
