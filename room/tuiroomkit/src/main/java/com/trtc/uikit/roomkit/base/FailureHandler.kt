package com.trtc.uikit.roomkit.base

import android.content.Context
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import io.trtc.tuikit.atomicxcore.api.CompletionHandler

class FailureHandler(context: Context) : CompletionHandler {

    private val appContext = context.applicationContext

    override fun onSuccess() = Unit

    override fun onFailure(code: Int, desc: String) {
        ErrorLocalized.showError(appContext, code, desc)
    }
}
