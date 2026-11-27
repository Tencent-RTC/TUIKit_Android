package com.trtc.uikit.livekit.player.living.audience

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LevelBadgeDrawable
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AudienceListPanel(
    context: Context,
    private val liveID: String,
    private val onMemberClick: ((LiveUserInfo) -> Unit)? = null,
    private val giftCoinsProvider: ((String) -> Long?)? = null,
    private val giftUpdates: Flow<Unit>? = null,
) : BottomSheetView(context) {

    private val audienceList: RecyclerView
    private val adapter = AudienceListPanelAdapter(giftCoinsProvider) { user ->
        val userID = user.userID.orEmpty()
        val selfUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        if (userID.isEmpty() || userID == selfUserID) return@AudienceListPanelAdapter
        dismiss()
        val handler = onMemberClick
        if (handler != null) handler(user) else AudienceProfilePanel(context, user).show()
    }

    private var subscribeJob: Job? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.livekit_audience_list_panel, contentView, false)
        audienceList = view.findViewById(R.id.rv_audience_list)

        audienceList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        audienceList.adapter = adapter
        audienceList.addItemDecoration(RowDivider(context))

        contentView.addView(view)
        sheetTitle = context.getString(R.string.common_anchor_audience_list_panel_title)
    }

    override fun show() {
        super.show()
        if (liveID.isEmpty()) return
        val store = LiveAudienceStore.create(liveID)
        val audienceListFlow = store.liveAudienceState.audienceList
        adapter.submit(audienceListFlow.value)
        store.fetchAudienceList(null)
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                audienceListFlow.collect { list ->
                    adapter.submit(list)
                }
            }
            giftUpdates?.let { updates ->
                launch {
                    updates.collect { adapter.refreshGiftValues() }
                }
            }
        }
    }

    override fun dismiss() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.dismiss()
    }

    private class AudienceListPanelAdapter(
        private val giftCoinsProvider: ((String) -> Long?)?,
        private val onUserClick: (LiveUserInfo) -> Unit,
    ) : RecyclerView.Adapter<AudienceListPanelAdapter.ViewHolder>() {

        private val data = mutableListOf<LiveUserInfo>()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<LiveUserInfo>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun refreshGiftValues() {
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.livekit_audience_list_panel_item, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = data[position]
            holder.tvMarker.text = (position + 1).toString()
            ImageLoader.loadAvatar(holder.avatar, user.avatarURL, R.drawable.livekit_livelist_default_avatar)
            val displayName = user.userName.takeIf { it.isNotEmpty() } ?: user.userID
            holder.tvName.text = if (displayName.length > 10) displayName.take(10) + "…" else displayName
            holder.ivLevelIcon.setImageDrawable(
                LevelBadgeDrawable.forLevel(holder.ivLevelIcon.context, user.level)
            )
            val coins = giftCoinsProvider?.invoke(user.userID)
            holder.tvGiftValue.text = if (coins != null) formatCoins(coins) else "0"

            val userID = user.userID
            val selfUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
            val isInteractive = userID.isNotEmpty() && userID != selfUserID
            holder.itemView.isClickable = isInteractive
            holder.itemView.isFocusable = isInteractive
            if (isInteractive) {
                holder.itemView.setDebounceClickListener { onUserClick(user) }
            } else {
                holder.itemView.setOnClickListener(null)
            }
        }

        override fun getItemCount(): Int = data.size

        private fun formatCoins(coins: Long): String {
            if (coins >= 10000) {
                val value = coins / 10000.0
                return if (value % 1.0 == 0.0) "${value.toLong()}w" else String.format("%.1fw", value)
            }
            return coins.toString()
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvMarker: AppCompatTextView = itemView.findViewById(R.id.tv_marker)
            val avatar: AppCompatImageView = itemView.findViewById(R.id.iv_head)
            val tvName: AppCompatTextView = itemView.findViewById(R.id.tv_name)
            val ivLevelIcon: ImageView = itemView.findViewById(R.id.iv_level_badge)
            val tvGiftValue: AppCompatTextView = itemView.findViewById(R.id.tv_gift_value)
        }
    }

    private class RowDivider(context: Context) : RecyclerView.ItemDecoration() {

        private val strokeHeightPx: Int = (context.resources.displayMetrics.density * 1f).toInt()
        private val paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.livekit_audience_row_separator)
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val itemCount = parent.adapter?.itemCount ?: 0
            outRect.set(0, 0, 0, if (position < itemCount - 1) strokeHeightPx else 0)
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val left = parent.paddingLeft.toFloat()
            val right = (parent.width - parent.paddingRight).toFloat()
            val childCount = parent.childCount
            val itemCount = parent.adapter?.itemCount ?: 0
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                if (position >= itemCount - 1) continue
                val top = child.bottom.toFloat()
                val bottom = top + strokeHeightPx
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }
    }
}
