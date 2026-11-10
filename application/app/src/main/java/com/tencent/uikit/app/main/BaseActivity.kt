package com.tencent.uikit.app.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tencent.uikit.app.login.LoginActivity
import com.tencent.uikit.app.setting.LanguageSelectActivity
import com.tencent.uikit.app.setting.LocaleManager
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.attachLanguageContext(newBase))
    }

    private val languageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LanguageSelectActivity.LANGUAGE_CHANGED_ACTION) {
                recreate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkUserLoginStatus(savedInstanceState)
        initStatusBar()
        hideSoftKeyboard()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            languageChangedReceiver,
            IntentFilter(LanguageSelectActivity.LANGUAGE_CHANGED_ACTION)
        )
    }

    private fun checkUserLoginStatus(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && LoginStore.shared.loginState.loginStatus.value == LoginStatus.UNLOGIN) {
            val loginIntent = Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(loginIntent)
            finish()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            currentFocus?.windowToken?.let {
                imm.hideSoftInputFromWindow(it, 0)
            }
        } catch (_: Exception) {
        }
        return super.onTouchEvent(event)
    }

    private fun initStatusBar() {
        val window = window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
    }

    protected fun hideSoftKeyboard() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
    }

    override fun onDestroy() {
        runCatching {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(languageChangedReceiver)
        }
        super.onDestroy()
    }
}