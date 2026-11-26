package com.trtc.uikit.roomkit.inroom.features.roomview

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.FillMode
import io.trtc.tuikit.atomicxcore.api.view.RoomParticipantView
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

abstract class RoomParticipantCell(context: Context) : FrameLayout(context) {

    abstract val streamType: VideoStreamType

    @get:ColorInt
    abstract val cellBackgroundColor: Int

    private val videoContainer: FrameLayout
    private val overlayView: RoomParticipantOverlayView
    private var participant: RoomParticipant? = null
    private var volume = 0
    private var networkQuality: NetworkQuality? = null
    protected var isLocal = false
    private var renderer: RoomParticipantView? = null
    private var isRendererBound = false
    private var isRendererActive = false
    private var isPlaying = false

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_room_participant_item, this, true)
        videoContainer = findViewById(R.id.tile_video)
        overlayView = findViewById(R.id.participant_overlay)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val cornerRadiusPx = RoomParticipantLayout.CORNER_RADIUS_DP * resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
    }

    abstract fun hasVideoStream(participant: RoomParticipant): Boolean

    abstract fun configureRenderer(renderer: RoomParticipantView)

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
        Log.d(TAG, "bind ${old?.userID} -> ${participant.userID} identity=$identityChanged video=$hadVideo->$hasVideo playing=$isPlaying size=${width}x$height")
        overlayView.bind(participant, streamType, isLocal, volume, hasVideo, networkQuality)
        applyStyle()
        if (identityChanged || hadVideo != hasVideo) updatePlayback()
    }

    fun setPlaying(value: Boolean) {
        if (isPlaying == value) return
        Log.d(TAG, "setPlaying id=${participant?.userID} active=$value bound=$isRendererBound size=${width}x$height")
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
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyStyle()
        updatePlayback()
    }

    override fun onDetachedFromWindow() {
        isRendererActive = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePlayback()
    }

    fun onLaidOut() {
        val videoView = renderer
        val containerWidth = videoContainer.width
        val containerHeight = videoContainer.height
        if (videoView != null && containerWidth > 0 && containerHeight > 0 &&
            (videoView.width != containerWidth || videoView.height != containerHeight)
        ) {
            videoView.measure(
                MeasureSpec.makeMeasureSpec(containerWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(containerHeight, MeasureSpec.EXACTLY),
            )
            videoView.layout(0, 0, containerWidth, containerHeight)
        }
        if (isPlaying && !isRendererActive && videoView != null) {
            Log.d(TAG, "rendererReady id=${participant?.userID} viewSize=${videoView.width}x${videoView.height} attached=${videoView.isAttachedToWindow} container=${containerWidth}x$containerHeight")
        }
        updatePlayback()
    }

    private fun updatePlayback() {
        val participant = participant ?: run {
            if (renderer != null) Log.w(TAG, "release reason=noParticipant")
            releaseRenderer()
            return
        }
        if (width <= 0 || height <= 0 || !hasVideoStream(participant)) {
            if (renderer != null) {
                Log.w(TAG, "release reason=invalidPlayback id=${participant.userID} hasVideo=${hasVideoStream(participant)} size=${width}x$height")
            }
            releaseRenderer()
            return
        }
        bindRenderer(participant)
    }

    private fun bindRenderer(participant: RoomParticipant) {
        val videoView = renderer ?: RoomParticipantView(context).also { newVideoView ->
            Log.d(TAG, "rendererCreate id=${participant.userID} playing=$isPlaying size=${width}x$height")
            renderer = newVideoView
            newVideoView.setActive(false)
            videoContainer.addView(newVideoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        if (isRendererBound) {
            updateRendererActive(videoView)
            return
        }

        videoView.setActive(false)
        configureRenderer(videoView)
        videoView.init(streamType, participant)
        Log.d(TAG, "rendererInit id=${participant.userID} stream=$streamType active=$isPlaying")
        isRendererBound = true
        updateRendererActive(videoView)
    }

    private fun updateRendererActive(videoView: RoomParticipantView) {
        if (renderer !== videoView) return
        val isReady = videoView.isAttachedToWindow && videoView.width > 0 && videoView.height > 0
        val shouldBeActive = isPlaying && isReady
        if (isRendererActive == shouldBeActive) return
        isRendererActive = shouldBeActive
        Log.d(TAG, "rendererActive id=${participant?.userID} active=$shouldBeActive viewSize=${videoView.width}x${videoView.height} attached=${videoView.isAttachedToWindow}")
        videoView.setActive(shouldBeActive)
    }

    private fun releaseRenderer() {
        if (renderer != null) {
            Log.w(TAG, "rendererRelease id=${participant?.userID} playing=$isPlaying attached=$isAttachedToWindow size=${width}x$height")
        }
        renderer?.let { videoView ->
            videoView.setActive(false)
            videoContainer.removeView(videoView)
        }
        renderer = null
        isRendererBound = false
        isRendererActive = false
    }

    private fun applyStyle() {
        background = GradientDrawable().apply {
            cornerRadius = RoomParticipantLayout.CORNER_RADIUS_DP * resources.displayMetrics.density
            setColor(cellBackgroundColor)
        }
    }

    private companion object {
        const val TAG = "RoomParticipantCell"
    }
}

class RoomParticipantCameraCell(context: Context) : RoomParticipantCell(context) {

    override val streamType: VideoStreamType = VideoStreamType.CAMERA

    override val cellBackgroundColor: Int
        get() = ContextCompat.getColor(context, R.color.roomkit_bg_canvas)

    override fun hasVideoStream(participant: RoomParticipant): Boolean =
        participant.cameraStatus == DeviceStatus.ON

    override fun configureRenderer(renderer: RoomParticipantView) {
        renderer.setFillMode(FillMode.FILL)
    }
}

class RoomParticipantScreenShareCell(context: Context) : RoomParticipantCell(context) {

    override val streamType: VideoStreamType = VideoStreamType.SCREEN

    override val cellBackgroundColor: Int
        get() = ContextCompat.getColor(context, R.color.roomkit_bg_inverse)

    override fun hasVideoStream(participant: RoomParticipant): Boolean =
        !isLocal && participant.screenShareStatus == DeviceStatus.ON

    override fun configureRenderer(renderer: RoomParticipantView) {
        renderer.setFillMode(FillMode.FIT)
    }
}
