package io.trtc.tuikit.chat.demo

import io.trtc.tuikit.chat.demo.common.AppConstants

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.gson.Gson
import com.tencent.imsdk.v2.V2TIMConversation
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tim.push.TIMPushListener
import com.tencent.qcloud.tim.push.TIMPushManager
import com.tencent.qcloud.tim.push.TIMPushMessage
import com.tencent.qcloud.tuicore.push.OfflinePushExtInfo
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.customMessages.CustomLinkMessageManager
import io.trtc.tuikit.chat.demo.customerservice.CustomerServiceManager
import io.trtc.tuikit.chat.demo.login.LoginActivity
import io.trtc.tuikit.chat.demo.main.MainActivity

private const val TAG = "Application"

data class PendingOfflinePush(
    val conversationID: String,
    val targetUserID: String
)

var pendingOfflinePush: PendingOfflinePush? = null

class Application : Application() {

    private val loginListener = object : LoginListener() {
        override fun onKickedOffline() {
            redirectToLogin(R.string.demo_force_offline)
        }

        override fun onLoginExpired() {
            redirectToLogin(R.string.demo_login_expired)
        }
    }

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)

        applyLanguageFromSettings()

        MMKV.defaultMMKV().decodeBool(AppConstants.KEY_ENABLE_READ_RECEIPT, false).also {
            AppBuilderConfig.enableReadReceipt = it
        }
        if (!MMKV.defaultMMKV().decodeBool(AppConstants.KEY_CALLS_TAB_DEFAULT_OFF_MIGRATED, false)) {
            MMKV.defaultMMKV().encode(AppConstants.KEY_SHOW_CALLS_TAB, false)
            MMKV.defaultMMKV().encode(AppConstants.KEY_CALLS_TAB_DEFAULT_OFF_MIGRATED, true)
        }

        initOfflinePush()
        CustomLinkMessageManager.registerMessageSummary()
        CustomerServiceManager.registerSummary()
        LoginStore.shared.addLoginListener(loginListener)
    }

    private fun redirectToLogin(messageResId: Int) {
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER_SIG, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, "")
        Toast.makeText(this, getString(messageResId), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
    }

    private fun applyLanguageFromSettings() {
        val languageTag = MMKV.defaultMMKV().decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
        val targetLocales = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        if (AppCompatDelegate.getApplicationLocales() != targetLocales) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }

    private fun initOfflinePush() {
        TIMPushManager.getInstance().addPushListener(object : TIMPushListener() {
            override fun onRecvPushMessage(message: TIMPushMessage?) {
                Log.d(TAG, "onRecvPushMessage: $message")
            }

            override fun onNotificationClicked(ext: String?) {
                Log.d(TAG, "onNotificationClicked: $ext")
                handleOfflinePush(ext)
            }
        })
    }

    private fun handleOfflinePush(ext: String?) {
        if (ext.isNullOrEmpty()) {
            return
        }

        val target = parseOfflinePushTarget(ext) ?: return

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancelAll()

        when (target) {
            OfflinePushTarget.Call -> {
                pendingOfflinePush = null
                val intent = if (LoginStore.shared.loginState.loginStatus.value != LoginStatus.UNLOGIN) {
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }
                } else {
                    Intent(this, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                startActivity(intent)
            }
            is OfflinePushTarget.Chat -> {
                if (LoginStore.shared.loginState.loginStatus.value != LoginStatus.UNLOGIN) {
                    startActivity(Intent(this, ChatActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("conversationID", target.conversationID)
                    })
                    return
                }

                val targetUserID = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_USER, "").orEmpty()
                pendingOfflinePush = PendingOfflinePush(target.conversationID, targetUserID)
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
}

private sealed interface OfflinePushTarget {
    data class Chat(val conversationID: String) : OfflinePushTarget
    object Call : OfflinePushTarget
}

private fun parseOfflinePushTarget(ext: String): OfflinePushTarget? {
    val offlinePushExtInfo = try {
        Gson().fromJson(ext, OfflinePushExtInfo::class.java)
    } catch (exception: Exception) {
        Log.w(TAG, "parseOfflinePushTarget error: ${exception.message}")
        null
    }

    val businessInfo = offlinePushExtInfo?.businessInfo ?: return null
    if (businessInfo.version != 1) {
        return null
    }

    return when (businessInfo.chatAction) {
        OfflinePushExtInfo.REDIRECT_ACTION_CALL -> OfflinePushTarget.Call
        OfflinePushExtInfo.REDIRECT_ACTION_CHAT -> {
            val senderId = businessInfo.senderId
            if (TextUtils.isEmpty(senderId)) {
                return null
            }

            val conversationID = when (businessInfo.chatType) {
                V2TIMConversation.V2TIM_C2C -> "c2c_$senderId"
                V2TIMConversation.V2TIM_GROUP -> "group_$senderId"
                else -> return null
            }
            OfflinePushTarget.Chat(conversationID)
        }
        else -> null
    }
}
