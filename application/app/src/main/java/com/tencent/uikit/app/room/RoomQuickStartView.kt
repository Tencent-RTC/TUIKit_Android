package com.tencent.uikit.app.room

import com.tencent.uikit.app.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import com.trtc.uikit.roomkit.base.util.applyBottomWindowInsets
import com.trtc.uikit.roomkit.base.util.generateRoomID
import com.trtc.uikit.roomkit.base.util.getDisplayName
import com.trtc.uikit.roomkit.inroom.views.MeetingActivity
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.GetRoomInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomStore

class RoomQuickStartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val nameInput: TextView
    private val microphoneSwitch: SwitchCompat
    private val speakerSwitch: SwitchCompat
    private val cameraSwitch: SwitchCompat
    private var isGeneratingRoomID = false

    init {
        LayoutInflater.from(context).inflate(R.layout.app_quick_start_view, this, true)

        nameInput = findViewById(R.id.meeting_name_input)
        microphoneSwitch = findViewById(R.id.microphone_switch)
        speakerSwitch = findViewById(R.id.speaker_switch)
        cameraSwitch = findViewById(R.id.camera_switch)


        LoginStore.shared.loginState.loginUserInfo.value?.let { profile ->
            nameInput.setText(
                context.getString(
                    R.string.app_pre_room_quick_meeting_name,
                    profile.getDisplayName().take(DISPLAY_NAME_MAX_LENGTH)
                )
            )
        }

        findViewById<View>(R.id.toolbar).applyTopWindowInsets()
        findViewById<View>(R.id.nav_back_button).setOnClickListener {
            (context as? Activity)?.finish()
        }
        applyBottomWindowInsets()
        findViewById<View>(R.id.submit_button).setOnClickListener {
            startMeeting(
                roomName = nameInput.text.toString().trim(),
                isMicrophoneOn = microphoneSwitch.isChecked,
                isSpeakerOn = speakerSwitch.isChecked,
                isCameraOn = cameraSwitch.isChecked,
            )
        }
    }

    private fun startMeeting(roomName: String, isMicrophoneOn: Boolean, isSpeakerOn: Boolean, isCameraOn: Boolean) {
        if (isGeneratingRoomID) return
        isGeneratingRoomID = true
        generateUnusedRoomID(remainingAttempts = 5) { roomID ->
            isGeneratingRoomID = false
            context.startActivity(
                Intent(context, MeetingActivity::class.java).apply {
                    putExtra(MeetingActivity.EXTRA_ROOM_ID, roomID)
                    putExtra(MeetingActivity.EXTRA_ROOM_NAME, roomName.ifEmpty { roomID })
                    putExtra(MeetingActivity.EXTRA_IS_CREATE, true)
                    putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_MICROPHONE, isMicrophoneOn)
                    putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_CAMERA, isCameraOn)
                    putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_SPEAKER, isSpeakerOn)
                },
            )
            (context as? Activity)?.finish()
        }
    }

    private fun generateUnusedRoomID(remainingAttempts: Int, onReady: (String) -> Unit) {
        val roomID = generateRoomID()
        RoomStore.shared().getRoomInfo(roomID, object : GetRoomInfoCompletionHandler {
            override fun onSuccess(roomInfo: RoomInfo) {
                if (remainingAttempts > 0) {
                    generateUnusedRoomID(remainingAttempts - 1, onReady)
                } else {
                    onReady(roomID + "-"+ System.currentTimeMillis().toString().take(6))
                }
            }

            override fun onFailure(code: Int, desc: String) {
                if (code == ROOM_NOT_FOUND_CODE) {
                    onReady(roomID)
                } else {
                    onReady(roomID + "-" + System.currentTimeMillis().toString().take(6))
                }
            }
        })
    }

    private companion object {
        const val ROOM_NOT_FOUND_CODE = 100004
        const val DISPLAY_NAME_MAX_LENGTH = 10
    }
}
