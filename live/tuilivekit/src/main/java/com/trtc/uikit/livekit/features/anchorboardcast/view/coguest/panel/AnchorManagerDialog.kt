package com.trtc.uikit.livekit.features.anchorboardcast.view.coguest.panel

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import com.trtc.tuikit.common.system.ContextProvider
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.features.anchorboardcast.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorboardcast.store.MediaStore
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AnchorManagerDialog(
    private val context: Context,
    private val anchorManager: AnchorStore
) : AtomicPopover(context) {

    companion object {
        private val LOGGER = LiveKitLogger.getFeaturesLogger("AnchorManagerDialog")
        private const val BUTTON_DISABLE_ALPHA = 0.24f
        private const val BUTTON_ENABLE_ALPHA = 1.0f
    }

    private var seatInfo: SeatInfo? = null
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
    private var confirmDialog: AtomicAlertDialog? = null
    private var subscribeStateJob: Job? = null

    init {
        initView()
    }

    fun init(userInfo: SeatInfo) {
        this.seatInfo = userInfo
        anchorManager.getUserStore().checkFollowUser(userInfo.userInfo.userID)
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

        handUpContainer.setOnClickListener { clickHangupButton() }
        flipCameraContainer.setOnClickListener { clickSwitchCameraButton() }
        audioContainer.setOnClickListener { clickMicrophoneButton() }
        videoContainer.setOnClickListener { clickCameraButton() }
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
        updateMediaDeviceButton()
    }

    private fun updateMediaDeviceButton() {
        if (isSelfUser()) {
            flipCameraContainer.visibility = VISIBLE
            handUpContainer.visibility = GONE
            followContainer.visibility = GONE
            videoContainer.visibility = GONE
        } else {
            flipCameraContainer.visibility = GONE
            handUpContainer.visibility = VISIBLE
            followContainer.visibility = VISIBLE
            videoContainer.visibility = VISIBLE
        }
    }

    private fun isSelfUser(): Boolean {
        val currentUserInfo = seatInfo ?: return false
        if (TextUtils.isEmpty(currentUserInfo.userInfo.userID)) {
            return false
        }
        return currentUserInfo.userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
    }

    private fun isAdmin(): Boolean {
        return LoginStore.shared.loginState.loginUserInfo.value?.userID == LiveListStore.shared().liveState.currentLive.value.liveOwner.userID
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                onCameraStatusChanged()
            }
            launch {
                onLockAudioUserListChanged()
            }
            launch {
                onFollowingUserChanged()
            }
            launch {
                onMicrophoneStatusChanged()
            }
            launch {
                onCameraStatusChanged()
            }
            launch {
                onConnectUserListChanged()
            }
        }
        TUIRoomEngine.sharedInstance().addObserver(tuiRoomObserver)
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
        TUIRoomEngine.sharedInstance().removeObserver(tuiRoomObserver)
    }

    private fun initFollowButtonView(rootView: View) {
        rootView.findViewById<View>(R.id.fl_follow_panel).setOnClickListener {
            val currentUserInfo = seatInfo ?: return@setOnClickListener
            if (anchorManager.getUserState().followingUserList.value.contains(currentUserInfo.userInfo.userID)) {
                anchorManager.getUserStore().unfollowUser(currentUserInfo.userInfo.userID)
            } else {
                anchorManager.getUserStore().followUser(currentUserInfo.userInfo.userID)
            }
        }
    }

    private suspend fun onFollowingUserChanged() {
        anchorManager.getUserState().followingUserList.collect { followUsers ->
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

    private fun clickHangupButton() {
        val currentUserInfo = seatInfo ?: return

        if (confirmDialog == null) {
            confirmDialog = AtomicAlertDialog(context)
        }

        if (isAdmin()) {
            confirmDialog?.init {
                title = context.getString(R.string.common_disconnect_tips)
                confirmButton(context.getString(R.string.common_disconnection), onClick = {
                    LiveSeatStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
                        .kickUserOutOfSeat(
                            currentUserInfo.userInfo.userID,
                            completionHandler {
                                onError { code, desc ->
                                    LOGGER.error("disconnectUser failed:code:$code,desc:$desc")
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
            confirmDialog?.show()
            return
        }

        if (isSelfUser()) {
            confirmDialog?.init {
                title = context.getString(R.string.common_terminate_room_connection_message)
                confirmButton(context.getString(R.string.common_disconnection), onClick = {
                    CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
                        .disconnect(null)
                    dismiss()
                },type = AtomicAlertDialog.TextColorPreset.RED)
                cancelButton(context.getString(R.string.common_cancel),type = AtomicAlertDialog.TextColorPreset.PRIMARY)
            }
            confirmDialog?.show()
        }
    }

    private fun clickMicrophoneButton() {
        seatInfo?.let {
            if (isSelfUser()) {
                if (it.userInfo.microphoneStatus == DeviceStatus.ON) {
                    LiveSeatStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).muteMicrophone()
                } else {
                    LiveSeatStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
                        .unmuteMicrophone(object : CompletionHandler {
                            override fun onSuccess() {}

                            override fun onFailure(code: Int, desc: String) {
                                LOGGER.error("unMuteMicrophone failed:code:$code,desc:$desc")
                                ErrorLocalized.onError(code)
                            }

                        })
                }
                dismiss()
                return
            }

            if (isAdmin()) {
                val isAudioLocked = anchorManager.getState().lockAudioUserList.value.contains(it.userInfo.userID)
                anchorManager.getMediaStore().disableUserMediaDevice(
                    it.userInfo.userID,
                    MediaStore.MediaDevice.MICROPHONE,
                    isAudioLocked
                )
                dismiss()
            }
        }
    }

    private fun clickCameraButton() {
        val currentUserInfo = seatInfo ?: return

        if (isSelfUser()) {
            if (DeviceStore.shared().deviceState.cameraStatus.value == DeviceStatus.ON) {
                DeviceStore.shared().closeLocalCamera()
                flipCameraContainer.visibility = GONE
            } else {
                startCamera()
            }
            dismiss()
            return
        }

        if (isAdmin()) {
            seatInfo?.let {
                val isVideoLocked = anchorManager.getState().lockVideoUserList.value.contains(it.userInfo.userID)
                anchorManager.getMediaStore().disableUserMediaDevice(
                    currentUserInfo.userInfo.userID,
                    MediaStore.MediaDevice.CAMERA,
                    isVideoLocked
                )
            }
            dismiss()
        }
    }

    private fun clickSwitchCameraButton() {
        val isFront = DeviceStore.shared().deviceState.isFrontCamera.value
        DeviceStore.shared().switchCamera(!isFront)
        dismiss()
    }

    private fun startCamera() {
        val isFrontCamera = DeviceStore.shared().deviceState.isFrontCamera.value
        PermissionRequest.requestCameraPermissions(ContextProvider.getApplicationContext(), object :
            PermissionCallback() {
            override fun onRequesting() {
                LOGGER.info("requestCameraPermissions:[onRequesting]")
            }

            override fun onGranted() {
                LOGGER.info("requestCameraPermissions:[onGranted]")
                DeviceStore.shared().openLocalCamera(isFrontCamera, object : CompletionHandler {
                    override fun onSuccess() {
                        TODO("Not yet implemented")
                    }

                    override fun onFailure(code: Int, desc: String) {
                        LOGGER.error("startCamera failed:code:$code,desc:$desc")
                        ErrorLocalized.onError(code)
                    }

                })
            }
        })
    }

    private suspend fun onMicrophoneStatusChanged() {
        DeviceStore.shared().deviceState.microphoneStatus.collect {
            seatInfo?.let {
                val isAudioLocked = anchorManager.getState().lockAudioUserList.value?.contains(it.userInfo.userID)
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
                } else if (isAudioLocked == true) {
                    audioContainer.isEnabled = false
                    ivAudio.alpha = BUTTON_DISABLE_ALPHA
                    ivAudio.setImageResource(R.drawable.livekit_ic_mute_audio)
                    tvAudio.setText(R.string.common_unmute_audio)
                } else {
                    audioContainer.isEnabled = true
                    ivAudio.alpha = BUTTON_ENABLE_ALPHA
                    val isMicrophoneMuted = it.userInfo.microphoneStatus != DeviceStatus.ON
                    if (isMicrophoneMuted) {
                        ivAudio.setImageResource(R.drawable.livekit_ic_mute_audio)
                        tvAudio.setText(R.string.common_unmute_audio)
                    } else {
                        ivAudio.setImageResource(R.drawable.livekit_ic_unmute_audio)
                        tvAudio.setText(R.string.common_mute_audio)
                    }
                }
            }
        }
    }

    private suspend fun onLockAudioUserListChanged() {
        anchorManager.getState().lockAudioUserList.collect { lockAudioUserList ->
            seatInfo?.let {
                val isAudioLocked = lockAudioUserList.contains(it.userInfo.userID)
                if (!isSelfUser()) {
                    if (isAdmin()) {
                        audioContainer.isEnabled = true
                        ivAudio.alpha = BUTTON_ENABLE_ALPHA
                        ivAudio.setImageResource(
                            if (isAudioLocked) R.drawable.livekit_ic_disable_audio
                            else R.drawable.livekit_ic_unmute_audio
                        )
                        tvAudio.setText(
                            if (isAudioLocked) R.string.common_enable_audio
                            else R.string.common_disable_audio
                        )
                    }
                } else if (isAudioLocked) {
                    audioContainer.isEnabled = false
                    ivAudio.alpha = BUTTON_DISABLE_ALPHA
                    ivAudio.setImageResource(R.drawable.livekit_ic_mute_audio)
                    tvAudio.setText(R.string.common_unmute_audio)

                } else {
                    audioContainer.isEnabled = true
                    ivAudio.alpha = BUTTON_ENABLE_ALPHA
                    val isMicrophoneMuted = DeviceStore.shared().deviceState.microphoneStatus.value == DeviceStatus.OFF
                    if (isMicrophoneMuted) {
                        ivAudio.setImageResource(R.drawable.livekit_ic_mute_audio)
                        tvAudio.setText(R.string.common_unmute_audio)
                    } else {
                        ivAudio.setImageResource(R.drawable.livekit_ic_unmute_audio)
                        tvAudio.setText(R.string.common_mute_audio)
                    }
                }
            }
        }
    }

    private suspend fun onCameraStatusChanged() {
        anchorManager.getState().lockVideoUserList.collect { lockVideoUserList ->
            seatInfo?.let {
                val isVideoLocked = lockVideoUserList.contains(it.userInfo.userID)

                if (!isSelfUser()) {
                    if (isAdmin()) {
                        videoContainer.isEnabled = true
                        ivVideo.alpha = BUTTON_ENABLE_ALPHA
                        ivVideo.setImageResource(
                            if (isVideoLocked) R.drawable.livekit_ic_disable_video
                            else R.drawable.livekit_ic_start_video
                        )
                        tvVideo.setText(
                            if (isVideoLocked) R.string.common_enable_video
                            else R.string.common_disable_video
                        )
                    }
                } else {
                    val isCameraOpened = (DeviceStatus.ON == DeviceStore.shared().deviceState.cameraStatus.value)
                    if (isVideoLocked) {
                        videoContainer.isEnabled = false
                        ivVideo.alpha = BUTTON_DISABLE_ALPHA
                        ivVideo.setImageResource(R.drawable.livekit_ic_stop_video)
                        tvVideo.setText(R.string.common_start_video)
                        flipCameraContainer.visibility = GONE
                    } else {
                        videoContainer.isEnabled = true
                        ivVideo.alpha = BUTTON_ENABLE_ALPHA
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
                }
            }
        }
    }

    private suspend fun onConnectUserListChanged() {
        CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coGuestState.connected.collect {
            val userList: List<SeatUserInfo> =
                it.filterNot { it.liveID != LiveListStore.shared().liveState.currentLive.value.liveID }
            seatInfo?.let {
                val isConnected = userList.any { item ->
                    it.userInfo.userID == item.userID
                }
                if (!isConnected) {
                    dismiss()
                    confirmDialog?.dismiss()
                }
            }

        }

    }

    private val tuiRoomObserver = object : TUIRoomObserver() {
        override fun onKickedOffSeat(seatIndex: Int, operateUser: TUIRoomDefine.UserInfo, extensionInfo: String) {
            dismiss()
            confirmDialog?.dismiss()
        }

        override fun onRemoteUserLeaveRoom(roomId: String, userInfo: TUIRoomDefine.UserInfo) {
            val currentUserInfo = this@AnchorManagerDialog.seatInfo ?: return
            if (TextUtils.isEmpty(currentUserInfo.userInfo.userID)) {
                return
            }
            if (userInfo.userId == currentUserInfo.userInfo.userID) {
                dismiss()
                confirmDialog?.dismiss()
            }
        }
    }
}