package com.trtc.uikit.roomkit.view.schedule

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.room.RoomUser

/**
 * Adapter for the "selected attendees" list (avatar + name + optional delete button).
 *

 * Two modes:
 * - **Editable** (selection flow "selected members" dialog): pass [onRemove]; each item shows a
 *   delete button that fires the callback.
 * - **Read-only** (scheduled-room detail attendees dialog): pass `null` for [onRemove]; the delete
 *   button is hidden.
 */
internal class RoomSelectedAttendeeAdapter(
    private val context: Context,
    private val onRemove: ((RoomUser) -> Unit)? = null
) : RecyclerView.Adapter<RoomSelectedAttendeeAdapter.ViewHolder>() {

    private val items = mutableListOf<RoomUser>()

    fun setData(list: List<RoomUser>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_selected_attendee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(context, items[position], onRemove)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageFilterView = itemView.findViewById(R.id.iv_selected_avatar)
        private val tvName: TextView = itemView.findViewById(R.id.tv_selected_name)
        private val flDelete: FrameLayout = itemView.findViewById(R.id.fl_delete_selected)

        fun bind(context: Context, user: RoomUser, onRemove: ((RoomUser) -> Unit)?) {
            tvName.text = user.userName.ifEmpty { user.userID }
            ImageLoader.load(context, ivAvatar, user.avatarURL, R.drawable.roomkit_ic_default_avatar)
            if (onRemove == null) {
                flDelete.visibility = View.GONE
                flDelete.setOnClickListener(null)
            } else {
                flDelete.visibility = View.VISIBLE
                flDelete.setOnClickListener { onRemove(user) }
            }
        }
    }
}
