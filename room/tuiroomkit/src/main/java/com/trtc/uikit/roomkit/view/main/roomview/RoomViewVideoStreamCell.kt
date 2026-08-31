package com.trtc.uikit.roomkit.view.main.roomview

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.utils.dpToPx
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.FillMode
import io.trtc.tuikit.atomicxcore.api.view.RoomParticipantView
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

class RoomViewVideoStreamCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_AVATAR_SIZE_DP = 64
        private const val DEFAULT_AVATAR_RADIUS_DP = 32
        private const val DEFAULT_OVERLAY_MARGIN_DP = 8
    }

    private val participantView: RoomParticipantView = RoomParticipantView(context)
    private var boundStreamId: String? = null
    private var streamType: VideoStreamType = VideoStreamType.CAMERA

    private val avatarPlaceholder: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.roomkit_color_video_item))
        visibility = GONE
    }

    private val avatarView: ImageFilterView = ImageFilterView(context).apply {
        setImageResource(R.drawable.roomkit_ic_default_avatar)
        scaleType = ImageView.ScaleType.CENTER_CROP
        round = dpToPx(DEFAULT_AVATAR_RADIUS_DP).toFloat()
    }

    private val speakingBorder: View = View(context).apply {
        background = ContextCompat.getDrawable(context, R.drawable.roomkit_bg_video_cell_speaking)
        visibility = INVISIBLE
    }

    private val nameOverlay: RoomVideoNameOverlayView = RoomVideoNameOverlayView(context).apply {
        setStreamType(VideoStreamType.CAMERA)
    }


    init {
        addView(
            participantView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        avatarPlaceholder.addView(
            avatarView,
            FrameLayout.LayoutParams(
                dpToPx(DEFAULT_AVATAR_SIZE_DP),
                dpToPx(DEFAULT_AVATAR_SIZE_DP)
            ).apply { gravity = Gravity.CENTER }
        )
        addView(
            avatarPlaceholder,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            speakingBorder,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            nameOverlay,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                val margin = dpToPx(DEFAULT_OVERLAY_MARGIN_DP)
                setMargins(margin, margin, margin, margin)
            }
        )
    }

    fun setAvatarSize(sizeDp: Int, radiusDp: Int) {
        val lp = avatarView.layoutParams as LayoutParams
        val sizePx = dpToPx(sizeDp)
        if (lp.width != sizePx || lp.height != sizePx) {
            lp.width = sizePx
            lp.height = sizePx
            avatarView.layoutParams = lp
        }
        avatarView.round = dpToPx(radiusDp).toFloat()
    }

    fun setOverlayMargin(marginDp: Int) {
        val lp = nameOverlay.layoutParams as LayoutParams
        val marginPx = dpToPx(marginDp)
        lp.setMargins(marginPx, marginPx, marginPx, marginPx)
        nameOverlay.layoutParams = lp
    }

    fun setOrientationSwitchClickListener(listener: (() -> Unit)?) {
        nameOverlay.setOrientationSwitchClickListener(listener)
    }

    fun bind(participant: RoomParticipant, streamType: VideoStreamType) {
        setStreamType(streamType)

        val streamUniqueId = VideoStreamItem.buildUniqueId(participant.userID, streamType)
        val isStreamChanged = boundStreamId != streamUniqueId
        if (isStreamChanged) {
            boundStreamId = streamUniqueId
            participantView.init(streamType, participant)
        } else {
            participantView.updateParticipant(participant)
        }

        val fillMode = if (streamType == VideoStreamType.SCREEN) FillMode.FIT else FillMode.FILL
        participantView.setFillMode(fillMode)

        nameOverlay.updateParticipant(participant)
        updateAvatarVisibility(participant)
        resetSpeakingStateFromMic(participant)
    }

    fun updateParticipant(participant: RoomParticipant) {
        nameOverlay.updateParticipant(participant)
        participantView.updateParticipant(participant)
        updateAvatarVisibility(participant)
        resetSpeakingStateFromMic(participant)
    }

    fun setActive(active: Boolean) {
        participantView.setActive(active)
    }

    fun release() {
        participantView.setActive(false)
        boundStreamId = null
    }

    fun resetBoundStream() {
        boundStreamId = null
    }

    fun updateSpeakingState(isSpeaking: Boolean) {
        speakingBorder.visibility = if (isSpeaking) VISIBLE else INVISIBLE
    }

    private fun setStreamType(streamType: VideoStreamType) {
        this.streamType = streamType
        nameOverlay.setStreamType(streamType)
    }

    private fun updateAvatarVisibility(participant: RoomParticipant) {
        if (streamType == VideoStreamType.SCREEN) {
            avatarPlaceholder.visibility = GONE
            return
        }
        if (participant.cameraStatus != DeviceStatus.ON) {
            loadAvatar(participant)
            avatarPlaceholder.visibility = VISIBLE
        } else {
            avatarPlaceholder.visibility = GONE
        }
    }

    private fun loadAvatar(participant: RoomParticipant) {
        if (participant.avatarURL.isEmpty()) {
            avatarView.setImageResource(R.drawable.roomkit_ic_default_avatar)
        } else {
            ImageLoader.load(
                participantView.context,
                avatarView,
                participant.avatarURL,
                R.drawable.roomkit_ic_default_avatar
            )
        }
    }

    private fun resetSpeakingStateFromMic(participant: RoomParticipant) {
        if (participant.microphoneStatus == DeviceStatus.OFF) {
            speakingBorder.visibility = INVISIBLE
        }
    }
}
