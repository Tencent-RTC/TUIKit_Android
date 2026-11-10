package com.trtc.uikit.roomkit.inroom.features.roomview

import android.content.Context
import androidx.core.content.ContextCompat
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.FillMode
import io.trtc.tuikit.atomicxcore.api.view.RoomParticipantView
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

class RoomParticipantItemView(context: Context) : FrameLayout(context) {

    private val videoContainer: FrameLayout
    private val overlayView: RoomParticipantOverlayView
    private var participant: RoomParticipant? = null
    private var participantID: String? = null
    private var streamType = VideoStreamType.CAMERA
    private var isLocal = false
    private var renderer: RoomParticipantView? = null
    private var isRendererBound = false
    private var isPlaying = false

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_room_participant_item, this, true)
        videoContainer = findViewById(R.id.tile_video)
        overlayView = findViewById(R.id.participant_overlay)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val cornerRadiusPx = RoomVideoLayoutCalculator.CORNER_RADIUS_DP * resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
    }

    fun bind(
        participant: RoomParticipant,
        streamType: VideoStreamType,
        volume: Int,
        networkQuality: NetworkQuality? = null,
    ) {
        val nextIsLocal = participant.userID ==
            LoginStore.shared.loginState.loginUserInfo.value?.userID
        val bindingChanged = participantID != participant.userID ||
            this.streamType != streamType || isLocal != nextIsLocal
        if (bindingChanged) releaseRenderer()

        this.participant = participant
        participantID = participant.userID
        this.streamType = streamType
        isLocal = nextIsLocal
        overlayView.bind(participant, streamType, volume, hasVideoStream(participant), networkQuality)
        updatePlayback()
        applyStyle()
    }

    fun setPlaying(value: Boolean) {
        isPlaying = value
        updatePlayback()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyStyle()
        updatePlayback()
    }

    override fun onDetachedFromWindow() {
        isPlaying = false
        releaseRenderer()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePlayback()
    }

    private fun hasVideoStream(participant: RoomParticipant): Boolean = when (streamType) {
        VideoStreamType.SCREEN -> !isLocal && participant.screenShareStatus == DeviceStatus.ON
        VideoStreamType.CAMERA -> participant.cameraStatus == DeviceStatus.ON
    }

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

        val fillMode = if (streamType == VideoStreamType.SCREEN) FillMode.FIT else FillMode.FILL
        videoView.setActive(false)
        videoView.setFillMode(fillMode)
        videoView.init(streamType, participant)
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

    private fun applyStyle() {
        background = GradientDrawable().apply {
            cornerRadius = RoomVideoLayoutCalculator.CORNER_RADIUS_DP * resources.displayMetrics.density
            setColor(if (streamType == VideoStreamType.SCREEN) ContextCompat.getColor(context, R.color.roomkit_bg_inverse) else ContextCompat.getColor(context, R.color.roomkit_bg_canvas))
        }
    }
}
