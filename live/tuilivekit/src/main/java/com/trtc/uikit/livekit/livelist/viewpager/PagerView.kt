package com.trtc.uikit.livekit.livelist.viewpager

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.trtc.uikit.livekit.player.PlayerStatus
import com.trtc.uikit.livekit.player.PlayerView
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class SwipeBehavior {
    PREVIEW_ONLY,
    AUTO_ENTER_ROOM
}

@SuppressLint("ViewConstructor")
internal class PagerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val initialLiveID: String = "",
    private val onLoadMoreCallback: () -> Unit = {},
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val LOAD_MORE_THRESHOLD: Int = 2
    }

    private val fragmentActivity: FragmentActivity = context as FragmentActivity
    private val adapter: InnerAdapter by lazy { InnerAdapter() }
    private val viewPager2: ViewPager2

    private var currentPosition = -1
    private var positionOffset = -1f
    private var currentItemId: String? = null
    private var willSlideInItemId: String? = null
    private var willSlideOutItemId: String? = null
    private var didSettleCurrentGesture = false

    private var currentPlayerView: PlayerView? = null
    private var currentPlayerStatusJob: Job? = null
    private var swipeBehavior = SwipeBehavior.PREVIEW_ONLY
        set(value) {
            if (field == value) return
            field = value
            applyJoinLiveEntranceEnabled()
        }
    private var swipeDisabledByJoin = false
    private var swipeDisabledByCoGuest = false

    init {
        viewPager2 = createViewPager()
        viewPager2.adapter = adapter
        addView(viewPager2)
        prependInitialLiveIDIfNeeded()
    }

    fun setData(liveList: List<LiveInfo>) {
        adapter.setData(mergeWithInitialLiveID(deduplicated(liveList)))
        resyncCurrentPageAfterDataChange()
    }

    fun appendData(liveList: List<LiveInfo>) {
        val deduped = deduplicated(liveList)
        if (deduped.isEmpty()) return

        val currentList = adapter.dataList
        val existingIndexByID = HashMap<String, Int>(currentList.size)
        currentList.forEachIndexed { index, info -> existingIndexByID[info.liveID] = index }

        val toAppend = mutableListOf<LiveInfo>()
        for (info in deduped) {
            val index = existingIndexByID[info.liveID]
            if (index != null) {
                adapter.updateItem(index, info)
            } else {
                toAppend.add(info)
            }
        }

        if (toAppend.isNotEmpty()) {
            adapter.appendData(toAppend)
        }
    }

    private fun notifyWillSlideIn(itemId: String?) {
        cellOf(itemId)?.startPreview()
    }

    private fun notifyDidSlideIn(itemId: String?) {
        cellOf(itemId)?.joinChannel()
    }

    private fun notifySlideInCancelled(itemId: String?) {
        cellOf(itemId)?.stopPreview()
    }

    private fun notifyWillSlideOut(itemId: String?) {
    }

    private fun notifyDidSlideOut(itemId: String?) {
        cellOf(itemId)?.leaveChannel()
    }

    private fun notifySlideOutCancelled(itemId: String?) {
    }

    fun onDetached() {
        stopAll()
        swipeBehavior = SwipeBehavior.PREVIEW_ONLY
        stopObservingCurrentPlayerStatus()
    }

    private fun createViewPager(): ViewPager2 {
        return ViewPager2(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            offscreenPageLimit = 1
            orientation = ViewPager2.ORIENTATION_VERTICAL
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    when (state) {
                        ViewPager2.SCROLL_STATE_DRAGGING -> {
                            willSlideInItemId = null
                            willSlideOutItemId = null
                            positionOffset = -1f
                            didSettleCurrentGesture = false
                        }

                        ViewPager2.SCROLL_STATE_IDLE -> {
                            onScrollEnd()
                            didSettleCurrentGesture = false
                        }

                        else -> {}
                    }
                }

                override fun onPageSelected(position: Int) {
                    if (adapter !is InnerAdapter) return
                    val targetItemId = (adapter as InnerAdapter).itemIdAt(position) ?: return
                    if (currentPosition == -1) {
                        notifyWillSlideIn(targetItemId)
                        notifyDidSlideIn(targetItemId)
                        currentPosition = position
                        currentItemId = targetItemId
                        return
                    }
                    if (!isSliding()) return
                    val pendingItemId = willSlideInItemId
                    if (pendingItemId != null && pendingItemId != targetItemId) {
                        notifySlideInCancelled(pendingItemId)
                        notifyWillSlideIn(targetItemId)
                    }
                    val outgoingItemId = willSlideOutItemId ?: currentItemId
                    willSlideInItemId = null
                    willSlideOutItemId = null
                    didSettleCurrentGesture = true
                    notifyDidSlideOut(outgoingItemId)
                    notifyDidSlideIn(targetItemId)
                    currentPosition = position
                    currentItemId = targetItemId
                }

                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int
                ) {
                    if (positionOffset <= 0) return
                    if (didSettleCurrentGesture) return
                    if (isSliding()) return
                    if (isSlideToPrevious(position, positionOffset)) {
                        onSlideToPrevious()
                    } else {
                        onSlideToNext()
                    }
                    this@PagerView.positionOffset = positionOffset
                }
            })
        }
    }

    private fun resyncCurrentPageAfterDataChange() {
        post {
            if (currentPosition == -1) return@post
            val position = viewPager2.currentItem
            val targetItemId = adapter.itemIdAt(position) ?: return@post
            currentPosition = position
            if (targetItemId == currentItemId) return@post
            notifyDidSlideOut(currentItemId)
            notifyDidSlideIn(targetItemId)
            currentItemId = targetItemId
        }
    }

    private fun isSlideToPrevious(position: Int, positionOffset: Float): Boolean {
        if (position < currentPosition) {
            if (this.positionOffset == -1f) return true
            return positionOffset < this.positionOffset
        }
        return false
    }

    private fun isSliding(): Boolean {
        return willSlideInItemId != null || willSlideOutItemId != null
    }

    private fun onSlideToNext() {
        slideTo(currentPosition + 1)
    }

    private fun onSlideToPrevious() {
        slideTo(currentPosition - 1)
    }

    private fun slideTo(targetPosition: Int) {
        if (targetPosition < 0 || targetPosition >= adapter.dataList.size) return
        val outItemId = adapter.itemIdAt(currentPosition) ?: return
        val inItemId = adapter.itemIdAt(targetPosition) ?: return
        willSlideOutItemId = outItemId
        willSlideInItemId = inItemId
        notifyWillSlideOut(outItemId)
        notifyWillSlideIn(inItemId)
    }

    private fun onScrollEnd() {
        if (!isSliding()) return
        val settledItemId = adapter.itemIdAt(viewPager2.currentItem)
        if (settledItemId != null && settledItemId != willSlideInItemId) {
            willSlideInItemId?.let { notifySlideInCancelled(it) }
            willSlideOutItemId?.let { notifySlideOutCancelled(it) }
        }
        willSlideInItemId = null
        willSlideOutItemId = null
    }

    private fun cellOf(itemId: String?): PageCell? {
        if (itemId.isNullOrEmpty()) return null
        return adapter.getCellByItemId(itemId)
    }

    private fun handleDidSlideIn(playerView: PlayerView, remainingCount: Int) {
        currentPlayerView = playerView
        observeCurrentPlayerStatus(playerView)
        if (remainingCount <= LOAD_MORE_THRESHOLD) {
            onLoadMoreCallback()
        }
    }

    private fun handleDidSlideOut(playerView: PlayerView) {
        if (currentPlayerView == playerView) {
            currentPlayerView = null
        }
    }

    private fun handleCloseClicked() {
        swipeBehavior = SwipeBehavior.PREVIEW_ONLY
    }

    private fun handleJoinClicked() {
        swipeBehavior = SwipeBehavior.AUTO_ENTER_ROOM
    }

    private fun handleJoinChannelStart() {
        swipeDisabledByJoin = true
        updateSwipeEnabled()
    }

    private fun handleJoinChannelResult(success: Boolean) {
        swipeDisabledByJoin = false
        updateSwipeEnabled()
    }

    private fun prependInitialLiveIDIfNeeded() {
        if (initialLiveID.isEmpty()) return
        val initialLiveInfo = LiveListStore.shared().liveState.liveList.value.find { it.liveID == initialLiveID }
            ?: LiveInfo().apply { liveID = initialLiveID }
        adapter.setData(listOf(initialLiveInfo))
    }

    private fun mergeWithInitialLiveID(liveList: List<LiveInfo>): List<LiveInfo> {
        if (initialLiveID.isEmpty()) return liveList
        val existingIndex = liveList.indexOfFirst { it.liveID == initialLiveID }
        if (existingIndex == 0) return liveList
        val result = liveList.toMutableList()
        if (existingIndex > 0) {
            val existing = result.removeAt(existingIndex)
            result.add(0, existing)
        } else {
            val initialLiveInfo = LiveListStore.shared().liveState.liveList.value.find { it.liveID == initialLiveID }
                ?: LiveInfo().apply { liveID = initialLiveID }
            result.add(0, initialLiveInfo)
        }
        return result
    }

    private fun deduplicated(list: List<LiveInfo>): List<LiveInfo> {
        val seen = HashSet<String>(list.size)
        return list.filter { seen.add(it.liveID) }
    }

    private fun updateSwipeEnabled() {
        viewPager2.isUserInputEnabled = !(swipeDisabledByJoin || swipeDisabledByCoGuest)
    }

    private fun applyJoinLiveEntranceEnabled() {
        val enabled = swipeBehavior == SwipeBehavior.PREVIEW_ONLY
        for (position in adapter.dataList.indices) {
            val itemId = adapter.itemIdAt(position) ?: continue
            adapter.getCellByItemId(itemId)?.setJoinLiveEntranceEnabled(enabled)
        }
    }

    private fun observeCurrentPlayerStatus(playerView: PlayerView) {
        currentPlayerStatusJob?.cancel()
        swipeDisabledByCoGuest = false
        updateSwipeEnabled()
        currentPlayerStatusJob = fragmentActivity.lifecycleScope.launch {
            playerView.status.collect { status ->
                swipeDisabledByCoGuest = status == PlayerStatus.CO_GUEST
                updateSwipeEnabled()
            }
        }
    }

    private fun stopObservingCurrentPlayerStatus() {
        currentPlayerStatusJob?.cancel()
        currentPlayerStatusJob = null
        swipeDisabledByCoGuest = false
        updateSwipeEnabled()
    }

    private fun stopAll() {
        currentPlayerView?.stopPlay()
        currentPlayerView?.leaveChannel()
        currentPlayerView = null
    }

    private inner class InnerAdapter : FragmentStateAdapter(fragmentActivity) {

        private val fragmentManager: FragmentManager = fragmentActivity.supportFragmentManager
        val dataList = mutableListOf<LiveInfo>()

        fun getCellByPosition(position: Int): PageCell? {
            if (position !in dataList.indices) return null
            return fragmentManager.findFragmentByTag("f${getItemId(position)}") as? PageCell
        }

        fun getCellByItemId(itemId: String): PageCell? {
            return fragmentManager.findFragmentByTag("f${itemId.hashCode().toLong()}") as? PageCell
        }

        fun itemIdAt(position: Int): String? {
            if (position !in dataList.indices) return null
            return dataList[position].liveID
        }

        fun indexOfItemId(itemId: String): Int {
            return dataList.indexOfFirst { it.liveID == itemId }
        }

        override fun getItemId(position: Int): Long {
            if (position !in dataList.indices) return RecyclerView.NO_ID
            return dataList[position].liveID.hashCode().toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return dataList.any { it.liveID.hashCode().toLong() == itemId }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun setData(list: List<LiveInfo>) {
            dataList.clear()
            dataList.addAll(list)
            notifyDataSetChanged()
            list.forEachIndexed { index, data ->
                getCellByPosition(index)?.updateData(data)
            }
        }

        fun appendData(list: List<LiveInfo>) {
            if (list.isEmpty()) return
            val startPosition = dataList.size
            dataList.addAll(list)
            notifyItemRangeInserted(startPosition, list.size)
        }

        fun updateItem(position: Int, data: LiveInfo) {
            if (position !in dataList.indices) return
            dataList[position] = data
            getCellByPosition(position)?.updateData(data)
        }

        override fun createFragment(position: Int): Fragment {
            val data = dataList[position]
            val itemId = data.liveID
            return PageCell(
                liveInfo = data,
                getSwipeBehavior = { swipeBehavior },
                onCloseClicked = { handleCloseClicked() },
                onJoinClicked = { handleJoinClicked() },
                onDidSlideIn = { view ->
                    val idx = indexOfItemId(itemId).coerceAtLeast(0)
                    val size = dataList.size
                    val remaining = if (size <= 0) 0 else size - 1 - idx
                    handleDidSlideIn(view, remaining)
                },
                onDidSlideOut = { handleDidSlideOut(it) },
                onJoinChannelStart = { handleJoinChannelStart() },
                onJoinChannelResult = { success -> handleJoinChannelResult(success) },
            )
        }

        override fun getItemCount(): Int = dataList.size
    }
}