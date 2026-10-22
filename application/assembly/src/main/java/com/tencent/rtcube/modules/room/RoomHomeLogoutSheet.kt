package com.tencent.rtcube.modules.room

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup

class RoomHomeLogoutSheet(context: Context) : FrameLayout(context) {

    var onSettings: (() -> Unit)? = null
    var onLogout: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var popup: RoomPopup? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_home_logout_sheet, this, true)
        findViewById<View>(R.id.profile_setting_button).setOnClickListener {
            dismiss()
            onSettings?.invoke()
        }
        findViewById<View>(R.id.profile_logout_button).setOnClickListener {
            dismiss()
            onLogout?.invoke()
        }
    }

    fun show() {
        if (popup != null) return
        val popup = RoomPopup(context)
        popup.setContentView(this, RoomPopup.ContentHeight.AUTO)
        popup.onDismiss = { onDismiss?.invoke() }
        this.popup = popup
        popup.show()
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}
