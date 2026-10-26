package com.trtc.uikit.roomkit.base.utils

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowManager

/**
 * Display utility functions for dimension conversion and screen metrics.
 * Provides extension functions for Context and View to convert between dp and px.
 */

/**
 * Get screen height in pixels.
 */
fun getScreenHeight(context: Context): Int {
    val displayMetrics = DisplayMetrics()
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager.defaultDisplay.getMetrics(displayMetrics)
    return displayMetrics.heightPixels
}

/**
 * Get screen width in pixels.
 */
fun getScreenWidth(context: Context): Int {
    val displayMetrics = DisplayMetrics()
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager.defaultDisplay.getMetrics(displayMetrics)
    return displayMetrics.widthPixels
}

/**
 * Extension function: Convert dp to px.
 */
fun Context.dpToPx(dp: Int): Int {
    val displayMetrics: DisplayMetrics = resources.displayMetrics
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), displayMetrics).toInt()
}

/**
 * Extension function: Convert dp to px (Float parameter).
 */
fun Context.dpToPx(dp: Float): Int {
    val displayMetrics: DisplayMetrics = resources.displayMetrics
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics).toInt()
}

/**
 * Extension function: Convert px to dp.
 */
fun Context.pxToDp(px: Int): Int {
    return (px / resources.displayMetrics.density).toInt()
}

/**
 * Extension function: Convert px to dp (Float return).
 */
fun Context.pxToDpFloat(px: Int): Float {
    return px / resources.displayMetrics.density
}

/**
 * View extension: Convert dp to px using view's context.
 */
fun View.dpToPx(dp: Int): Int = context.dpToPx(dp)

/**
 * View extension: Convert dp to px (Float parameter).
 */
fun View.dpToPx(dp: Float): Int = context.dpToPx(dp)

/**
 * View extension: Convert px to dp using view's context.
 */
fun View.pxToDp(px: Int): Int = context.pxToDp(px)