package com.trtc.uikit.roomkit.inroom.common.miclevel

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R

class RoomMicLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var volume: Int = 0
        private set

    private var hasBoundVolume = false
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    init {
        isClickable = false
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cavityLeft = width * CAVITY_X
        val cavityTop = height * CAVITY_Y
        val cavityWidth = width * CAVITY_WIDTH
        val cavityHeight = height * CAVITY_HEIGHT
        if (cavityWidth <= 0f || cavityHeight <= 0f) return
        if (volume <= 0) return
        val fillHeight = cavityHeight * volume.coerceAtMost(MAX_VOLUME) / MAX_VOLUME.toFloat()
        clipPath.reset()
        clipPath.addRoundRect(
            cavityLeft,
            cavityTop,
            cavityLeft + cavityWidth,
            cavityTop + cavityHeight,
            cavityWidth / 2f,
            cavityWidth / 2f,
            Path.Direction.CW,
        )
        canvas.save()
        canvas.clipPath(clipPath)
        paint.color = ContextCompat.getColor(context, R.color.roomkit_success)
        canvas.drawRect(
            cavityLeft,
            cavityTop + cavityHeight - fillHeight,
            cavityLeft + cavityWidth,
            cavityTop + cavityHeight,
            paint,
        )
        canvas.restore()
    }

    fun setVolume(value: Int) {
        val clamped = value.coerceIn(0, MAX_VOLUME)
        if (clamped == volume && hasBoundVolume) return
        val from = volume
        val hasAnimated = hasBoundVolume && isAttachedToWindow && height > 0
        volume = clamped
        hasBoundVolume = true
        animator?.cancel()
        if (hasAnimated && from != clamped) {
            animator = ValueAnimator.ofInt(from, clamped).apply {
                duration = ANIM_DURATION
                interpolator = LinearInterpolator()
                addUpdateListener {
                    volume = it.animatedValue as Int
                    invalidate()
                }
                start()
            }
        } else {
            invalidate()
        }
    }

    fun reset() {
        animator?.cancel()
        animator = null
        volume = 0
        hasBoundVolume = false
        invalidate()
    }

    companion object {
        const val MAX_VOLUME = 100
        const val UPDATE_INTERVAL_MS = 200L
        private const val ANIM_DURATION = 120L
        private const val CAVITY_X = 0.34375f
        private const val CAVITY_Y = 0.125f
        private const val CAVITY_WIDTH = 0.3125f
        private const val CAVITY_HEIGHT = 0.53125f
    }
}
