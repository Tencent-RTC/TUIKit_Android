package com.trtc.uikit.roomkit.base.theme

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp

class RoomLoadingDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val animators = mutableListOf<ObjectAnimator>()

    init {
        orientation = HORIZONTAL
        repeat(3) { index ->
            val dot = View(context).apply {
                background = context.getDrawable(R.drawable.roomkit_bg_loading_dot)
            }
            addView(dot, LayoutParams(context.dp(3), context.dp(3)).apply {
                if (index > 0) marginStart = context.dp(3)
            })
        }
    }

    fun setDotColor(@ColorInt color: Int) {
        for (index in 0 until childCount) {
            getChildAt(index).backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) startAnimations() else stopAnimations()
    }

    private fun startAnimations() {
        if (animators.isNotEmpty()) return
        for (index in 0 until childCount) {
            val animator = ObjectAnimator.ofFloat(getChildAt(index), View.ALPHA, 0.28f, 1f).apply {
                duration = 700L
                startDelay = index * 180L
                interpolator = LinearInterpolator()
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            }
            animators.add(animator)
            animator.start()
        }
    }

    private fun stopAnimations() {
        animators.forEach { it.cancel() }
        animators.clear()
    }
}
