package com.trtc.uikit.roomkit.inroom.features.roomview

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.inroom.features.device.micbutton.RoomMicLevelView
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.NetworkInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantState
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

class RoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val recyclerView: RecyclerView by lazy { findViewById(R.id.room_pager) }
    private val emptyView: TextView by lazy { findViewById(R.id.empty_text) }
    private val pageIndicator: TextView by lazy { findViewById(R.id.page_indicator) }
    private val previousPageButton: AppCompatImageView by lazy { findViewById(R.id.page_previous_icon) }
    private val nextPageButton: AppCompatImageView by lazy { findViewById(R.id.page_next_icon) }

    private val flowLayout = RoomParticipantLayout()
    private val participantAdapter = ParticipantAdapter()
    private val floatingView = RoomParticipantFloatView(context)

    private var roomID = ""
    private var boundState: RoomParticipantState? = null
    private var targetPageKey: String? = null
    private var speakerID: String? = null
    private var speakerSilentSinceMs: Long? = null

    private var pageBeforeDrag = 0
    private var reportedPageIndex = 0
    private var pageChromeVisible = false
    private var isUserSwiping = false

    private var participants: List<RoomParticipant> = emptyList()
    private var screenParticipant: RoomParticipant? = null
    private var floatingParticipant: RoomParticipant? = null
    private var localParticipantID: String? = null
    private var volumes: Map<String, Int> = emptyMap()
    private var networkQualities: Map<String, NetworkInfo> = emptyMap()
    private var pageKeys: List<String> = emptyList()
    private var itemKeys: List<String> = emptyList()
    private var playingPageKey: String? = null

    private var subscribeJob: Job? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchOnControl = false

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_room_view, this, true)
        recyclerView.layoutManager = flowLayout
        recyclerView.adapter = participantAdapter
        recyclerView.itemAnimator = null
        recyclerView.overScrollMode = OVER_SCROLL_NEVER
        recyclerView.onFlingListener = object : RecyclerView.OnFlingListener() {
            override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                scrollToPageInternal(flowLayout.targetPageForFling(velocityX), true)
                return true
            }
        }
        previousPageButton.setOnClickListener { scrollToPage(currentPageIndex - 1) }
        nextPageButton.setOnClickListener { scrollToPage(currentPageIndex + 1) }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val index = currentPageIndex
                if (index == reportedPageIndex) return
                Log.d(TAG, "page ${reportedPageIndex} -> $index offset=${flowLayout.currentScrollOffset} children=${recyclerView.childCount}")
                reportedPageIndex = index
                updatePageIndicator()
                decidePlayablePage()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                Log.d(TAG, "scrollState=$newState page=$currentPageIndex offset=${flowLayout.currentScrollOffset}")
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        isUserSwiping = true
                        pageBeforeDrag = currentPageIndex
                        decidePlayablePage()
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (isUserSwiping) {
                            snapToNearestPage()
                            if (currentPageIndex != pageBeforeDrag) showPageChrome()
                            isUserSwiping = false
                        }
                        decidePlayablePage()
                    }
                }
            }
        })
    }

    fun init(roomID: String) {
        if (roomID.isBlank()) return
        this.roomID = roomID
        updateState(RoomParticipantStore.create(roomID).state)
    }

    internal fun updateState(state: RoomParticipantState) {
        subscribeJob?.cancel()
        boundState = state
        targetPageKey = null
        speakerID = null
        speakerSilentSinceMs = null
        flowLayout.floatingPosition = RoomParticipantLayout.FloatingWindowPosition()
        setPlayingPage(null)
        networkQualities = emptyMap()
        applyVideo(emptyList(), null, null, null, emptyMap())
        emptyView.visibility = VISIBLE
        updatePageIndicator()
        if (isAttachedToWindow) observeState(state)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        boundState?.let(::observeState)
        showTargetPage()
        decidePlayablePage()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        removeCallbacks(syncPlaybackRunnable)
        removeCallbacks(hideChromeRunnable)
        setPlayingPage(null)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        decidePlayablePage()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        decidePlayablePage()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val draggingFrame = if (floatingView.parent === this && floatingView.isDragging) {
            intArrayOf(floatingView.left, floatingView.top, floatingView.right, floatingView.bottom)
        } else {
            null
        }
        super.onLayout(changed, left, top, right, bottom)
        if (draggingFrame != null) {
            layoutFloating(draggingFrame[0], draggingFrame[1], draggingFrame[2], draggingFrame[3])
        } else {
            updateFloatingFrame()
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeState(state: RoomParticipantState) {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            val volumes = state.speakingUsers.sample(RoomMicLevelView.UPDATE_INTERVAL_MS.milliseconds)
            val networkQualities = state.networkQualities.sample(RoomMicLevelView.UPDATE_INTERVAL_MS.milliseconds)
            combine(
                state.participantList,
                state.participantWithScreen,
                state.localParticipant,
                volumes,
            ) { participants, screenParticipant, localParticipant, volumes ->
                val merged = volumes.toMutableMap()
                localParticipant?.userID?.takeIf { it.isNotBlank() }?.let { id ->
                    volumes[LOCAL_VOLUME_KEY]?.let { merged[id] = it }
                }
                updatePages(participants, screenParticipant, localParticipant, merged)
            }.collect()
            launch {
                networkQualities.collect { qualities ->
                    this@RoomView.networkQualities = qualities
                    refreshVisibleCells()
                }
            }
        }
    }

    private fun updatePages(
        participants: List<RoomParticipant>,
        screenParticipant: RoomParticipant?,
        localParticipant: RoomParticipant?,
        volumes: Map<String, Int>,
    ) {
        val validLocal = localParticipant?.takeIf { it.userID.isNotBlank() }
        val speaker = selectSpeaker(participants, validLocal, volumes)
        speakerID = speaker?.userID
        val members = participants.filter { it.userID.isNotBlank() }.distinctBy { it.userID }
        val screen = screenParticipant?.takeIf { it.userID.isNotBlank() }
        val floating = if (screen == null) null else speaker ?: validLocal

        decideTargetPage(makePageKeys(members, screen))
        val structureChanged = applyVideo(members, screen, floating, validLocal?.userID, volumes)
        configureFloating()
        emptyView.visibility = if (flowLayout.pageCount > 0) GONE else VISIBLE
        if (structureChanged) {
            updatePageIndicator()
            showTargetPage()
            decidePlayablePage()
        }
    }

    private fun makePageKeys(participants: List<RoomParticipant>, screen: RoomParticipant?): List<String> {
        val screenKeys = screen?.let { listOf("screen:${it.userID}") } ?: emptyList()
        val pageCount = (participants.size + RoomParticipantLayout.PAGE_CAPACITY - 1) / RoomParticipantLayout.PAGE_CAPACITY
        return screenKeys + (0 until pageCount).map { "participants:$it" }
    }

    private fun decideTargetPage(nextKeys: List<String>) {
        val previousKeys = pageKeys
        if (previousKeys == nextKeys) return

        val startedSharing = nextKeys.firstOrNull()?.startsWith("screen:") == true &&
            nextKeys.firstOrNull() != previousKeys.firstOrNull()
        if (startedSharing) {
            targetPageKey = nextKeys.firstOrNull()
            flowLayout.floatingPosition = RoomParticipantLayout.FloatingWindowPosition()
            return
        }

        val currentPageKey = previousKeys.getOrNull(currentPageIndex)
        val keyToKeep = targetPageKey ?: currentPageKey
        val matchingIndex = nextKeys.indexOfFirst { it == keyToKeep }
        val targetIndex = if (matchingIndex >= 0) matchingIndex else currentPageIndex.coerceAtMost((nextKeys.size - 1).coerceAtLeast(0))
        targetPageKey = nextKeys.getOrNull(targetIndex)
    }

    private fun showTargetPage() {
        val key = targetPageKey ?: return
        recyclerView.doOnPreDraw {
            targetPageKey = null
            val index = pageKeys.indexOfFirst { it == key }
            if (index >= 0) scrollToPageInternal(index, false)
            updatePageIndicator()
            decidePlayablePage()
        }
    }

    private fun applyVideo(
        nextParticipants: List<RoomParticipant>,
        screen: RoomParticipant?,
        floating: RoomParticipant?,
        localParticipantID: String?,
        volumes: Map<String, Int>,
    ): Boolean {
        val nextPageKeys = makePageKeys(nextParticipants, screen)
        val nextItemKeys = (screen?.let { listOf("screen:${it.userID}") } ?: emptyList()) +
            nextParticipants.map { "participant:${it.userID}" }
        val structureChanged = nextItemKeys != itemKeys || nextPageKeys != pageKeys
        if (structureChanged) {
            Log.d(TAG, "structureChanged items=${itemKeys.size}->${nextItemKeys.size} pages=${pageKeys.size}->${nextPageKeys.size}")
        }
        val previousItemKeys = itemKeys
        val previousAdapterCount = participantAdapter.itemCount

        this.localParticipantID = localParticipantID
        this.volumes = volumes
        if (!nextPageKeys.contains(playingPageKey)) setPlayingPage(null)
        participants = nextParticipants
        screenParticipant = screen
        floatingParticipant = floating
        pageKeys = nextPageKeys
        itemKeys = nextItemKeys
        flowLayout.update(nextParticipants.size, screen != null, floating != null)

        if (floating == null) {
            floatingView.release()
            if (floatingView.parent === this) removeView(floatingView)
        }

        if (!structureChanged) {
            refreshVisibleCells()
            return false
        }

        val leadingChanged = leadingItemKeys(previousItemKeys) != leadingItemKeys(nextItemKeys)
        if (leadingChanged || previousItemKeys == nextItemKeys) {
            Log.w(TAG, "fullReload leadingChanged=$leadingChanged sameItems=${previousItemKeys == nextItemKeys}")
            flowLayout.markFullReload()
            participantAdapter.notifyDataSetChanged()
            return true
        }

        val oldItems = previousItemKeys.filterNot { it.startsWith("screen:") }
        val newItems = nextItemKeys.filterNot { it.startsWith("screen:") }
        if (oldItems == newItems) {
            refreshVisibleCells()
            return true
        }
        if (recyclerView.isComputingLayout ||
            previousAdapterCount != previousItemKeys.size ||
            newItems.size != flowLayout.itemCountInSection(1)
        ) {
            Log.w(TAG, "fullReload fallback computing=${recyclerView.isComputingLayout} adapterCount=$previousAdapterCount oldItems=${previousItemKeys.size}")
            flowLayout.markFullReload()
            participantAdapter.notifyDataSetChanged()
            return true
        }

        val difference = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition] == newItems[newItemPosition]

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = true
        })
        val sectionOffset = flowLayout.itemCountInSection(0)
        difference.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                Log.d(TAG, "adapterInsert position=${position + sectionOffset} count=$count")
                participantAdapter.notifyItemRangeInserted(position + sectionOffset, count)
            }

            override fun onRemoved(position: Int, count: Int) {
                Log.d(TAG, "adapterRemove position=${position + sectionOffset} count=$count")
                participantAdapter.notifyItemRangeRemoved(position + sectionOffset, count)
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                val from = fromPosition + sectionOffset
                val to = toPosition + sectionOffset
                Log.d(TAG, "adapterMove $from -> $to")
                participantAdapter.notifyItemMoved(from, to)
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                participantAdapter.notifyItemRangeChanged(position + sectionOffset, count, payload)
            }
        })
        recyclerView.doOnPreDraw {
            decidePlayablePage()
        }
        return true
    }

    private fun leadingItemKeys(keys: List<String>): List<String> = keys.filter { it.startsWith("screen:") }

    private fun refreshVisibleCells() {
        for (index in 0 until recyclerView.childCount) {
            val view = recyclerView.getChildAt(index)
            val position = recyclerView.getChildAdapterPosition(view)
            if (position != RecyclerView.NO_POSITION) {
                (recyclerView.getChildViewHolder(view) as? ParticipantHolder)?.let { configure(it.cell, position) }
            }
        }
    }

    private fun participantAt(position: Int): RoomParticipant? {
        if (flowLayout.isScreenPosition(position)) return screenParticipant
        val index = flowLayout.participantIndexForPosition(position)
        return participants.getOrNull(index)
    }

    private fun isPlayingPosition(position: Int): Boolean {
        val page = flowLayout.pageIndexForPosition(position)
        return pageKeys.getOrNull(page) == playingPageKey
    }

    private fun setPlayingPage(pageKey: String?) {
        if (playingPageKey != pageKey) {
            Log.d(TAG, "playingPage $playingPageKey -> $pageKey currentPage=$currentPageIndex")
        }
        playingPageKey = pageKey
        syncPlayback()
    }

    private fun syncPlayback() {
        for (index in 0 until recyclerView.childCount) {
            val view = recyclerView.getChildAt(index)
            val position = recyclerView.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) continue
            (recyclerView.getChildViewHolder(view) as? ParticipantHolder)?.cell
                ?.setPlaying(playingPageKey != null && isPlayingPosition(position))
        }
        updateFloatingVisibility()
        val floatingPageKey = pageKeys.firstOrNull { it.startsWith("screen:") }
        floatingView.setPlaying(floatingView.visibility == VISIBLE && floatingPageKey == playingPageKey)
    }

    private fun configureFloating() {
        val participant = floatingParticipant ?: return
        val needsInitialLayout = floatingView.parent !== this
        if (needsInitialLayout) {
            addView(floatingView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        floatingView.bind(
            participant,
            localParticipantID,
            volumes[participant.userID] ?: 0,
            networkQualities[participant.userID]?.quality,
        )
        if (floatingView.onFloatingPan == null) {
            floatingView.onFloatingPan = { action, translationX, translationY ->
                flowLayout.handleFloatingPan(floatingView, action, translationX, translationY)
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    requestLayout()
                }
            }
        }
        updateFloatingVisibility()
        if (needsInitialLayout) requestLayout()
    }

    private fun updateFloatingVisibility() {
        val floatingPageKey = pageKeys.firstOrNull { it.startsWith("screen:") }
        val isScreenPage = floatingPageKey != null && pageKeys.getOrNull(currentPageIndex) == floatingPageKey
        floatingView.visibility = if (floatingView.parent === this && isScreenPage) VISIBLE else GONE
    }

    private fun updateFloatingFrame() {
        if (floatingView.parent !== this || floatingView.isDragging) return
        val frame = flowLayout.floatingFrame()
        if (frame.width() <= 0f || frame.height() <= 0f) return
        val width = frame.width().toInt()
        val height = frame.height().toInt()
        val params = floatingView.layoutParams
        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            floatingView.layoutParams = params
        }
        layoutFloating(frame.left.toInt(), frame.top.toInt(), frame.right.toInt(), frame.bottom.toInt())
    }

    private fun layoutFloating(left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        floatingView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        floatingView.layout(left, top, right, bottom)
    }

    private fun configure(cell: RoomParticipantCell, position: Int) {
        val participant = participantAt(position) ?: return
        cell.bind(
            participant,
            localParticipantID,
            volumes[participant.userID] ?: 0,
            networkQualities[participant.userID]?.quality,
        )
        cell.setPlaying(playingPageKey != null && isPlayingPosition(position))
    }

    private val currentPageIndex: Int
        get() = flowLayout.currentPageIndex()

    private fun updatePageIndicator() {
        val pageCount = flowLayout.pageCount
        val currentPage = currentPageIndex
        pageIndicator.text = context.getString(R.string.roomkit_room_view_page_number, currentPage + 1, pageCount)
        val hasPrevious = currentPage > 0
        val hasNext = currentPage < pageCount - 1
        previousPageButton.isEnabled = hasPrevious
        nextPageButton.isEnabled = hasNext
        if (pageIndicator.alpha > 0f) {
            previousPageButton.alpha = if (hasPrevious) 1f else DISABLED_ALPHA
            nextPageButton.alpha = if (hasNext) 1f else DISABLED_ALPHA
        }
    }

    private fun scrollToPage(index: Int) {
        showPageChrome()
        decidePlayablePage()
        scrollToPageInternal(index, true)
    }

    private fun scrollToPageInternal(index: Int, animated: Boolean) {
        val target = flowLayout.offsetForPage(index)
        val delta = target - flowLayout.currentScrollOffset
        if (delta == 0) return
        if (animated) recyclerView.smoothScrollBy(delta, 0) else recyclerView.scrollBy(delta, 0)
    }

    private fun snapToNearestPage() {
        scrollToPageInternal(currentPageIndex, true)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchOnControl = hitClickableChild(this, event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                val moved = abs(event.x - touchStartX) > slop || abs(event.y - touchStartY) > slop
                if (!moved && !touchOnControl) togglePageChrome()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun hitClickableChild(root: View, x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f || x >= root.width || y >= root.height) return false
        if (root.isClickable && root.isEnabled) return true
        if (root is ViewGroup) {
            for (index in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(index)
                if (hitClickableChild(child, x - child.left, y - child.top)) return true
            }
        }
        return false
    }

    private fun togglePageChrome() {
        if (flowLayout.pageCount <= 1) return
        if (pageChromeVisible) hidePageChrome() else showPageChrome()
    }

    private fun showPageChrome() {
        if (flowLayout.pageCount <= 1) return
        pageChromeVisible = true
        updatePageIndicator()
        listOf(previousPageButton, nextPageButton, pageIndicator).forEach { view ->
            view.visibility = VISIBLE
            view.animate().cancel()
            view.animate()
                .alpha(if (view.isEnabled) 1f else DISABLED_ALPHA)
                .setDuration(CHROME_ANIMATION_DURATION)
                .start()
        }
        removeCallbacks(hideChromeRunnable)
        postDelayed(hideChromeRunnable, CHROME_AUTO_HIDE_DELAY)
    }

    private val hideChromeRunnable = Runnable { hidePageChrome() }

    private fun hidePageChrome() {
        removeCallbacks(hideChromeRunnable)
        pageChromeVisible = false
        listOf(previousPageButton, nextPageButton, pageIndicator).forEach {
            it.animate().cancel()
            it.animate().alpha(0f).setDuration(CHROME_ANIMATION_DURATION)
                .withEndAction { it.visibility = GONE }
                .start()
        }
    }

    private fun decidePlayablePage() {
        removeCallbacks(syncPlaybackRunnable)
        post(syncPlaybackRunnable)
    }

    private val syncPlaybackRunnable = Runnable {
        val isPageReady = targetPageKey == null
        val isViewVisible = isAttachedToWindow && isShown && windowVisibility == VISIBLE
        val hasActiveSubscription = subscribeJob?.isActive == true
        val currentKey = pageKeys.getOrNull(currentPageIndex)
        setPlayingPage(if (isPageReady && isViewVisible && hasActiveSubscription) currentKey else null)
    }

    private fun selectSpeaker(
        participants: List<RoomParticipant>,
        localParticipant: RoomParticipant?,
        volumes: Map<String, Int>,
    ): RoomParticipant? {
        val candidates = (participants + listOfNotNull(localParticipant))
            .filter { it.userID.isNotBlank() }
            .distinctBy { it.userID }
            .filter { it.microphoneStatus == DeviceStatus.ON }

        val current = candidates.firstOrNull { it.userID == speakerID }
        val loudest = candidates
            .filter { (volumes[it.userID] ?: 0) > 0 }
            .maxByOrNull { volumes[it.userID] ?: 0 }

        if (current != null && (volumes[current.userID] ?: 0) > 0) {
            speakerSilentSinceMs = null
            return current
        }
        if (current != null) {
            if (loudest == null) {
                speakerSilentSinceMs = null
                return current
            }
            val now = SystemClock.elapsedRealtime()
            val since = speakerSilentSinceMs ?: now.also { speakerSilentSinceMs = it }
            if (now - since < SPEAKER_HOLD_MS) return current
            speakerSilentSinceMs = null
            return loudest
        }
        return loudest
    }

    private inner class ParticipantAdapter : RecyclerView.Adapter<ParticipantHolder>() {

        override fun getItemCount(): Int = flowLayout.totalItemCount

        override fun getItemViewType(position: Int): Int =
            if (flowLayout.isScreenPosition(position)) VIEW_TYPE_SCREEN else VIEW_TYPE_CAMERA

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantHolder {
            val cell = if (viewType == VIEW_TYPE_SCREEN) {
                RoomParticipantScreenShareCell(parent.context)
            } else {
                RoomParticipantCameraCell(parent.context)
            }
            cell.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            return ParticipantHolder(cell)
        }

        override fun onBindViewHolder(holder: ParticipantHolder, position: Int) {
            configure(holder.cell, position)
        }

        override fun onViewAttachedToWindow(holder: ParticipantHolder) {
            Log.d(TAG, "adapterAttach position=${holder.absoluteAdapterPosition}")
            super.onViewAttachedToWindow(holder)
            if (holder.absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                configure(holder.cell, holder.absoluteAdapterPosition)
            }
            decidePlayablePage()
        }

        override fun onViewDetachedFromWindow(holder: ParticipantHolder) {
            Log.d(TAG, "adapterDetach position=${holder.absoluteAdapterPosition}")
            super.onViewDetachedFromWindow(holder)
        }

        override fun onViewRecycled(holder: ParticipantHolder) {
            Log.w(TAG, "adapterRecycle position=${holder.absoluteAdapterPosition}")
            holder.cell.release()
            super.onViewRecycled(holder)
        }
    }

    private inner class ParticipantHolder(val cell: RoomParticipantCell) : RecyclerView.ViewHolder(cell)

    companion object {
        private const val TAG = "RoomView"
        private const val CHROME_ANIMATION_DURATION = 160L
        private const val DISABLED_ALPHA = 0.22f
        private const val CHROME_AUTO_HIDE_DELAY = 2500L
        private const val SPEAKER_HOLD_MS = 1000L
        private const val LOCAL_VOLUME_KEY = ""
        private const val VIEW_TYPE_SCREEN = 1
        private const val VIEW_TYPE_CAMERA = 2
    }
}
