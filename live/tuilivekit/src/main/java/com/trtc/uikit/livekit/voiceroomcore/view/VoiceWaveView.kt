package com.trtc.uikit.livekit.voiceroomcore.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil

@RequiresApi(Build.VERSION_CODES.S)
class VoiceWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var innerRadiusPx: Float
    private var outerRadiusPx: Float
    private var waveColor: Int

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ripples = mutableListOf<Ripple>()

    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var lastRippleAddTime: Long = 0
    private var isRunning = false

    init {
        val defaultInnerRadius = dp2px(DEFAULT_INNER_RADIUS_DP)
        val defaultOuterRadius = dp2px(DEFAULT_OUTER_RADIUS_DP)
        waveColor = DEFAULT_COLOR
        innerRadiusPx = defaultInnerRadius
        outerRadiusPx = defaultOuterRadius

        if (attrs != null) {
            val typedArray = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.DouyinRippleView,
                defStyleAttr,
                0
            )
            try {
                waveColor = typedArray.getColor(R.styleable.DouyinRippleView_ripple_color, waveColor)
                innerRadiusPx = typedArray.getDimensionPixelSize(R.styleable.DouyinRippleView_ripple_inner_radius, innerRadiusPx.toInt()).toFloat()
                outerRadiusPx = typedArray.getDimensionPixelSize(R.styleable.DouyinRippleView_ripple_outer_radius, outerRadiusPx.toInt()).toFloat()
            } finally {
                typedArray.recycle()
            }
        }
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startRipple()
        } else {
            stopRipple()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRipple()
    }

    fun startRipple() {
        if (isRunning) return
        isRunning = true
        ripples.clear()
        lastRippleAddTime = System.currentTimeMillis()
        ripples.add(Ripple(lastRippleAddTime))
        postInvalidateOnAnimation()
    }

    fun stopRipple() {
        isRunning = false
        ripples.clear()
        removeCallbacks(null)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isRunning || ripples.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()
        centerX = width / 2f
        centerY = height / 2f

        if (ripples.size < 2 && now - lastRippleAddTime > RIPPLE_INTERVAL) {
            lastRippleAddTime = now
            ripples.add(Ripple(now))
        }

        val thickStrokeWidthPx = dp2px(THICK_STROKE_WIDTH_DP)
        val thinStrokeWidthPx = dp2px(THIN_STROKE_WIDTH_DP)
        val baseAlpha = Color.alpha(waveColor)
        paint.color = waveColor

        val iterator = ripples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            val progress = (now - ripple.startTime) / RIPPLE_DURATION.toFloat()
            if (progress > 1f) {
                iterator.remove()
                continue
            }

            val currentProgress = progress.coerceAtMost(1f)
            val travelDistance = outerRadiusPx - innerRadiusPx
            val currentRadius = innerRadiusPx + travelDistance * currentProgress
            val strokeWidthChange = thickStrokeWidthPx - thinStrokeWidthPx
            val currentStrokeWidth = thickStrokeWidthPx - strokeWidthChange * currentProgress

            paint.strokeWidth = currentStrokeWidth
            paint.alpha = (baseAlpha * (1 - currentProgress)).toInt()

            canvas.drawCircle(centerX, centerY, currentRadius, paint)
        }

        if (isRunning) {
            postInvalidateOnAnimation()
        }
    }

    private fun dp2px(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    private data class Ripple(val startTime: Long)

    companion object {
        private val DEFAULT_COLOR = Color.parseColor("#66FFFFFF")
        private const val DEFAULT_INNER_RADIUS_DP = 20f
        private const val DEFAULT_OUTER_RADIUS_DP = 42f
        private const val THICK_STROKE_WIDTH_DP = 2.5f
        private const val THIN_STROKE_WIDTH_DP = 0.8f
        private const val RIPPLE_DURATION = 1500
        private const val RIPPLE_INTERVAL = 500
    }
}