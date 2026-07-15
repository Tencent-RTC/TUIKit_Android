package com.trtc.uikit.roomkit

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.trtc.uikit.roomkit.view.RoomMainView
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import io.trtc.tuikit.atomicx.common.foregroundservice.VideoForegroundService
import io.trtc.tuikit.atomicxcore.api.room.CreateRoomOptions
import io.trtc.tuikit.atomicxcore.api.room.RoomType

/**
 * RoomMainActivity - Main container activity for the video conference room.
 * This activity serves as a pure container with no business logic,
 * only responsible for loading the RoomMainView.
 */
class RoomMainActivity : FullScreenActivity() {

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_ROOM_NAME = "room_name"
        const val EXTRA_IS_CREATE = "is_create"
        const val EXTRA_AUTO_ENABLE_MICROPHONE = "auto_enable_microphone"
        const val EXTRA_AUTO_ENABLE_CAMERA = "auto_enable_camera"
        const val EXTRA_AUTO_ENABLE_SPEAKER = "auto_enable_speaker"
    }

    private var roomMainView: RoomMainView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        roomMainView = RoomMainView(this)
        setContentView(roomMainView)

        val roomID = intent.getStringExtra(EXTRA_ROOM_ID) ?: return
        val roomName = intent.getStringExtra(EXTRA_ROOM_NAME) ?: roomID
        val isCreate = intent.getBooleanExtra(EXTRA_IS_CREATE, false)
        val autoEnableMicrophone = intent.getBooleanExtra(EXTRA_AUTO_ENABLE_MICROPHONE, false)
        val autoEnableCamera = intent.getBooleanExtra(EXTRA_AUTO_ENABLE_CAMERA, false)
        val autoEnableSpeaker = intent.getBooleanExtra(EXTRA_AUTO_ENABLE_SPEAKER, false)

        val behavior = if (isCreate) {
            val options = CreateRoomOptions(roomName = roomName)
            RoomMainView.RoomBehavior.Create(options)
        } else {
            RoomMainView.RoomBehavior.Join
        }

        val config = RoomMainView.ConnectConfig(
            autoEnableMicrophone = autoEnableMicrophone,
            autoEnableCamera = autoEnableCamera,
            autoEnableSpeaker = autoEnableSpeaker
        )

        val roomType = if (isWebinarRoom(roomID)) RoomType.WEBINAR else RoomType.STANDARD
        roomMainView?.init(roomID, roomType, behavior, config)
        applySystemBarStyle(resources.configuration.orientation)
        startForegroundService()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarStyle(newConfig.orientation)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
        roomMainView = null
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
    }

    private fun applySystemBarStyle(orientation: Int) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun startForegroundService() {
        val context = ContextProvider.getApplicationContext()
        VideoForegroundService.start(
            context,
            context.getString(context.applicationInfo.labelRes),
            context.getString(R.string.roomkit_room_running),
            0
        )
    }

    private fun stopForegroundService() {
        val context = ContextProvider.getApplicationContext()
        VideoForegroundService.stop(context)
    }

    private fun isWebinarRoom(roomID: String): Boolean {
        return roomID.startsWith("webinar")
    }
}
