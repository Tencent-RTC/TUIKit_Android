package com.tencent.rtcube.modules.room

import android.content.Intent
import android.os.Bundle
import com.tencent.rtcube.v2.login.LoginActivity
import com.trtc.uikit.roomkit.base.activity.BaseActivity
import com.trtc.uikit.roomkit.inroom.features.setting.RoomSettingsActivity

class RoomHomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边（WindowCompat.setDecorFitsSystemWindows）已在 BaseActivity.onCreate 里做过了。
        val homeView = RoomHomeView(this)
        homeView.onLoggedOut = { handleLoggedOut() }
        homeView.onOpenSettings = {
            startActivity(Intent(this, RoomSettingsActivity::class.java))
        }
        setContentView(homeView)
    }

    private fun handleLoggedOut() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}
