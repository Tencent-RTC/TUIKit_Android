package io.trtc.tuikit.chat.login

import android.webkit.JavascriptInterface

class ImageVerificationJsBridge(private val callback: Callback) {

    interface Callback {
        fun onVerifySuccess(ticket: String, randStr: String)
        fun onVerifyError(errorCode: Int, errorMsg: String?)
    }

    @JavascriptInterface
    fun verifySuccess(ticket: String, randStr: String) {
        callback.onVerifySuccess(ticket, randStr)
    }

    @JavascriptInterface
    fun verifyError(errorCode: Int, errorMsg: String?) {
        callback.onVerifyError(errorCode, errorMsg)
    }
}
