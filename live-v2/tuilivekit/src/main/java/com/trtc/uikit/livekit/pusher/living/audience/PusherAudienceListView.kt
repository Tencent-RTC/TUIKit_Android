package com.trtc.uikit.livekit.pusher.living.audience

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LevelBadgeDrawable
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel

internal class PusherAudienceListView(context: Context) : BottomSheetView(context) {
    var onRefresh: (() -> Unit)? = null
    var onSelectAudience: ((PusherAudienceStore.User) -> Unit)? = null

    private val adapter = PusherAudienceListAdapter(::selectAudience)
    private var audience: List<PusherAudienceStore.User> = emptyList()
    private var selectedAudience: PusherAudienceStore.User? = null

    override val sheetHeight: Int
        get() = dp(422)

    init {
        sheetTitle = context.getString(R.string.common_anchor_audience_list_panel_title)
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_pusher_audience_list_view, contentView, true)
        contentView.findViewById<RecyclerView>(R.id.livekit_pusher_audience_list_recycler).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PusherAudienceListView.adapter
            itemAnimator = null
        }
    }

    fun bind(audience: List<PusherAudienceStore.User>) {
        if (this.audience == audience) return
        this.audience = audience
        adapter.submitList(audience.mapIndexed { index, user ->
            PusherAudienceListAdapter.Row(index + 1, audience.size.toString().length, user)
        })
    }

    override fun show() {
        selectedAudience = null
        onRefresh?.invoke()
        super.show()
    }

    fun cancelPendingSelection() {
        selectedAudience = null
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    override fun sheetDidDismiss() {
        val user = selectedAudience ?: return
        selectedAudience = null
        onSelectAudience?.invoke(user)
    }

    private fun selectAudience(user: PusherAudienceStore.User) {
        selectedAudience = user
        dismiss()
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

private class PusherAudienceListAdapter(
    private val onSelect: (PusherAudienceStore.User) -> Unit,
) : ListAdapter<PusherAudienceListAdapter.Row, PusherAudienceListAdapter.Holder>(DIFF) {
    data class Row(val rank: Int, val rankDigits: Int, val user: PusherAudienceStore.User)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.livekit_pusher_audience_list_item, parent, false),
        onSelect,
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        holder.bind(row.rank, row.rankDigits, row.user)
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == RANK_CHANGED }) {
            val row = getItem(position)
            holder.bindRank(row.rank, row.rankDigits)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    class Holder(
        itemView: View,
        private val onSelect: (PusherAudienceStore.User) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val rankView: AtomicLabel = itemView.findViewById(R.id.livekit_pusher_audience_item_rank)
        private val avatarView: AtomicAvatar = itemView.findViewById(R.id.livekit_pusher_audience_item_avatar)
        private val nameView: AtomicLabel = itemView.findViewById(R.id.livekit_pusher_audience_item_name)
        private val levelView: AppCompatImageView = itemView.findViewById(R.id.livekit_pusher_audience_item_level)
        private val giftView: AtomicLabel = itemView.findViewById(R.id.livekit_pusher_audience_item_gift)
        private var user: PusherAudienceStore.User? = null

        init {
            itemView.setOnClickListener { user?.let(onSelect) }
        }

        fun bind(rank: Int, rankDigits: Int, user: PusherAudienceStore.User) {
            this.user = user
            bindRank(rank, rankDigits)
            avatarView.setContent(
                AtomicAvatar.AvatarContent.URL(
                    user.avatarURL,
                    R.drawable.livekit_livelist_default_avatar,
                ),
            )
            nameView.text = user.userName.ifEmpty { user.userID }
            levelView.setImageDrawable(LevelBadgeDrawable.forLevel(itemView.context, user.level))
            giftView.text = "0"
        }

        fun bindRank(rank: Int, rankDigits: Int) {
            val digitWidth = ('0'..'9').maxOf { rankView.paint.measureText(it.toString()) }
            val columnWidth = maxOf(
                (12 * itemView.resources.displayMetrics.density).toInt(),
                kotlin.math.ceil(digitWidth * rankDigits).toInt(),
            )
            if (rankView.layoutParams.width != columnWidth) {
                rankView.layoutParams = rankView.layoutParams.apply { width = columnWidth }
            }
            rankView.text = rank.toString()
        }
    }

    private companion object {
        val RANK_CHANGED = Any()
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(
                oldItem: Row,
                newItem: Row,
            ): Boolean = oldItem.user.userID == newItem.user.userID

            override fun areContentsTheSame(
                oldItem: Row,
                newItem: Row,
            ): Boolean = oldItem == newItem

            override fun getChangePayload(oldItem: Row, newItem: Row): Any? =
                if (oldItem.user == newItem.user) RANK_CHANGED else null
        }
    }
}
