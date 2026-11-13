package com.tencent.uikit.app.call.common

import android.util.Log

internal object CallAccessLogger {

    private const val TAG_PREFIX = "AtomicUIKit-"

    fun d(tag: String, message: String) {
        Log.d(TAG_PREFIX + tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(TAG_PREFIX + tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(TAG_PREFIX + tag, message)
    }

    fun e(tag: String, message: String) {
        Log.e(TAG_PREFIX + tag, message)
    }
}
