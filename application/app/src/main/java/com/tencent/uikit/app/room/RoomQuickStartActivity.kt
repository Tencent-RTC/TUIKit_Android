package com.tencent.uikit.app.room

import android.os.Bundle

import com.trtc.uikit.roomkit.base.activity.BaseActivity

class RoomQuickStartActivity : BaseActivity() {

    private lateinit var quickStartView: RoomQuickStartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quickStartView = RoomQuickStartView(this)
        setContentView(quickStartView)
    }
}
