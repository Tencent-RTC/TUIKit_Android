package com.trtc.uikit.roomkit.base.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applyTopWindowInsets() {
    val base = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        view.setPadding(base[0], base[1] + top, base[2], base[3])
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.applyBottomWindowInsets() {
    val base = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        view.setPadding(base[0], base[1], base[2], base[3] + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
