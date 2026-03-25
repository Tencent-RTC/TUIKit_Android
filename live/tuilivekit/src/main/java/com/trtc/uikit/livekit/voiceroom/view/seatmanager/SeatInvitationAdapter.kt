package com.trtc.uikit.livekit.voiceroom.view.seatmanager

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.button.AtomicButton
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonColorType
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonVariant
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import java.util.concurrent.CopyOnWriteArrayList

class SeatInvitationAdapter(
    private val context: Context,
) : RecyclerView.Adapter<SeatInvitationAdapter.ViewHolder>() {

    private var onInviteButtonClickListener: OnInviteButtonClickListener? = null
    private val data = CopyOnWriteArrayList<LiveUserInfo>()
    private val liveListStore = LiveListStore.shared()
    private val liveSeatStore =
        LiveSeatStore.create(liveListStore.liveState.currentLive.value.liveID)
    private val coGuestStore = CoGuestStore.create(liveListStore.liveState.currentLive.value.liveID)

    init {
        initData()
    }

    private fun initData() {
        data.clear()
        val audienceList =
            LiveAudienceStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
                .liveAudienceState.audienceList.value
        val seatList = liveSeatStore.liveSeatState.seatList.value
        for (userInfo in audienceList) {
            if (!seatList.none { it.userInfo.userID == userInfo.userID }) {
                continue
            }
            data.add(userInfo)
        }
    }

    fun setOnInviteButtonClickListener(listener: OnInviteButtonClickListener) {
        onInviteButtonClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.livekit_voiceroom_item_invite_audience,
            parent, false
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val userInfo = data[position]
        holder.imageHead.setContent(
            AvatarContent.URL(
                userInfo.avatarURL,
                R.drawable.livekit_ic_avatar
            )
        )

        holder.textName.text = if (TextUtils.isEmpty(userInfo.userName)) {
            userInfo.userID
        } else {
            userInfo.userName
        }

        val invited = coGuestStore.coGuestState.invitees.value
            .find { it.userID == userInfo.userID } != null

        if (invited) {
            holder.inviteButton.isSelected = true
            holder.inviteButton.text = context.getString(R.string.common_cancel)
            holder.inviteButton.variant = ButtonVariant.OUTLINED
            holder.inviteButton.colorType = ButtonColorType.SECONDARY
        } else {
            holder.inviteButton.isSelected = false
            holder.inviteButton.text = context.getString(R.string.common_voiceroom_invite)
            holder.inviteButton.variant = ButtonVariant.FILLED
            holder.inviteButton.colorType = ButtonColorType.PRIMARY
        }

        holder.inviteButton.setOnClickListener {
            onInviteButtonClickListener?.onItemClick(holder.inviteButton, userInfo)
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData() {
        initData()
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageHead: AtomicAvatar = itemView.findViewById(R.id.iv_head)
        val textName: TextView = itemView.findViewById(R.id.tv_name)
        val textLevel: TextView = itemView.findViewById(R.id.tv_level)
        val inviteButton: AtomicButton = itemView.findViewById(R.id.invite_button)
    }

    interface OnInviteButtonClickListener {
        fun onItemClick(inviteButton: View, userInfo: LiveUserInfo)
    }
}