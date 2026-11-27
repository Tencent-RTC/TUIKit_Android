package io.trtc.tuikit.chat.demo.login

import android.content.Context
import android.content.Intent

object LocalLoginEntry {
    const val available = true

    fun start(context: Context) {
        context.startActivity(Intent(context, LocalLoginActivity::class.java))
    }
}
