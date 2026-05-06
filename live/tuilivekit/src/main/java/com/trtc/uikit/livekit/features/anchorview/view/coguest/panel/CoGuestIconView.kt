package com.trtc.uikit.livekit.features.anchorview.view.coguest.panel

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.trtc.uikit.livekit.R

class CoGuestIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val ANIMATION_INTERVAL_MS = 500L
        private val ANIMATION_ICON_RES_ID_ARRAY = intArrayOf(
            R.drawable.livekit_function_link_1,
            R.drawable.livekit_function_link_2,
            R.drawable.livekit_function_link_3,
        )

        private val DEFAULT_ICON_RES_ID = R.drawable.livekit_function_link_default
    }

    private var currentAnimationResIndex = -1

    private val startAnimationTask = object : Runnable {
        override fun run() {
            val index = currentAnimationResIndex
            val count = ANIMATION_ICON_RES_ID_ARRAY.size
            if (index >= 0 && index < count) {
                setBackgroundResource(ANIMATION_ICON_RES_ID_ARRAY[index])
                postDelayed(this, ANIMATION_INTERVAL_MS)
                currentAnimationResIndex = (index + 1) % count
            }
        }
    }

    init {
        setBackgroundResource(DEFAULT_ICON_RES_ID)
    }

    fun startAnimation() {
        if (currentAnimationResIndex == -1) {
            currentAnimationResIndex = 0
            post(startAnimationTask)
        }
    }

    fun stopAnimation() {
        currentAnimationResIndex = -1
        setBackgroundResource(DEFAULT_ICON_RES_ID)
        removeCallbacks(startAnimationTask)
    }
}