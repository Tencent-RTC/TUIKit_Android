package io.trtc.tuikit.chat.demo

import io.trtc.tuikit.chat.demo.common.AppConstants

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.chat.demo.customerservice.CustomerServiceManager
import io.trtc.tuikit.chat.demo.login.LocalLoginActivity
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryRegistry

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


        CustomLinkMessageManager.registerMessageSummary()
        CustomerServiceManager.registerSummary()
        LoginStore.shared.addLoginListener(loginListener)
    }

    private fun redirectToLogin(messageResId: Int) {
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, "")
        Toast.makeText(this, getString(messageResId), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LocalLoginActivity::class.java).apply {
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

}
