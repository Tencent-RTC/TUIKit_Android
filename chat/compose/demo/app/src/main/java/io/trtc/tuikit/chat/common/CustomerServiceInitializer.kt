package io.trtc.tuikit.chat.common

import android.content.Context
import io.trtc.tuikit.chat.customerservice.CustomerServiceManager

object CustomerServiceInitializer {

    fun init(context: Context, sdkAppId: Int, userId: String, userSig: String?) {
        CustomerServiceManager.initAndStart(context, sdkAppId, userId, userSig.orEmpty())
    }
}
