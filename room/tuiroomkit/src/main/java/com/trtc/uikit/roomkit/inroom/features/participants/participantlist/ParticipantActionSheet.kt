package com.trtc.uikit.roomkit.inroom.features.participants.participantlist

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
import com.trtc.uikit.roomkit.base.ActionCompletion
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.util.RoomDevice
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore

class ParticipantActionSheet(private val context: Context, roomID: String) {

    private val sheet = RoomPopup(context)
    private val deviceStore = DeviceStore.shared()
    private val participantStore = RoomParticipantStore.create(roomID)
    private val container: LinearLayout =
        LayoutInflater.from(context).inflate(R.layout.roomkit_participant_action_sheet, null)
            .findViewById(R.id.action_container)

    fun show(target: RoomParticipant) {
        val local = participantStore.state.localParticipant.value
        val isSelf = target.userID == local?.userID
        if (local == null || (!isSelf && local.role.ordinal >= target.role.ordinal)) return
        container.removeAllViews()
        if (isSelf) addSelfActions(target) else addManageActions(target, local)
        sheet.setContentView(container, RoomPopup.ContentHeight.AUTO)
        sheet.show()
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
                    participantStore.setAdmin(
                        target.userID,
                        ActionCompletion(
                            context,
                            context.getString(R.string.roomkit_participants_set_admin_done, displayName)
                        ),
                    )
                }
            }
            if (target.role == ParticipantRole.ADMIN) {
                addAction(R.drawable.roomkit_icon_revoke_admin, R.string.roomkit_participants_revoke_admin) {
                    participantStore.revokeAdmin(
                        target.userID,
                        ActionCompletion(
                            context,
                            context.getString(R.string.roomkit_participants_revoke_admin_done, displayName)
                        ),
                    )
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
            sheet.dismiss()
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
        RoomLocalMediaUtils.runWithPermission(context, RoomDevice.MICROPHONE) {
            if (microphoneOn) {
                participantStore.muteMicrophone()
            } else {
                participantStore.unmuteMicrophone(ActionCompletion(context))
            }
        }
    }

    private fun toggleSelfCamera(cameraOn: Boolean) {
        if (cameraOn) {
            deviceStore.closeLocalCamera()
        } else {
            RoomLocalMediaUtils.openCamera(context, ActionCompletion(context))
        }
    }

    private fun toggleParticipantDevice(target: RoomParticipant, deviceType: DeviceType, on: Boolean) {
        if (on) {
            participantStore.closeParticipantDevice(target.userID, deviceType, ActionCompletion(context))
        } else {
            participantStore.inviteToOpenDevice(target.userID, deviceType, 30, ActionCompletion(context))
        }
    }

    private fun toggleParticipantChat(target: RoomParticipant, chatMuted: Boolean) {
        participantStore.disableUserMessage(target.userID, !chatMuted, ActionCompletion(context))
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
        showConfirmDialog(
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
                ActionCompletion(context, context.getString(R.string.roomkit_participants_rename_done)),
            )
        }
    }

    private fun showTransferOwnerDialog(target: RoomParticipant) {
        showConfirmDialog(
            R.string.roomkit_participants_transfer_owner,
            context.getString(R.string.roomkit_participants_transfer_owner_message, target.getDisplayName()),
            R.string.roomkit_participants_transfer_owner_confirm,
        ) {
            participantStore.transferOwner(target.userID, ActionCompletion(context))
        }
    }

    private fun showKickDialog(target: RoomParticipant) {
        showConfirmDialog(
            R.string.roomkit_room_common_notification,
            context.getString(R.string.roomkit_participants_kick_message, target.getDisplayName()),
            R.string.roomkit_participants_kick_confirm,
            danger = true,
        ) {
            participantStore.kickUser(
                target.userID,
                ActionCompletion(
                    context,
                    context.getString(R.string.roomkit_participants_kick_done, target.getDisplayName())
                ),
            )
        }
    }

    private fun showConfirmDialog(
        titleRes: Int,
        message: String,
        confirmRes: Int,
        danger: Boolean = false,
        contentView: View? = null,
        onConfirm: () -> Unit,
    ) {
        val builder = RoomDialog.Builder(context)
            .title(context.getString(titleRes))
            .message(message)
            .closable(false)
        contentView?.let { builder.contentView(it) }
        if (danger) {
            builder.primaryAction(context.getString(confirmRes), variant = RoomDialog.Variant.DANGER) { onConfirm() }
        } else {
            builder.primaryAction(context.getString(confirmRes)) { onConfirm() }
        }
        builder.secondaryAction(context.getString(R.string.roomkit_room_common_cancel))
        builder.show()
    }

    companion object {
        private const val NAME_MAX_LENGTH = 10
    }
}
