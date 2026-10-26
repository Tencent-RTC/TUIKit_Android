package com.trtc.uikit.roomkit.view.main.participantlist

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomType

/**
 * Adapter for displaying participant list with avatar, name, role, and device status.
 */
class ParticipantListAdapter(private val roomType: RoomType) : RecyclerView.Adapter<ParticipantListAdapter.ParticipantViewHolder>() {

    private var participants: List<RoomParticipant> = emptyList()
    private var onItemClickListener: ((RoomParticipant) -> Unit)? = null

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newParticipants: List<RoomParticipant>) {
        participants = sortParticipants(newParticipants)
        notifyDataSetChanged()
    }

    private fun sortParticipants(participants: List<RoomParticipant>): List<RoomParticipant> {
        val currentUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        
        return participants.sortedWith(compareBy(
            // 1. Current user (me) comes first
            { it.userID != currentUserId },
            // 2. Room owner
            { it.role != ParticipantRole.OWNER },
            // 3. Admin
            { it.role != ParticipantRole.ADMIN },
            // 4. Screen sharing users
            { it.screenShareStatus != DeviceStatus.ON },
            // 5. Camera & microphone both on
            { !(it.cameraStatus == DeviceStatus.ON && it.microphoneStatus == DeviceStatus.ON) },
            // 6. Camera only
            { !(it.cameraStatus == DeviceStatus.ON && it.microphoneStatus != DeviceStatus.ON) },
            // 7. Microphone only
            { !(it.cameraStatus != DeviceStatus.ON && it.microphoneStatus == DeviceStatus.ON) },
            // 8. userName
            { it.userName }
        ))
    }

    fun setOnItemClickListener(listener: (RoomParticipant) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_participant, parent, false)
        return ParticipantViewHolder(view, roomType)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val participant = participants[position]
        holder.bind(participant)
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(participant)
        }
    }

    override fun getItemCount(): Int = participants.size

    class ParticipantViewHolder(itemView: View, private val roomType: RoomType) : RecyclerView.ViewHolder(itemView) {
        private val tvUsername: TextView = itemView.findViewById(R.id.tv_username)
        private val ivUserAvatar: ImageFilterView = itemView.findViewById(R.id.iv_avatar)
        private val ivScreenShare: ImageView = itemView.findViewById(R.id.iv_screen_share)
        private val ivMicrophone: ImageView = itemView.findViewById(R.id.iv_microphone)
        private val ivCamera: ImageView = itemView.findViewById(R.id.iv_camera)
        private val llRole: LinearLayout = itemView.findViewById(R.id.ll_role)
        private val ivUserRole: ImageView = itemView.findViewById(R.id.iv_user_role)
        private val tvUserRole: TextView = itemView.findViewById(R.id.tv_user_role)

        fun bind(participant: RoomParticipant) {
            var displayName = participant.getDisplayName()
            if (participant.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                displayName = "$displayName (${itemView.context.getString(R.string.roomkit_me)})"
            }
            tvUsername.text = displayName
            if (participant.avatarURL.isEmpty()) {
                ivUserAvatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
            } else {
                ImageLoader.load(
                    itemView.context,
                    ivUserAvatar,
                    participant.avatarURL,
                    R.drawable.roomkit_ic_default_avatar
                )
            }

            when (participant.role) {
                ParticipantRole.OWNER -> {
                    llRole.visibility = VISIBLE
                    ivUserRole.setImageResource(R.drawable.roomkit_icon_user_room_owner)
                    tvUserRole.text = itemView.context.getString(R.string.roomkit_role_owner)
                    tvUserRole.setTextColor(ContextCompat.getColor(itemView.context, R.color.roomkit_color_primary))
                }

                ParticipantRole.ADMIN -> {
                    llRole.visibility = VISIBLE
                    ivUserRole.setImageResource(R.drawable.roomkit_icon_user_room_manager)
                    tvUserRole.text = itemView.context.getString(R.string.roomkit_role_admin)
                    tvUserRole.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.roomkit_color_role_manager
                        )
                    )
                }

                ParticipantRole.GENERAL_USER -> {
                    llRole.visibility = GONE
                }
            }

            when (participant.microphoneStatus) {
                DeviceStatus.ON -> {
                    ivMicrophone.setImageResource(R.drawable.roomkit_participant_list_view_ic_microphone_on)
                }

                else -> {
                    ivMicrophone.setImageResource(R.drawable.roomkit_participant_list_view_ic_microphone_off)
                }
            }

            if (roomType == RoomType.WEBINAR) {
                return
            }

            ivCamera.visibility = VISIBLE
            when (participant.cameraStatus) {
                DeviceStatus.ON -> {
                    ivCamera.setImageResource(R.drawable.roomkit_participant_list_view_ic_camera_on)
                }

                else -> {
                    ivCamera.setImageResource(R.drawable.roomkit_participant_list_view_ic_camera_off)
                }
            }

            ivScreenShare.visibility = VISIBLE
            when (participant.screenShareStatus) {
                DeviceStatus.ON -> {
                    ivScreenShare.visibility = VISIBLE
                }

                else -> {
                    ivScreenShare.visibility = GONE
                }
            }


        }
    }
}
