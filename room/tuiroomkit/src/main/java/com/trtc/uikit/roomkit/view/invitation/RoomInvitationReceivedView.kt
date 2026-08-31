package com.trtc.uikit.roomkit.view.invitation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.RoomMainActivity
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.CallRejectionReason
import io.trtc.tuikit.atomicxcore.api.room.RoomCall
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomListener
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomUser

class RoomInvitationReceivedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomInvitationReceivedView")

    private val imgBackground: ImageView
    private val imgCallerAvatar: ImageView
    private val tvInvitation: TextView
    private val tvRoomName: TextView
    private val tvRoomMeta: TextView
    private val viewSlideToAccept: SlideToAcceptView
    private val btnReject: Button

    private var roomID: String = ""

    @Volatile
    private var isHandling: Boolean = false

    private val roomStore = RoomStore.Companion.shared()

    private var mediaPlayer: MediaPlayer? = null
    private var bellHandler: Handler? = null
    private var bellThread: HandlerThread? = null
    private var vibrator: Vibrator? = null

    private val vibratePattern = longArrayOf(0, 1000, 1000)
    private val vibrateAmplitude = intArrayOf(0, 255, 0)

    private val roomListener = object : RoomListener() {
        override fun onCallCancelled(roomInfo: RoomInfo, call: RoomCall) {
            if (roomInfo.roomID != roomID) {
                logger.info("onCallCancelled ignored: event roomID=${roomInfo.roomID}, current=$roomID")
                return
            }
            logger.info("onCallCancelled: roomID=${roomInfo.roomID}, finish")
            finishActivity()
        }

        override fun onCallTimeout(roomInfo: RoomInfo, call: RoomCall) {
            if (roomInfo.roomID != roomID) {
                logger.info("onCallTimeout ignored: event roomID=${roomInfo.roomID}, current=$roomID")
                return
            }
            logger.info("onCallTimeout: roomID=${roomInfo.roomID}, finish")
            finishActivity()
        }

        override fun onCallHandledByOtherDevice(roomInfo: RoomInfo, isAccepted: Boolean) {
            if (roomInfo.roomID != roomID) {
                logger.info("onCallHandledByOtherDevice ignored: event roomID=${roomInfo.roomID}, current=$roomID")
                return
            }
            logger.info("onCallHandledByOtherDevice: roomID=${roomInfo.roomID}, isAccepted=$isAccepted, finish")
            finishActivity()
        }

        override fun onCallRevokedByAdmin(roomInfo: RoomInfo, call: RoomCall, operator: RoomUser) {
            if (roomInfo.roomID != roomID) {
                logger.info("onCallRevokedByAdmin ignored: event roomID=${roomInfo.roomID}, current=$roomID")
                return
            }
            logger.info("onCallRevokedByAdmin: roomID=${roomInfo.roomID}, operator=${operator.userID}, finish")
            finishActivity()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_invitation_received, this, true)
        imgBackground = findViewById(R.id.img_background)
        imgCallerAvatar = findViewById(R.id.img_caller_avatar)
        tvInvitation = findViewById(R.id.tv_invitation)
        tvRoomName = findViewById(R.id.tv_room_name)
        tvRoomMeta = findViewById(R.id.tv_room_meta)
        viewSlideToAccept = findViewById(R.id.view_slide_to_accept)
        btnReject = findViewById(R.id.btn_reject)

        viewSlideToAccept.setListener { handleAccept() }
        btnReject.setOnClickListener { handleReject() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        roomStore.addRoomListener(roomListener)
        RoomInvitationManager.getInstance().setInvitationPending(true)
        startRinging()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRinging()
        roomStore.removeRoomListener(roomListener)
        RoomInvitationManager.getInstance().setInvitationPending(false)
    }

    fun bind(
        roomID: String,
        roomName: String,
        ownerName: String,
        callerName: String,
        callerAvatarUrl: String,
        participantCount: Int
    ) {
        this.roomID = roomID

        ImageLoader.load(context, imgBackground, callerAvatarUrl, R.drawable.roomkit_ic_default_avatar)
        ImageLoader.load(context, imgCallerAvatar, callerAvatarUrl, R.drawable.roomkit_ic_default_avatar)

        tvInvitation.text = context.getString(R.string.roomkit_invite_you_to_join_room, callerName)
        tvRoomName.text = roomName
        val participantDesc = context.getString(R.string.roomkit_format_add_attendee, participantCount.toString())
        tvRoomMeta.text = context.getString(R.string.roomkit_invitation_room_meta, ownerName, participantDesc)
    }

    private fun handleAccept() {
        if (isHandling) {
            logger.warn("handleAccept skipped: already handling")
            return
        }
        if (roomID.isEmpty()) {
            logger.error("handleAccept skipped: roomID empty")
            return
        }
        isHandling = true
        setButtonsEnabled(false)
        logger.info("acceptCall: roomID=$roomID")
        roomStore.acceptCall(
            roomID,
            object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("acceptCall success, launching RoomMainActivity")
                    launchRoomMainActivity()
                    finishActivity()
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("acceptCall failed: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                    finishActivity()
                }
            }
        )
    }

    private fun handleReject() {
        if (isHandling) {
            logger.warn("handleReject skipped: already handling")
            return
        }
        if (roomID.isEmpty()) {
            logger.error("handleReject skipped: roomID empty")
            return
        }
        isHandling = true
        setButtonsEnabled(false)
        logger.info("rejectCall: roomID=$roomID")
        roomStore.rejectCall(
            roomID,
            CallRejectionReason.REJECTED,
            object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("rejectCall success")
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("rejectCall failed: code=$code, desc=$desc")
                }
            }
        )
        finishActivity()
    }

    private fun launchRoomMainActivity() {
        val intent = Intent(context, RoomMainActivity::class.java).apply {
            putExtra(RoomMainActivity.Companion.EXTRA_ROOM_ID, roomID)
            putExtra(RoomMainActivity.Companion.EXTRA_IS_CREATE, false)
            putExtra(RoomMainActivity.Companion.EXTRA_AUTO_ENABLE_MICROPHONE, true)
            putExtra(RoomMainActivity.Companion.EXTRA_AUTO_ENABLE_CAMERA, false)
            putExtra(RoomMainActivity.Companion.EXTRA_AUTO_ENABLE_SPEAKER, true)
        }
        context.startActivity(intent)
    }

    private fun finishActivity() {
        (context as? Activity)?.finish()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        viewSlideToAccept.isEnabled = enabled
        btnReject.isEnabled = enabled
    }

    private fun startRinging() {
        startMediaPlayer()
        startVibration()
    }

    private fun stopRinging() {
        stopMediaPlayer()
        stopVibration()
    }

    private fun startMediaPlayer() {
        if (mediaPlayer != null) return
        val thread = HandlerThread("InvitationBell").also { it.start() }
        val handler = Handler(thread.looper)

        handler.post {
            try {
                val afd = context.resources.openRawResourceFd(R.raw.phone_ringing)
                try {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.mode = AudioManager.MODE_RINGTONE

                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(attrs)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        isLooping = true
                        prepare()
                        start()
                    }
                    bellThread = thread
                    bellHandler = handler
                } finally {
                    afd.close()
                }
            } catch (e: Exception) {
                logger.error("startMediaPlayer failed: ${e.message}")
                mediaPlayer = null
                thread.quitSafely()
            }
        }
    }

    private fun stopMediaPlayer() {
        bellHandler?.post {
            mediaPlayer?.run {
                try {
                    if (isPlaying) stop()
                    release()
                } catch (e: Exception) {
                    logger.warn("stopMediaPlayer release: ${e.message}")
                }
            }
            mediaPlayer = null
        }
        bellThread?.quitSafely()
        bellThread = null
        bellHandler = null
    }

    private fun startVibration() {
        if (vibrator != null) return
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: run {
            logger.warn("Vibrator service unavailable")
            return
        }
        if (!vib.hasVibrator()) {
            logger.warn("Device has no vibrator")
            return
        }
        vibrator = vib
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(vibratePattern, vibrateAmplitude, 0)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(vibratePattern, 0)
            }
        } catch (e: Exception) {
            logger.error("startVibration failed: ${e.message}")
            vibrator = null
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }
}