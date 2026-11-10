package com.tencent.uikit.app.room

import com.tencent.uikit.app.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import com.trtc.uikit.roomkit.base.util.applyBottomWindowInsets
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.inroom.views.MeetingActivity

class RoomJoinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val roomIDInput: EditText
    private val microphoneSwitch: SwitchCompat
    private val speakerSwitch: SwitchCompat
    private val cameraSwitch: SwitchCompat

    init {
        LayoutInflater.from(context).inflate(R.layout.app_join_view, this, true)

        roomIDInput = findViewById(R.id.room_id_input)
        microphoneSwitch = findViewById(R.id.microphone_switch)
        speakerSwitch = findViewById(R.id.speaker_switch)
        cameraSwitch = findViewById(R.id.camera_switch)

        findViewById<View>(R.id.toolbar).applyTopWindowInsets()
        findViewById<View>(R.id.nav_back_button).setOnClickListener {
            (context as? Activity)?.finish()
        }
        applyBottomWindowInsets()
        findViewById<View>(R.id.submit_button).setOnClickListener { joinWithCurrentInput() }

    }

    private fun joinWithCurrentInput() {
        val roomID = roomIDInput.text?.toString()?.trim().orEmpty()
        if (roomID.isEmpty()) {
            RoomToast.show(context, context.getString(R.string.app_pre_room_room_id_empty))
            return
        }
        joinMeeting(
            roomID = roomID,
            isMicrophoneOn = microphoneSwitch.isChecked,
            isSpeakerOn = speakerSwitch.isChecked,
            isCameraOn = cameraSwitch.isChecked,
        )
    }

    private fun joinMeeting(roomID: String, isMicrophoneOn: Boolean, isSpeakerOn: Boolean, isCameraOn: Boolean) {
        context.startActivity(
            Intent(context, MeetingActivity::class.java).apply {
                putExtra(MeetingActivity.EXTRA_ROOM_ID, roomID)
                putExtra(MeetingActivity.EXTRA_IS_CREATE, false)
                putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_MICROPHONE, isMicrophoneOn)
                putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_CAMERA, isCameraOn)
                putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_SPEAKER, isSpeakerOn)
            },
        )
        (context as? Activity)?.finish()
    }
}
