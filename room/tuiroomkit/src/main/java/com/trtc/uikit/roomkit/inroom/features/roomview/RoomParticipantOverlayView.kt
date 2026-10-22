package com.trtc.uikit.roomkit.inroom.features.roomview

import androidx.core.content.ContextCompat
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.avatar.RoomAvatar
import androidx.appcompat.widget.AppCompatImageView
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.base.util.dpF
import com.trtc.uikit.roomkit.inroom.common.miclevel.RoomMicLevelView
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

class RoomParticipantOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val avatar: RoomAvatar
    private val screenStatus: TextView
    private val microphone: AppCompatImageView
    private val micLevel: RoomMicLevelView
    private val nameLabel: TextView
    private val localBadge: TextView
    private val roleBadge: AppCompatImageView
    private val speakingBorder: View
    private val flipCamera: AppCompatImageView
    private val netMark: LinearLayout
    private var role: ParticipantRole? = null
    private var isLocal = false
    private var isCameraOn = false
    private val deviceStore = DeviceStore.shared()

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_room_participant_overlay, this, true)
        flipCamera = findViewById(R.id.flip_camera)
        avatar = findViewById(R.id.tile_avatar)
        screenStatus = findViewById(R.id.screen_status)
        microphone = findViewById(R.id.microphone_icon)
        micLevel = findViewById(R.id.mic_level)
        nameLabel = findViewById(R.id.tv_name)
        localBadge = findViewById(R.id.local_badge)
        roleBadge = findViewById(R.id.role_badge)
        speakingBorder = findViewById(R.id.speaking_border)
        netMark = findViewById(R.id.net_mark)
        buildNetBars()
        isClickable = false
        isFocusable = false
        flipCamera.setOnClickListener {
            deviceStore.switchCamera(!deviceStore.deviceState.isFrontCamera.value)
        }
    }

    fun bind(
        participant: RoomParticipant,
        streamType: VideoStreamType,
        volume: Int,
        hasVideoStream: Boolean,
        networkQuality: NetworkQuality? = null,
    ) {
        this.isLocal = participant.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
        role = participant.role
        updateNetworkMark(networkQuality)
        isCameraOn = participant.cameraStatus == DeviceStatus.ON
        val isMicrophoneOn = participant.microphoneStatus == DeviceStatus.ON
        val isSpeaking = isMicrophoneOn && volume > 0
        speakingBorder.visibility = if (isSpeaking) VISIBLE else GONE
        updateParticipantInfo(participant)
        updateMicrophone(participant.microphoneStatus)
        micLevel.visibility = if (isMicrophoneOn) VISIBLE else GONE
        micLevel.setVolume(if (isMicrophoneOn) volume else 0)
        updateBadges()
        updatePlaceholder(streamType, hasVideoStream)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBadges()
    }

    private fun updateParticipantInfo(participant: RoomParticipant) {
        val displayName = participant.getDisplayName()
        avatar.text = displayName
        avatar.url = participant.avatarURL
        nameLabel.text = displayName
    }

    private fun updateMicrophone(status: DeviceStatus) {
        val isMicrophoneOn = status == DeviceStatus.ON
        val iconResource = if (isMicrophoneOn) {
            R.drawable.roomkit_icon_microphone_on_roomview
        } else {
            R.drawable.roomkit_icon_microphone_off_roomview
        }
        val descriptionResource = if (isMicrophoneOn) {
            R.string.roomkit_participants_mic_on_hint
        } else {
            R.string.roomkit_participants_mic_off_hint
        }
        microphone.setImageResource(iconResource)
        microphone.contentDescription = context.getString(descriptionResource)
    }

    private fun updateBadges() {
        val showLocalBadge = isLocal && width >= context.dp(96)
        val hasPrivilegedRole = role != null && role != ParticipantRole.GENERAL_USER
        val showRoleBadge = hasPrivilegedRole && width >= context.dp(112)
        localBadge.visibility = if (showLocalBadge) VISIBLE else GONE
        roleBadge.visibility = if (showRoleBadge) VISIBLE else GONE
        flipCamera.visibility = if (isLocal && isCameraOn) VISIBLE else GONE
        localBadge.text = context.getString(R.string.roomkit_participants_me)
        localBadge.setTextColor(ContextCompat.getColor(context, R.color.roomkit_fg_on_media))
        localBadge.background = chip(ContextCompat.getColor(context, R.color.roomkit_bg_press_on_media))
        if (role == ParticipantRole.OWNER) {
            roleBadge.setImageResource(R.drawable.roomkit_icon_host_role)
            roleBadge.background = chip(ContextCompat.getColor(context, R.color.roomkit_accent))
            roleBadge.contentDescription = context.getString(R.string.roomkit_participants_owner)
        } else {
            roleBadge.setImageResource(R.drawable.roomkit_icon_role_user)
            roleBadge.background = chip(ContextCompat.getColor(context, R.color.roomkit_warning))
            roleBadge.contentDescription = context.getString(R.string.roomkit_participants_admin)
        }
    }

    private fun chip(color: Int): GradientDrawable = chipCache.getOrPut(color) {
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun updatePlaceholder(streamType: VideoStreamType, hasVideoStream: Boolean) {
        val isScreenShare = streamType == VideoStreamType.SCREEN
        avatar.visibility = if (!isScreenShare && !hasVideoStream) VISIBLE else GONE
        screenStatus.visibility = if (isLocal && isScreenShare && !hasVideoStream) VISIBLE else GONE
        screenStatus.setText(R.string.roomkit_room_view_sharing_screen)
    }

    private fun updateNetworkMark(quality: NetworkQuality?) {
        netMark.visibility = when (quality) {
            NetworkQuality.BAD, NetworkQuality.VERY_BAD, NetworkQuality.DOWN -> VISIBLE
            else -> GONE
        }
    }

    private fun buildNetBars() {
        val danger = ContextCompat.getColor(context, R.color.roomkit_danger)
        NET_BAR_HEIGHTS_DP.forEachIndexed { index, height ->
            val params = LinearLayout.LayoutParams(context.dp(NET_BAR_WIDTH_DP), context.dp(height))
            if (index > 0) params.marginStart = context.dp(NET_BAR_GAP_DP)
            params.gravity = android.view.Gravity.BOTTOM
            netMark.addView(
                View(context).apply {
                    layoutParams = params
                    alpha = if (index == 0) 1f else NET_BAR_DIM_ALPHA
                    background = GradientDrawable().apply {
                        cornerRadius = context.dpF(NET_BAR_CORNER_RADIUS_DP)
                        setColor(danger)
                    }
                },
            )
        }
    }

    private val chipCache = mutableMapOf<Int, GradientDrawable>()

    private companion object {
        val NET_BAR_HEIGHTS_DP = listOf(4, 8, 12)
        const val NET_BAR_WIDTH_DP = 3
        const val NET_BAR_GAP_DP = 2
        const val NET_BAR_CORNER_RADIUS_DP = 1f
        const val NET_BAR_DIM_ALPHA = 0.18f
    }
}
