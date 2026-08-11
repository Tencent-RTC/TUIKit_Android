package com.trtc.uikit.roomkit.view.main.participantlist

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant

class PendingParticipantListAdapter :
    RecyclerView.Adapter<PendingParticipantListAdapter.PendingParticipantViewHolder>() {

    private var participants: List<RoomParticipant> = emptyList()
    private var roomID: String = ""

    fun setRoomID(roomID: String) {
        this.roomID = roomID
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newParticipants: List<RoomParticipant>) {
        participants = newParticipants.sortedWith(compareBy { it.userName })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingParticipantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_pending_participant, parent, false)
        return PendingParticipantViewHolder(view)
    }

    override fun onBindViewHolder(holder: PendingParticipantViewHolder, position: Int) {
        holder.bind(roomID, participants[position])
        holder.itemView.setOnClickListener(null)
        holder.itemView.isClickable = false
    }

    override fun getItemCount(): Int = participants.size

    class PendingParticipantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUsername: TextView = itemView.findViewById(R.id.tv_username)
        private val ivAvatar: ImageFilterView = itemView.findViewById(R.id.iv_avatar)
        private val callUserView: CallUserView = itemView.findViewById(R.id.call_user_view)

        fun bind(roomID: String, participant: RoomParticipant) {
            tvUsername.text = participant.getDisplayName()

            if (participant.avatarURL.isEmpty()) {
                ivAvatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
            } else {
                ImageLoader.load(
                    itemView.context,
                    ivAvatar,
                    participant.avatarURL,
                    R.drawable.roomkit_ic_default_avatar
                )
            }

            callUserView.bind(roomID, participant.userID, participant.roomStatus)
        }
    }
}
