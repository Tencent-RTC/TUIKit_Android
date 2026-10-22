package com.tencent.rtcube.modules.room

import android.content.pm.PackageManager
import android.os.Bundle

import com.trtc.uikit.roomkit.base.activity.BaseActivity
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils

class RoomQuickStartActivity : BaseActivity() {

    private lateinit var quickStartView: RoomQuickStartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quickStartView = RoomQuickStartView(this)
        setContentView(quickStartView)
    }

    override fun onResume() {
        super.onResume()
        quickStartView.updatePermissionHints()
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
