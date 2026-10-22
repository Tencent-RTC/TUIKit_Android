package com.trtc.uikit.roomkit.inroom.features.participants.participantlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class ParticipantAdapter(
    private val onParticipantTap: (participant: RoomParticipant) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row {
        data class Section(val title: String, val count: Int) : Row
        data class Participant(val value: RoomParticipant, val isSelf: Boolean, val isSharing: Boolean) : Row
    }

    private var rows: List<Row> = emptyList()
    private var joinedCollapsed = false
    private var participants: List<RoomParticipant> = emptyList()
    private var sectionTitle: String = ""
    private var screenSharerID: String? = null

    fun updateData(list: List<RoomParticipant>, sectionTitle: String, screenSharerID: String?) {
        participants = list
        this.sectionTitle = sectionTitle
        this.screenSharerID = screenSharerID
        rebuildRows()
    }

    private fun rebuildRows() {
        val localUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID
        rows = buildList {
            add(Row.Section(sectionTitle, participants.size))
            if (!joinedCollapsed) {
                participants.forEach {
                    add(Row.Participant(it, it.userID == localUserID, it.userID == screenSharerID))
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SECTION) {
            SectionHolder(inflater.inflate(R.layout.roomkit_participant_section_header, parent, false))
        } else {
            ParticipantItemHolder(inflater.inflate(R.layout.roomkit_participant_item, parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Section) VIEW_TYPE_SECTION else VIEW_TYPE_PARTICIPANT

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Section -> {
                (holder as SectionHolder).bind(row.title, row.count, joinedCollapsed) {
                    joinedCollapsed = !joinedCollapsed
                    rebuildRows()
                }
            }
            is Row.Participant -> {
                (holder as ParticipantItemHolder).bind(row.value, row.isSelf, row.isSharing) { onParticipantTap(row.value) }
            }
        }
    }

    private companion object {
        const val VIEW_TYPE_SECTION = 0
        const val VIEW_TYPE_PARTICIPANT = 1
    }

    class SectionHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val chevron: AppCompatImageView = view.findViewById(R.id.section_chevron)
        private val titleText: AppCompatTextView = view.findViewById(R.id.section_title)
        private val countText: AppCompatTextView = view.findViewById(R.id.section_count)

        fun bind(title: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
            titleText.text = title
            countText.text = count.toString()
            chevron.rotation = if (collapsed) -90f else 0f
            itemView.setOnClickListener { onToggle() }
        }
    }

    class ParticipantItemHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val avatar: RoomAvatar = view.findViewById(R.id.item_avatar)
        private val nameText: AppCompatTextView = view.findViewById(R.id.item_name)
        private val selfTag: AppCompatTextView = view.findViewById(R.id.item_self_tag)
        private val ownerTag: AppCompatTextView = view.findViewById(R.id.item_owner_tag)
        private val adminTag: AppCompatTextView = view.findViewById(R.id.item_admin_tag)
        private val sharingTag: View = view.findViewById(R.id.item_sharing_tag)
        private val micStatus: AppCompatImageView = view.findViewById(R.id.item_mic_status)
        private val camStatus: AppCompatImageView = view.findViewById(R.id.item_cam_status)

        fun bind(participant: RoomParticipant, isSelf: Boolean, isSharing: Boolean, onTap: () -> Unit) {
            avatar.url = participant.avatarURL
            avatar.text = participant.getDisplayName()
            nameText.text = participant.getDisplayName()
            selfTag.visibility = if (isSelf) View.VISIBLE else View.GONE
            ownerTag.visibility = if (participant.role == ParticipantRole.OWNER) View.VISIBLE else View.GONE
            adminTag.visibility = if (participant.role == ParticipantRole.ADMIN) View.VISIBLE else View.GONE
            sharingTag.visibility = if (isSharing) View.VISIBLE else View.GONE
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
            itemView.setOnClickListener { onTap() }
        }
    }
}
