package io.trtc.tuikit.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.login.LoginActivity

abstract class BaseActivity : AppCompatActivity() {

    protected open val requiresLogin: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        redirectToLoginIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        redirectToLoginIfNeeded()
    }

    private fun redirectToLoginIfNeeded(): Boolean {
        if (!requiresLogin || isFinishing) {
            return false
        }
        if (LoginStore.shared.loginState.loginStatus.value != LoginStatus.UNLOGIN) {
            return false
        }
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
        return true
    }
}
