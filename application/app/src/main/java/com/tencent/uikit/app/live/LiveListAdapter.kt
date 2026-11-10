package com.tencent.uikit.app.live

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.uikit.app.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

internal class LiveListAdapter(
    private val onItemClick: (LiveInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 1
        private const val TYPE_FOOTER = 2
    }

    private val liveInfoList = mutableListOf<LiveInfo>()
    private var isLoading = false

    @SuppressLint("NotifyDataSetChanged")
    fun setData(list: List<LiveInfo>) {
        liveInfoList.clear()
        liveInfoList.addAll(list)
        notifyDataSetChanged()
    }

    fun setLoading(loading: Boolean) {
        isLoading = loading
        notifyItemChanged(itemCount - 1)
    }

    override fun getItemCount(): Int = liveInfoList.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position == liveInfoList.size) TYPE_FOOTER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOOTER) {
            FooterViewHolder(inflater.inflate(R.layout.app_livelist_refresh_footer, parent, false))
        } else {
            LiveInfoViewHolder(inflater.inflate(R.layout.app_livelist_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FooterViewHolder -> {
                val visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
                holder.progress.visibility = visibility
                holder.loadingText.visibility = visibility
            }
            is LiveInfoViewHolder -> {
                val info = liveInfoList[position]
                holder.bind(info)
                holder.itemView.setOnClickListener { onItemClick(info) }
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (getItemViewType(position) == TYPE_FOOTER) layoutManager.spanCount else 1
        }
    }

    private class LiveInfoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cover: ImageView = view.findViewById(R.id.cover_image)
        private val avatar: ImageView = view.findViewById(R.id.iv_avatar)
        private val roomName: TextView = view.findViewById(R.id.tv_room_name)
        private val anchorName: TextView = view.findViewById(R.id.tv_anchor_name)
        private val audienceCount: TextView = view.findViewById(R.id.tv_audience_count_info)

        fun bind(info: LiveInfo) {
            ImageLoader.load(
                itemView.context, cover,
                info.coverURL.ifEmpty { LiveKitResources.defaultCoverURL },
                R.drawable.app_livelist_default_cover
            )
            ImageLoader.loadAvatar(avatar, info.liveOwner.avatarURL, R.drawable.app_livelist_default_avatar)
            roomName.text = info.liveName.ifEmpty { info.liveID }
            anchorName.text = info.liveOwner.userName.ifEmpty { info.liveOwner.userID }
            audienceCount.text = info.totalViewerCount.toString()
        }
    }

    private class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val progress: ProgressBar = view.findViewById(R.id.pb_progress_bar)
        val loadingText: TextView = view.findViewById(R.id.tv_loading_data)
    }

    class GridDividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val spacing = (3 * context.resources.displayMetrics.density).toInt()

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.set(spacing, spacing, spacing, spacing)
        }
    }
}
