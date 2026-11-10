package com.tencent.uikit.app.room

import android.os.Bundle
import com.trtc.uikit.roomkit.base.activity.BaseActivity
import com.trtc.uikit.roomkit.inroom.features.setting.RoomSettingsDialog

class RoomHomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val homeView = RoomHomeView(this)
        homeView.onOpenSettings = {
            RoomSettingsDialog(this).show()
        }
        setContentView(homeView)
    }
}
