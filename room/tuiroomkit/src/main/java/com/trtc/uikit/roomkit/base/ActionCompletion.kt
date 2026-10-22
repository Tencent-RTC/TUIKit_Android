package com.trtc.uikit.roomkit.base

import android.content.Context
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.toast.RoomToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler

class ActionCompletion(
    context: Context,
    private val successMessage: String? = null,
) : CompletionHandler {

    private val appContext = context.applicationContext
    private val logger = RoomKitLogger.getCommonLogger("ActionCompletion")

    override fun onSuccess() {
        successMessage?.let { RoomToast.show(appContext, it) }
    }

    override fun onFailure(code: Int, desc: String) {
        logger.error("onFailure code=$code, desc=$desc")
        ErrorLocalized.showError(appContext, code, desc)
    }
}
