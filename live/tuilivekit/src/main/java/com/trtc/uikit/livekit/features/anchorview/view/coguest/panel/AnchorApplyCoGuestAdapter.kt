package com.trtc.uikit.livekit.features.anchorview.view.coguest.panel

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine.Error.ALL_SEAT_OCCUPIED
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import java.util.concurrent.CopyOnWriteArrayList

class AnchorApplyCoGuestAdapter(
    private val context: Context
) : RecyclerView.Adapter<AnchorApplyCoGuestAdapter.ApplyLinkMicViewHolder>() {

    private val logger = LiveKitLogger.getFeaturesLogger("AnchorApplyCoGuestAdapter")
    private val data = CopyOnWriteArrayList<LiveUserInfo>()

    init {
        initData()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplyLinkMicViewHolder {
        val view = LayoutInflater.from(context).inflate(
            R.layout.livekit_layout_anchor_link_mic_panel_item_request, parent, false
        )
        return ApplyLinkMicViewHolder(view)
    }

    override fun onBindViewHolder(holder: ApplyLinkMicViewHolder, position: Int) {
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

        holder.textReject.tag = userInfo
        holder.textReject.isEnabled = true
        holder.textReject.setOnClickListener { view ->
            view.isEnabled = false
            val taggedUserInfo = view.tag as LiveUserInfo
            CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
                .rejectApplication(taggedUserInfo.userID, object : CompletionHandler {
                    override fun onSuccess() {}

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }

                })
        }

        holder.textAccept.tag = userInfo
        holder.textAccept.isEnabled = true
        holder.textAccept.setOnClickListener { view ->
            view.isEnabled = false
            val taggedUserInfo = view.tag as LiveUserInfo
            CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).acceptApplication(
                taggedUserInfo.userID,
                object : CompletionHandler {
                    override fun onSuccess() {}

                    override fun onFailure(code: Int, desc: String) {
                        if (code == ALL_SEAT_OCCUPIED.value) {
                            view.isEnabled = true
                        }
                        ErrorLocalized.onError(code)
                        logger.error("AnchorApplyCoGuestAdapter respondIntraRoomConnection failed:code:$code,desc:$desc")
                    }

                })
        }
    }

    private fun initData() {
        data.clear()
        data.addAll(CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coGuestState.applicants.value)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData() {
        initData()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class ApplyLinkMicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageHead: AtomicAvatar = itemView.findViewById(R.id.iv_head)
        val textName: TextView = itemView.findViewById(R.id.tv_name)
        val textAccept: TextView = itemView.findViewById(R.id.atomic_btn_accept)
        val textReject: TextView = itemView.findViewById(R.id.atomic_btn_reject)
    }
}