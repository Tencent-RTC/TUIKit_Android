package com.trtc.uikit.livekit.component.audiencelist.view.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceState
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent

class AudienceListIconAdapter(
    private val audienceState: LiveAudienceState
) : RecyclerView.Adapter<AudienceListIconAdapter.ViewHolder>() {

    private var data: List<LiveUserInfo> = ArrayList(audienceState.audienceList.value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.audience_list_layout_icon_item, parent, false
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.imageHead.setContent(
            AvatarContent.URL(
                data[position].avatarURL,
                R.drawable.audience_list_default_avatar
            )
        )
    }

    override fun getItemCount(): Int {
        return minOf(data.size, 2)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData() {
        data = ArrayList(audienceState.audienceList.value)
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageHead: AtomicAvatar = itemView.findViewById(R.id.iv_head)
        val layoutRoot: RelativeLayout = itemView.findViewById(R.id.rl_root)
    }
}