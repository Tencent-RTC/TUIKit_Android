package com.tencent.uikit.app.room

import android.os.Bundle

import com.trtc.uikit.roomkit.base.activity.BaseActivity

class RoomJoinActivity : BaseActivity() {

    private lateinit var joinView: RoomJoinView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        joinView = RoomJoinView(this)
        setContentView(joinView)
    }
}
