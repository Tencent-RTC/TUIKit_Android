package com.trtc.uikit.roomkit.inroom.views.meeting

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.applyBottomWindowInsets
import com.trtc.uikit.roomkit.inroom.common.moremenu.RoomMoreMenuSheet
import com.trtc.uikit.roomkit.inroom.common.toolbarbutton.RoomToolbarButton
import com.trtc.uikit.roomkit.inroom.features.device.camerabutton.CameraButton
import com.trtc.uikit.roomkit.inroom.features.device.micbutton.MicButton
import com.trtc.uikit.roomkit.inroom.features.participants.participantbutton.ParticipantButton
import com.trtc.uikit.roomkit.inroom.features.screenshare.RoomScreenShareController
import com.trtc.uikit.roomkit.inroom.features.setting.RoomSettingsActivity

class MeetingToolBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var onScreenShare: (() -> Unit)? = null

    private val micButton: MicButton
    private val camButton: CameraButton
    private val participantButton: ParticipantButton

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_meeting_tool_bar, this, true)
        micButton = findViewById(R.id.microphone_button)
        camButton = findViewById(R.id.camera_button)
        participantButton = findViewById(R.id.members_button)
        applyBottomWindowInsets()
    }

    fun bind(roomID: String) {
        micButton.bind(roomID)
        camButton.bind(roomID)
        participantButton.bind(roomID)
        findViewById<RoomToolbarButton>(R.id.chat_button).apply {
            icon = context.getDrawable(R.drawable.roomkit_icon_chat)
            setOnClickListener { showFeatureComing() }
        }
        findViewById<RoomToolbarButton>(R.id.more_button).apply {
            icon = context.getDrawable(R.drawable.roomkit_icon_more)
            setOnClickListener { showMoreMenu() }
        }
    }

    private fun showMoreMenu() {
        RoomMoreMenuSheet(context).apply {
            setScreenSharing(RoomScreenShareController.isSharing.value)
            onScreenShare = { this@MeetingToolBar.onScreenShare?.invoke() }
            onSettings = {
                context.startActivity(Intent(context, RoomSettingsActivity::class.java))
            }
            onComingSoon = { showFeatureComing() }
            show()
        }
    }

    private fun showFeatureComing() {
        RoomToast.show(context, context.getString(R.string.roomkit_main_feature_coming))
    }
}
