package com.trtc.uikit.roomkit.inroom.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import com.trtc.uikit.roomkit.inroom.features.recording.cloudrecordingstatus.CloudRecordingStatus
import com.trtc.uikit.roomkit.inroom.features.roominfo.currentroominfo.RoomInfoView
import com.trtc.uikit.roomkit.inroom.features.roominfo.leaveroombutton.LeaveRoomButton

class MeetingTopBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_top_bar, this, true)
        applyTopWindowInsets()
    }

    fun init(roomID: String) {
        findViewById<RoomInfoView>(R.id.current_room_info).init(roomID)
        findViewById<CloudRecordingStatus>(R.id.cloud_recording_status).init(roomID)
        findViewById<LeaveRoomButton>(R.id.leave_room_button).init(roomID)
    }
}
