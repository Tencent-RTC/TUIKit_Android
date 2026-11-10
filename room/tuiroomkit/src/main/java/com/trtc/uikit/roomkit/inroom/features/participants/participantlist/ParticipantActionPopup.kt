package com.trtc.uikit.roomkit.inroom.features.participants.participantlist

import android.app.Activity
import android.content.Context
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.service.RoomForegroundService
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.RoomDevicePermission
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ParticipantActionPopup(private val context: Context, private val roomID: String) {

    private val popup = RoomPopup(context)
    private val deviceStore = DeviceStore.shared()
    private val participantStore = RoomParticipantStore.create(roomID)
    private val container: LinearLayout =
        LayoutInflater.from(context).inflate(R.layout.roomkit_participant_action_popup, null)
            .findViewById(R.id.action_container)
    private var targetUserID: String = ""
    private var subscribeJob: Job? = null
    private var dialogGuardJob: Job? = null
    private var renderedTarget: RoomParticipant? = null
    private var renderedLocalRole: ParticipantRole? = null
    private var renderedChatMuted = false

    init {
        popup.onDismiss = { subscribeJob?.cancel() }
    }

    fun show(target: RoomParticipant) {
        targetUserID = target.userID
        if (!isTargetManageable()) return
        updateActions()
        popup.setContentView(container, RoomPopup.ContentHeight.AUTO)
        popup.show()
        observeParticipants()
    }

    private fun observeParticipants() {
        subscribeJob?.cancel()
        val state = participantStore.state
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                state.participantList,
                state.localParticipant,
                state.messageDisabledUserList,
            ) { list, local, mutedUsers -> Triple(list, local, mutedUsers) }
                .collect {
                    if (!isTargetManageable()) {
                        subscribeJob?.cancel()
                        popup.dismiss()
                        return@collect
                    }
                    updateActions()
                }
        }
    }

    private fun isTargetManageable(): Boolean {
        val state = participantStore.state
        val local = state.localParticipant.value ?: return false
        val target = state.participantList.value.find { it.userID == targetUserID } ?: return false
        return target.userID == local.userID || local.role.ordinal < target.role.ordinal
    }

    private fun updateActions() {
        val state = participantStore.state
        val local = state.localParticipant.value ?: return
        val target = state.participantList.value.find { it.userID == targetUserID } ?: return
        val chatMuted = state.messageDisabledUserList.value.any { it.userID == target.userID }
        if (target == renderedTarget && local.role == renderedLocalRole && chatMuted == renderedChatMuted) return
        renderedTarget = target
        renderedLocalRole = local.role
        renderedChatMuted = chatMuted
        container.removeAllViews()
        if (target.userID == local.userID) addSelfActions(target) else addManageActions(target, local)
    }

    private fun dismissPopup() {
        subscribeJob?.cancel()
        popup.dismiss()
    }

    private fun isLocalOwner(): Boolean =
        participantStore.state.localParticipant.value?.role == ParticipantRole.OWNER

    private fun dismissDialogWhenInvalid(dialog: RoomDialog, isValid: () -> Boolean) {
        dialogGuardJob?.cancel()
        val state = participantStore.state
        dialogGuardJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                state.participantList,
                state.localParticipant,
            ) { list, local -> list to local }
                .collect {
                    if (!isValid()) {
                        dialogGuardJob?.cancel()
                        dialog.dismiss()
                    }
                }
        }
        dialog.setOnDismissListener { dialogGuardJob?.cancel() }
    }

    private fun addSelfActions(target: RoomParticipant) {
        val microphoneOn = target.microphoneStatus == DeviceStatus.ON
        val cameraOn = target.cameraStatus == DeviceStatus.ON
        addAction(
            if (microphoneOn) R.drawable.roomkit_icon_microphone_on else R.drawable.roomkit_icon_microphone_off,
            if (microphoneOn) R.string.roomkit_participants_mic_close else R.string.roomkit_participants_mic_open,
        ) { toggleSelfMicrophone(microphoneOn) }
        addAction(
            if (cameraOn) R.drawable.roomkit_icon_camera_on else R.drawable.roomkit_icon_camera_off,
            if (cameraOn) R.string.roomkit_participants_cam_close else R.string.roomkit_participants_cam_open,
        ) { toggleSelfCamera(cameraOn) }
        addAction(R.drawable.roomkit_icon_rename, R.string.roomkit_participants_rename) { showRenameDialog(target) }
    }

    private fun addManageActions(target: RoomParticipant, local: RoomParticipant) {
        val isOwner = local.role == ParticipantRole.OWNER
        val displayName = target.getDisplayName()
        addAction(R.drawable.roomkit_icon_rename, R.string.roomkit_participants_rename) { showRenameDialog(target) }
        if (isOwner) {
            addAction(R.drawable.roomkit_icon_transfer_owner, R.string.roomkit_participants_transfer_owner) {
                showTransferOwnerDialog(target)
            }
            if (target.role == ParticipantRole.GENERAL_USER) {
                addAction(R.drawable.roomkit_icon_set_admin, R.string.roomkit_participants_set_admin) {
                    participantStore.setAdmin(target.userID, object : CompletionHandler {
                        override fun onSuccess() {
                            RoomToast.show(
                                context,
                                context.getString(R.string.roomkit_participants_set_admin_done, displayName)
                            )
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    })
                }
            }
            if (target.role == ParticipantRole.ADMIN) {
                addAction(R.drawable.roomkit_icon_revoke_admin, R.string.roomkit_participants_revoke_admin) {
                    participantStore.revokeAdmin(target.userID, object : CompletionHandler {
                        override fun onSuccess() {
                            RoomToast.show(
                                context,
                                context.getString(R.string.roomkit_participants_revoke_admin_done, displayName)
                            )
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    })
                }
            }
            addDivider()
        }
        addMediaActions(target)
        addDivider()
        addAction(R.drawable.roomkit_icon_kick_danger, R.string.roomkit_participants_kick, danger = true) {
            showKickDialog(target)
        }
    }

    private fun addMediaActions(target: RoomParticipant) {
        val microphoneOn = target.microphoneStatus == DeviceStatus.ON
        val cameraOn = target.cameraStatus == DeviceStatus.ON
        val chatMuted = participantStore.state.messageDisabledUserList.value.any { it.userID == target.userID }
        addAction(
            if (microphoneOn) R.drawable.roomkit_icon_microphone_on else R.drawable.roomkit_icon_microphone_off,
            if (microphoneOn) R.string.roomkit_participants_audio_disable else R.string.roomkit_participants_audio_allow,
        ) { toggleParticipantDevice(target, DeviceType.MICROPHONE, microphoneOn) }
        addAction(
            if (cameraOn) R.drawable.roomkit_icon_camera_on else R.drawable.roomkit_icon_camera_off,
            if (cameraOn) R.string.roomkit_participants_video_disable else R.string.roomkit_participants_video_allow,
        ) { toggleParticipantDevice(target, DeviceType.CAMERA, cameraOn) }
        addAction(
            if (chatMuted) R.drawable.roomkit_icon_chat else R.drawable.roomkit_icon_chat_off,
            if (chatMuted) R.string.roomkit_participants_chat_unmute else R.string.roomkit_participants_chat_mute,
        ) { toggleParticipantChat(target, chatMuted) }
    }

    private fun addAction(
        iconRes: Int,
        labelRes: Int,
        danger: Boolean = false,
        onClick: () -> Unit,
    ) {
        val row = LayoutInflater.from(context).inflate(R.layout.roomkit_participant_action_row, container, false)
        row.findViewById<AppCompatImageView>(R.id.action_icon).setImageResource(iconRes)
        row.findViewById<AppCompatTextView>(R.id.action_label).apply {
            setText(labelRes)
            if (danger) setTextColor(ContextCompat.getColor(context, R.color.roomkit_danger))
        }
        row.setOnClickListener {
            dismissPopup()
            onClick()
        }
        container.addView(row)
    }

    private fun addDivider() {
        container.addView(
            LayoutInflater.from(context).inflate(R.layout.roomkit_participant_action_divider, container, false),
        )
    }

    private fun toggleSelfMicrophone(microphoneOn: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            if (microphoneOn) {
                participantStore.muteMicrophone()
                return@launch
            }
            val granted = RoomDevicePermission.requestMicrophonePermission(context as Activity)
            if (granted) {
                deviceStore.openLocalMicrophone(object : CompletionHandler {
                    override fun onSuccess() {
                        RoomForegroundService.start(context)
                        participantStore.unmuteMicrophone(object : CompletionHandler {
                            override fun onSuccess() = Unit
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
    }

    private fun toggleSelfCamera(cameraOn: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            if (cameraOn) {
                deviceStore.closeLocalCamera()
                return@launch
            }
            val selfRole = participantStore.state.localParticipant.value?.role
            val allCamDisabled = RoomStore.shared().state.currentRoom.value?.isAllCameraDisabled == true
            val roomDisabled = allCamDisabled && selfRole != null &&
                selfRole != ParticipantRole.OWNER && selfRole != ParticipantRole.ADMIN
            if (roomDisabled) {
                RoomToast.show(context, context.getString(R.string.roomkit_device_all_cam_disabled))
                return@launch
            }
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

    private fun toggleParticipantDevice(target: RoomParticipant, deviceType: DeviceType, on: Boolean) {
        if (on) {
            participantStore.closeParticipantDevice(target.userID, deviceType, object : CompletionHandler {
                override fun onSuccess() {
                    val res = if (deviceType == DeviceType.MICROPHONE) {
                        R.string.roomkit_participants_mute_done
                    } else {
                        R.string.roomkit_participants_cam_stop_done
                    }
                    RoomToast.show(context, context.getString(res, target.getDisplayName()))
                }

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            })
        } else {
            participantStore.inviteToOpenDevice(target.userID, deviceType, 30, object : CompletionHandler {
                override fun onSuccess() {}
                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            })
        }
    }

    private fun toggleParticipantChat(target: RoomParticipant, chatMuted: Boolean) {
        participantStore.disableUserMessage(target.userID, !chatMuted, object : CompletionHandler {
            override fun onSuccess() {
                val res = if (chatMuted) {
                    R.string.roomkit_participants_unmute_chat_done
                } else {
                    R.string.roomkit_participants_mute_chat_done
                }
                RoomToast.show(context, context.getString(res, target.getDisplayName()))
            }

            override fun onFailure(code: Int, desc: String) {
                ErrorLocalized.showError(context, code, desc)
            }
        })
    }

    private fun showRenameDialog(target: RoomParticipant) {
        val input = AppCompatEditText(context).apply {
            hint = context.getString(R.string.roomkit_participants_rename_placeholder)
            setText(target.getDisplayName())
            setSelection(text?.length ?: 0)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setBackgroundResource(R.drawable.roomkit_input_box)
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
            textSize = 15f
            filters = arrayOf(InputFilter.LengthFilter(NAME_MAX_LENGTH))
        }
        val dialog = showConfirmDialog(
            R.string.roomkit_participants_rename,
            context.getString(R.string.roomkit_participants_rename_desc),
            R.string.roomkit_participants_confirm,
            contentView = input,
        ) {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@showConfirmDialog
            participantStore.updateParticipantNameCard(
                target.userID,
                name,
                object : CompletionHandler {
                    override fun onSuccess() {}

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.showError(context, code, desc)
                    }
                },
            )
        }
        dismissDialogWhenInvalid(dialog) { isTargetManageable() }
    }

    private fun showTransferOwnerDialog(target: RoomParticipant) {
        val dialog = showConfirmDialog(
            R.string.roomkit_participants_transfer_owner,
            context.getString(R.string.roomkit_participants_transfer_owner_message, target.getDisplayName()),
            R.string.roomkit_participants_transfer_owner_confirm,
        ) {
            participantStore.transferOwner(target.userID, object : CompletionHandler {
                override fun onSuccess() {
                    RoomToast.show(
                        context,
                        context.getString(R.string.roomkit_participants_transfer_owner_done, target.getDisplayName()),
                    )
                }

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            })
        }
        dismissDialogWhenInvalid(dialog) { isLocalOwner() && isTargetManageable() }
    }

    private fun showKickDialog(target: RoomParticipant) {
        val dialog = showConfirmDialog(
            R.string.roomkit_room_common_notification,
            context.getString(R.string.roomkit_participants_kick_message, target.getDisplayName()),
            R.string.roomkit_participants_kick_confirm,
            danger = true,
        ) {
            participantStore.kickUser(
                target.userID,
                object : CompletionHandler {
                    override fun onSuccess() {
                        RoomToast.show(
                            context,
                            context.getString(R.string.roomkit_participants_kick_done, target.getDisplayName())
                        )
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.showError(context, code, desc)
                    }
                },
            )
        }
        dismissDialogWhenInvalid(dialog) { isTargetManageable() }
    }

    private fun showConfirmDialog(
        titleRes: Int,
        message: String,
        confirmRes: Int,
        danger: Boolean = false,
        contentView: View? = null,
        onConfirm: () -> Unit,
    ): RoomDialog {
        val builder = RoomDialog.Builder(context)
            .title(context.getString(titleRes))
            .message(message)
        contentView?.let { builder.contentView(it) }
        if (danger) {
            builder.primaryAction(context.getString(confirmRes), variant = RoomDialog.Variant.DANGER) { onConfirm() }
        } else {
            builder.primaryAction(context.getString(confirmRes)) { onConfirm() }
        }
        builder.secondaryAction(context.getString(R.string.roomkit_room_common_cancel))
        return builder.show()
    }

    companion object {
        private const val NAME_MAX_LENGTH = 10
    }
}
