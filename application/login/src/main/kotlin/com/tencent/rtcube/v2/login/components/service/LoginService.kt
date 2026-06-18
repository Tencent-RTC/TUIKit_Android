package com.tencent.rtcube.v2.login.components.service

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUIService
import com.tencent.qcloud.tuicore.interfaces.TUIServiceCallback

/**
 * TUICore service implementation for the login module.
 *
 * Registered as "LoginService" via [TUICore.registerService],
 * allowing other modules to invoke login capabilities via [TUICore.callService].
 */
class LoginService : ITUIService {

    companion object {
        private const val TAG = "LoginService"

        const val SERVICE_NAME = "LoginService"

        const val METHOD_GET_USER_MODEL = "methodGetUserModel"
        const val METHOD_GET_LOGIN_STATUS = "methodGetLoginStatus"

        const val PARAM_KEY_USER_MODEL = "paramUserModel"

        const val CALL_BACK_CODE_SUCCESS = 0

        fun register() {
            TUICore.registerService(SERVICE_NAME, LoginService())
        }
    }

    override fun onCall(
        method: String,
        param: Map<String, Any>?,
        callback: TUIServiceCallback?,
    ): Any? {
        when {
            TextUtils.equals(METHOD_GET_LOGIN_STATUS, method) -> {
                val isLogin = LoginManager.currentUser != null
                callback?.onServiceCallback(CALL_BACK_CODE_SUCCESS, isLogin.toString(), null)
            }

            TextUtils.equals(METHOD_GET_USER_MODEL, method) -> {
                val userModel = LoginManager.currentUser
                val bundle = Bundle()
                if (userModel != null) {
                    bundle.putSerializable(PARAM_KEY_USER_MODEL, userModel)
                }
                callback?.onServiceCallback(CALL_BACK_CODE_SUCCESS, null, bundle)
            }

            else -> {
                Log.w(TAG, "onCall: unknown method=$method")
            }
        }
        return null
    }
}
