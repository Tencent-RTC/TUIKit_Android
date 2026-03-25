package com.trtc.uikit.livekit.voiceroom.interaction.common

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.voiceroom.interaction.battle.BattleInviteAdapter
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CoHostViewManagerPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private lateinit var liveSeatStore: LiveSeatStore
    private lateinit var liveListStore: LiveListStore
    private lateinit var seatInfo: SeatInfo
    private lateinit var imageAvatar: AtomicAvatar
    private lateinit var textName: TextView
    private lateinit var textUserId: TextView
    private lateinit var followContainer: FrameLayout
    private lateinit var buttonFollowAction: Button
    private lateinit var imageFollowedCheck: ImageView
    private lateinit var imageMuteAudio: ImageView
    private lateinit var imageDisableAudio: ImageView
    private lateinit var textMicrophone: TextView
    private lateinit var textDisableAudio: TextView
    private lateinit var imageKickOutSeat: ImageView
    private lateinit var textKickOutSeat: TextView
    private lateinit var layoutMute: LinearLayout
    private lateinit var layoutDisableAudio: LinearLayout
    private lateinit var layoutKickOutSeat: LinearLayout
    private lateinit var layoutEndLink: LinearLayout
    private var listener: BattleInviteAdapter.OnInviteButtonClickListener? = null
    private var subscribeStateJob: Job? = null
    private var voiceRoomManager: VoiceRoomManager? = null

    init {
        initView(context)
    }

    fun init(seatInfo: SeatInfo, voiceRoomManager: VoiceRoomManager?) {
        this.seatInfo = seatInfo
        this.voiceRoomManager = voiceRoomManager
        liveSeatStore = LiveSeatStore.create(seatInfo.userInfo.liveID)
        liveListStore = LiveListStore.shared()
        initUserAvatar()
        initUserName()
        initUserIdView()
        initFollowView()
        initDisableAudio()
        initMicrophoneStatus()
        initKickOutSeatView()
        initEndLinkView()
    }

    fun setOnInviteButtonClickListener(listener: BattleInviteAdapter.OnInviteButtonClickListener) {
        this.listener = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            voiceRoomManager?.imStore?.imState?.followingUserList?.collect { followingList ->
                refreshFollowButtonUI(followingList)
            }
        }
        if (!TextUtils.isEmpty(seatInfo.userInfo.userID) && !checkIsSelf(seatInfo.userInfo.userID)) {
            voiceRoomManager?.imStore?.checkFollowUser(seatInfo.userInfo.userID)
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun initView(context: Context) {
        LayoutInflater.from(context).inflate(R.layout.livekit_voiceroom_co_host_view_manager_panel, this, true)
        bindViewId()
    }

    private fun bindViewId() {
        imageAvatar = findViewById(R.id.iv_avatar)
        textName = findViewById(R.id.tv_name)
        textUserId = findViewById(R.id.tv_user_id)
        followContainer = findViewById(R.id.fl_follow_container)
        buttonFollowAction = findViewById(R.id.btn_follow_action)
        imageFollowedCheck = findViewById(R.id.iv_followed_check)
        imageKickOutSeat = findViewById(R.id.iv_kick_out_seat)
        textKickOutSeat = findViewById(R.id.tv_kick_out_seat)
        layoutKickOutSeat = findViewById(R.id.ll_kick_out_seat)
        imageMuteAudio = findViewById(R.id.iv_mute)
        textMicrophone = findViewById(R.id.tv_mute)
        layoutMute = findViewById(R.id.ll_mute)
        imageDisableAudio = findViewById(R.id.iv_disable_audio)
        textDisableAudio = findViewById(R.id.tv_disable_audio)
        layoutDisableAudio = findViewById(R.id.ll_disable_audio)
        layoutEndLink = findViewById(R.id.ll_end_link)
    }

    private fun initUserAvatar() {
        imageAvatar.setContent(
            AvatarContent.URL(
                seatInfo.userInfo.avatarURL,
                R.drawable.livekit_ic_avatar
            )
        )
    }

    private fun initDisableAudio() {
        val liveId = liveListStore.liveState.currentLive.value.liveID
        val seatUserId = seatInfo.userInfo.userID
        val isAudienceInMyRoom = TextUtils.equals(liveId, seatInfo.userInfo.liveID)
                && seatInfo.userInfo.userID != liveListStore.liveState.currentLive.value.liveOwner.userID
        val isOwner = TextUtils.equals(liveListStore.liveState.currentLive.value.liveOwner.userID,
            TUIRoomEngine.getSelfInfo().userId)

        if (!isOwner || !isAudienceInMyRoom) {
            layoutDisableAudio.visibility = View.GONE
            return
        }

        layoutDisableAudio.visibility = View.VISIBLE
        updateDisableAudioUI(seatInfo.userInfo.allowOpenMicrophone)
        layoutDisableAudio.setOnClickListener {
            val isCurrentlyAllowed = seatInfo.userInfo.allowOpenMicrophone
            val completionHandler = object : CompletionHandler {
                override fun onSuccess() {
                    updateDisableAudioUI(!isCurrentlyAllowed)
                }

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.onError(code)
                }
            }

            if (isCurrentlyAllowed) {
                liveSeatStore.closeRemoteMicrophone(seatUserId, completionHandler)
            } else {
                liveSeatStore.openRemoteMicrophone(seatUserId, DeviceControlPolicy.UNLOCK_ONLY, completionHandler)
            }
            listener?.onInviteClicked()
        }
    }

    private fun updateDisableAudioUI(isAllowedToOpenMicrophone: Boolean) {
        if (isAllowedToOpenMicrophone) {
            imageDisableAudio.setImageResource(R.drawable.livekit_open_microphone)
            textDisableAudio.setText(R.string.common_disable_audio)
        } else {
            imageDisableAudio.setImageResource(R.drawable.livekit_ic_disable_audio)
            textDisableAudio.setText(R.string.common_enable_audio)
        }
    }

    private fun initUserName() {
        textName.text = seatInfo.userInfo.userName.ifEmpty { seatInfo.userInfo.userID }
    }

    private fun initUserIdView() {
        textUserId.text = "ID: ${seatInfo.userInfo.userID}"
    }

    private fun initFollowView() {
        val seatUserId = seatInfo.userInfo.userID
        if (checkIsSelf(seatUserId)) {
            followContainer.visibility = View.GONE
            return
        }
        followContainer.visibility = View.VISIBLE
        refreshFollowButtonUI(voiceRoomManager?.imStore?.imState?.followingUserList?.value)
        followContainer.setOnClickListener {
            if (voiceRoomManager?.imStore?.imState?.followingUserList?.value?.contains(seatUserId) == true) {
                voiceRoomManager?.imStore?.unfollow(seatUserId)
            } else {
                voiceRoomManager?.imStore?.follow(seatUserId)
            }
        }
    }

    private fun refreshFollowButtonUI(followingList: Set<String>?) {
        val seatUserId = seatInfo.userInfo.userID
        if (followingList?.contains(seatUserId) == true) {
            imageFollowedCheck.visibility = View.VISIBLE
            buttonFollowAction.visibility = View.GONE
        } else {
            imageFollowedCheck.visibility = View.GONE
            buttonFollowAction.visibility = View.VISIBLE
        }
    }

    private fun initMicrophoneStatus() {
        val seatUserId = seatInfo.userInfo.userID
        if (!checkIsSelf(seatUserId)) {
            layoutMute.visibility = View.GONE
            return
        }
        layoutMute.visibility = View.VISIBLE
        updateMicrophoneUI(seatInfo.userInfo.microphoneStatus)
        layoutMute.setOnClickListener {
            val currentStatus = seatInfo.userInfo.microphoneStatus
            if (currentStatus == DeviceStatus.ON) {
                liveSeatStore.muteMicrophone()
                updateMicrophoneUI(DeviceStatus.OFF)
            } else {
                liveSeatStore.unmuteMicrophone(null)
                updateMicrophoneUI(DeviceStatus.ON)
            }
            listener?.onInviteClicked()
        }
    }

    private fun updateMicrophoneUI(status: DeviceStatus) {
        if (status == DeviceStatus.ON) {
            imageMuteAudio.setImageResource(R.drawable.livekit_open_microphone)
            textMicrophone.setText(R.string.common_mute_audio)
        } else {
            imageMuteAudio.setImageResource(R.drawable.livekit_ic_mute_audio)
            textMicrophone.setText(R.string.common_unmute_audio)
        }
    }

    private fun initKickOutSeatView() {
        val liveId = liveListStore.liveState.currentLive.value.liveID
        val isOwner = TextUtils.equals(liveListStore.liveState.currentLive.value.liveOwner.userID,
            TUIRoomEngine.getSelfInfo().userId)
        val isMyRoomAudience = TextUtils.equals(liveId, seatInfo.userInfo.liveID)
                && seatInfo.userInfo.userID != liveListStore.liveState.currentLive.value.liveOwner.userID
        layoutKickOutSeat.visibility = if (isMyRoomAudience && isOwner) VISIBLE else GONE
        layoutKickOutSeat.setOnClickListener {
            liveSeatStore.kickUserOutOfSeat(seatInfo.userInfo.userID, object : CompletionHandler {
                override fun onSuccess() {}
                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.onError(code)
                }
            })
            listener?.onInviteClicked()
        }
    }

    private fun initEndLinkView() {
        val seatUserId = seatInfo.userInfo.userID
        val isSelf = checkIsSelf(seatUserId)
        val liveId = liveListStore.liveState.currentLive.value.liveID
        val isAudience = TextUtils.equals(liveId, seatInfo.userInfo.liveID)
                && seatInfo.userInfo.userID != liveListStore.liveState.currentLive.value.liveOwner.userID
        layoutEndLink.visibility = if (isSelf && isAudience) VISIBLE else GONE
        layoutEndLink.setOnClickListener {
            liveSeatStore.leaveSeat(object : CompletionHandler {
                override fun onSuccess() {
                }

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.onError(code)
                }
            })
            listener?.onInviteClicked()
        }
    }

    private fun checkIsSelf(userId: String): Boolean {
        return TextUtils.equals(userId, TUIRoomEngine.getSelfInfo().userId)
    }
}