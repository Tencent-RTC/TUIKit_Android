package com.trtc.uikit.livekit.common.basic

import android.view.View

fun View.setDebounceClickListener(
    interval: Long = 1000L,
    action: (View) -> Unit
) {
    var lastClickTime = 0L
    setOnClickListener { view ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= interval) {
            lastClickTime = currentTime
            action(view)
        }
    }
}

fun View.setDebounceClickListenerWithFeedback(
    interval: Long = 1000L,
    action: (View) -> Unit
) {
    var isClickEnabled = true
    setOnClickListener { view ->
        if (isClickEnabled) {
            isClickEnabled = false
            action(view)

            postDelayed({
                isClickEnabled = true
            }, interval)
        }
    }
}
