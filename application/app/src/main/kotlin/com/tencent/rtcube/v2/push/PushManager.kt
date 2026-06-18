package com.tencent.rtcube.v2.push

import android.content.Context
import android.content.Intent
import android.util.Log
import com.tencent.qcloud.tim.push.TIMPushListener
import com.tencent.qcloud.tim.push.TIMPushManager
import com.tencent.qcloud.tuicore.TUIConfig
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.rtcube.v2.login.LoginEntry

/**
 * Offline push manager.
 * Domestic (rtcube): huawei + xiaomi + oppo + vivo + honor + meizu + fcm
 * Overseas (tencentrtc): FCM only
 */
object PushManager {

    private const val TAG = "PushManager"
    fun init(context: Context, sdkAppId: Int, secretKey: String) {
        setupIMSettings(context)
        registerPushClickListener()
        registerIMLoginAfterWakeup()
    }

    private fun setupIMSettings(context: Context) {
        // TIMAppKit adjusts behavior based on host type
        TUIConfig.setTUIHostType(TUIConfig.TUI_HOST_TYPE_RTCUBE)

        TUICore.registerEvent(
            TUIConstants.TIMAppKit.NOTIFY_RTCUBE_EVENT_KEY, TUIConstants.TIMAppKit.NOTIFY_RTCUBE_LOGIN_SUB_KEY
        ) { _, _, _ ->
            Log.d(TAG, "IM login success, navigating to MainActivity")
            val intent = Intent(context, com.tencent.rtcube.v2.MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun registerPushClickListener() {
        TIMPushManager.getInstance().addPushListener(object : TIMPushListener() {
            override fun onNotificationClicked(ext: String?) {
                Log.d(TAG, "onNotificationClicked ext=$ext")
            }
        })
    }

/**
     * Register IM login event after FCM wakeup (required by overseas edition).
     * When the App is woken by an FCM push, complete IM login before handling the message.
     */
    private fun registerIMLoginAfterWakeup() {
        TUICore.registerEvent(
            TUIConstants.TIMPush.EVENT_IM_LOGIN_AFTER_APP_WAKEUP_KEY, TUIConstants.TIMPush.EVENT_IM_LOGIN_AFTER_APP_WAKEUP_SUB_KEY
        ) { key, subKey, _ ->
            if (TUIConstants.TIMPush.EVENT_IM_LOGIN_AFTER_APP_WAKEUP_KEY == key
                && TUIConstants.TIMPush.EVENT_IM_LOGIN_AFTER_APP_WAKEUP_SUB_KEY == subKey
            ) {
                Log.d(TAG, "App wakeup by push, performing token login")
                loginByToken()
            }
        }
    }

    private fun loginByToken() {
        LoginEntry.performTokenAuth { result ->
            result.onSuccess {
                Log.d(TAG, "loginByToken success after push wakeup")
            }.onFailure { error ->
                Log.e(TAG, "loginByToken failed after push wakeup: ${error.message}")
            }
        }
    }

}
