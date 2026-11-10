package com.tencent.uikit.app.live

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.tencent.uikit.app.login.LoginActivity
import com.tencent.uikit.app.main.BaseActivity
import com.trtc.uikit.livekit.pusher.PusherView
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class PusherActivity : BaseActivity() {

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
        initLiveWindow()

        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        pushView = PusherView(this)
        LoginStore.shared.addLoginListener(loginListener)
        pushView.onExit = ::exitPusher
        pushView.bind("live_$userID")
        setContentView(pushView)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pushView.handleBackPressed()) return
                exitPusher()
            }
        })
    }

    override fun onDestroy() {
        if (::pushView.isInitialized) {
            LoginStore.shared.removeLoginListener(loginListener)
            pushView.onExit = null
        }
        super.onDestroy()
    }

    private fun exitPusher() {
        if (isFinishing || isDestroyed) return
        if (accountInvalidated) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        finish()
    }
}
