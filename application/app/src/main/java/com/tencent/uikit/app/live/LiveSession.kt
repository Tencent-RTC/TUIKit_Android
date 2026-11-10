package com.tencent.uikit.app.live

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tencent.uikit.app.login.LoginActivity
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

internal fun Activity.requireLoggedIn(): Boolean {
    if (isFinishing || isDestroyed) return false
    val state = LoginStore.shared.loginState
    if (state.loginStatus.value != LoginStatus.UNLOGIN &&
        !state.loginUserInfo.value?.userID.isNullOrEmpty()
    ) return true
    startActivity(Intent(this, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
    finish()
    return false
}

internal fun Activity.initLiveWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}
