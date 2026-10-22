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
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import com.trtc.uikit.roomkit.base.util.RoomDevice
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils
import com.trtc.uikit.roomkit.base.util.applyBottomWindowInsets
import com.trtc.uikit.roomkit.base.util.generateRoomID
import com.trtc.uikit.roomkit.base.util.getDisplayName
import com.trtc.uikit.roomkit.inroom.views.meeting.MeetingActivity
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.GetRoomInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomStore

class RoomQuickStartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val nameInput: EditText
    private val microphoneSwitch: SwitchCompat
    private val speakerSwitch: SwitchCompat
    private val cameraSwitch: SwitchCompat
    private val micPermissionHint: View
    private val cameraPermissionHint: View
    private var isGeneratingRoomID = false

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_quick_start_view, this, true)

        nameInput = findViewById(R.id.meeting_name_input)
        microphoneSwitch = findViewById(R.id.microphone_switch)
        speakerSwitch = findViewById(R.id.speaker_switch)
        cameraSwitch = findViewById(R.id.camera_switch)
        micPermissionHint = findViewById(R.id.mic_permission_hint)
        cameraPermissionHint = findViewById(R.id.camera_permission_hint)

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

        LoginStore.shared.loginState.loginUserInfo.value?.let { profile ->
            nameInput.setText(
                context.getString(
                    R.string.roomkit_pre_room_quick_meeting_name,
                    profile.getDisplayName()
                )
            )
        }

        findViewById<RoomNavigationBar>(R.id.toolbar).setOnBackClickListener {
            (context as? Activity)?.finish()
        }
        applyBottomWindowInsets()
        findViewById<View>(R.id.submit_button).setOnClickListener {
            startMeeting(
                roomName = nameInput.text?.toString()?.trim().orEmpty(),
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

    fun updatePermissionHints() {
        micPermissionHint.visibility =
            if (RoomLocalMediaUtils.isPermissionDenied(context, RoomDevice.MICROPHONE)) VISIBLE else GONE
        cameraPermissionHint.visibility =
            if (RoomLocalMediaUtils.isPermissionDenied(context, RoomDevice.CAMERA)) VISIBLE else GONE
    }

    private fun openSystemSettings() {
        RoomLocalMediaUtils.openSystemSettings(context)
    }

    private companion object {
        const val ROOM_NOT_FOUND_CODE = 100004
    }
}
