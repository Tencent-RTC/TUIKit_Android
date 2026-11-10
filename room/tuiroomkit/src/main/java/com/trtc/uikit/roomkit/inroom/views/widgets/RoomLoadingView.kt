package com.trtc.uikit.roomkit.inroom.views.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp

class RoomLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val textLabel: AppCompatTextView

    private val strokeWidth = context.dp(SPINNER_STROKE_WIDTH_DP).toFloat()
    private val spinnerBounds = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.roomkit_border)
        strokeWidth = this@RoomLoadingView.strokeWidth
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.roomkit_accent)
        strokeWidth = this@RoomLoadingView.strokeWidth
        strokeCap = Paint.Cap.BUTT
    }

    private var spinRotation = 0f
    private var spinAnimator: ValueAnimator? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_entering_view, this)
        textLabel = findViewById(R.id.entering_text)
        setBackgroundColor(ContextCompat.getColor(context, R.color.roomkit_bg_canvas))
        isClickable = true
        visibility = GONE
        alpha = 0f
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val spinnerSize = context.dp(SPINNER_SIZE_DP).toFloat()
        val inset = strokeWidth / 2
        val centerX = width / 2f
        val centerY = height / 2f - context.dp(SPINNER_CENTER_OFFSET_DP)
        spinnerBounds.set(
            centerX - spinnerSize / 2 + inset,
            centerY - spinnerSize / 2 + inset,
            centerX + spinnerSize / 2 - inset,
            centerY + spinnerSize / 2 - inset,
        )
        canvas.drawOval(spinnerBounds, trackPaint)
        canvas.save()
        canvas.rotate(spinRotation, centerX, centerY)
        canvas.drawArc(spinnerBounds, ARC_START_DEGREES, ARC_SWEEP_DEGREES, false, arcPaint)
        canvas.restore()
    }

    fun setShown(shown: Boolean) {
        if (shown) {
            visibility = VISIBLE
            startSpin()
            animate().alpha(1f).setDuration(FADE_MS).start()
        } else {
            animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                visibility = GONE
                stopSpin()
            }.start()
        }
    }

    private fun startSpin() {
        if (spinAnimator?.isRunning == true) return
        spinAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = SPIN_DURATION_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                spinRotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopSpin() {
        spinAnimator?.cancel()
        spinAnimator = null
    }

    override fun onDetachedFromWindow() {
        stopSpin()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FADE_MS = 200L
        const val SPINNER_SIZE_DP = 44
        const val SPINNER_STROKE_WIDTH_DP = 3
        const val SPINNER_CENTER_OFFSET_DP = 20
        const val ARC_START_DEGREES = -135f
        const val ARC_SWEEP_DEGREES = 180f
        const val SPIN_DURATION_MS = 750L
    }
}
