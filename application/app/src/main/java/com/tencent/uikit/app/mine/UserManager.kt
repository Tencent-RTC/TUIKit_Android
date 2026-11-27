package com.tencent.uikit.app.mine

import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile

object UserManager {
    @JvmStatic
    fun getInstance(): UserManager = this
    fun updateSelfUserInfo(info: V2TIMUserFullInfo?, callback: V2TIMCallback?) {
        val profile = LoginStore.shared.loginState.loginUserInfo.value
        if (profile == null || info == null) {
            callback?.onError(-1, "not logged in"); return
        }
        LoginStore.shared.setSelfInfo(
            UserProfile(
                userID = profile.userID, nickname = info.getNickName(), avatarURL = info.faceUrl
            ), object : CompletionHandler {
                override fun onSuccess() {
                    callback?.onSuccess()
                }

                override fun onFailure(code: Int, desc: String) {
                    callback?.onError(code, desc)
                }
            })
    }
}
