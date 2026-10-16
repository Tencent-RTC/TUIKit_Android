package com.trtc.uikit.livekit.common.theme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.trtc.uikit.livekit.R

/** Shared beauty/ear-monitor switch: a solid rounded track and an inset white thumb. */
class LiveSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    var onCheckedChange: ((Boolean) -> Unit)? = null

    private val knobInset = resources.getDimension(R.dimen.livekit_pusher_audio_switch_knob_inset)
    private val offTrackColor = ContextCompat.getColor(context, R.color.livekit_pusher_audio_switch_off_track)
    private val onTrackColor = ContextCompat.getColor(context, R.color.livekit_switch_on_track)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val trackBounds = RectF()
    private var toggleAnimator: ValueAnimator? = null
    private var progress = 0f
    private var isChecked = false

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            setChecked(!isChecked, animate = true)
            onCheckedChange?.invoke(isChecked)
        }
    }

    fun setChecked(checked: Boolean, animate: Boolean) {
        // SDK state echoes must not interrupt the animation started by the tap.
        if (isChecked == checked) return
        isChecked = checked
        toggleAnimator?.cancel()
        toggleAnimator = null
        val target = if (checked) 1f else 0f
        if (!animate || !isAttachedToWindow) {
            progress = target
            invalidate()
            return
        }
        toggleAnimator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 180L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (toggleAnimator === animation) toggleAnimator = null
                }
            })
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(resources.getDimensionPixelSize(R.dimen.livekit_pusher_audio_switch_width), widthMeasureSpec),
            resolveSize(resources.getDimensionPixelSize(R.dimen.livekit_pusher_audio_switch_height), heightMeasureSpec)
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        trackBounds.set(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackBounds.isEmpty) return
        val radius = minOf(trackBounds.width(), trackBounds.height()) / 2f
        // Blend all four channels; setting Paint.alpha would discard the off track's alpha.
        trackPaint.color = ColorUtils.blendARGB(offTrackColor, onTrackColor, progress)
        canvas.drawRoundRect(trackBounds, radius, radius, trackPaint)
        val knobRadius = (radius - knobInset).coerceAtLeast(0f)
        val position = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f - progress else progress
        val centerX = radius + (trackBounds.width() - radius * 2f) * position
        canvas.drawCircle(centerX, trackBounds.centerY(), knobRadius, knobPaint)
    }

    override fun onDetachedFromWindow() {
        toggleAnimator?.cancel()
        toggleAnimator = null
        progress = if (isChecked) 1f else 0f
        super.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Switch::class.java.name
        info.isCheckable = true
        info.isChecked = isChecked
    }
}
