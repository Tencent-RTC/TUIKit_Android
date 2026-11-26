package com.trtc.uikit.roomkit.inroom.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R

class MeetingLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val spinner: ImageView
    private val spinAnimation = RotateAnimation(
        0f,
        360f,
        Animation.RELATIVE_TO_SELF,
        0.5f,
        Animation.RELATIVE_TO_SELF,
        0.5f,
    ).apply {
        duration = 750L
        interpolator = LinearInterpolator()
        repeatCount = Animation.INFINITE
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_entering_view, this)
        spinner = findViewById(R.id.entering_spinner)
        setBackgroundColor(ContextCompat.getColor(context, R.color.roomkit_bg_canvas))
        isClickable = true
        visibility = GONE
        alpha = 0f
    }

    fun show() {
        visibility = VISIBLE
        if (spinner.animation == null) {
            spinner.startAnimation(spinAnimation)
        }
        animate().alpha(1f).setDuration(200L).start()
    }

    fun hide() {
        animate().alpha(0f).setDuration(200L).withEndAction {
            visibility = GONE
            spinner.clearAnimation()
        }.start()
    }

    override fun onDetachedFromWindow() {
        spinner.clearAnimation()
        super.onDetachedFromWindow()
    }
}
