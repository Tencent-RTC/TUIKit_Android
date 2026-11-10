package com.trtc.uikit.roomkit.inroom.views

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import io.trtc.tuikit.atomicxcore.api.room.CreateRoomOptions

class MeetingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_ROOM_NAME = "room_name"
        const val EXTRA_IS_CREATE = "is_create"
        const val EXTRA_AUTO_ENABLE_MICROPHONE = "auto_enable_microphone"
        const val EXTRA_AUTO_ENABLE_CAMERA = "auto_enable_camera"
        const val EXTRA_AUTO_ENABLE_SPEAKER = "auto_enable_speaker"
    }

    private lateinit var meetingView: MeetingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        meetingView = MeetingView(this)
        setContentView(meetingView)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val roomID = intent.getStringExtra(EXTRA_ROOM_ID) ?: return
        val roomName = intent.getStringExtra(EXTRA_ROOM_NAME) ?: roomID
        val behavior = if (intent.getBooleanExtra(EXTRA_IS_CREATE, false)) {
            MeetingView.RoomBehavior.CreateAndJoinRoom(CreateRoomOptions(roomName = roomName))
        } else {
            MeetingView.RoomBehavior.Join
        }
        val config = MeetingView.ConnectConfig(
            autoEnableMicrophone = intent.getBooleanExtra(EXTRA_AUTO_ENABLE_MICROPHONE, false),
            autoEnableCamera = intent.getBooleanExtra(EXTRA_AUTO_ENABLE_CAMERA, false),
            autoEnableSpeaker = intent.getBooleanExtra(EXTRA_AUTO_ENABLE_SPEAKER, false),
        )
        meetingView.init(roomID, behavior, config)
    }
}
