package com.livekit.demo

import android.content.Intent
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.trtc.uikit.livekit.pusher.PusherView

class PusherActivity : AppCompatActivity() {

    private lateinit var pushView: PusherView
    private var accountInvalidated = false
    private val loginListener = object : LoginListener() {
        override fun onKickedOffline() {
            runOnUiThread { accountInvalidated = true }
        }
        override fun onLoginExpired() {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                accountInvalidated = true
                exitPusher()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoggedIn()) return
        initFullScreen()

        LoginStore.shared.addLoginListener(loginListener)
        pushView = PusherView(this)
        pushView.onExit = ::exitPusher
        pushView.bind("live_${APPConfig.LOGIN_USER_ID}")
        setContentView(pushView)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pushView.handleBackPressed()) return
                exitPusher()
            }
        })
    }

    override fun onDestroy() {
        if (::pushView.isInitialized) LoginStore.shared.removeLoginListener(loginListener)
        super.onDestroy()
    }

    private fun exitPusher() {
        if (accountInvalidated) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        finish()
    }

    private fun initFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
