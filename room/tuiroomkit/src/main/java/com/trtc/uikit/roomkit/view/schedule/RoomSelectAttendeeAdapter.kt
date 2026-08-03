package com.trtc.uikit.roomkit.view.schedule

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

/**
 * Adapter for the friend list shown on the "Add attendee" page.
 * The current selection set (of userIDs) is pushed in via [setSelectedIds]; item clicks are
 * forwarded to [onToggle] so the caller can flip selection state.
 */
internal class RoomSelectAttendeeAdapter(
    private val context: Context,
    private val onToggle: (ContactInfo) -> Unit
) : RecyclerView.Adapter<RoomSelectAttendeeAdapter.ViewHolder>() {

    private val items = mutableListOf<ContactInfo>()
    private val selectedIds = mutableSetOf<String>()

    fun setData(list: List<ContactInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setSelectedIds(ids: Collection<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_select_attendee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = items[position]
        val isSelected = selectedIds.contains(info.userID)
        holder.bind(context, info, isSelected, onToggle)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivSelect: ImageView = itemView.findViewById(R.id.iv_attendee_select)
        private val ivAvatar: ImageFilterView = itemView.findViewById(R.id.iv_attendee_avatar)
        private val tvName: TextView = itemView.findViewById(R.id.tv_attendee_name)

        fun bind(
            context: Context,
            info: ContactInfo,
            isSelected: Boolean,
            onToggle: (ContactInfo) -> Unit
        ) {
            ivSelect.setImageDrawable(null)
            ivSelect.setBackgroundResource(
                if (isSelected) R.drawable.roomkit_bg_attendee_selected
                else R.drawable.roomkit_bg_attendee_unselected
            )
            tvName.text = displayName(info)
            ImageLoader.load(context, ivAvatar, info.avatarURL, R.drawable.roomkit_ic_default_avatar)
            itemView.setOnClickListener { onToggle(info) }
        }

        private fun displayName(info: ContactInfo): String {
            val remark = info.friendRemark
            if (!remark.isNullOrEmpty()) return remark
            val nickname = info.nickname
            if (!nickname.isNullOrEmpty()) return nickname
            return info.userID
        }
    }
}
