package com.trtc.uikit.roomkit.view

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.barrage.BarrageInputView
import com.trtc.uikit.roomkit.barrage.BarrageStreamView
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import com.trtc.uikit.roomkit.base.extension.getSenderDisplayName
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.operator.DeviceOperator
import com.trtc.uikit.roomkit.base.operator.DeviceOperator.DeviceOperatorType
import com.trtc.uikit.roomkit.base.report.RoomDataReporter
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import com.trtc.uikit.roomkit.view.main.RoomBottomBarView
import com.trtc.uikit.roomkit.view.main.RoomTopBarView
import com.trtc.uikit.roomkit.view.main.RoomView
import com.trtc.uikit.roomkit.view.main.screenshare.ScreenShareOverlayView
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ListResultCompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.AudioRoute
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.CreateRoomOptions
import io.trtc.tuikit.atomicxcore.api.room.DeviceRequestInfo
import io.trtc.tuikit.atomicxcore.api.room.KickedOutOfRoomReason
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomListener
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantListener
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import io.trtc.tuikit.atomicxcore.api.room.RoomUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Main room view orchestrating all room UI components and handling room lifecycle.
 * Manages room connection, device controls, participant events, and dialog interactions.
 */
class RoomMainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_main_view, this)
    }

    sealed class RoomBehavior {
        data class Create(val options: CreateRoomOptions) : RoomBehavior()
        object Join : RoomBehavior()
    }

    data class ConnectConfig(
        val autoEnableMicrophone: Boolean = true,
        val autoEnableCamera: Boolean = true,
        val autoEnableSpeaker: Boolean = false
    )

    private val logger = RoomKitLogger.getLogger("RoomMainView")

    private val scope = CoroutineScope(Dispatchers.Main)
    private val deviceOperator by lazy { DeviceOperator(context) }

    private val topBarView: RoomTopBarView by lazy { findViewById(R.id.room_top_bar) }
    private val roomView: RoomView by lazy { findViewById(R.id.room_view) }
    private val bottomBarView: RoomBottomBarView by lazy { findViewById(R.id.room_bottom_bar) }
    private val barrageInputView: BarrageInputView by lazy { findViewById(R.id.barrage_input_view) }
    private val barrageStreamView: BarrageStreamView by lazy { findViewById(R.id.barrage_stream_view) }
    private val screenShareOverlayView: ScreenShareOverlayView by lazy { findViewById(R.id.screen_share_overlay_view) }

    private var roomType = RoomType.STANDARD
    private val roomStore = RoomStore.shared()
    private val deviceStore = DeviceStore.shared()
    private var participantStore: RoomParticipantStore? = null
    private var cameraInvitationDialog: Dialog? = null
    private var microphoneInvitationDialog: Dialog? = null
    private var localUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID
    private var connectConfig: ConnectConfig? = null

    private val participantListener = object : RoomParticipantListener() {
        override fun onDeviceInvitationReceived(invitation: DeviceRequestInfo) {
            logger.info("Device invitation received: device=${invitation.device}, from=${invitation.senderUserID}")
            when (invitation.device) {
                DeviceType.MICROPHONE -> showMicrophoneInvitationDialog(invitation)
                DeviceType.CAMERA -> showCameraInvitationDialog(invitation)
                else -> Unit
            }
        }

        override fun onDeviceInvitationCancelled(invitation: DeviceRequestInfo) {
            logger.info("Device invitation cancelled: device=${invitation.device}, from=${invitation.senderUserID}")
            when (invitation.device) {
                DeviceType.MICROPHONE -> dismissMicrophoneInvitationDialog()
                DeviceType.CAMERA -> dismissCameraInvitationDialog()
                else -> Unit
            }
        }

        override fun onDeviceInvitationTimeout(invitation: DeviceRequestInfo) {
            logger.info("Device invitation timeout: device=${invitation.device}, from=${invitation.senderUserID}")
            when (invitation.device) {
                DeviceType.MICROPHONE -> dismissMicrophoneInvitationDialog()
                DeviceType.CAMERA -> dismissCameraInvitationDialog()
                else -> Unit
            }
        }

        override fun onKickedFromRoom(reason: KickedOutOfRoomReason, message: String) {
            logger.info("onKickedFromRoom: reason=$reason, from=$message")
            showKickoutDialog()
        }

        override fun onOwnerChanged(newOwner: RoomUser, oldOwner: RoomUser) {
            logger.info("onOwnerChanged: newOwner=${newOwner.userID} oldOwner=${oldOwner.userID}")
            if (localUserID == newOwner.userID) {
                AtomicToast.show(context, context.getString(R.string.roomkit_toast_you_are_owner), Style.INFO)
            }
        }

        override fun onAdminSet(userInfo: RoomUser) {
            logger.info("onAdminSet: userInfo=$userInfo")
            if (localUserID == userInfo.userID) {
                AtomicToast.show(context, context.getString(R.string.roomkit_toast_you_are_admin), Style.INFO)
            }
        }

        override fun onAdminRevoked(userInfo: RoomUser) {
            logger.info("onAdminRevoked: userInfo=$userInfo")
            if (localUserID == userInfo.userID) {
                AtomicToast.show(
                    context,
                    context.getString(R.string.roomkit_toast_you_are_no_longer_admin),
                    Style.INFO
                )
            }
        }

        override fun onParticipantDeviceClosed(device: DeviceType, operator: RoomUser) {
            logger.info("onParticipantDeviceClosed: device=$device operator:$operator")
            when (device) {
                DeviceType.CAMERA -> AtomicToast.show(
                    context, context.getString(R.string.roomkit_toast_camera_closed_by_host), Style.WARNING
                )

                DeviceType.MICROPHONE -> AtomicToast.show(
                    context, context.getString(R.string.roomkit_toast_muted_by_host), Style.WARNING
                )

                DeviceType.SCREEN_SHARE -> AtomicToast.show(
                    context, context.getString(R.string.roomkit_toast_screen_share_closed_by_host), Style.WARNING
                )
            }
        }

        override fun onAllDevicesDisabled(device: DeviceType, disable: Boolean, operator: RoomUser) {
            logger.info("onAllDevicesDisabled: device=$device disable:$disable operator:$operator")
            when (device) {
                DeviceType.CAMERA -> {
                    if (disable) {
                        AtomicToast.show(
                            context,
                            context.getString(R.string.roomkit_toast_all_video_disabled),
                            Style.WARNING
                        )
                    } else {
                        AtomicToast.show(
                            context,
                            context.getString(R.string.roomkit_toast_all_video_enabled),
                            Style.INFO
                        )
                    }
                }

                DeviceType.MICROPHONE -> {
                    if (disable) {
                        AtomicToast.show(
                            context,
                            context.getString(R.string.roomkit_toast_all_audio_disabled),
                            Style.WARNING
                        )
                    } else {
                        AtomicToast.show(
                            context,
                            context.getString(R.string.roomkit_toast_all_audio_enabled),
                            Style.INFO
                        )
                    }
                }

                else -> Unit
            }
        }

        override fun onAudiencePromotedToParticipant(userInfo: RoomUser) {
            if (userInfo.userID == localUserID) {
                AtomicToast.show(context, context.getString(R.string.roomkit_switch_to_participant_byself), Style.INFO)
            } else {
                AtomicToast.show(
                    context,
                    context.getString(R.string.roomkit_switch_to_participant, userInfo.getDisplayName()), Style.INFO
                )
            }
        }

        override fun onParticipantDemotedToAudience(userInfo: RoomUser) {
            if (userInfo.userID == localUserID) {
                DeviceStore.shared().closeLocalMicrophone()
                DeviceStore.shared().closeLocalCamera()
            }
        }

        override fun onUserMessageDisabled(disable: Boolean, operator: RoomUser) {
            if (disable) {
                AtomicToast.show(context, context.getString(R.string.roomkit_toast_text_chat_disabled), Style.WARNING)
            } else {
                AtomicToast.show(context, context.getString(R.string.roomkit_toast_text_chat_enabled), Style.INFO)
            }
        }
    }

    private val roomListener = object : RoomListener() {
        override fun onRoomEnded(roomInfo: RoomInfo) {
            logger.info("Room ended: roomID=${roomInfo.roomID}, roomName=${roomInfo.roomName}")
            showRoomDismissedDialog()
        }
    }

    fun init(roomID: String, roomType: RoomType, behavior: RoomBehavior, config: ConnectConfig) {
        logger.info("init roomID=$roomID, roomType=$roomType behavior:$behavior config=$config")
        this.roomType = roomType
        connectConfig = config
        super.init(roomID)
        roomView.init(roomID, roomType)
        topBarView.init(roomID, roomType)
        bottomBarView.init(roomID, roomType)
        if (roomType == RoomType.WEBINAR) {
            barrageInputView.init(roomID)
            barrageStreamView.init(roomID)
            barrageInputView.visibility = VISIBLE
            barrageStreamView.visibility = VISIBLE
        } else {
            barrageInputView.visibility = GONE
            barrageStreamView.visibility = GONE
        }
        RoomDataReporter.reportComponent()
        when (behavior) {
            is RoomBehavior.Create -> createRoom(roomID, behavior.options)
            is RoomBehavior.Join -> joinRoom(roomID)
        }
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        val participantStore = participantStore ?: return
        participantStore.addRoomParticipantListener(participantListener)
        roomStore.addRoomListener(roomListener)
        scope.launch {
            participantStore.state.localParticipant
                .map { it?.screenShareStatus ?: DeviceStatus.OFF }
                .distinctUntilChanged()
                .collect { screenShareOverlayView.updateScreenStatus(it) }
        }
    }

    override fun removeObserver() {
        participantStore?.removeRoomParticipantListener(participantListener)
        roomStore.removeRoomListener(roomListener)
        dismissCameraInvitationDialog()
        dismissMicrophoneInvitationDialog()
        scope.cancel()
    }

    private fun createRoom(roomID: String, createRoomOptions: CreateRoomOptions) {
        roomStore.createAndJoinRoom(roomID, roomType, createRoomOptions, object : CompletionHandler {
            override fun onSuccess() {
                val roomInfo = roomStore.state.currentRoom.value
                logger.info("createAndJoinRoom success $roomInfo")
                getParticipantList()
                if (roomType == RoomType.WEBINAR) {
                    getAudienceList()
                }
                connectConfig?.let {
                    initConnectConfig(it)
                }
                bottomBarView.visibility = VISIBLE
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("createAndJoinRoom failed:error:$code,desc:$desc")
                ErrorLocalized.showError(context, code)
                (context as? Activity)?.finish()
            }
        })
    }

    private fun joinRoom(roomID: String) {
        roomStore.joinRoom(roomID = roomID, roomType, completion = object : CompletionHandler {
            override fun onSuccess() {
                val roomInfo = roomStore.state.currentRoom.value
                logger.info("joinRoom success $roomInfo")
                getParticipantList()
                if (roomType == RoomType.WEBINAR) {
                    getAudienceList()
                } else {
                    connectConfig?.let {
                        initConnectConfig(it)
                    }
                }
                bottomBarView.visibility = VISIBLE
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("joinRoom failed:error:$code,desc:$desc")
                ErrorLocalized.showError(context, code)
                (context as? Activity)?.finish()
            }
        })
    }

    private fun getParticipantList() {
        logger.info("Store instance: ${participantStore.hashCode()} getParticipantList")

        participantStore?.getParticipantList("", object : ListResultCompletionHandler<RoomParticipant> {
            override fun onSuccess(result: List<RoomParticipant>, cursor: String) {
                logger.info("getParticipantList success result size:${result.size} cursor:$cursor")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("getParticipantList failed:error:$code,desc:$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun getAudienceList() {
        logger.info("Store instance: ${participantStore.hashCode()} getAudienceList")
        participantStore?.getAudienceList("", object : ListResultCompletionHandler<RoomUser> {
            override fun onSuccess(result: List<RoomUser>, cursor: String) {
                logger.info("getAudienceList success result size:${result.size} cursor:$cursor")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("getAudienceList failed:error:$code,desc:$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun initConnectConfig(config: ConnectConfig) {
        scope.launch {
            if (config.autoEnableMicrophone) {
                try {
                    deviceOperator.unmuteMicrophone(participantStore)
                } catch (e: Exception) {
                    logger.error("Failed to open microphone: ${e.message}")
                }
            }

            if (config.autoEnableCamera) {
                try {
                    deviceOperator.openCamera()
                } catch (e: Exception) {
                    logger.error("Failed to open camera: ${e.message}")
                }
            }
        }
        enableSpeaker(config.autoEnableSpeaker)
    }

    private fun enableSpeaker(enableSpeaker: Boolean) {
        val audioRoute = if (enableSpeaker) AudioRoute.SPEAKERPHONE else AudioRoute.EARPIECE
        deviceStore.setAudioRoute(audioRoute)
        logger.info("Speaker enabled (SPEAKERPHONE mode)")
    }

    private fun showCameraInvitationDialog(invitation: DeviceRequestInfo) {
        if (cameraInvitationDialog != null) {
            cameraInvitationDialog?.dismiss()
            microphoneInvitationDialog = null
        }
        val title = context.getString(R.string.roomkit_msg_invite_start_video, invitation.getSenderDisplayName())
        cameraInvitationDialog = buildDeviceInvitationDialog(title, invitation)
        cameraInvitationDialog?.show()
    }

    private fun showMicrophoneInvitationDialog(invitation: DeviceRequestInfo) {
        if (microphoneInvitationDialog != null) {
            microphoneInvitationDialog?.dismiss()
            microphoneInvitationDialog = null
        }
        val title = context.getString(R.string.roomkit_msg_invite_unmute_audio, invitation.getSenderDisplayName())
        microphoneInvitationDialog = buildDeviceInvitationDialog(title, invitation)
        microphoneInvitationDialog?.show()
    }

    private fun dismissCameraInvitationDialog() {
        if (cameraInvitationDialog != null) {
            cameraInvitationDialog?.dismiss()
            cameraInvitationDialog = null
        }
    }

    private fun dismissMicrophoneInvitationDialog() {
        if (microphoneInvitationDialog != null) {
            microphoneInvitationDialog?.dismiss()
            microphoneInvitationDialog = null
        }
    }

    private fun buildDeviceInvitationDialog(tile: String, invitation: DeviceRequestInfo): Dialog {
        return RoomAlertDialog.Builder(context)
            .setTitle(tile)
            .setNegativeButton(R.string.roomkit_reject) { handleDeclineInvitation(invitation) }
            .setPositiveButton(R.string.roomkit_agree) { handleAcceptInvitation(invitation) }
            .build()
    }

    private fun handleAcceptInvitation(invitation: DeviceRequestInfo) {
        val device = invitation.device
        logger.info("Accepting $device invitation from ${invitation.senderUserID}")
        scope.launch {
            try {
                val hasPermission = when (device) {
                    DeviceType.MICROPHONE -> deviceOperator.requestPermission(DeviceOperatorType.MICROPHONE)
                    DeviceType.CAMERA -> deviceOperator.requestPermission(DeviceOperatorType.CAMERA)
                    else -> {
                        logger.warn("Unsupported device type: $device")
                        false
                    }
                }

                logger.info("requestPermission result hasPermission: $hasPermission")

                if (hasPermission) {
                    val store = participantStore
                    if (store == null) {
                        logger.error("participantStore is null, cannot accept invitation")
                        return@launch
                    }

                    store.acceptOpenDeviceInvitation(
                        userID = invitation.senderUserID,
                        device = device,
                        completion = object : CompletionHandler {
                            override fun onSuccess() {
                                logger.info("Successfully accepted $device invitation")
                            }

                            override fun onFailure(code: Int, desc: String) {
                                logger.error("Failed to accept $device invitation: code=$code, desc=$desc")
                                ErrorLocalized.showError(context, code)
                            }
                        }
                    )
                } else {
                    handleDeclineInvitation(invitation)
                }
            } catch (e: Exception) {
                logger.error("Error handling invitation acceptance: ${e.message}")
                handleDeclineInvitation(invitation)
            }
        }
    }

    private fun handleDeclineInvitation(invitation: DeviceRequestInfo) {
        logger.info("Declining ${invitation.device} invitation from ${invitation.senderUserID}")
        participantStore?.declineOpenDeviceInvitation(
            userID = invitation.senderUserID,
            device = invitation.device,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("Successfully declined ${invitation.device} invitation")
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("Failed to decline ${invitation.device} invitation: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                }
            }
        )
    }

    private fun showRoomDismissedDialog() {
        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_toast_room_closed)
            .setPositiveButton(android.R.string.ok) {
                (context as? Activity)?.finish()
            }
            .show()
    }

    private fun showKickoutDialog() {
        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_toast_you_were_removed)
            .setPositiveButton(android.R.string.ok) {
                (context as? Activity)?.finish()
            }
            .show()
    }
}