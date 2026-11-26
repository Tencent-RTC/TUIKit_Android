package com.trtc.uikit.roomkit.inroom.views

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.service.RoomForegroundService
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.RoomDataReporter
import com.trtc.uikit.roomkit.base.util.RoomDevicePermission
import com.trtc.uikit.roomkit.base.util.getDisplayName
import com.trtc.uikit.roomkit.base.util.getSenderName
import com.trtc.uikit.roomkit.inroom.features.recording.CloudRecordingConfirm
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomView
import com.trtc.uikit.roomkit.inroom.features.screenshare.RoomScreenSharingPlaceholderView
import com.trtc.uikit.roomkit.inroom.features.transcription.captionoverlay.CaptionOverlay
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ListResultCompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.AudioRoute
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.CreateRoomOptions
import io.trtc.tuikit.atomicxcore.api.room.DeviceRequestInfo
import io.trtc.tuikit.atomicxcore.api.room.KickedOutOfRoomReason
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RecordingStopReason
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
import kotlinx.coroutines.launch

class MeetingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val topBar: MeetingTopBar
    private val toolBar: MeetingToolBar
    private val roomView: RoomView
    private val sharingPlaceholder: RoomScreenSharingPlaceholderView
    private val captionOverlay: CaptionOverlay
    private val loadingView: MeetingLoadingView

    private val roomStore = RoomStore.shared()
    private lateinit var participantStore: RoomParticipantStore
    private var roomID: String = ""
    private var connectConfig: ConnectConfig? = null
    private var observingRoomEvents = false
    private val invitationDialogs = mutableMapOf<DeviceType, RoomDialog>()
    private var recordingDialog: RoomDialog? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_meeting_view, this, true)
        topBar = findViewById(R.id.top_bar)
        toolBar = findViewById(R.id.meeting_tool_bar)
        roomView = findViewById(R.id.room_view)
        loadingView = findViewById(R.id.entering_view)
        sharingPlaceholder = findViewById(R.id.screen_sharing_placeholder)
        captionOverlay = findViewById(R.id.caption_overlay)
    }

    fun init(roomID: String, behavior: RoomBehavior, config: ConnectConfig) {
        connectConfig = config
        this.roomID = roomID
        topBar.init(roomID)
        roomView.init(roomID)
        sharingPlaceholder.init(roomID)
        toolBar.init(roomID)
        captionOverlay.init(roomID)
        participantStore = RoomParticipantStore.create(roomID)
        participantStore.addRoomParticipantListener(participantListener)
        roomStore.addRoomListener(roomListener)
        observingRoomEvents = true
        loadingView.show()
        RoomDataReporter.reportComponent()
        when (behavior) {
            is RoomBehavior.CreateAndJoinRoom -> quickStart(behavior.options)
            RoomBehavior.Join -> joinRoom()
        }
    }

    override fun onDetachedFromWindow() {
        stopObservingRoomEvents()
        RoomForegroundService.stop(context)
        super.onDetachedFromWindow()
    }

    private fun quickStart(options: CreateRoomOptions) {
        roomStore.createAndJoinRoom(
            roomID,
            RoomType.STANDARD,
            options,
            object : CompletionHandler {
                override fun onSuccess() {
                    loadingView.hide()
                    applyConnectConfig()
                    RoomParticipantStore.create(roomID).getParticipantList(
                        null,
                        object : ListResultCompletionHandler<RoomParticipant> {
                            override fun onSuccess(list: List<RoomParticipant>, nextCursor: String) {}
                            override fun onFailure(code: Int, desc: String) {}
                        },
                    )
                }

                override fun onFailure(code: Int, desc: String) {
                    loadingView.hide()
                    ErrorLocalized.showError(context, code, desc)
                    closeMeetingPage()
                }
            },
        )
    }

    private fun joinRoom(password: String = "") {
        roomStore.joinRoom(
            roomID,
            RoomType.STANDARD,
            password,
            object : CompletionHandler {
                override fun onSuccess() {
                    loadingView.hide()
                    applyConnectConfig()
                    RoomParticipantStore.create(roomID).getParticipantList(
                        null,
                        object : ListResultCompletionHandler<RoomParticipant> {
                            override fun onSuccess(list: List<RoomParticipant>, nextCursor: String) {}
                            override fun onFailure(code: Int, desc: String) {}
                        },
                    )
                }

                override fun onFailure(code: Int, desc: String) {
                    when (val rule = ErrorLocalized.rule(code)) {
                        ErrorLocalized.Rule.PASSWORD_REQUIRED, ErrorLocalized.Rule.WRONG_PASSWORD -> {
                            loadingView.hide()
                            showPasswordDialog(wrongPassword = rule == ErrorLocalized.Rule.WRONG_PASSWORD)
                        }

                        else -> {
                            loadingView.hide()
                            ErrorLocalized.showError(context, code, desc)
                            closeMeetingPage()
                        }
                    }
                }
            },
        )
    }

    private fun showPasswordDialog(wrongPassword: Boolean) {
        MeetingPasswordDialog(
            context = context,
            wrongPassword = wrongPassword,
            onSubmit = { password ->
                loadingView.show()
                joinRoom(password)
            },
            onCancel = { closeMeetingPage() },
        ).show()
    }

    private fun closeMeetingPage() {
        stopObservingRoomEvents()
        (context as? Activity)?.finish()
    }

    private fun applyConnectConfig() {
        val config = connectConfig ?: return
        DeviceStore.shared().setAudioRoute(
            if (config.autoEnableSpeaker) AudioRoute.SPEAKERPHONE else AudioRoute.EARPIECE,
        )
        CoroutineScope(Dispatchers.Main).launch {
            val deviceStore = DeviceStore.shared()
            if (config.autoEnableMicrophone && canOpenMicrophone()) {
                val granted = RoomDevicePermission.requestMicrophonePermission(context as Activity)
                if (granted) {
                    deviceStore.openLocalMicrophone(object : CompletionHandler {
                        override fun onSuccess() {
                            RoomForegroundService.start(context)
                            RoomParticipantStore.create(roomID).unmuteMicrophone(object : CompletionHandler {
                                override fun onSuccess() {}
                                override fun onFailure(code: Int, desc: String) {
                                    ErrorLocalized.showError(context, code, desc)
                                }
                            })
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    })
                } else {
                    RoomToast.show(context, context.getString(R.string.roomkit_device_mic_no_permission))
                }
            }
            if (config.autoEnableCamera && canOpenCamera()) {
                val granted = RoomDevicePermission.requestCameraPermission(context as Activity)
                if (granted) {
                    val isFront = deviceStore.deviceState.isFrontCamera.value
                    deviceStore.openLocalCamera(isFront, object : CompletionHandler {
                        override fun onSuccess() {
                            RoomForegroundService.start(context)
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    })
                } else {
                    RoomToast.show(context, context.getString(R.string.roomkit_device_camera_no_permission))
                }
            }
        }
    }

    private fun isLocalOwnerOrAdmin(): Boolean {
        val room = roomStore.state.currentRoom.value
        val localUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val isOwner = localUserID.isNotBlank() && localUserID == room?.roomOwner?.userID
        val isAdmin = RoomParticipantStore.create(roomID).state.localParticipant.value?.role == ParticipantRole.ADMIN
        return isOwner || isAdmin
    }

    private fun canOpenMicrophone(): Boolean =
        isLocalOwnerOrAdmin() || roomStore.state.currentRoom.value?.isAllMicrophoneDisabled != true

    private fun canOpenCamera(): Boolean =
        isLocalOwnerOrAdmin() || roomStore.state.currentRoom.value?.isAllCameraDisabled != true

    // RoomEvent
    private val roomListener = object : RoomListener() {
        override fun onRoomEnded(roomInfo: RoomInfo) {
            RoomToast.show(context, context.getString(R.string.roomkit_room_ended_by_host))
            closeMeetingPage()
        }

        override fun onRecordingStarted(roomInfo: RoomInfo, operator: RoomUser) {
            presentRecordingNotice(operator)
        }

        override fun onRecordingStopped(roomInfo: RoomInfo, operator: RoomUser, reason: RecordingStopReason) {
            recordingDialog?.dismiss()
            if (reason == RecordingStopReason.RECORDER_LEFT_ROOM) {
                toast(R.string.roomkit_cloud_recording_ended_abnormal)
                return
            }
            if (reason == RecordingStopReason.STOPPED_BY_USER && isLocal(operator.userID)) return
            toast(R.string.roomkit_cloud_recording_end_title)
        }
    }

    // RoomParticipantEvent
    private val participantListener = object : RoomParticipantListener() {
        override fun onAdminSet(userInfo: RoomUser) {
            if (isLocal(userInfo.userID)) toast(R.string.roomkit_room_notifications_become_admin)
        }

        override fun onAdminRevoked(userInfo: RoomUser) {
            if (isLocal(userInfo.userID)) toast(R.string.roomkit_room_notifications_admin_revoked)
        }

        override fun onOwnerChanged(newOwner: RoomUser, oldOwner: RoomUser) {
            if (isLocal(newOwner.userID)) toast(R.string.roomkit_room_notifications_become_owner)
        }

        override fun onKickedFromRoom(reason: KickedOutOfRoomReason, message: String) {
            val messageRes = when (reason) {
                KickedOutOfRoomReason.KICKED_BY_ADMIN -> R.string.roomkit_room_notifications_kicked_by_admin
                KickedOutOfRoomReason.KICKED_BY_SERVER -> R.string.roomkit_room_notifications_kicked_by_server
                KickedOutOfRoomReason.REPLACED_BY_ANOTHER_DEVICE ->
                    R.string.roomkit_room_notifications_replaced_by_another_device

                KickedOutOfRoomReason.CONNECTION_TIMEOUT -> R.string.roomkit_room_notifications_connection_timeout
                KickedOutOfRoomReason.INVALID_STATUS_ON_RECONNECT ->
                    R.string.roomkit_room_notifications_invalid_status_on_reconnect

                KickedOutOfRoomReason.ROOM_LIMIT_EXCEEDED -> R.string.roomkit_room_notifications_room_limit_exceeded
                else -> R.string.roomkit_room_notifications_default_kicked_out
            }
            RoomToast.show(context, context.getString(messageRes))
            closeMeetingPage()
        }

        override fun onParticipantDeviceClosed(device: DeviceType, operator: RoomUser) {
            val name = operator.getDisplayName()
            when (device) {
                DeviceType.MICROPHONE -> {
                    toast(R.string.roomkit_room_notifications_microphone_closed, name)
                }

                DeviceType.CAMERA -> {
                    toast(R.string.roomkit_room_notifications_camera_closed, name)
                }

                DeviceType.SCREEN_SHARE -> {
                    toast(R.string.roomkit_room_notifications_screen_share_closed, name)
                }
            }
        }

        override fun onUserMessageDisabled(disable: Boolean, operator: RoomUser) {
            toast(
                if (disable) R.string.roomkit_room_notifications_message_muted
                else R.string.roomkit_room_notifications_message_unmuted
            )
        }

        override fun onAllDevicesDisabled(device: DeviceType, disable: Boolean, operator: RoomUser) {
            if (isLocal(operator.userID)) return
            val res = when (device) {
                DeviceType.MICROPHONE -> if (disable) {
                    R.string.roomkit_room_notifications_all_microphones_disabled
                } else {
                    R.string.roomkit_room_notifications_all_microphones_enabled
                }

                DeviceType.CAMERA -> if (disable) {
                    R.string.roomkit_room_notifications_all_cameras_disabled
                } else {
                    R.string.roomkit_room_notifications_all_cameras_enabled
                }

                DeviceType.SCREEN_SHARE -> if (disable) {
                    R.string.roomkit_room_notifications_all_screen_shares_disabled
                } else {
                    R.string.roomkit_room_notifications_all_screen_shares_enabled
                }
            }
            toast(res)
        }

        override fun onAllMessagesDisabled(disable: Boolean, operator: RoomUser) {
            if (isLocal(operator.userID)) return
            toast(
                if (disable) R.string.roomkit_room_notifications_all_messages_disabled
                else R.string.roomkit_room_notifications_all_messages_enabled
            )
        }

        override fun onDeviceInvitationReceived(invitation: DeviceRequestInfo) {
            presentDeviceInvitation(invitation)
        }

        override fun onDeviceInvitationCancelled(invitation: DeviceRequestInfo) {
            dismissInvitation(invitation.device)
        }

        override fun onDeviceInvitationTimeout(invitation: DeviceRequestInfo) {
            dismissInvitation(invitation.device)
        }
    }

    private fun stopObservingRoomEvents() {
        if (!observingRoomEvents) return
        participantStore.removeRoomParticipantListener(participantListener)
        roomStore.removeRoomListener(roomListener)
        observingRoomEvents = false
        invitationDialogs.values.forEach { it.dismiss() }
        invitationDialogs.clear()
        recordingDialog?.dismiss()
    }

    private fun presentDeviceInvitation(invitation: DeviceRequestInfo) {
        if (invitation.device != DeviceType.MICROPHONE && invitation.device != DeviceType.CAMERA) return
        dismissInvitation(invitation.device)

        val isMicrophone = invitation.device == DeviceType.MICROPHONE
        val title = context.getString(
            if (isMicrophone) R.string.roomkit_room_notifications_microphone_invitation_received
            else R.string.roomkit_room_notifications_camera_invitation_received,
            invitation.getSenderName(),
        )
        val dialog = RoomDialog.Builder(context)
            .title(context.getString(R.string.roomkit_room_common_notification))
            .message(title)
            .cancelable(false)
            .secondaryAction(
                context.getString(
                    if (isMicrophone) R.string.roomkit_room_notifications_keep_mute
                    else R.string.roomkit_room_notifications_keep_off
                )
            ) {
                invitationDialogs.remove(invitation.device)
                participantStore.declineOpenDeviceInvitation(
                    invitation.senderUserID, invitation.device,
                    object : CompletionHandler {
                        override fun onSuccess() {}
                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    }
                )
            }
            .primaryAction(
                context.getString(
                    if (isMicrophone) R.string.roomkit_room_notifications_open_microphone
                    else R.string.roomkit_room_notifications_open_camera
                )
            ) {
                invitationDialogs.remove(invitation.device)
                participantStore.acceptOpenDeviceInvitation(
                    invitation.senderUserID, invitation.device,
                    object : CompletionHandler {
                        override fun onSuccess() {}
                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    }
                )
            }
            .show()
        invitationDialogs[invitation.device] = dialog
    }

    private fun dismissInvitation(device: DeviceType) {
        invitationDialogs.remove(device)?.dismiss()
    }

    private fun presentRecordingNotice(operator: RoomUser) {
        if (operator.userID.isBlank() || isLocal(operator.userID)) return
        recordingDialog?.dismiss()
        CloudRecordingConfirm.dismiss()
        recordingDialog = RoomDialog.Builder(context)
            .title(context.getString(R.string.roomkit_cloud_recording_notify_title))
            .message(context.getString(R.string.roomkit_cloud_recording_notify_content, operator.getDisplayName()))
            .secondaryAction(context.getString(R.string.roomkit_cloud_recording_notify_cancel)) {
                roomStore.leaveRoom(
                    object : CompletionHandler {
                        override fun onSuccess() {}
                        override fun onFailure(code: Int, desc: String) {}
                    }
                )
                closeMeetingPage()
            }
            .primaryAction(context.getString(R.string.roomkit_cloud_recording_notify_confirm))
            .show()
    }

    private fun toast(textRes: Int, vararg args: Any?) {
        val text = if (args.isEmpty()) context.getString(textRes) else context.getString(textRes, *args)
        RoomToast.show(context, text)
    }

    private fun isLocal(userID: String): Boolean =
        userID.isNotBlank() && userID == LoginStore.shared.loginState.loginUserInfo.value?.userID

    private fun deviceName(device: DeviceType): String = context.getString(
        when (device) {
            DeviceType.MICROPHONE -> R.string.roomkit_room_notifications_microphone
            DeviceType.CAMERA -> R.string.roomkit_room_notifications_camera
            DeviceType.SCREEN_SHARE -> R.string.roomkit_room_notifications_screen_share
        }
    )

    sealed class RoomBehavior {
        class CreateAndJoinRoom(val options: CreateRoomOptions) : RoomBehavior()
        object Join : RoomBehavior()
    }

    data class ConnectConfig(
        val autoEnableMicrophone: Boolean,
        val autoEnableCamera: Boolean,
        val autoEnableSpeaker: Boolean,
    )
}
