package com.livekit.demo

import android.app.Activity
import android.content.Intent
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

/** Restored activities must not use a saved user ID as proof of an SDK login. */
internal fun Activity.requireLoggedIn(): Boolean {
    if (isFinishing || isDestroyed) return false
    if (!LoginStore.shared.loginState.loginUserInfo.value?.userID.isNullOrEmpty()) return true
    startActivity(Intent(this, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
    finish()
    return false
}
