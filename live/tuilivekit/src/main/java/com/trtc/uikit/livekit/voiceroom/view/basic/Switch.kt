package com.trtc.uikit.livekit.voiceroom.view.basic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R

class Switch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var trackWidth: Int = 0
    var trackHeight: Int = 0
    var thumbPadding: Int = (2 * context.resources.displayMetrics.density + 0.5f).toInt()
    var trackOnDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.livekit_switch_track_on)
    var trackOffDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.livekit_switch_track_off)
    var thumbDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.livekit_switch_thumb)
    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    var isChecked: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            if (changed) {
                animateThumb()
            }
            invalidate()
        }

    private var thumbPosition: Float = 0f
    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            isChecked = !isChecked
            onCheckedChangeListener?.invoke(isChecked)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (trackWidth > 0) trackWidth else resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val h = if (trackHeight > 0) trackHeight else resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        trackWidth = w
        trackHeight = h
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (animator == null) {
            thumbPosition = if (isChecked) 1f else 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val track = if (isChecked) trackOnDrawable else trackOffDrawable
        track?.setBounds(0, 0, trackWidth, trackHeight)
        track?.draw(canvas)

        val thumbSize = trackHeight - thumbPadding * 2
        val thumbTravel = trackWidth - thumbPadding * 2 - thumbSize
        val thumbLeft = thumbPadding + (thumbTravel * thumbPosition).toInt()
        val thumbTop = thumbPadding
        thumbDrawable?.setBounds(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize)
        thumbDrawable?.draw(canvas)
    }

    private fun animateThumb() {
        animator?.cancel()
        val start = thumbPosition
        val end = if (isChecked) 1f else 0f
        animator = ValueAnimator.ofFloat(start, end).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                thumbPosition = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}