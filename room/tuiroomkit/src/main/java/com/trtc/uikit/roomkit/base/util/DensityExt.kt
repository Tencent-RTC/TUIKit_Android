package com.trtc.uikit.roomkit.base.util

import android.content.Context
import android.util.TypedValue

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.dpF(value: Float): Float = value * resources.displayMetrics.density

internal fun Context.sp(value: Float): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    value,
    resources.displayMetrics,
)
