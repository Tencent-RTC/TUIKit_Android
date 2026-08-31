package com.trtc.uikit.roomkit.view.main.roomview

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.base.utils.pxToDp
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

class GridRoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val PAGING_GRID_ROWS = 3
        private const val PAGING_GRID_COLUMNS = 2
        private const val PAGE_SIZE = PAGING_GRID_ROWS * PAGING_GRID_COLUMNS
        private const val MAX_RECYCLED_VIEWS = 12
        private const val ITEM_SPACING_DP = 8
        private const val SPEAKING_VOLUME_THRESHOLD = 25
    }

    private val logger = RoomKitLogger.getLogger("GridRoomView")

    private val recyclerView: RecyclerView
    private val arrowLeft: ImageView
    private val arrowRight: ImageView
    private val adapter: RoomVideoGridAdapter
    private val layoutStrategy: RoomVideoLayoutStrategy
    private val itemSizeDecoration: RoomVideoGridDecoration

    private var itemWidthPx = 0
    private var itemHeightPx = 0
    private val spacingPx by lazy { dpToPx(ITEM_SPACING_DP) }
    private val speakingStateCache = mutableMapOf<String, Boolean>()

    private var cachedVisibleRange: PagedVideoLayoutManager.VisibleRange? = null

    private var latestSpeakingMap: Map<String, Int> = emptyMap()

    var onOrientationSwitchClick: (() -> Unit)? = null
        set(value) {
            field = value
            adapter.onOrientationSwitchClick = value
        }

    var onPageIndexChanged: ((pageIndex: Int) -> Unit)? = null
    var onVisibleItemsUpdated: (() -> Unit)? = null

    private var lastReportedPageIndex: Int = -1

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_grid_room_view, this, true)
        recyclerView = findViewById(R.id.rv_video_grid)
        arrowLeft = findViewById(R.id.iv_arrow_left)
        arrowRight = findViewById(R.id.iv_arrow_right)

        calculateItemSize()

        itemSizeDecoration = RoomVideoGridDecoration(itemWidthPx, itemHeightPx, spacingPx)
        adapter = RoomVideoGridAdapter()
        layoutStrategy = RoomVideoLayoutStrategy(context, recyclerView, itemSizeDecoration)

        adapter.onDataUpdateCompleted = {
            recyclerView.post { updateVisibleItems() }
        }

        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(itemSizeDecoration)
        recyclerView.setItemViewCacheSize(0)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, MAX_RECYCLED_VIEWS)
        recyclerView.itemAnimator = null
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val newRange = layoutStrategy.getVisibleRange() ?: return
                if (newRange == cachedVisibleRange) return
                updateVisibleItems()
            }
        })
    }

    fun bind(
        participants: List<RoomParticipant>,
        screenShareParticipant: RoomParticipant? = null
    ) {
        val displayList = buildList {
            screenShareParticipant?.let { add(VideoStreamItem.screenShare(it)) }
            participants.forEach { add(VideoStreamItem.camera(it)) }
        }

        val hasScreenShare = screenShareParticipant != null
        logger.info(
            "bind: participants=${participants.size}, hasScreenShare=$hasScreenShare"
        )

        layoutStrategy.configureForParticipantCount(displayList.size, hasScreenShare = hasScreenShare)
        adapter.updateData(displayList)
        updateArrowsVisibility()

        speakingStateCache.keys.removeAll { userId ->
            displayList.none { it.participant.userID == userId }
        }

        recyclerView.post {
            val range = layoutStrategy.getVisibleRange() ?: return@post
            cachedVisibleRange = range
            updateArrowsVisibility()
            maybeNotifyPageIndexChanged(range.pageIndex)
        }
    }

    fun updateSpeakingStates(speakingMap: Map<String, Int>) {
        latestSpeakingMap = speakingMap
        val currentRange = cachedVisibleRange ?: return

        forEachViewHolder { holder, position, streamItem ->
            val isVisible = position in currentRange.startPosition..currentRange.endPosition
            if (isVisible && streamItem.streamType == VideoStreamType.CAMERA) {
                updateViewHolderSpeakingState(holder, streamItem.participant, speakingMap)
            }
        }
    }

    private fun updateVisibleItems() {
        val currentRange = layoutStrategy.getVisibleRange() ?: return
        cachedVisibleRange = currentRange
        processVisibleItems(currentRange.startPosition, currentRange.endPosition)
        updateArrowsVisibility()
        maybeNotifyPageIndexChanged(currentRange.pageIndex)
        onVisibleItemsUpdated?.invoke()
    }

    private fun maybeNotifyPageIndexChanged(newPageIndex: Int) {
        if (newPageIndex == lastReportedPageIndex) return
        lastReportedPageIndex = newPageIndex
        onPageIndexChanged?.invoke(newPageIndex)
    }

    private fun processVisibleItems(startPosition: Int, endPosition: Int) {
        forEachViewHolder { holder, position, streamItem ->
            val isVisible = position in startPosition..endPosition
            holder.setActive(isVisible)

            if (isVisible && streamItem.streamType == VideoStreamType.CAMERA) {
                updateViewHolderSpeakingState(holder, streamItem.participant, latestSpeakingMap)
            }
        }
    }

    private inline fun forEachViewHolder(
        action: (holder: RoomVideoGridAdapter.VideoStreamViewHolder, position: Int, streamItem: VideoStreamItem) -> Unit
    ) {
        val streamItems = adapter.getStreamItems()
        if (streamItems.isEmpty()) return

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val holder =
                recyclerView.getChildViewHolder(child) as? RoomVideoGridAdapter.VideoStreamViewHolder ?: continue

            val position = holder.adapterPosition
            if (position < 0 || position >= streamItems.size) continue

            action(holder, position, streamItems[position])
        }
    }

    private fun calculateItemSize() {
        val containerWidth = if (width > 0) width else context.resources.displayMetrics.widthPixels
        val containerHeight = if (height > 0) height else context.resources.displayMetrics.heightPixels

        val totalHorizontalSpacing = spacingPx * (PAGING_GRID_COLUMNS + 1)
        val availableWidth = containerWidth - totalHorizontalSpacing
        val maxItemWidth = availableWidth / PAGING_GRID_COLUMNS

        val totalVerticalSpacing = spacingPx * (PAGING_GRID_ROWS + 1)
        val availableHeight = containerHeight - totalVerticalSpacing
        val maxItemHeight = availableHeight / PAGING_GRID_ROWS

        val itemSize = minOf(maxItemWidth, maxItemHeight)
        itemWidthPx = itemSize
        itemHeightPx = itemSize

        logger.info(
            "Item size calculated: ${pxToDp(itemWidthPx)}dp x ${pxToDp(itemHeightPx)}dp " +
                    "(container: ${pxToDp(containerWidth)}dp x ${pxToDp(containerHeight)}dp)"
        )
    }

    private fun updateArrowsVisibility() {
        val streamItems = adapter.getStreamItems()
        val totalPages = (streamItems.size + PAGE_SIZE - 1) / PAGE_SIZE

        if (totalPages <= 1) {
            arrowLeft.visibility = GONE
            arrowRight.visibility = GONE
            return
        }

        val currentPage = cachedVisibleRange?.pageIndex ?: 0
        arrowLeft.visibility = if (currentPage > 0) VISIBLE else GONE
        arrowRight.visibility = if (currentPage < totalPages - 1) VISIBLE else GONE
    }

    private fun updateViewHolderSpeakingState(
        viewHolder: RoomVideoGridAdapter.VideoStreamViewHolder,
        participant: RoomParticipant,
        speakingMap: Map<String, Int>
    ) {
        val volume = speakingMap[participant.userID] ?: 0
        val isMicOn = participant.microphoneStatus == DeviceStatus.ON
        val isSpeaking = isMicOn && volume > SPEAKING_VOLUME_THRESHOLD

        val cachedState = speakingStateCache[participant.userID]
        if (cachedState != isSpeaking) {
            speakingStateCache[participant.userID] = isSpeaking
            viewHolder.updateSpeakingState(isSpeaking)
        }
    }

    fun release() {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            (child as? RoomViewVideoStreamCell)?.release()
        }
        speakingStateCache.clear()
        cachedVisibleRange = null
        lastReportedPageIndex = -1
    }
}
