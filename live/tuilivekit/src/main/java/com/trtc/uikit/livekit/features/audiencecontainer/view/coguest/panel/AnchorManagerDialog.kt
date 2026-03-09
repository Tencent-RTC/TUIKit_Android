package com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.panel

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import com.trtc.tuikit.common.system.ContextProvider
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceStore
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.GuestListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AnchorManagerDialog(
    context: Context,
    private val audienceStore: AudienceStore,
) : AtomicPopover(context) {

    private var seatUserInfo: LiveUserInfo? = null
    private var confirmDialog: AtomicAlertDialog? = null
    private var subscribeStateJob: Job? = null

    private lateinit var imageHeadView: AtomicAvatar
    private lateinit var userIdText: TextView
    private lateinit var userNameText: TextView
    private lateinit var flipCameraContainer: View
    private lateinit var followContainer: View
    private lateinit var handUpContainer: View
    private lateinit var audioContainer: View
    private lateinit var videoContainer: View
    private lateinit var ivAudio: ImageView
    private lateinit var tvAudio: TextView
    private lateinit var ivVideo: ImageView
    private lateinit var tvVideo: TextView
    private lateinit var textUnfollow: TextView
    private lateinit var imageFollowIcon: ImageView

    init {
        initView()
    }

    fun init(seatInfo: LiveUserInfo) {
        seatUserInfo = seatInfo
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
        val rootView = View.inflate(context, R.layout.livekit_anchor_manager_panel, null)
        setContent(rootView)
        bindViewId(rootView)
        initFollowButtonView(rootView)
    }

    private fun bindViewId(rootView: View) {
        userIdText = rootView.findViewById(R.id.user_id)
        userNameText = rootView.findViewById(R.id.user_name)
        imageHeadView = rootView.findViewById(R.id.iv_head)
        handUpContainer = rootView.findViewById(R.id.hand_up_container)
        flipCameraContainer = rootView.findViewById(R.id.flip_camera_container)
        followContainer = rootView.findViewById(R.id.fl_follow_panel)
        ivAudio = rootView.findViewById(R.id.iv_audio)
        audioContainer = rootView.findViewById(R.id.audio_container)
        tvAudio = rootView.findViewById(R.id.tv_audio)
        videoContainer = rootView.findViewById(R.id.video_container)
        ivVideo = rootView.findViewById(R.id.iv_video)
        tvVideo = rootView.findViewById(R.id.tv_video)
        textUnfollow = rootView.findViewById(R.id.tv_unfollow)
        imageFollowIcon = rootView.findViewById(R.id.iv_follow)

        handUpContainer.setOnClickListener { onHangupButtonClicked() }
        flipCameraContainer.setOnClickListener { onSwitchCameraButtonClicked() }
        audioContainer.setOnClickListener { onMicrophoneButtonClicked() }
        videoContainer.setOnClickListener { onCameraButtonClicked() }
    }

    private fun updateView() {
        if (seatUserInfo == null) {
            return
        }
        if (TextUtils.isEmpty(seatUserInfo!!.userID)) {
            return
        }
        val avatarUrl = seatUserInfo!!.avatarURL
        imageHeadView.setContent(AvatarContent.URL(avatarUrl, R.drawable.livekit_ic_avatar))
        userNameText.text = seatUserInfo!!.userName
        userIdText.text = context.getString(R.string.common_user_id, seatUserInfo!!.userID)
        updateMediaDeviceButton()
    }

    private fun updateMediaDeviceButton() {
        if (isAdmin()) {
            updateAdminDeviceButton()
        } else {
            updateGeneralUserDeviceButton()
        }
    }

    private fun updateAdminDeviceButton() {
        if (isSelfUser()) {
            flipCameraContainer.visibility = VISIBLE
            handUpContainer.visibility = GONE
            followContainer.visibility = GONE
        } else {
            flipCameraContainer.visibility = GONE
            handUpContainer.visibility = VISIBLE
            followContainer.visibility = VISIBLE
        }
    }

    private fun updateGeneralUserDeviceButton() {
        if (isSelfUser()) {
            flipCameraContainer.visibility = VISIBLE
            handUpContainer.visibility = VISIBLE
            followContainer.visibility = GONE
            updateCameraButton(
                audienceStore.getLiveSeatState().seatList.value.find { it.userInfo.userID == seatUserInfo?.userID }?.userInfo?.allowOpenCamera == false
            )
            updateMicrophoneButton(
                audienceStore.getLiveSeatState().seatList.value.find { it.userInfo.userID == seatUserInfo?.userID }?.userInfo?.allowOpenMicrophone == false
            )
        }
    }

    private fun isSelfUser(): Boolean {
        if (seatUserInfo == null) {
            return false
        }
        if (TextUtils.isEmpty(seatUserInfo!!.userID)) {
            return false
        }
        return seatUserInfo!!.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
    }

    private fun isAdmin(): Boolean {
        val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        if (TextUtils.isEmpty(selfUserId)) {
            return false
        }
        return selfUserId == audienceStore.getLiveListState().currentLive.value.liveOwner.userID
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                audienceStore.getLiveSeatState().seatList
                    .map { seatList -> seatList.find { it.userInfo.userID == seatUserInfo?.userID }?.userInfo?.allowOpenMicrophone }
                    .distinctUntilChanged()
                    .collect { allowOpenMicrophone ->
                        if (allowOpenMicrophone == null) return@collect
                        updateMicrophoneButton(!allowOpenMicrophone)
                    }
            }
            launch {
                audienceStore.getLiveSeatState().seatList
                    .map { seatList -> seatList.find { it.userInfo.userID == seatUserInfo?.userID }?.userInfo?.allowOpenCamera }
                    .distinctUntilChanged()
                    .collect { allowOpenCamera ->
                        if (allowOpenCamera == null) return@collect
                        updateCameraButton(!allowOpenCamera)
                    }
            }
            launch {
                audienceStore.getIMState().followingUserList.collect {
                    onFollowingUserChanged(it)
                }
            }
            launch {
                audienceStore.getCoGuestState().connected.collect {
                    onConnectUserListChanged(it)
                }
            }
        }
        audienceStore.getLiveListStore().addLiveListListener(liveLisListener)
        audienceStore.getCoGuestStore().addGuestListener(coGuestListener)
        audienceStore.getLiveAudienceStore().addLiveAudienceListener(liveAudienceListener)
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
        audienceStore.getLiveListStore().removeLiveListListener(liveLisListener)
        audienceStore.getCoGuestStore().removeGuestListener(coGuestListener)
        audienceStore.getLiveAudienceStore().removeLiveAudienceListener(liveAudienceListener)
    }

    private fun initFollowButtonView(rootView: View) {
        rootView.findViewById<View>(R.id.fl_follow_panel).setOnClickListener {
            if (seatUserInfo == null) {
                return@setOnClickListener
            }
            if (audienceStore.getIMState().followingUserList.value.contains(seatUserInfo!!.userID) == true) {
                audienceStore.getIMStore().unfollowUser(seatUserInfo!!.userID)
            } else {
                audienceStore.getIMStore().followUser(seatUserInfo!!.userID)
            }
        }
    }

    private fun onFollowingUserChanged(followUsers: Set<String>) {
        if (seatUserInfo == null) {
            return
        }
        if (followUsers.contains(seatUserInfo!!.userID)) {
            textUnfollow.visibility = GONE
            imageFollowIcon.visibility = VISIBLE
        } else {
            imageFollowIcon.visibility = GONE
            textUnfollow.visibility = VISIBLE
        }
    }

    private fun onHangupButtonClicked() {
        if (seatUserInfo == null) {
            return
        }

        if (confirmDialog == null) {
            confirmDialog = AtomicAlertDialog(context)
        }

        if (isAdmin()) {
            confirmDialog?.init {
                title = context.getString(R.string.common_disconnect_tips)
                confirmButton(context.getString(R.string.common_disconnection), onClick = {
                    audienceStore.getLiveSeatStore().kickUserOutOfSeat(
                        seatUserInfo!!.userID,
                        completionHandler {
                            onError { code, _ ->
                                ErrorLocalized.onError(code)
                            }
                        })
                    dismiss()
                }, type = AtomicAlertDialog.TextColorPreset.RED)
                cancelButton(
                    context.getString(R.string.common_cancel),
                    type = AtomicAlertDialog.TextColorPreset.PRIMARY
                )
            }
            confirmDialog!!.show()
            return
        }

        if (isSelfUser()) {
            confirmDialog?.init {
                title = context.getString(R.string.common_terminate_room_connection_message)
                confirmButton(context.getString(R.string.common_disconnection), onClick = {
                    audienceStore.getCoGuestStore().disconnect(null)
                    audienceStore.getViewStore().updateTakeSeatState(false)
                    dismiss()
                }, type = AtomicAlertDialog.TextColorPreset.RED)
                cancelButton(
                    context.getString(R.string.common_cancel),
                    type = AtomicAlertDialog.TextColorPreset.PRIMARY
                )
            }
            confirmDialog!!.show()
        }
    }

    private fun onMicrophoneButtonClicked() {
        if (seatUserInfo == null) {
            return
        }

        if (isSelfUser()) {
            val isSelfMicrophoneOpened = audienceStore.getLiveSeatState().seatList.value.find {
                it.userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
            }?.userInfo?.microphoneStatus == DeviceStatus.ON
            if (isSelfMicrophoneOpened) {
                audienceStore.getLiveSeatStore().muteMicrophone()
            } else {
                unMuteMicrophone()
            }
            dismiss()
            return
        }

        if (isAdmin()) {
            audienceStore.getLiveSeatStore().closeRemoteMicrophone(seatUserInfo?.userID, null)
            dismiss()
        }
    }

    private fun onCameraButtonClicked() {
        if (seatUserInfo == null) {
            return
        }

        if (isSelfUser()) {
            val isSelfCameraOpened = audienceStore.getLiveSeatState().seatList.value.find {
                it.userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
            }?.userInfo?.cameraStatus == DeviceStatus.ON
            if (isSelfCameraOpened) {
                audienceStore.getDeviceStore().closeLocalCamera()
                flipCameraContainer.visibility = GONE
            } else {
                startCamera()
            }
            dismiss()
            return
        }

        if (isAdmin()) {
            audienceStore.getLiveSeatStore().closeRemoteCamera(seatUserInfo?.userID, null)
            dismiss()
        }
    }

    private fun onSwitchCameraButtonClicked() {
        val isFront = audienceStore.getDeviceStore().deviceState.isFrontCamera.value
        audienceStore.getDeviceStore().switchCamera(!isFront)
        dismiss()
    }

    private fun unMuteMicrophone() {
        audienceStore.getLiveSeatStore().unmuteMicrophone(completionHandler {
            onError { code, _ ->
                ErrorLocalized.onError(code)
            }
        })
    }

    private fun startCamera() {
        val isFrontCamera = audienceStore.getDeviceStore().deviceState.isFrontCamera.value
        PermissionRequest.requestCameraPermissions(
            ContextProvider.getApplicationContext(),
            object : PermissionCallback() {
                override fun onRequesting() {
                    LOGGER.info("requestCameraPermissions:[onRequesting]")
                }

                override fun onGranted() {
                    LOGGER.info("requestCameraPermissions:[onGranted]")
                    this@AnchorManagerDialog.audienceStore.getDeviceStore()
                        .openLocalCamera(isFrontCamera, completionHandler {
                            onError { code, _ ->
                                ErrorLocalized.onError(code)
                            }
                        })
                }
            })
    }

    private fun updateMicrophoneButton(isAudioLocked: Boolean) {
        if (!isSelfUser()) {
            if (isAdmin()) {
                audioContainer.isEnabled = true
                ivAudio.alpha = BUTTON_ENABLE_ALPHA
                ivAudio.setImageResource(
                    if (isAudioLocked == true) R.drawable.livekit_ic_disable_audio
                    else R.drawable.livekit_ic_unmute_audio
                )
                tvAudio.setText(
                    if (isAudioLocked == true) R.string.common_enable_audio
                    else R.string.common_disable_audio
                )
            }
            return
        }

        if (isAudioLocked == true) {
            audioContainer.isEnabled = false
            ivAudio.alpha = BUTTON_DISABLE_ALPHA
            ivAudio.setImageResource(R.drawable.livekit_ic_mute_audio)
            tvAudio.setText(R.string.common_unmute_audio)
            return
        }

        audioContainer.isEnabled = true
        ivAudio.alpha = BUTTON_ENABLE_ALPHA
        val isMicrophoneMuted =
            audienceStore.getLiveSeatState().seatList.value.find {
                it.userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
            }?.userInfo?.microphoneStatus == DeviceStatus.OFF
        if (isMicrophoneMuted) {
            ivAudio.setImageResource(R.drawable.livekit_ic_mute_audio)
            tvAudio.setText(R.string.common_unmute_audio)
        } else {
            ivAudio.setImageResource(R.drawable.livekit_ic_unmute_audio)
            tvAudio.setText(R.string.common_mute_audio)
        }
    }

    private fun updateCameraButton(isVideoLocked: Boolean) {
        if (!isSelfUser()) {
            if (isAdmin()) {
                videoContainer.isEnabled = true
                ivVideo.alpha = BUTTON_ENABLE_ALPHA
                ivVideo.setImageResource(
                    if (isVideoLocked == true) R.drawable.livekit_ic_disable_video
                    else R.drawable.livekit_ic_start_video
                )
                tvVideo.setText(
                    if (isVideoLocked == true) R.string.common_enable_video
                    else R.string.common_disable_video
                )
            }
            return
        }


        if (isVideoLocked == true) {
            videoContainer.isEnabled = false
            ivVideo.alpha = BUTTON_DISABLE_ALPHA
            ivVideo.setImageResource(R.drawable.livekit_ic_stop_video)
            tvVideo.setText(R.string.common_start_video)
            flipCameraContainer.visibility = GONE
            return
        }

        videoContainer.isEnabled = true
        ivVideo.alpha = BUTTON_ENABLE_ALPHA
        val isCameraOpened =
            audienceStore.getLiveSeatState().seatList.value.find {
                it.userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
            }?.userInfo?.cameraStatus == DeviceStatus.ON
        if (isCameraOpened) {
            ivVideo.setImageResource(R.drawable.livekit_ic_start_video)
            tvVideo.setText(R.string.common_stop_video)
            flipCameraContainer.visibility = VISIBLE
        } else {
            ivVideo.setImageResource(R.drawable.livekit_ic_stop_video)
            tvVideo.setText(R.string.common_start_video)
            flipCameraContainer.visibility = GONE
        }
    }

    private fun onConnectUserListChanged(connectedUserList: List<SeatUserInfo>) {
        if (seatUserInfo == null) {
            return
        }
        if (TextUtils.isEmpty(seatUserInfo!!.userID)) {
            return
        }
        var isConnected = false
        for (userInfo in connectedUserList) {
            if (this.seatUserInfo!!.userID == userInfo.userID) {
                isConnected = true
                break
            }
        }
        if (!isConnected) {
            dismiss()
            confirmDialog?.dismiss()
        }
    }

    private val liveLisListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            dismiss()
            confirmDialog?.dismiss()
        }
    }

    private val liveAudienceListener = object : LiveAudienceListener() {
        override fun onAudienceLeft(audience: LiveUserInfo) {
            if (this@AnchorManagerDialog.seatUserInfo == null) {
                return
            }
            if (TextUtils.isEmpty(this@AnchorManagerDialog.seatUserInfo!!.userID)) {
                return
            }
            if (audience.userID == this@AnchorManagerDialog.seatUserInfo!!.userID) {
                dismiss()
                confirmDialog?.dismiss()
            }
        }
    }

    private val coGuestListener = object : GuestListener() {
        override fun onKickedOffSeat(seatIndex: Int, hostUser: LiveUserInfo) {
            dismiss()
            confirmDialog?.dismiss()
        }
    }

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("AnchorManagerDialog")
        private const val BUTTON_DISABLE_ALPHA = 0.24f
        private const val BUTTON_ENABLE_ALPHA = 1.0f
    }
}
