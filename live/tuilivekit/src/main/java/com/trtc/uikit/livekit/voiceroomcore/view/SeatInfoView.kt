package com.trtc.uikit.livekit.voiceroomcore.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.convertToSeatInfo
import com.trtc.uikit.livekit.voiceroomcore.impl.SeatGridViewObserverManager
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo

@SuppressLint("ViewConstructor")
class SeatInfoView @JvmOverloads constructor(
    context: Context,
    private val observerManager: SeatGridViewObserverManager,
    private var seatInfo: SeatInfo?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val VOLUME_CAN_HEARD_MIN_LIMIT = 25
    }

    private lateinit var imgHead: AtomicAvatar
    private lateinit var emptyViewContainer: View
    private lateinit var ivEmptyView: ImageView
    private lateinit var textName: TextView
    private lateinit var ivMute: ImageView
    private lateinit var ivRoomOwner: ImageView
    private lateinit var voiceWaveView: VoiceWaveView
    private var isShowTalkBorder = false
    private val liveListStore = LiveListStore.shared()

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_seat_info_view, this, true)
        initViews()
        updateView(seatInfo)
        setOnClickListener { onItemViewClicked(it, seatInfo) }
    }

    private fun initViews() {
        imgHead = findViewById(R.id.iv_head)
        emptyViewContainer = findViewById(R.id.empty_seat_container)
        ivEmptyView = findViewById(R.id.iv_empty_seat)
        textName = findViewById(R.id.tv_name)
        ivMute = findViewById(R.id.iv_mute)
        voiceWaveView = findViewById(R.id.iv_talk_border)
        ivRoomOwner = findViewById(R.id.iv_room_owner)
    }

    fun updateSeatView(seatInfo: SeatInfo?) {
        seatInfo.let {
            this.seatInfo = seatInfo
            updateView(seatInfo)
        }
    }

    fun updateUserVolume(seatInfo: SeatInfo?, volume: Int) {
        seatInfo?.userInfo?.let { userInfo ->
            if (userInfo.userID.isEmpty()) {
                return
            }
            if (userInfo.microphoneStatus == DeviceStatus.OFF) {
                voiceWaveView.visibility = GONE
                ivMute.visibility = VISIBLE
                return
            }

            val shouldShowBorder = volume > VOLUME_CAN_HEARD_MIN_LIMIT
            if (shouldShowBorder == isShowTalkBorder) return

            voiceWaveView.visibility = if (shouldShowBorder) VISIBLE else GONE
            isShowTalkBorder = shouldShowBorder
        }
    }

    private fun updateView(seatInfo: SeatInfo?) {
        seatInfo?.let {
            if (seatInfo.userInfo.userID.isEmpty()) updateEmptySeatView(seatInfo)
            else updateSeatedView(seatInfo)
        }
    }

    private fun updateEmptySeatView(seatInfo: SeatInfo) {
        emptyViewContainer.visibility = VISIBLE
        ivEmptyView.setImageResource(if (seatInfo.isLocked) R.drawable.livekit_ic_lock else R.drawable.livekit_empty_seat)
        imgHead.visibility = GONE
        ivMute.visibility = GONE
        voiceWaveView.visibility = GONE
        isShowTalkBorder = false
        textName.visibility = VISIBLE
        textName.text = context.getString(R.string.common_seat_number, seatInfo.index + 1)
        ivRoomOwner.visibility = GONE
    }

    private fun updateSeatedView(seatInfo: SeatInfo) {
        emptyViewContainer.visibility = GONE
        textName.text = seatInfo.userInfo.userName.ifEmpty { seatInfo.userInfo.userID }
        updateUserAvatar(seatInfo.userInfo.avatarURL)
        updateUserRole(seatInfo)
        if (!seatInfo.userInfo.allowOpenMicrophone || seatInfo.userInfo.microphoneStatus == DeviceStatus.OFF) {
            ivMute.visibility = VISIBLE
            voiceWaveView.visibility = GONE
            isShowTalkBorder = false
        } else {
            ivMute.visibility = GONE
        }
    }

    private fun updateUserRole(seatInfo: SeatInfo) {
        val isOwner = seatInfo.userInfo.userID == liveListStore.liveState.currentLive.value.liveOwner.userID
        ivRoomOwner.visibility = if (isOwner) VISIBLE else GONE
    }

    private fun updateUserAvatar(avatarUrl: String?) {
        imgHead.visibility = VISIBLE
        imgHead.apply {
            setContent(
                AvatarContent.URL(
                    url = avatarUrl ?: "",
                    placeImage = R.drawable.livekit_ic_avatar
                )
            )
        }
    }

    private fun onItemViewClicked(view: View, seatInfo: SeatInfo?) {
        seatInfo?.let {
            observerManager.onSeatViewClicked(view, convertToSeatInfo(seatInfo))
        }
    }
}