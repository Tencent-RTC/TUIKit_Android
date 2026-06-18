package com.tencent.rtcube.v2.login.components.service

import android.util.Log
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMSDKListener
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.rtcube.v2.login.LoginEntry

object TUILoginListenerHandler : V2TIMSDKListener() {

    private const val TAG = "TUILoginListenerHandler"

    fun register() {
        V2TIMManager.getInstance().addIMSDKListener(this)
    }

    override fun onKickedOffline() {
        LoginEntry.onKickedOffline()
    }

    override fun onUserSigExpired() {
        LoginEntry.onUserTokenExpired()
    }

    override fun onSelfInfoUpdated(info: V2TIMUserFullInfo) {
        Log.i(TAG, "onSelfInfoUpdated: nickName=${info.nickName} faceUrl=${info.faceUrl}")
        LoginManager.onIMSelfInfoUpdated(info)
    }
}
