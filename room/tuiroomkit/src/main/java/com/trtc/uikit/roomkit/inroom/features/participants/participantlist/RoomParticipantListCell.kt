package com.trtc.uikit.roomkit.inroom.features.participants.participantlist

import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.avatar.RoomAvatar
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant

class RoomParticipantListCell(view: View) : RecyclerView.ViewHolder(view) {

    private val avatar: RoomAvatar = view.findViewById(R.id.item_avatar)
    private val nameText: AppCompatTextView = view.findViewById(R.id.item_name)
    private val selfTag: AppCompatTextView = view.findViewById(R.id.item_self_tag)
    private val ownerTag: AppCompatTextView = view.findViewById(R.id.item_owner_tag)
    private val adminTag: AppCompatTextView = view.findViewById(R.id.item_admin_tag)
    private val sharingTag: View = view.findViewById(R.id.item_sharing_tag)
    private val micStatus: AppCompatImageView = view.findViewById(R.id.item_mic_status)
    private val camStatus: AppCompatImageView = view.findViewById(R.id.item_cam_status)

    fun bind(
        participant: RoomParticipant,
        isActionable: Boolean,
        onTap: () -> Unit,
    ) {
        avatar.url = participant.avatarURL
        avatar.text = participant.getDisplayName()
        nameText.text = participant.getDisplayName()
        val isSelf = participant.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
        selfTag.visibility = if (isSelf) View.VISIBLE else View.GONE
        ownerTag.visibility = if (participant.role == ParticipantRole.OWNER) View.VISIBLE else View.GONE
        adminTag.visibility = if (participant.role == ParticipantRole.ADMIN) View.VISIBLE else View.GONE
        sharingTag.visibility = if (participant.screenShareStatus == DeviceStatus.ON) View.VISIBLE else View.GONE
        micStatus.setImageResource(
            if (participant.microphoneStatus == DeviceStatus.ON) {
                R.drawable.roomkit_icon_microphone_on
            } else {
                R.drawable.roomkit_icon_microphone_off
            },
        )
        camStatus.setImageResource(
            if (participant.cameraStatus == DeviceStatus.ON) {
                R.drawable.roomkit_icon_camera_on
            } else {
                R.drawable.roomkit_icon_camera_off
            },
        )
        if (isActionable) {
            itemView.setOnClickListener { onTap() }
        } else {
            itemView.setOnClickListener(null)
            itemView.isClickable = false
        }
    }
}
