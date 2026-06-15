package com.trtc.uikit.livekit.voiceroom.view.seatmanager

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.common.displayName
import com.trtc.uikit.livekit.common.ui.setDebounceClickListener
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class UserManagerDialog(
    private val context: Context,
    private val voiceRoomManager: VoiceRoomManager?
) : AtomicPopover(context) {

    private val liveListStore = LiveListStore.shared()
    private val liveSeatStore: LiveSeatStore =
        LiveSeatStore.create(liveListStore.liveState.currentLive.value.liveID)

    private lateinit var imageHeadView: AtomicAvatar
    private lateinit var userIdText: TextView
    private lateinit var userNameText: TextView
    private lateinit var ivMute: ImageView
    private lateinit var tvMute: TextView
    private lateinit var textUnfollow: TextView
    private lateinit var imageFollowIcon: ImageView
    private lateinit var userControllerView: View

    private var seatIndex = -1
    private var subscribeStateJob: Job? = null

    init {
        initView()
    }

    fun setSeatIndex(seatIndex: Int) {
        if (this@UserManagerDialog.seatIndex == seatIndex) {
            return
        }
        this.seatIndex = seatIndex
        if (seatIndex == -1) {
            return
        }
        getSeatInfo()?.let {
            updateSeatInfoView()
            voiceRoomManager?.imStore?.checkFollowUser(it.userInfo.userID)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun initView() {
        val rootView = View.inflate(context, R.layout.livekit_user_manager_panel, null)
        setContent(rootView)
        bindViewId(rootView)
        initFollowButtonView(rootView)
        rootView.findViewById<View>(R.id.hand_up).setOnClickListener { hangup() }
        rootView.findViewById<View>(R.id.mute_container).setOnClickListener { muteSeatAudio() }
        userControllerView.visibility = if (TextUtils.equals(
                liveListStore.liveState.currentLive.value.liveOwner.userID,
                TUIRoomEngine.getSelfInfo().userId
            )
        ) VISIBLE else GONE
    }

    private fun bindViewId(rootView: View) {
        userControllerView = rootView.findViewById(R.id.user_controller_container)
        userIdText = rootView.findViewById(R.id.user_id)
        userNameText = rootView.findViewById(R.id.user_name)
        imageHeadView = rootView.findViewById(R.id.iv_head)
        ivMute = rootView.findViewById(R.id.iv_mute)
        tvMute = rootView.findViewById(R.id.tv_mute)
        textUnfollow = rootView.findViewById(R.id.tv_unfollow)
        imageFollowIcon = rootView.findViewById(R.id.iv_follow)
    }

    private fun updateSeatInfoView() {
        val seatInfo = getSeatInfo() ?: return
        if (seatInfo.userInfo.userID.isEmpty() == true) {
            return
        }
        imageHeadView.setContent(AvatarContent.URL(seatInfo.userInfo.avatarURL, R.drawable.livekit_ic_avatar))

        userNameText.text = seatInfo.userInfo.displayName
        userIdText.text = context.getString(R.string.common_user_id, seatInfo.userInfo.userID)
        updateAudioLockState(seatInfo.userInfo.allowOpenMicrophone == false)
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                voiceRoomManager?.imStore?.imState?.followingUserList?.collect { followingList ->
                    onFollowingUserChanged(followingList)
                }
            }
            launch {
                liveSeatStore.liveSeatState.seatList
                    .map { seatList ->
                        seatList.find { it.userInfo.userID == getSeatInfo()?.userInfo?.userID }?.userInfo?.allowOpenMicrophone
                    }.distinctUntilChanged()
                    .collect {
                        if (it == null) return@collect
                        updateAudioLockState(!it)
                    }
            }
            launch {
                liveSeatStore.liveSeatState.seatList.map {
                    it.find { it.index == seatIndex }?.userInfo?.userID
                }.distinctUntilChanged().collect {
                    if (it.isNullOrBlank()) {
                        dismiss()
                    }
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun hangup() {
        kickUserOffSeatByAdmin()
        dismiss()
    }

    private fun initFollowButtonView(rootView: View) {
        rootView.findViewById<View>(R.id.fl_follow_panel).setDebounceClickListener {
            val seatUserId = getSeatInfo()?.userInfo?.userID
            if (voiceRoomManager?.imStore?.imState?.followingUserList?.value?.contains(
                    seatUserId
                ) == true
            ) {
                voiceRoomManager.imStore.unfollow(seatUserId ?: "")
            } else {
                voiceRoomManager?.imStore?.follow(seatUserId ?: "")
            }
        }
    }

    private fun onFollowingUserChanged(followUsers: Set<String>) {
        if (followUsers.contains(getSeatInfo()?.userInfo?.userID)) {
            textUnfollow.visibility = GONE
            imageFollowIcon.visibility = VISIBLE
        } else {
            imageFollowIcon.visibility = GONE
            textUnfollow.visibility = VISIBLE
        }
    }

    private fun muteSeatAudio() {
        val seatInfo = getSeatInfo() ?: return
        if (!seatInfo.userInfo.allowOpenMicrophone) {
            liveSeatStore.openRemoteMicrophone(
                seatInfo.userInfo.userID,
                DeviceControlPolicy.UNLOCK_ONLY,
                null
            )
        } else {
            liveSeatStore.closeRemoteMicrophone(
                getSeatInfo()?.userInfo?.userID,
                null
            )
        }
    }

    private fun updateAudioLockState(isAudioLocked: Boolean) {
        if (isAudioLocked) {
            ivMute.setImageResource(R.drawable.livekit_ic_unmute_microphone)
            tvMute.setText(R.string.common_voiceroom_unmuted_seat)
        } else {
            ivMute.setImageResource(R.drawable.livekit_ic_mute_microphone)
            tvMute.setText(R.string.common_voiceroom_mute_seat)
        }
    }

    private fun kickUserOffSeatByAdmin() {
        val seatInfo = getSeatInfo() ?: return
        liveSeatStore.kickUserOutOfSeat(seatInfo.userInfo.userID, completionHandler {
            onError { code, desc ->
                LOGGER.error("kickUserOutOfSeat failed,error:$code,message:$desc")
                ErrorLocalized.onError(code)
            }
        })
    }

    private fun getSeatInfo(): SeatInfo? {
        return liveSeatStore.liveSeatState.seatList.value.find { it.index == seatIndex }
    }

    companion object {
        private val LOGGER = LiveKitLogger.getVoiceRoomLogger("UserManagerDialog")
    }
}
