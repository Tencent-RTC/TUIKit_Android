package com.tencent.uikit.app.call.common

import android.content.Context
import android.util.TypedValue

internal fun Int.dp(context: Context): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics).toInt()
}
