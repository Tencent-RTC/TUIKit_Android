package com.tencent.rtcube.modules.room

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.inroom.features.setting.RoomNavigationBar
import com.trtc.uikit.roomkit.base.util.RoomDevice
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils
import com.trtc.uikit.roomkit.base.util.applyBottomWindowInsets
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.inroom.views.meeting.MeetingActivity

class RoomJoinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val roomIDInput: EditText
    private val microphoneSwitch: SwitchCompat
    private val speakerSwitch: SwitchCompat
    private val cameraSwitch: SwitchCompat
    private val micPermissionHint: View
    private val cameraPermissionHint: View

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_join_view, this, true)

        roomIDInput = findViewById(R.id.room_id_input)
        microphoneSwitch = findViewById(R.id.microphone_switch)
        speakerSwitch = findViewById(R.id.speaker_switch)
        cameraSwitch = findViewById(R.id.camera_switch)
        micPermissionHint = findViewById(R.id.mic_permission_hint)
        cameraPermissionHint = findViewById(R.id.camera_permission_hint)

        findViewById<RoomNavigationBar>(R.id.toolbar).setOnBackClickListener {
            (context as? Activity)?.finish()
        }
        applyBottomWindowInsets()
        findViewById<View>(R.id.submit_button).setOnClickListener { joinWithCurrentInput() }
        micPermissionHint.findViewById<View>(R.id.mic_permission_go_enable).setOnClickListener { openSystemSettings() }
        cameraPermissionHint.findViewById<View>(R.id.camera_permission_go_enable).setOnClickListener { openSystemSettings() }
        updatePermissionHints()
        microphoneSwitch.isChecked = !RoomLocalMediaUtils.isPermissionDenied(context, RoomDevice.MICROPHONE)
        cameraSwitch.isChecked = !RoomLocalMediaUtils.isPermissionDenied(context, RoomDevice.CAMERA)
        microphoneSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) return@setOnCheckedChangeListener
            microphoneSwitch.isChecked = false
            RoomLocalMediaUtils.runWithPermission(context, RoomDevice.MICROPHONE) {
                microphoneSwitch.isChecked = true
                updatePermissionHints()
            }
        }
        cameraSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) return@setOnCheckedChangeListener
            cameraSwitch.isChecked = false
            RoomLocalMediaUtils.runWithPermission(context, RoomDevice.CAMERA) {
                cameraSwitch.isChecked = true
                updatePermissionHints()
            }
        }
    }

    fun updatePermissionHints() {
        micPermissionHint.visibility =
            if (RoomLocalMediaUtils.isPermissionDenied(context, RoomDevice.MICROPHONE)) VISIBLE else GONE
        cameraPermissionHint.visibility =
            if (RoomLocalMediaUtils.isPermissionDenied(context, RoomDevice.CAMERA)) VISIBLE else GONE
    }

    private fun openSystemSettings() {
        RoomLocalMediaUtils.openSystemSettings(context)
    }

    private fun joinWithCurrentInput() {
        val roomID = roomIDInput.text?.toString()?.trim().orEmpty()
        if (roomID.isEmpty()) {
            RoomToast.show(context, context.getString(R.string.roomkit_pre_room_room_id_empty))
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
    }
}
