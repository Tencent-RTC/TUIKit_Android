package com.trtc.uikit.roomkit.view.main

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomActionSheetDialog
import com.trtc.uikit.roomkit.base.ui.RoomPopupDialog
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.AudioRoute
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * RoomTopBarView - Top navigation bar displaying room name, duration, and controls.
 * Provides audio route switch, camera switch, and end room functionality.
 * Subscribes to room info changes from RoomStore.
 */
class RoomTopBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomTopBarView")

    private var subscribeJob: Job? = null

    private val rlMeetingInfo: RelativeLayout by lazy { findViewById(R.id.rl_meeting_info) }
    private val ivAudioRoute: ImageView by lazy { findViewById(R.id.iv_audio_route) }
    private val ivCameraSwitch: ImageView by lazy { findViewById(R.id.iv_camera_switch) }
    private val tvRoomName: TextView by lazy { findViewById(R.id.tv_room_name) }
    private val tvDuration: TextView by lazy { findViewById(R.id.tv_duration) }
    private val llExitRoom: LinearLayout by lazy { findViewById(R.id.ll_exit_room) }

    private var roomType = RoomType.STANDARD
    private var roomStore = RoomStore.shared()
    private var participantStore: RoomParticipantStore? = null
    private val deviceStore = DeviceStore.shared()
    private var roomInfoDialog: RoomPopupDialog? = null

    private val durationHandler = Handler(Looper.getMainLooper())
    private var durationStartTime: Long = 0L
    private var isTimerRunning = false

    private val durationUpdateRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                val elapsedSeconds = (System.currentTimeMillis() - durationStartTime) / 1000
                updateDurationDisplay(elapsedSeconds)
                durationHandler.postDelayed(this, 1000)
            }
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_top_bar, this)
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startDurationTimer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopDurationTimer()
    }

    fun init(roomID: String, roomType: RoomType) {
        this.roomType = roomType
        super.init(roomID)
        ivCameraSwitch.visibility = if (roomType == RoomType.WEBINAR) GONE else VISIBLE
        ivAudioRoute.visibility = if (roomType == RoomType.WEBINAR) GONE else VISIBLE
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                roomStore.state.currentRoom.collect { roomInfo ->
                    roomInfo?.let {
                        updateRoomName(it.getDisplayName())
                    }
                }
            }

            launch {
                deviceStore.deviceState.currentAudioRoute.collect { audioRoute ->
                    updateAudioRouteIcon(audioRoute)
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
        subscribeJob = null
        roomInfoDialog?.dismiss()
        roomInfoDialog = null
    }

    private fun initView() {
        rlMeetingInfo.setOnClickListener {
            handleMeetingInfoClick()
        }

        ivAudioRoute.setOnClickListener {
            handleAudioRouteClick()
        }

        ivCameraSwitch.setOnClickListener {
            handleCameraSwitchClick()
        }

        llExitRoom.setOnClickListener {
            handleExitClick()
        }
    }

    private fun updateRoomName(name: String) {
        tvRoomName.text = name
    }

    private fun updateAudioRouteIcon(audioRoute: AudioRoute) {
        when (audioRoute) {
            AudioRoute.SPEAKERPHONE -> {
                ivAudioRoute.setImageResource(R.drawable.roomkit_ic_speaker)
            }

            AudioRoute.EARPIECE -> {
                ivAudioRoute.setImageResource(R.drawable.roomkit_ic_headset)
            }
        }
    }

    private fun handleMeetingInfoClick() {
        logger.info("handleMeetingInfoClick")
        if (roomInfoDialog == null) {
            val view = RoomInfoView(context).apply {
                init(roomID)
            }
            roomInfoDialog = RoomPopupDialog(context).apply {
                setView(view)
            }
        }
        roomInfoDialog?.show()
    }

    private fun handleAudioRouteClick() {
        logger.info("handleAudioRouteClick")
        val currentRoute = deviceStore.deviceState.currentAudioRoute.value
        val newRoute = if (currentRoute == AudioRoute.SPEAKERPHONE) {
            AudioRoute.EARPIECE
        } else {
            AudioRoute.SPEAKERPHONE
        }
        deviceStore.setAudioRoute(newRoute)
        logger.info("Audio route switched to: $newRoute")
    }

    private fun handleCameraSwitchClick() {
        logger.info("handleCameraSwitchClick")
        val isFrontCamera = deviceStore.deviceState.isFrontCamera.value
        deviceStore.switchCamera(!isFrontCamera)
        logger.info("Camera switched to: ${if (!isFrontCamera) "front" else "back"}")
    }

    private fun handleExitClick() {
        logger.info("handleExitClick")
        showExitRoomDialog()
    }

    private fun showExitRoomDialog() {
        val localParticipant = participantStore?.state?.localParticipant?.value
        if (localParticipant == null) {
            logger.warn("Local participant is null, cannot show exit dialog")
            return
        }

        val isOwner = localParticipant.role == ParticipantRole.OWNER

        if (isOwner) {
            RoomActionSheetDialog.Builder(context)
                .setTips(R.string.roomkit_confirm_leave_room_by_owner)
                .addAction(R.string.roomkit_leave_room, isWarning = false) {
                    handleLeaveRoom()
                }
                .addAction(R.string.roomkit_end_room, isWarning = true) {
                    handleEndRoom()
                }
                .show()
        } else {
            RoomActionSheetDialog.Builder(context)
                .setTips(R.string.roomkit_confirm_leave_room_by_genera_user)
                .addAction(R.string.roomkit_leave_room, isWarning = false) {
                    handleLeaveRoom()
                }
                .show()
        }
    }

    private fun handleLeaveRoom() {
        logger.info("User confirmed to leave room")
        roomStore.leaveRoom(object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Leave room success")
                (context as? Activity)?.finish()
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Leave room failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
                (context as? Activity)?.finish()
            }
        })
    }

    private fun handleEndRoom() {
        logger.info("Owner confirmed to end room")
        roomStore.endRoom(object : CompletionHandler {
            override fun onSuccess() {
                logger.info("End room success")
                (context as? Activity)?.finish()
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("End room failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
                (context as? Activity)?.finish()
            }
        })
    }

    private fun startDurationTimer() {
        if (!isTimerRunning) {
            durationStartTime = System.currentTimeMillis()
            isTimerRunning = true
            durationHandler.post(durationUpdateRunnable)
            logger.info("Duration timer started")
        }
    }

    private fun stopDurationTimer() {
        if (isTimerRunning) {
            isTimerRunning = false
            durationHandler.removeCallbacks(durationUpdateRunnable)
            logger.info("Duration timer stopped")
        }
    }

    private fun updateDurationDisplay(seconds: Long) {
        val minutes = seconds / 60
        val secs = seconds % 60
        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        tvDuration.text = formattedTime
    }
}
