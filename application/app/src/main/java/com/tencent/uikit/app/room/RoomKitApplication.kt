package com.tencent.uikit.app.room

import com.tencent.uikit.app.R
import com.tencent.uikit.app.login.LoginActivity
import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.trtc.uikit.roomkit.base.toast.RoomToast
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class RoomKitApplication : Application() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        LoginStore.shared.addLoginListener(object : LoginListener() {
            override fun onLoginExpired() {
                relaunchLogin(R.string.app_login_expired)
            }

            override fun onKickedOffline() {
                relaunchLogin(R.string.app_login_kicked_offline)
            }
        })
    }

    private fun relaunchLogin(messageRes: Int) {
        mainHandler.post {
            RoomToast.show(this, getString(messageRes))
            startActivity(
                Intent(this, LoginActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            )
        }
    }
}
