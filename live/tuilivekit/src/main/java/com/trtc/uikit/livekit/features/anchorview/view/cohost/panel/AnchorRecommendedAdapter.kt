package com.trtc.uikit.livekit.features.anchorview.view.cohost.panel

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.BuildConfig
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.store.CoHostAnchor
import com.trtc.uikit.livekit.features.anchorview.store.ConnectionStatus
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.button.AtomicButton
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonColorType
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonVariant
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.CoHostLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.util.concurrent.CopyOnWriteArrayList

class AnchorRecommendedAdapter(
    private val context: Context,
    private val anchorStore: AnchorStore
) : RecyclerView.Adapter<AnchorRecommendedAdapter.RecommendViewHolder>() {

    private val logger = LiveKitLogger.getFeaturesLogger("AnchorRecommendedAdapter")
    private val data = CopyOnWriteArrayList<CoHostAnchor>()

    init {
        initData()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendViewHolder {
        val view = LayoutInflater.from(context).inflate(
            R.layout.livekit_layout_anchor_connection_recommendation_list, parent, false
        )
        return RecommendViewHolder(view)
    }

    fun updateData(recommendList: List<CoHostAnchor>) {
        data.clear()
        val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        for (recommendUser in recommendList) {
            if (!TextUtils.equals(recommendUser.userId, selfUserId)) {
                data.add(recommendUser)
            }
        }
    }

    override fun onBindViewHolder(holder: RecommendViewHolder, position: Int) {
        val recommendUser = data[position]

        setUserName(holder, recommendUser)
        setAvatar(holder, recommendUser)
        setConnectionStatus(holder, recommendUser)
        setConnectionClickListener(holder, recommendUser)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    private fun setUserName(holder: RecommendViewHolder, recommendUser: CoHostAnchor) {
        holder.textName.text = if (TextUtils.isEmpty(recommendUser.userName)) {
            recommendUser.userId
        } else {
            recommendUser.userName
        }
    }

    private fun setAvatar(holder: RecommendViewHolder, recommendUser: CoHostAnchor) {
        holder.imageHead.setContent(
            AvatarContent.URL(
                recommendUser.avatarUrl,
                R.drawable.livekit_ic_avatar
            )
        )
    }

    private fun setConnectionStatus(holder: RecommendViewHolder, recommendUser: CoHostAnchor) {
        if (recommendUser.connectionStatus == ConnectionStatus.INVITING) {
            holder.buttonConnect.text = context.getString(R.string.common_connect_inviting)
            holder.buttonConnect.colorType = ButtonColorType.SECONDARY
            holder.buttonConnect.variant = ButtonVariant.OUTLINED
        } else {
            holder.buttonConnect.text = context.getString(R.string.common_voiceroom_invite)
            holder.buttonConnect.colorType = ButtonColorType.PRIMARY
            holder.buttonConnect.variant = ButtonVariant.FILLED
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setConnectionClickListener(holder: RecommendViewHolder, recommendUser: CoHostAnchor) {
        holder.buttonConnect.setOnClickListener {
            if (recommendUser.connectionStatus == ConnectionStatus.UNKNOWN) {
                recommendUser.connectionStatus = ConnectionStatus.INVITING
                notifyDataSetChanged()
                val currentLiveId = LiveListStore.shared().liveState.currentLive.value.liveID
                CoHostStore.create(currentLiveId).requestHostConnection(
                    recommendUser.roomId, CoHostLayoutTemplate.HOST_DYNAMIC_GRID, COHOST_REQUEST_TIMEOUT, "",
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.error("AnchorRecommendedAdapter requestHostConnection onSuccess")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("AnchorRecommendedAdapter requestHostConnection failed:code:$code,desc:$desc")
                            anchorStore.getAnchorCoHostStore()
                                .restoreConnectionStatus(recommendUser.roomId, ConnectionStatus.UNKNOWN)
                            ErrorLocalized.onError(code)
                        }
                    })
            }
        }
    }

    private fun initData() {
        anchorStore.getCoHostState().recommendUsers.value.let {
            data.clear()
            val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
            for (recommendUser in it) {
                if (!TextUtils.equals(recommendUser.userId, selfUserId)) {
                    data.add(recommendUser)
                }
            }
        }
    }

    class RecommendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageHead: AtomicAvatar = itemView.findViewById(R.id.iv_head)
        val textName: TextView = itemView.findViewById(R.id.tv_name)
        val buttonConnect: AtomicButton = itemView.findViewById(R.id.atomic_btn_connect)
    }

    companion object {
private val COHOST_REQUEST_TIMEOUT = if (BuildConfig.LIVE_DEV_MODE) 30 else 10
    }
}