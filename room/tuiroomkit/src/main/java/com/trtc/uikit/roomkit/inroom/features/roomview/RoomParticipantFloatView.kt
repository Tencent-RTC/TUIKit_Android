package com.trtc.uikit.roomkit.inroom.features.roomview

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.FillMode
import io.trtc.tuikit.atomicxcore.api.view.RoomParticipantView
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

class RoomParticipantFloatView(context: Context) : FrameLayout(context) {

    var onFloatingPan: ((action: Int, translationX: Float, translationY: Float) -> Unit)? = null

    var isDragging = false
        private set

    private val videoContainer: FrameLayout
    private val overlayView: RoomParticipantOverlayView
    private var participant: RoomParticipant? = null
    private var isLocal = false
    private var volume = 0
    private var networkQuality: NetworkQuality? = null
    private var isPlaying = false
    private var renderer: RoomParticipantView? = null
    private var isRendererBound = false

    private var downRawX = 0f
    private var downRawY = 0f

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_room_participant_item, this, true)
        videoContainer = findViewById(R.id.tile_video)
        overlayView = findViewById(R.id.participant_overlay)
        isClickable = true
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val cornerRadiusPx = RoomParticipantLayout.CORNER_RADIUS_DP * resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        background = GradientDrawable().apply {
            cornerRadius = RoomParticipantLayout.CORNER_RADIUS_DP * resources.displayMetrics.density
            setColor(ContextCompat.getColor(context, R.color.roomkit_bg_canvas))
        }
    }

    fun bind(
        participant: RoomParticipant,
        localParticipantID: String?,
        volume: Int,
        networkQuality: NetworkQuality? = null,
    ) {
        val nextIsLocal = participant.userID == localParticipantID
        val nextVolume = if (participant.microphoneStatus == DeviceStatus.ON) volume else 0
        val networkChanged = this.networkQuality != networkQuality
        val old = this.participant
        if (old != null &&
            old.userID == participant.userID &&
            !networkChanged &&
            old.getDisplayName() == participant.getDisplayName() &&
            old.avatarURL == participant.avatarURL &&
            old.role == participant.role &&
            old.microphoneStatus == participant.microphoneStatus &&
            old.cameraStatus == participant.cameraStatus &&
            old.screenShareStatus == participant.screenShareStatus &&
            isLocal == nextIsLocal &&
            this.volume == nextVolume
        ) {
            this.participant = participant
            return
        }

        val identityChanged = old?.userID != participant.userID || isLocal != nextIsLocal
        val hadVideo = old?.let(::hasVideoStream) ?: false
        if (identityChanged) releaseRenderer()

        this.participant = participant
        isLocal = nextIsLocal
        this.volume = nextVolume
        this.networkQuality = networkQuality

        val hasVideo = hasVideoStream(participant)
        overlayView.bind(participant, VideoStreamType.CAMERA, isLocal, volume, hasVideo, networkQuality)
        if (identityChanged || hadVideo != hasVideo) updatePlayback()
    }

    fun setPlaying(value: Boolean) {
        if (isPlaying == value) return
        isPlaying = value
        updatePlayback()
    }

    fun release() {
        isPlaying = false
        releaseRenderer()
        participant = null
        isLocal = false
        volume = 0
        networkQuality = null
        overlayView.reset()
        translationX = 0f
        translationY = 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePlayback()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                downRawX = event.rawX
                downRawY = event.rawY
                parent?.requestDisallowInterceptTouchEvent(true)
                onFloatingPan?.invoke(MotionEvent.ACTION_DOWN, 0f, 0f)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                onFloatingPan?.invoke(
                    MotionEvent.ACTION_MOVE,
                    event.rawX - downRawX,
                    event.rawY - downRawY,
                )
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                onFloatingPan?.invoke(
                    event.actionMasked,
                    event.rawX - downRawX,
                    event.rawY - downRawY,
                )
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hasVideoStream(participant: RoomParticipant): Boolean =
        participant.cameraStatus == DeviceStatus.ON

    private fun updatePlayback() {
        val participant = participant ?: return
        val hasLayoutSize = width > 0 && height > 0
        val canRender = isPlaying && isAttachedToWindow && hasLayoutSize && hasVideoStream(participant)
        if (!canRender) {
            releaseRenderer()
            return
        }
        bindRenderer(participant)
    }

    private fun bindRenderer(participant: RoomParticipant) {
        val videoView = renderer ?: RoomParticipantView(context).also { newVideoView ->
            renderer = newVideoView
            newVideoView.setActive(false)
            videoContainer.addView(newVideoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        if (isRendererBound) return

        videoView.setActive(false)
        videoView.setFillMode(FillMode.FILL)
        videoView.init(VideoStreamType.CAMERA, participant)
        videoView.setActive(true)
        isRendererBound = true
    }

    private fun releaseRenderer() {
        renderer?.let { videoView ->
            videoView.setActive(false)
            videoContainer.removeView(videoView)
        }
        renderer = null
        isRendererBound = false
    }
}
