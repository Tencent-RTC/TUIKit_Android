package com.trtc.uikit.roomkit.inroom.features.roomview

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.trtc.uikit.roomkit.R
import androidx.appcompat.widget.AppCompatImageView
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.LayoutMode
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.VideoPageLayout
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantState
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import com.trtc.uikit.roomkit.inroom.features.device.micbutton.RoomMicLevelView

class RoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val pager: ViewPager2 by lazy { findViewById(R.id.room_pager) }
    private val emptyView: TextView by lazy { findViewById(R.id.empty_text) }
    private val pageIndicator: TextView by lazy { findViewById(R.id.page_indicator) }
    private val previousPageButton: AppCompatImageView by lazy { findViewById(R.id.page_previous_icon) }
    private val nextPageButton: AppCompatImageView by lazy { findViewById(R.id.page_next_icon) }
    private val pageAdapter: RoomVideoPageAdapter
    private var floatingWindowPosition = RoomVideoLayoutCalculator.FloatingWindowPosition()
    private var roomID: String = ""
    private var boundState: RoomParticipantState? = null
    private var targetPageKey: String? = null
    private var speakerID: String? = null
    private var pageChromeVisible = false
    private var isUserSwiping = false
    private var speakerSilentSinceMs: Long? = null

    private var subscribeJob: Job? = null
    private var currentVolumes: Map<String, Int> = emptyMap()
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchOnControl = false

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_room_view, this, true)
        previousPageButton.setOnClickListener { showPreviousPage() }
        nextPageButton.setOnClickListener { showNextPage() }
        pageAdapter = RoomVideoPageAdapter(
            onPageAttached = { invalidatePlayback() },
            onFloatingWindowPositionChange = { floatingWindowPosition = it },
        )
        pager.adapter = pageAdapter
        (pager.getChildAt(0) as? RecyclerView)?.itemAnimator = null
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (isUserSwiping) {
                    isUserSwiping = false
                    showPageChrome()
                }
                updatePageIndicator()
                invalidatePlayback()
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) isUserSwiping = true
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
        pageAdapter.setPlayingPage(null)
        speakerID = null
        speakerSilentSinceMs = null
        floatingWindowPosition = RoomVideoLayoutCalculator.FloatingWindowPosition()
        pageAdapter.updatePages(emptyList(), emptyMap(), floatingWindowPosition)
        if (isAttachedToWindow) observeState(state)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        boundState?.let { observeState(it) }
        showTargetPage()
        invalidatePlayback()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        removeCallbacks(syncPlaybackRunnable)
        removeCallbacks(hideChromeRunnable)
        pageAdapter.setPlayingPage(null)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        invalidatePlayback()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        invalidatePlayback()
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
                    pageAdapter.updatePages(
                        pageAdapter.pages,
                        currentVolumes,
                        floatingWindowPosition,
                        qualities,
                    )
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
        currentVolumes = volumes
        val validLocalParticipant = localParticipant?.takeIf { it.userID.isNotBlank() }
        val speaker = selectSpeaker(participants, validLocalParticipant, volumes)
        speakerID = speaker?.userID
        val pages = RoomVideoLayoutCalculator.buildPages(
            participants = participants,
            screenParticipant = screenParticipant,
            floatingParticipant = speaker ?: validLocalParticipant,
        )
        decideTargetPage(pages)
        pageAdapter.updatePages(pages, volumes, floatingWindowPosition)
        emptyView.visibility = if (pages.isEmpty()) VISIBLE else GONE
        updatePageIndicator()
        showTargetPage()
        invalidatePlayback()
    }

    private fun decideTargetPage(nextPages: List<VideoPageLayout>) {
        val previousPages = pageAdapter.pages
        if (previousPages.map { it.key } == nextPages.map { it.key }) return

        val firstPage = nextPages.firstOrNull()
        val startedSharing = firstPage?.mode == LayoutMode.SCREEN_SHARE &&
            firstPage.key != previousPages.firstOrNull()?.key
        if (startedSharing) {
            targetPageKey = firstPage?.key
            floatingWindowPosition = RoomVideoLayoutCalculator.FloatingWindowPosition()
            return
        }

        val currentPageKey = previousPages.getOrNull(pager.currentItem)?.key
        val pageKeyToKeep = targetPageKey ?: currentPageKey
        val matchingPageIndex = nextPages.indexOfFirst { it.key == pageKeyToKeep }
        val lastPageIndex = (nextPages.size - 1).coerceAtLeast(0)
        val targetPageIndex = if (matchingPageIndex >= 0) {
            matchingPageIndex
        } else {
            pager.currentItem.coerceAtMost(lastPageIndex)
        }
        targetPageKey = nextPages.getOrNull(targetPageIndex)?.key
    }

    private fun showTargetPage() {
        val key = targetPageKey ?: return
        pager.doOnPreDraw {
            targetPageKey = null
            val index = pageAdapter.pages.indexOfFirst { it.key == key }
            if (index >= 0) pager.setCurrentItem(index, false)
            updatePageIndicator()
            invalidatePlayback()
        }
    }

    private fun updatePageIndicator() {
        val pageCount = pageAdapter.itemCount
        val currentPage = pager.currentItem
        pageIndicator.text = context.getString(R.string.roomkit_room_view_page_number, currentPage + 1, pageCount)
        val hasPrevious = currentPage > 0
        val hasNext = currentPage < pageCount - 1
        previousPageButton.isEnabled = hasPrevious
        nextPageButton.isEnabled = hasNext
        previousPageButton.alpha = if (hasPrevious) 1f else DISABLED_ALPHA
        nextPageButton.alpha = if (hasNext) 1f else DISABLED_ALPHA
    }

    private fun showPreviousPage() {
        val targetPage = (pager.currentItem - 1).coerceAtLeast(0)
        pager.setCurrentItem(targetPage, true)
        showPageChrome()
    }

    private fun showNextPage() {
        val targetPage = (pager.currentItem + 1).coerceAtMost(pageAdapter.itemCount - 1)
        pager.setCurrentItem(targetPage, true)
        showPageChrome()
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
        if (pageAdapter.itemCount <= 1) return
        if (pageChromeVisible) hidePageChrome() else showPageChrome()
    }

    private fun showPageChrome() {
        if (pageAdapter.itemCount <= 1) return
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

    private fun invalidatePlayback() {
        removeCallbacks(syncPlaybackRunnable)
        post(syncPlaybackRunnable)
    }

    private val syncPlaybackRunnable = Runnable {
        val pageSettled = targetPageKey == null
        val viewVisible = isAttachedToWindow && isShown && windowVisibility == VISIBLE
        val subscriptionActive = subscribeJob?.isActive == true
        val pageKey = if (pageSettled && viewVisible && subscriptionActive) {
            pageAdapter.pages.getOrNull(pager.currentItem)?.key
        } else {
            null
        }
        pageAdapter.setPlayingPage(pageKey)
    }

    companion object {
        private const val CHROME_ANIMATION_DURATION = 160L
        private const val DISABLED_ALPHA = 0.22f
        private const val CHROME_AUTO_HIDE_DELAY = 2500L
        private const val SPEAKER_HOLD_MS = 1000L
        private const val LOCAL_VOLUME_KEY = ""
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
}
