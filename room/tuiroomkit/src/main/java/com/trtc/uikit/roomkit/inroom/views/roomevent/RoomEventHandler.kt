package com.trtc.uikit.roomkit.inroom.views.roomevent

import android.app.Activity
import android.content.Context
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.toast.RoomToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.room.DeviceRequestInfo
import io.trtc.tuikit.atomicxcore.api.room.KickedOutOfRoomReason
import io.trtc.tuikit.atomicxcore.api.room.RecordingStopReason
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomListener
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantListener
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomUser

class RoomEventHandler(
    private val context: Context,
    private val roomID: String,
) {

    private val participantStore = RoomParticipantStore.create(roomID)
    private val deviceStore = DeviceStore.shared()
    private val invitationDialogs = mutableMapOf<DeviceType, RoomDialog>()
    private var recordingDialog: RoomDialog? = null

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
            closeMeetingPage(context.getString(kickedTextRes(reason)))
        }

        override fun onParticipantDeviceClosed(device: DeviceType, operator: RoomUser) {
            val name = operator.displayName()
            when (device) {
                DeviceType.MICROPHONE -> {
                    deviceStore.closeLocalMicrophone()
                    toast(R.string.roomkit_room_notifications_microphone_closed, name)
                }

                DeviceType.CAMERA -> {
                    deviceStore.closeLocalCamera()
                    toast(R.string.roomkit_room_notifications_camera_closed, name)
                }

                DeviceType.SCREEN_SHARE -> {
                    DeviceStore.shared().stopScreenShare()
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

        override fun onDeviceInvitationDeclined(invitation: DeviceRequestInfo, operator: RoomUser) {
            toast(R.string.roomkit_room_notifications_device_request_rejected, deviceName(invitation.device))
        }
    }

    private val roomListener = object : RoomListener() {
        override fun onRoomEnded(roomInfo: RoomInfo) {
            closeMeetingPage(context.getString(R.string.roomkit_room_ended_by_host))
        }

        override fun onRecordingStarted(roomInfo: RoomInfo, operator: RoomUser) {
            presentRecordingNotice(operator)
        }

        override fun onRecordingStopped(roomInfo: RoomInfo, operator: RoomUser, reason: RecordingStopReason) {
            recordingDialog?.dismiss()
            if (reason == RecordingStopReason.RECORDER_LEFT_ROOM) {
                toast(R.string.roomkit_cloud_recording_end_title)
                return
            }
            if (reason == RecordingStopReason.STOPPED_BY_USER && isLocal(operator.userID)) return
            toast(R.string.roomkit_cloud_recording_end_title)
        }
    }

    fun start() {
        participantStore.addRoomParticipantListener(participantListener)
        RoomStore.shared().addRoomListener(roomListener)
    }

    fun stop() {
        participantStore.removeRoomParticipantListener(participantListener)
        RoomStore.shared().removeRoomListener(roomListener)
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
            senderName(invitation),
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
                        override fun onSuccess() {
                        }

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
        recordingDialog = RoomDialog.Builder(context)
            .title(context.getString(R.string.roomkit_cloud_recording_notify_title))
            .message(context.getString(R.string.roomkit_cloud_recording_notify_content, operator.displayName()))
            .secondaryAction(context.getString(R.string.roomkit_cloud_recording_notify_cancel)) {
                RoomStore.shared().leaveRoom(
                    object : CompletionHandler {
                        override fun onSuccess() {}
                        override fun onFailure(code: Int, desc: String) {}
                    }
                )
                closeMeetingPage(null)
            }
            .primaryAction(context.getString(R.string.roomkit_cloud_recording_notify_confirm))
            .show()
    }

    private fun toast(textRes: Int, vararg args: Any?) {
        val text = if (args.isEmpty()) context.getString(textRes) else context.getString(textRes, *args)
        RoomToast.show(context, text)
    }

    private fun closeMeetingPage(message: String?) {
        stop()
        message?.let { RoomToast.show(context, it) }
        (context as? Activity)?.finish()
    }

    private fun isLocal(userID: String): Boolean = userID.isNotBlank() && userID == localUserID()

    private fun localUserID(): String = participantStore.state.localParticipant.value?.userID.orEmpty()

    private fun RoomUser.displayName(): String = userName.takeIf { it.isNotBlank() } ?: userID

    private fun senderName(request: DeviceRequestInfo): String =
        request.senderNameCard.takeIf { it.isNotBlank() }
            ?: request.senderUserName.takeIf { it.isNotBlank() }
            ?: request.senderUserID

    private fun deviceName(device: DeviceType): String = context.getString(
        when (device) {
            DeviceType.MICROPHONE -> R.string.roomkit_room_notifications_microphone
            DeviceType.CAMERA -> R.string.roomkit_room_notifications_camera
            DeviceType.SCREEN_SHARE -> R.string.roomkit_room_notifications_screen_share
        }
    )

    private fun kickedTextRes(reason: KickedOutOfRoomReason): Int = when (reason) {
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
}
