package com.trtc.uikit.roomkit.base.ui.contactpicker

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.room.RoomUser

class SelectedContactAvatarAdapter(
    private val context: Context
) : RecyclerView.Adapter<SelectedContactAvatarAdapter.ViewHolder>() {

    private val items = mutableListOf<RoomUser>()

    fun setData(list: List<RoomUser>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_selected_avatar, parent, false) as ImageFilterView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = items[position]
        ImageLoader.load(context, holder.avatarView, user.avatarURL, R.drawable.roomkit_ic_default_avatar)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val avatarView: ImageFilterView) : RecyclerView.ViewHolder(avatarView)
}
