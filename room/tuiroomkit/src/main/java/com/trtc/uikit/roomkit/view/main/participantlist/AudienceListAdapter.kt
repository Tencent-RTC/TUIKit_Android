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
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomUser

class AudienceListAdapter : RecyclerView.Adapter<AudienceListAdapter.AudienceViewHolder>() {

    private var audienceList: List<RoomUser> = emptyList()
    private var adminUserIds: Set<String> = emptySet()
    private var onItemClickListener: ((RoomUser) -> Unit)? = null

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newAudienceList: List<RoomUser>) {
        audienceList = sortAudienceList(newAudienceList)
        notifyDataSetChanged()
    }

    private fun sortAudienceList(audienceList: List<RoomUser>): List<RoomUser> {
        val currentUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        
        return audienceList.sortedWith(compareBy(
            // 1. Current user (me) comes first
            { it.userID != currentUserId },
            // 2. Admin
            { !adminUserIds.contains(it.userID) },
            // 3. userName
            { it.userName }
        ))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateAdminList(adminIds: Set<String>) {
        adminUserIds = adminIds
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: (RoomUser) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudienceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_audience, parent, false)
        return AudienceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudienceViewHolder, position: Int) {
        val audience = audienceList[position]
        val isAdmin = adminUserIds.contains(audience.userID)
        holder.bind(audience, isAdmin)
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(audience)
        }
    }

    override fun getItemCount(): Int = audienceList.size

    class AudienceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUsername: TextView = itemView.findViewById(R.id.tv_username)
        private val ivUserAvatar: ImageFilterView = itemView.findViewById(R.id.iv_avatar)
        private val llRole: LinearLayout = itemView.findViewById(R.id.ll_role)
        private val ivUserRole: ImageView = itemView.findViewById(R.id.iv_user_role)
        private val tvUserRole: TextView = itemView.findViewById(R.id.tv_user_role)

        fun bind(audience: RoomUser, isAdmin: Boolean) {
            var displayName = audience.userName.ifEmpty { audience.userID }
            if (audience.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                displayName = "$displayName (${itemView.context.getString(R.string.roomkit_me)})"
            }
            tvUsername.text = displayName

            if (audience.avatarURL.isEmpty()) {
                ivUserAvatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
            } else {
                ImageLoader.load(
                    itemView.context,
                    ivUserAvatar,
                    audience.avatarURL,
                    R.drawable.roomkit_ic_default_avatar
                )
            }

            if (isAdmin) {
                llRole.visibility = VISIBLE
                ivUserRole.setImageResource(R.drawable.roomkit_icon_user_room_manager)
                tvUserRole.text = itemView.context.getString(R.string.roomkit_role_admin)
                tvUserRole.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.roomkit_color_role_manager
                    )
                )
            } else {
                llRole.visibility = GONE
            }
        }
    }
}
