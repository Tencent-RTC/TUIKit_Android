package com.example.atomicxcore.utils

import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.StopLiveCompletionHandler

/**
 * CompletionHandler convenience builder utility
 * SDK's CompletionHandler is not a SAM interface (has both onSuccess + onFailure methods),
 * so Kotlin lambda conversion cannot be used directly. These utility methods simplify the calls.
 */

/**
 * Create a CompletionHandler, callback (code: Int, message: String)
 * code == 0 indicates success
 */
fun completionHandler(callback: (code: Int, message: String) -> Unit): CompletionHandler {
    return object : CompletionHandler {
        override fun onSuccess() {
            callback(0, "success")
        }
        override fun onFailure(code: Int, message: String) {
            callback(code, message)
        }
    }
}

/**
 * Create a LiveInfoCompletionHandler, callback (liveInfo: LiveInfo?, code: Int, message: String)
 */
fun liveInfoCompletionHandler(callback: (liveInfo: LiveInfo?) -> Unit): LiveInfoCompletionHandler {
    return object : LiveInfoCompletionHandler {
        override fun onSuccess(liveInfo: LiveInfo) {
            callback(liveInfo)
        }
        override fun onFailure(code: Int, message: String) {
            callback(null)
        }
    }
}

/**
 * Create a StopLiveCompletionHandler
 */
fun stopLiveCompletionHandler(callback: (code: Int, message: String) -> Unit): StopLiveCompletionHandler {
    return object : StopLiveCompletionHandler {
        override fun onSuccess(data: com.tencent.cloud.tuikit.engine.extension.TUILiveListManager.LiveStatisticsData) {
            callback(0, "success")
        }
        override fun onFailure(code: Int, message: String) {
            callback(code, message)
        }
    }
}
