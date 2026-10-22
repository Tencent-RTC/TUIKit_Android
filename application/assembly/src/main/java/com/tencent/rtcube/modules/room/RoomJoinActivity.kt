package com.tencent.rtcube.modules.room

import android.content.pm.PackageManager
import android.os.Bundle

import com.trtc.uikit.roomkit.base.activity.BaseActivity
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils

class RoomJoinActivity : BaseActivity() {

    private lateinit var joinView: RoomJoinView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        joinView = RoomJoinView(this)
        setContentView(joinView)
    }

    override fun onResume() {
        super.onResume()
        joinView.updatePermissionHints()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        RoomLocalMediaUtils.handlePermissionResult(requestCode, granted)
    }
}
