package com.tencent.uikit.app.call.common

import android.util.Log

internal object CallAccessLogger {

    private const val TAG_PREFIX = "AtomicUIKit-"

    fun e(tag: String, message: String) {
        Log.e(TAG_PREFIX + tag, message)
    }
}
