package com.trtc.uikit.livekit.features.anchorview.view.cohost.panel

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.ui.setDebounceClickListener
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.CoHostListener
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AnchorCoHostOperateDialog(
    private val context: Context,
    private val anchorStore: AnchorStore
) : AtomicPopover(context) {

    companion object {
        private val LOGGER = LiveKitLogger.getFeaturesLogger("AnchorCoHostOperateDialog")
    }

    private var seatInfo: SeatInfo? = null
    private lateinit var imageHeadView: AtomicAvatar
    private lateinit var userIdText: TextView
    private lateinit var userNameText: TextView
    private lateinit var followContainer: View
    private lateinit var audioContainer: View
    private lateinit var ivAudio: ImageView
    private lateinit var tvAudio: TextView
    private lateinit var textUnfollow: TextView
    private lateinit var imageFollowIcon: ImageView
    private var subscribeStateJob: Job? = null
    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            dismiss()
        }
    }

    private val coHostListListener = object : CoHostListener() {
        override fun onCoHostUserLeft(userInfo: SeatUserInfo) {
            if (userInfo.liveID == seatInfo?.userInfo?.liveID) {
                dismiss()
            }
        }
    }

    init {
        initView()
    }

    fun init(userInfo: SeatInfo) {
        this.seatInfo = userInfo
        anchorStore.getUserStore().checkFollowUser(userInfo.userInfo.userID)
        updateView()
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
        val rootView = View.inflate(context, R.layout.livekit_anchor_co_host_operate_panel, null)
        setContent(rootView)
        bindViewId(rootView)
        initFollowButtonView(rootView)
    }

    private fun bindViewId(rootView: View) {
        userIdText = rootView.findViewById(R.id.user_id)
        userNameText = rootView.findViewById(R.id.user_name)
        imageHeadView = rootView.findViewById(R.id.iv_head)
        followContainer = rootView.findViewById(R.id.fl_follow_panel)
        ivAudio = rootView.findViewById(R.id.iv_audio)
        audioContainer = rootView.findViewById(R.id.audio_container)
        tvAudio = rootView.findViewById(R.id.tv_audio)

        textUnfollow = rootView.findViewById(R.id.tv_unfollow)
        imageFollowIcon = rootView.findViewById(R.id.iv_follow)

        audioContainer.setDebounceClickListener { clickMicrophoneButton() }
    }

    private fun updateView() {
        val currentUserInfo = seatInfo ?: return
        if (TextUtils.isEmpty(currentUserInfo.userInfo.userID)) {
            return
        }

        imageHeadView.setContent(
            AvatarContent.URL(
                currentUserInfo.userInfo.avatarURL,
                R.drawable.livekit_ic_avatar
            )
        )
        userNameText.text = currentUserInfo.userInfo.userName
        userIdText.text = context.getString(R.string.common_user_id, currentUserInfo.userInfo.userID)
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                onFollowingUserChanged()
            }
            launch {
                onSeatListChanged()
            }
        }

        LiveListStore.shared().addLiveListListener(liveListListener)
        CoHostStore.create(anchorStore.getState().liveInfo.liveID).addCoHostListener(coHostListListener)
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
        LiveListStore.shared().removeLiveListListener(liveListListener)
        CoHostStore.create(anchorStore.getState().liveInfo.liveID).removeCoHostListener(coHostListListener)
    }

    private fun initFollowButtonView(rootView: View) {
        rootView.findViewById<View>(R.id.fl_follow_panel).setDebounceClickListener {
            val currentUserInfo = seatInfo ?: return@setDebounceClickListener
            if (anchorStore.getUserState().followingUserList.value.contains(currentUserInfo.userInfo.userID)) {
                anchorStore.getUserStore().unfollowUser(currentUserInfo.userInfo.userID)
            } else {
                anchorStore.getUserStore().followUser(currentUserInfo.userInfo.userID)
            }
        }
    }

    private suspend fun onFollowingUserChanged() {
        anchorStore.getUserState().followingUserList.collect { followUsers ->
            seatInfo?.let { currentUserInfo ->
                if (followUsers.contains(currentUserInfo.userInfo.userID)) {
                    textUnfollow.visibility = GONE
                    imageFollowIcon.visibility = VISIBLE
                } else {
                    imageFollowIcon.visibility = GONE
                    textUnfollow.visibility = VISIBLE
                }
            }
        }
    }

    private fun clickMicrophoneButton() {
        seatInfo?.let {
            CoHostStore.create(anchorStore.getState().liveInfo.liveID).muteRemoteHostAudio(
                it.userInfo.liveID,
                it.userInfo.microphoneStatus == DeviceStatus.ON, object : CompletionHandler {
                    override fun onSuccess() {
                        dismiss()
                    }
                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                        dismiss()
                    }
                })
        }
    }

    private suspend fun onSeatListChanged() {
        LiveSeatStore.create(anchorStore.getState().liveInfo.liveID).liveSeatState.seatList.collect { seatList ->
            seatInfo?.let { info ->
                val realTimeSeatInfo = seatList.find { it.userInfo.liveID == info.userInfo.liveID }
                seatInfo = realTimeSeatInfo
                realTimeSeatInfo?.let {
                    if (realTimeSeatInfo.userInfo.microphoneStatus == DeviceStatus.ON) {
                        ivAudio.setImageResource(R.drawable.livekit_ic_disable_audio)
                        tvAudio.setText(R.string.common_mute_audio)
                    } else {
                        ivAudio.setImageResource(R.drawable.livekit_ic_unmute_audio)
                        tvAudio.setText(R.string.common_unmute_audio)
                    }
                }
            }
        }
    }
}