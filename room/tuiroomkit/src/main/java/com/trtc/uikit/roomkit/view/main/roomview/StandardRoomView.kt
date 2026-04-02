package com.trtc.uikit.roomkit.view.main.roomview

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.base.utils.pxToDp
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StandardRoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    companion object {
        private const val PAGING_GRID_ROWS = 3
        private const val PAGING_GRID_COLUMNS = 2
        private const val PAGE_SIZE = PAGING_GRID_ROWS * PAGING_GRID_COLUMNS
        private const val MAX_RECYCLED_VIEWS = 12
        private const val ITEM_SPACING_DP = 8
        private const val SPEAKING_VOLUME_THRESHOLD = 25
    }

    private val logger = RoomKitLogger.getLogger("StandardRoomView")
    private var subscribeJob: Job? = null

    private val recyclerView: RecyclerView by lazy { findViewById(R.id.rv_video_grid) }
    private val arrowLeft: ImageView by lazy { findViewById(R.id.iv_arrow_left) }
    private val arrowRight: ImageView by lazy { findViewById(R.id.iv_arrow_right) }

    private var itemWidthPx = 0
    private var itemHeightPx = 0
    private val spacingPx by lazy { dpToPx(ITEM_SPACING_DP) }

    private lateinit var adapter: RoomVideoGridAdapter
    private lateinit var layoutStrategy: RoomVideoLayoutStrategy
    private lateinit var itemSizeDecoration: RoomVideoGridDecoration

    private var participants: List<RoomParticipant> = emptyList()
    private var screenShareParticipant: RoomParticipant? = null
    private var participantStore: RoomParticipantStore? = null

    private val speakingStateCache = mutableMapOf<String, Boolean>()
    private var pendingUpdateJob: Job? = null
    private var isFirstUpdate = true
    private var lastHasScreenShare = false
    private var cachedVisibleRange: PagedVideoLayoutManager.VisibleRange? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_standard_room_view, this)
        calculateItemSize()
        initRecyclerView()
    }

    public override fun init(roomID: String) {
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        val store = participantStore ?: return

        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                store.state.participantList.collect { participants ->
                    logger.info("participantList changed, size: ${participants.size}")
                    updateParticipants(participants)
                }
            }

            launch {
                store.state.participantWithScreen.collect { screenParticipant ->
                    logger.info("participantWithScreen changed: ${screenParticipant?.userID}")
                    updateScreenShareParticipant(screenParticipant)
                }
            }

            launch {
                store.state.speakingUsers.collect { speakingMap ->
                    updateSpeakingStates(speakingMap)
                }
            }
        }
    }

    override fun removeObserver() {
        pendingUpdateJob?.cancel()
        pendingUpdateJob = null
        subscribeJob?.cancel()
        subscribeJob = null
        speakingStateCache.clear()
        isFirstUpdate = true
        lastHasScreenShare = false
        cachedVisibleRange = null
    }

    private fun initRecyclerView() {
        itemSizeDecoration = RoomVideoGridDecoration(itemWidthPx, itemHeightPx, spacingPx)
        adapter = RoomVideoGridAdapter()

        adapter.onDataUpdateCompleted = {
            recyclerView.post {
                updateVisibleItems()
            }
        }

        layoutStrategy = RoomVideoLayoutStrategy(context, recyclerView, itemSizeDecoration)

        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(itemSizeDecoration)
        recyclerView.setItemViewCacheSize(0)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, MAX_RECYCLED_VIEWS)
        recyclerView.itemAnimator = null

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateVisibleItems()
                }
            }
        })
    }

    private fun updateVisibleItems() {
        val currentRange = layoutStrategy.getVisibleRange() ?: return
        cachedVisibleRange = currentRange
        processVisibleItems(currentRange.startPosition, currentRange.endPosition)
        updateArrowsVisibility()
    }

    private fun processVisibleItems(startPosition: Int, endPosition: Int) {
        forEachViewHolder { holder, position, streamItem ->
            val isVisible = position in startPosition..endPosition
            holder.setActive(isVisible)

            if (isVisible && streamItem.streamType == VideoStreamType.CAMERA) {
                participantStore?.state?.speakingUsers?.value?.let { speakingMap ->
                    updateViewHolderSpeakingState(holder, streamItem.participant, speakingMap)
                }
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

            val streamItem = streamItems[position]
            action(holder, position, streamItem)
        }
    }

    private fun calculateItemSize() {
        // Use actual view size if available, otherwise fallback to screen metrics
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

        if (::itemSizeDecoration.isInitialized) {
            itemSizeDecoration.updateItemSize(itemWidthPx, itemHeightPx, spacingPx)
        }

        logger.info(
            "Item size calculated: ${pxToDp(itemWidthPx)}dp x ${pxToDp(itemHeightPx)}dp " +
                    "(container: ${pxToDp(containerWidth)}dp x ${pxToDp(containerHeight)}dp, " +
                    "spacing: ${ITEM_SPACING_DP}dp, maxWidth: ${pxToDp(maxItemWidth)}dp, " +
                    "maxHeight: ${pxToDp(maxItemHeight)}dp)"
        )
    }

    private fun updateParticipants(newParticipants: List<RoomParticipant>) {
        participants = newParticipants
        scheduleUpdateDisplayList()
    }

    private fun updateScreenShareParticipant(screenParticipant: RoomParticipant?) {
        val wasSharing = screenShareParticipant != null
        val isSharing = screenParticipant != null
        logger.info(
            "updateScreenShareParticipant: wasSharing=$wasSharing, isSharing=$isSharing, " +
                    "userID=${screenParticipant?.userID ?: "null"}"
        )
        screenShareParticipant = screenParticipant
        scheduleUpdateDisplayList()
    }

    private fun scheduleUpdateDisplayList() {
        val hasData = participants.isNotEmpty() || screenShareParticipant != null
        if (isFirstUpdate && hasData) {
            isFirstUpdate = false
            updateDisplayList()
        } else {
            pendingUpdateJob?.cancel()
            pendingUpdateJob = CoroutineScope(Dispatchers.Main).launch {
                delay(250)
                updateDisplayList()
            }
        }
    }

    private fun updateDisplayList() {
        val displayList = buildList {
            screenShareParticipant?.let { screenUser ->
                add(VideoStreamItem.screenShare(screenUser))
            }

            participants.forEach { participant ->
                add(VideoStreamItem.camera(participant))
            }
        }

        val hasScreenShare = screenShareParticipant != null

        logger.info(
            "updateDisplayList: total=${displayList.size}, hasScreenShare=$hasScreenShare, " +
                    "screenShareUserId=${screenShareParticipant?.userID}, participants=${participants.size}"
        )

        layoutStrategy.configureForParticipantCount(displayList.size, hasScreenShare)
        adapter.updateData(displayList)
        updateArrowsVisibility()

        speakingStateCache.keys.removeAll { userId ->
            displayList.none { it.participant.userID == userId }
        }

        if (!lastHasScreenShare && hasScreenShare) {
            recyclerView.scrollToPosition(0)
            recyclerView.post {
                updateVisibleItems()
            }
        }
        lastHasScreenShare = hasScreenShare
    }

    private fun updateSpeakingStates(speakingMap: Map<String, Int>) {
        val currentRange = cachedVisibleRange ?: return

        forEachViewHolder { holder, position, streamItem ->
            val isVisible = position in currentRange.startPosition..currentRange.endPosition

            if (isVisible && streamItem.streamType == VideoStreamType.CAMERA) {
                updateViewHolderSpeakingState(holder, streamItem.participant, speakingMap)
            }
        }
    }

    private fun updateArrowsVisibility() {
        val streamItems = adapter.getStreamItems()
        val hasScreenShare = screenShareParticipant != null

        val totalPages = if (hasScreenShare) {
            if (streamItems.size <= 1) {
                1
            } else {
                val remainingItems = streamItems.size - 1
                1 + (remainingItems + PAGE_SIZE - 1) / PAGE_SIZE
            }
        } else {
            (streamItems.size + PAGE_SIZE - 1) / PAGE_SIZE
        }

        if (totalPages <= 1) {
            arrowLeft.visibility = GONE
            arrowRight.visibility = GONE
            return
        }

        val currentPage = getCurrentPage()

        arrowLeft.visibility = if (currentPage > 0) VISIBLE else GONE
        arrowRight.visibility = if (currentPage < totalPages - 1) VISIBLE else GONE
    }

    private fun getCurrentPage(): Int {
        val currentRange = cachedVisibleRange ?: return 0
        return currentRange.pageIndex
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
}