package io.trtc.tuikit.chat.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tim.push.TIMPushCallback
import com.tencent.qcloud.tim.push.TIMPushManager
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.MainActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.common.AppConstants
import io.trtc.tuikit.chat.common.CustomerServiceInitializer
import io.trtc.tuikit.chat.common.WelcomeMessageSender
import org.json.JSONObject

private const val TAG = "LoginPerformer"

internal fun performLogin(
    activity: Activity,
    sdkAppId: Int,
    userId: String,
    userSig: String,
    loginType: String,
    token: String? = null,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((Int, String) -> Unit)? = null
) {
    LoginStore.shared.login(
        activity, sdkAppId, userId, userSig,
        object : CompletionHandler {
            override fun onSuccess() {
                initCall(context = activity)
                registerPush(activity.applicationContext)
                CustomerServiceInitializer.init(activity, sdkAppId, userId, userSig)
                WelcomeMessageSender.scheduleWelcomeMessage(activity)
                MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, userId)
                MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, loginType)
                if (token != null) {
                    MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, token)
                }
                onSuccess?.invoke()
                activity.startActivity(Intent(activity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            }

            override fun onFailure(code: Int, desc: String) {
                if (onFailure != null) {
                    onFailure(code, desc)
                } else {
                    Toast.error(activity, activity.getString(R.string.compose_demo_login_failed, desc))
                }
            }
        }
    )
}

private fun initCall(context : Context) {
        val innerJson = JSONObject()
            .put("api", "enableMultiDeviceAbility")
            .put("params", JSONObject().put("enabled", true))
            .toString()
        val payload = JSONObject()
            .put("api", "CallModule.callExperimentalAPI")
            .put("params", JSONObject().put("jsonStr", innerJson))
            .toString()
        TUICallKit.createInstance(context).callExperimentalAPI(payload)
}

private fun registerPush(context: Context) {
    TIMPushManager.getInstance().registerPush(
        context, 0, "",
        object : TIMPushCallback<Any>() {
            override fun onSuccess(data: Any?) {
                Log.i(TAG, "Push Register Success")
            }

            override fun onError(errCode: Int, errMsg: String?, data: Any?) {
                Log.e(TAG, "Push Register Error $errCode $errMsg")
            }
        }
    )
}
