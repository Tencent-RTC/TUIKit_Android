package io.trtc.tuikit.chat.demo.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin

// Live recording waveform aligned with MessageInput's smooth recorder overlay.
class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val barWidth = 4f * density
    private val barGap = 4f * density
    private val barRadius = barWidth / 2f
    private val minBarHeight = 4f * density
    private val minWaveformWidth = 160f * density

    private var phases = FloatArray(0)
    private var freqs = FloatArray(0)
    private val rect = RectF()
    // Bar color is supplied by the host via setBarColor using theme tokens; the
    // transparent default avoids rendering any non-token color before that.
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT }

    private var active = false
    private var powerLevel = 0
    private var startTimeNanos = System.nanoTime()
    private var animator: ValueAnimator? = null

    private fun ensureWaveformSeed(barCount: Int) {
        if (phases.size == barCount) return
        val rng = java.util.Random(20260421L)
        phases = FloatArray(barCount) { rng.nextFloat() * (Math.PI * 2).toFloat() }
        freqs = FloatArray(barCount) { 2.4f + rng.nextFloat() * 3.2f }
    }

    private fun startAnimator() {
        animator?.cancel()
        startTimeNanos = System.nanoTime()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    fun setBarColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setPowerLevel(level: Int) {
        powerLevel = level.coerceIn(0, 100)
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (active) {
            startAnimator()
        } else {
            animator?.cancel()
            animator = null
            powerLevel = 0
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val targetWidth = maxOf(width * 0.72f, minWaveformWidth).coerceAtMost(width.toFloat())
        val barCount = ceil((targetWidth + barGap) / (barWidth + barGap)).toInt().coerceAtLeast(1)
        ensureWaveformSeed(barCount)
        val totalWidth = barCount * barWidth + (barCount - 1) * barGap
        var startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        val timeSeconds = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0
        val maxAmplitude = (height * 0.42f).coerceAtLeast(18f * density)
        val amplitude = maxAmplitude * (0.45f + (powerLevel / 100f) * 0.55f)
        for (i in 0 until barCount) {
            val wave = if (active) {
                abs(sin(timeSeconds * freqs[i].toDouble() + phases[i].toDouble())).toFloat()
            } else {
                0f
            }
            val barHeight = minBarHeight + amplitude * wave
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            rect.set(startX, top, startX + barWidth, bottom)
            canvas.drawRoundRect(rect, barRadius, barRadius, paint)
            startX += barWidth + barGap
        }
    }
}
