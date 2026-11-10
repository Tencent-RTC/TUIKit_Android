package com.trtc.uikit.roomkit.base.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

const val MIN_BOTTOM_CLEARANCE_DP = 34

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
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val minClearance = (MIN_BOTTOM_CLEARANCE_DP * view.resources.displayMetrics.density).toInt()
        view.setPadding(base[0], base[1], base[2], base[3] + max(bars.bottom, minClearance))
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
