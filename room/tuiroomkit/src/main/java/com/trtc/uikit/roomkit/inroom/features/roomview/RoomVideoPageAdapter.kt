package com.trtc.uikit.roomkit.inroom.features.roomview

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.FloatingWindowPosition
import io.trtc.tuikit.atomicxcore.api.device.NetworkInfo
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.VideoPageLayout

class RoomVideoPageAdapter(
    private val onPageAttached: () -> Unit,
    private val onFloatingWindowPositionChange: (FloatingWindowPosition) -> Unit,
) : RecyclerView.Adapter<RoomVideoPageAdapter.VideoPageViewHolder>() {

    var pages: List<VideoPageLayout> = emptyList()
        private set

    private val attachedHolders = mutableSetOf<VideoPageViewHolder>()
    private var playingPageKey: String? = null
    private var volumes: Map<String, Int> = emptyMap()
    private var networkQualities: Map<String, NetworkInfo> = emptyMap()
    private var floatingWindowPosition = FloatingWindowPosition()

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoPageViewHolder {
        val pageView = RoomVideoPageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        return VideoPageViewHolder(pageView)
    }

    override fun onBindViewHolder(holder: VideoPageViewHolder, position: Int) {
        val page = pages[position]
        if (holder.pageKey != page.key) holder.pageView.setPlaying(false)
        holder.pageKey = page.key
        holder.pageView.bind(
            page, volumes, floatingWindowPosition, onFloatingWindowPositionChange, networkQualities,
        )
        holder.pageView.setPlaying(page.key == playingPageKey && holder in attachedHolders)
    }

    override fun onViewAttachedToWindow(holder: VideoPageViewHolder) {
        attachedHolders.add(holder)
        val page = pages.firstOrNull { it.key == holder.pageKey }
        if (page != null) {
            holder.pageView.bind(page, volumes, floatingWindowPosition, onFloatingWindowPositionChange, networkQualities)
        }
        onPageAttached()
    }

    override fun onViewDetachedFromWindow(holder: VideoPageViewHolder) {
        holder.pageView.setPlaying(false)
        attachedHolders.remove(holder)
    }

    override fun onViewRecycled(holder: VideoPageViewHolder) {
        holder.pageView.setPlaying(false)
        attachedHolders.remove(holder)
        holder.pageKey = null
    }

    fun updatePages(
        nextPages: List<VideoPageLayout>,
        volumes: Map<String, Int>,
        floatingWindowPosition: FloatingWindowPosition,
        networkQualities: Map<String, NetworkInfo> = emptyMap(),
    ) {
        val previousPages = pages
        this.volumes = volumes
        this.networkQualities = networkQualities
        this.floatingWindowPosition = floatingWindowPosition
        if (nextPages.none { it.key == playingPageKey }) setPlayingPage(null)
        pages = nextPages
        val layoutChanged = previousPages.size != nextPages.size ||
            previousPages.indices.any { !previousPages[it].hasSameLayout(nextPages[it]) }
        if (layoutChanged) dispatchPageChanges(previousPages, nextPages)
        updateAttachedPages()
    }

    fun setPlayingPage(pageKey: String?) {
        playingPageKey = pageKey
        attachedHolders.filter { it.pageKey != pageKey }.forEach { it.pageView.setPlaying(false) }
        if (pageKey == null) return
        attachedHolders.filter { it.pageKey == pageKey }.forEach { it.pageView.setPlaying(true) }
    }

    private fun dispatchPageChanges(previousPages: List<VideoPageLayout>, nextPages: List<VideoPageLayout>) {
        val difference = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previousPages.size
            override fun getNewListSize(): Int = nextPages.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previousPages[oldItemPosition].key == nextPages[newItemPosition].key

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previousPages[oldItemPosition].hasSameLayout(nextPages[newItemPosition])
        })
        difference.dispatchUpdatesTo(this)
    }

    private fun updateAttachedPages() {
        for (holder in attachedHolders.toList()) {
            val page = pages.firstOrNull { it.key == holder.pageKey }
            if (page != null) {
                holder.pageView.bind(
                    page, volumes, floatingWindowPosition, onFloatingWindowPositionChange,
                )
            } else {
                holder.pageView.setPlaying(false)
            }
        }
    }

    class VideoPageViewHolder(val pageView: RoomVideoPageView) : RecyclerView.ViewHolder(pageView) {
        var pageKey: String? = null
    }
}
