package com.trtc.uikit.livekit.features.anchorview.view.coguest.panel

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.util.concurrent.CopyOnWriteArrayList

class AnchorCoGuestAdapter(private val context: Context) :
    RecyclerView.Adapter<AnchorCoGuestAdapter.LinkMicViewHolder>() {

    companion object {
        private val LOGGER = LiveKitLogger.getFeaturesLogger("AnchorCoGuestAdapter")
    }

    private val data = CopyOnWriteArrayList<SeatUserInfo>()

    init {
        initData()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkMicViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.livekit_layout_anchor_link_mic_item, parent, false)
        return LinkMicViewHolder(view)
    }

    override fun onBindViewHolder(holder: LinkMicViewHolder, position: Int) {
        val userInfo = data[position]

        if (TextUtils.isEmpty(userInfo.userName)) {
            holder.textName.text = userInfo.userID
        } else {
            holder.textName.text = userInfo.userName
        }

        holder.imageHead.setContent(
            AvatarContent.URL(
                data[position].avatarURL,
                R.drawable.livekit_ic_avatar
            )
        )

        holder.textHangUp.tag = userInfo
        holder.textHangUp.isEnabled = true
        holder.textHangUp.setOnClickListener { view ->
            view.isEnabled = false
            val taggedUserInfo = view.tag as SeatUserInfo
            LiveSeatStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
                .kickUserOutOfSeat(taggedUserInfo.userID, object : CompletionHandler {
                    override fun onSuccess() {
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                        LOGGER.error("AnchorCoGuestAdapter disconnectUser failed:code:$code,desc:$desc")
                    }
                })
        }
    }

    private fun initData() {
        val userList =
            CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coGuestState.connected.value.filterNot { it.liveID != LiveListStore.shared().liveState.currentLive.value.liveID }
        data.clear()
        data.addAll(userList)
        val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        data.removeAll { it.userID == selfUserId }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData() {
        initData()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class LinkMicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageHead: AtomicAvatar = itemView.findViewById(R.id.iv_head)
        val textName: TextView = itemView.findViewById(R.id.tv_name)
        val textHangUp: TextView = itemView.findViewById(R.id.tv_hang_up)
    }
}