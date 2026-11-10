package com.trtc.uikit.livekit.common.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import kotlin.math.roundToInt

/** Live value slider with product styling and integer step snapping. */
internal class LiveSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    var onValueChanged: ((Int) -> Unit)? = null

    var step: Int = 1
        private set

    private var minimumValue = 0
    private var maximumValue = 100
    private var value = 0

    private val trackHeight = resources.getDimension(R.dimen.livekit_slider_track_height)
    private val thumbSize = resources.getDimension(R.dimen.livekit_slider_thumb_size)
    private val thumbBorderWidth = resources.getDimension(R.dimen.livekit_slider_thumb_border_width)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.livekit_slider_track)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.livekit_slider_progress)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.livekit_slider_thumb)
        setShadowLayer(
            resources.getDimension(R.dimen.livekit_slider_thumb_shadow_radius),
            0f,
            0f,
            ContextCompat.getColor(context, R.color.livekit_slider_thumb_shadow),
        )
    }
    private val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thumbBorderWidth
        color = ContextCompat.getColor(context, R.color.livekit_slider_progress)
    }
    private val trackBounds = RectF()
    private val progressBounds = RectF()

    init {
        val values = context.obtainStyledAttributes(attrs, R.styleable.LiveSlider)
        try {
            val minimum = values.getInt(R.styleable.LiveSlider_liveSliderMinimumValue, 0)
            val maximum = values.getInt(R.styleable.LiveSlider_liveSliderMaximumValue, 100)
            val configuredStep = values.getInt(R.styleable.LiveSlider_liveSliderStep, 1)
            setRange(minimum, maximum, configuredStep)
        } finally {
            values.recycle()
        }
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
    }

    fun setRange(minimumValue: Int, maximumValue: Int, step: Int = 1) {
        require(maximumValue >= minimumValue) { "maximumValue must be at least minimumValue" }
        require(step > 0) { "step must be positive" }
        this.minimumValue = minimumValue
        this.maximumValue = maximumValue
        this.step = step
        value = snap(value)
        invalidate()
    }

    fun setValue(value: Int) {
        val snappedValue = snap(value)
        if (this.value == snappedValue) return
        this.value = snappedValue
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val radius = thumbSize / 2f
        val centerY = height / 2f
        trackBounds.set(radius, centerY - trackHeight / 2f, width - radius, centerY + trackHeight / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackBounds.isEmpty) return

        val range = maximumValue - minimumValue
        val ratio = if (range == 0) 0f else (value - minimumValue).toFloat() / range
        val visualRatio = if (isRtl) {
            1f - ratio
        } else {
            ratio
        }
        val thumbCenterX = trackBounds.left + trackBounds.width() * visualRatio
        val trackRadius = trackHeight / 2f
        canvas.drawRoundRect(trackBounds, trackRadius, trackRadius, trackPaint)
        progressBounds.set(
            if (isRtl) thumbCenterX else trackBounds.left,
            trackBounds.top,
            if (isRtl) trackBounds.right else thumbCenterX,
            trackBounds.bottom,
        )
        if (progressBounds.width() > 0f) {
            canvas.drawRoundRect(progressBounds, trackRadius, trackRadius, progressPaint)
        }

        val thumbRadius = thumbSize / 2f
        canvas.drawCircle(thumbCenterX, trackBounds.centerY(), thumbRadius, thumbPaint)
        canvas.drawCircle(
            thumbCenterX,
            trackBounds.centerY(),
            thumbRadius - thumbBorderWidth / 2f,
            thumbBorderPaint,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.x)
                return true
            }

            MotionEvent.ACTION_UP -> {
                updateFromTouch(event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    @Suppress("DEPRECATION") // API 21 needs the RangeInfo factory; the constructor is newer.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
            minimumValue.toFloat(),
            maximumValue.toFloat(),
            value.toFloat(),
        )
        if (isEnabled && minimumValue < maximumValue) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val delta = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -step
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> step
            else -> return super.performAccessibilityAction(action, arguments)
        }
        return updateValue(value + delta)
    }

    private fun updateFromTouch(x: Float) {
        if (trackBounds.width() <= 0f) return
        var ratio = ((x - trackBounds.left) / trackBounds.width()).coerceIn(0f, 1f)
        if (isRtl) ratio = 1f - ratio
        updateValue(minimumValue + ((maximumValue - minimumValue) * ratio).roundToInt())
    }

    private fun updateValue(value: Int): Boolean {
        val next = snap(value)
        if (next == this.value) return false
        this.value = next
        invalidate()
        onValueChanged?.invoke(next)
        return true
    }

    private fun snap(value: Int): Int {
        val clamped = value.coerceIn(minimumValue, maximumValue)
        val stepped = ((clamped - minimumValue).toFloat() / step).roundToInt() * step + minimumValue
        return stepped.coerceIn(minimumValue, maximumValue)
    }

    private val isRtl: Boolean
        get() = layoutDirection == LAYOUT_DIRECTION_RTL
}
