package com.trtc.uikit.roomkit.view.main.roomview

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import kotlin.math.abs

/**
 * Paged video layout manager with horizontal scrolling
 * Includes built-in snap helper for page snapping behavior
 *
 * Supports two layout modes:
 *
 * **Normal Mode**: Displays items in a grid layout (rows × columns per page)
 * - Example: 3 rows × 2 columns = 6 items/page
 * - All pages use the same grid layout
 *
 * **Speaker Mode**: Triggered when first item is screen share (ViewType=1)
 * - Page 0: Full-screen screen share (item 0 only)
 * - Page 1+: Grid layout starting from item 1
 *   - Page 1: items 1-6
 *   - Page 2: items 7-12, etc.
 *
 * @param rows Number of rows per page in grid mode
 * @param columns Number of columns per page in grid mode
 * @param itemWidth Item width in pixels
 * @param itemHeight Item height in pixels
 * @param margin Margin between items in pixels
 */
class PagedVideoLayoutManager(
    private val rows: Int,
    private val columns: Int,
    private val itemWidth: Int,
    private val itemHeight: Int,
    private val margin: Int
) : RecyclerView.LayoutManager(), RecyclerView.SmoothScroller.ScrollVectorProvider {

    /**
     * Virtual grid-page size for speaker mode grid pages (page 1+).
     * When > 0, items are centered within this rectangle so speaker-mode grid
     * pages match the compact layout of normal PAGING mode; otherwise items
     * are centered in the full usable area.
     */
    private var gridPageWidth: Int = 0
    private var gridPageHeight: Int = 0

    /** Set virtual grid-page size; pass 0 to disable. */
    fun setGridPageSize(widthPx: Int, heightPx: Int) {
        if (gridPageWidth == widthPx && gridPageHeight == heightPx) return
        gridPageWidth = widthPx
        gridPageHeight = heightPx
        requestLayout()
    }

    /**
     * Data class to hold visible range information
     */
    data class VisibleRange(
        val startPosition: Int,
        val endPosition: Int,
        val pageIndex: Int
    )

    private val pageSize = rows * columns
    private var offsetX = 0
    private var maxScrollX = 0
    private var cachedAdapter: RecyclerView.Adapter<*>? = null

    private var scrollState = RecyclerView.SCROLL_STATE_IDLE
    private var lastSpeakerMode = false

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        val params = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        // Reset all margins to 0 to avoid coordinate offsets in layoutDecorated()
        params.setMargins(0, 0, 0, 0)
        return params
    }

    override fun canScrollHorizontally(): Boolean = true

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (state.isPreLayout) return

        // Handle empty state
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }

        val isSpeakerMode = isSpeakerModeOn()

        // Reset scroll when entering speaker mode to show screen share immediately
        if (isSpeakerMode && !lastSpeakerMode) {
            offsetX = 0
        }
        lastSpeakerMode = isSpeakerMode

        updateMaxScroll(isSpeakerMode)
        clampOffset()

        // Detach and scrap all views to force remeasure when item count changes
        detachAndScrapAttachedViews(recycler)

        // Layout items based on mode
        if (isSpeakerMode) {
            layoutForSpeakerMode(recycler)
        } else {
            layoutForNormalMode(recycler)
        }
    }

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        cachedAdapter = view.adapter

        // Listen to scroll state changes
        view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                scrollState = newState
            }
        })
    }

    /**
     * Check if speaker mode is active
     * Speaker mode is ON when first item is screen share stream
     */
    private fun isSpeakerModeOn(): Boolean {
        if (itemCount == 0) return false
        val viewType = cachedAdapter?.getItemViewType(0) ?: RoomVideoGridAdapter.VIEW_TYPE_CAMERA
        return viewType == RoomVideoGridAdapter.VIEW_TYPE_SCREEN_SHARE
    }

    /**
     * Layout items for speaker mode
     * Layouts current page plus adjacent pages for smooth scrolling
     */
    private fun layoutForSpeakerMode(recycler: RecyclerView.Recycler) {
        val currentPage = getCurrentPage()
        val totalPages = getTotalPages(true)

        // Layout previous page if exists
        if (currentPage > 0) {
            val prevPage = currentPage - 1
            if (prevPage == 0) {
                layoutFullScreenSpeaker(recycler)
            } else {
                layoutGridPage(recycler, prevPage)
            }
        }

        // Layout current page
        if (currentPage == 0) {
            layoutFullScreenSpeaker(recycler)
        } else {
            layoutGridPage(recycler, currentPage)
        }

        // Layout next page if exists
        if (currentPage < totalPages - 1) {
            val nextPage = currentPage + 1
            if (nextPage == 0) {
                layoutFullScreenSpeaker(recycler)
            } else {
                layoutGridPage(recycler, nextPage)
            }
        }
    }

    /**
     * Layout full-screen speaker view on Page 0
     */
    private fun layoutFullScreenSpeaker(recycler: RecyclerView.Recycler) {
        val pageWidth = getUsableWidth()
        val pageHeight = getUsableHeight()

        val speakerView = recycler.getViewForPosition(0)

        // Reset view position BEFORE addView to avoid coordinate accumulation when view is reused
        speakerView.offsetLeftAndRight(-speakerView.left)
        speakerView.offsetTopAndBottom(-speakerView.top)

        addView(speakerView)

        // Force recalculate ItemDecoration offsets for this view
        calculateItemDecorationsForChild(speakerView, android.graphics.Rect())

        val widthSpec = View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(pageHeight, View.MeasureSpec.EXACTLY)
        speakerView.measure(widthSpec, heightSpec)

        layoutDecorated(speakerView, -offsetX, 0, pageWidth - offsetX, pageHeight)
    }

    /**
     * Layout grid page for speaker mode (Page 1+)
     * Page 1: items 1-6, Page 2: items 7-12, etc.
     */
    private fun layoutGridPage(recycler: RecyclerView.Recycler, currentPage: Int) {
        val startPos = 1 + (currentPage - 1) * pageSize
        val endPos = minOf(startPos + pageSize - 1, itemCount - 1)

        for (i in startPos..endPos) {
            layoutGridItemForSpeakerPage(i, recycler, currentPage)
        }
    }

    /**
     * Layout a single grid item for speaker mode pages
     */
    private fun layoutGridItemForSpeakerPage(position: Int, recycler: RecyclerView.Recycler, page: Int) {
        val gridPosition = position - 1  // Item 0 is full screen, convert to 0-based grid
        val positionInPage = gridPosition % pageSize
        val row = positionInPage / columns
        val col = positionInPage % columns

        val pageStartX = page * getUsableWidth()

        // Use virtual grid-page area if provided, otherwise full usable area
        val gridAreaWidth = if (gridPageWidth > 0) gridPageWidth else getUsableWidth()
        val gridAreaHeight = if (gridPageHeight > 0) gridPageHeight else getUsableHeight()
        val gridAreaOffsetX = (getUsableWidth() - gridAreaWidth) / 2
        val gridAreaOffsetY = (getUsableHeight() - gridAreaHeight) / 2

        // Center items horizontally within grid area
        val totalItemsWidth = columns * itemWidth + (columns + 1) * margin
        val centerHorizontalMargin = (gridAreaWidth - totalItemsWidth) / 2
        val x = pageStartX + gridAreaOffsetX + centerHorizontalMargin + margin + col * (itemWidth + margin)

        // Center items vertically within grid area
        val totalItemsHeight = rows * itemHeight + (rows + 1) * margin
        val centerVerticalMargin = (gridAreaHeight - totalItemsHeight) / 2
        val y = gridAreaOffsetY + centerVerticalMargin + margin + row * (itemHeight + margin)

        val view = recycler.getViewForPosition(position)

        // Reset view position BEFORE addView to avoid coordinate accumulation when view is reused
        view.offsetLeftAndRight(-view.left)
        view.offsetTopAndBottom(-view.top)

        addView(view)

        // Force recalculate ItemDecoration offsets for this view
        calculateItemDecorationsForChild(view, android.graphics.Rect())

        val widthSpec = View.MeasureSpec.makeMeasureSpec(itemWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(itemHeight, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)

        layoutDecorated(view, x - offsetX, y, x - offsetX + itemWidth, y + itemHeight)
    }

    /**
     * Layout items for normal mode
     * Layouts current page plus adjacent pages for smooth scrolling
     */
    private fun layoutForNormalMode(recycler: RecyclerView.Recycler) {
        val currentPage = getCurrentPage()
        val startPos = ((currentPage - 1).coerceAtLeast(0) * pageSize)
        val endPos = ((currentPage + 2).coerceAtMost(getTotalPages(false)) * pageSize - 1).coerceAtMost(itemCount - 1)

        for (i in startPos..endPos) {
            layoutNormalItem(i, recycler)
        }
    }

    /**
     * Layout a single item in normal mode
     */
    private fun layoutNormalItem(position: Int, recycler: RecyclerView.Recycler) {
        val page = position / pageSize
        val positionInPage = position % pageSize
        val row = positionInPage / columns
        val col = positionInPage % columns

        val pageStartX = page * getUsableWidth()

        // Center items horizontally
        val totalItemsWidth = columns * itemWidth + (columns + 1) * margin
        val centerHorizontalMargin = (getUsableWidth() - totalItemsWidth) / 2
        val x = pageStartX + centerHorizontalMargin + margin + col * (itemWidth + margin)

        // Center items vertically
        val totalItemsHeight = rows * itemHeight + (rows + 1) * margin
        val centerVerticalMargin = (getUsableHeight() - totalItemsHeight) / 2
        val y = centerVerticalMargin + margin + row * (itemHeight + margin)

        val view = recycler.getViewForPosition(position)

        // Reset view position BEFORE addView to avoid coordinate accumulation when view is reused
        view.offsetLeftAndRight(-view.left)
        view.offsetTopAndBottom(-view.top)

        addView(view)

        // Force recalculate ItemDecoration offsets for this view
        // This ensures old cached offsets from GridLayoutManager are cleared
        calculateItemDecorationsForChild(view, android.graphics.Rect())

        val widthSpec = View.MeasureSpec.makeMeasureSpec(itemWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(itemHeight, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)

        layoutDecorated(view, x - offsetX, y, x - offsetX + itemWidth, y + itemHeight)
    }

    private fun updateMaxScroll(isSpeakerMode: Boolean) {
        val totalPages = getTotalPages(isSpeakerMode)
        maxScrollX = (totalPages - 1) * getUsableWidth()
    }

    private fun clampOffset() {
        offsetX = offsetX.coerceIn(0, maxScrollX)
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        val actualDx = calculateActualScroll(dx)
        offsetX += actualDx
        offsetChildrenHorizontal(-actualDx)
        onLayoutChildren(recycler, state)
        return actualDx
    }

    private fun calculateActualScroll(dx: Int): Int {
        val newX = offsetX + dx
        return when {
            newX > maxScrollX -> maxScrollX - offsetX
            newX < 0 -> -offsetX
            else -> dx
        }
    }

    private fun getUsableWidth(): Int = width - paddingLeft - paddingRight

    private fun getUsableHeight(): Int = height - paddingTop - paddingBottom

    /**
     * Calculate total number of pages
     */
    private fun getTotalPages(isSpeakerMode: Boolean): Int {
        if (itemCount == 0) return 1

        return if (isSpeakerMode) {
            var pageCount = 1  // Page 0 for full-screen speaker
            val remainingItems = itemCount - 1
            if (remainingItems > 0) {
                pageCount += remainingItems / pageSize
                if (remainingItems % pageSize != 0) {
                    pageCount++
                }
            }
            pageCount
        } else {
            var pageCount = itemCount / pageSize
            if (itemCount % pageSize != 0) {
                pageCount++
            }
            pageCount
        }
    }

    /**
     * Get current page index based on scroll position
     * Public method to allow external components to query current page
     */
    fun getCurrentPage(): Int {
        val pageWidth = getUsableWidth()
        if (pageWidth <= 0) return 0
        return (offsetX + pageWidth / 2) / pageWidth
    }

    /**
     * Get current visible range (start position, end position, page index)
     * This method can be called anytime to get the current visible items
     * 
     * @return VisibleRange containing startPosition, endPosition, and pageIndex
     */
    fun getVisibleRange(): VisibleRange {
        if (itemCount == 0) {
            return VisibleRange(0, 0, 0)
        }

        val pageIndex = getCurrentPage()
        val isSpeakerMode = isSpeakerModeOn()

        val (startPos, endPos) = if (isSpeakerMode) {
            if (pageIndex == 0) {
                0 to 0  // Page 0: full-screen speaker only
            } else {
                val start = 1 + (pageIndex - 1) * pageSize
                val end = minOf(start + pageSize - 1, itemCount - 1)
                start to end
            }
        } else {
            val start = pageIndex * pageSize
            val end = minOf(start + pageSize - 1, itemCount - 1)
            start to end
        }

        return VisibleRange(startPos, endPos, pageIndex)
    }

    // ========== SnapHelper Support ==========

    /**
     * Find the view to snap to
     */
    fun findSnapView(): View? {
        if (childCount == 0) return null

        val isSpeakerMode = isSpeakerModeOn()
        val currentPage = getCurrentPage()

        val targetPosition = if (isSpeakerMode) {
            if (currentPage == 0) 0 else 1 + (currentPage - 1) * pageSize
        } else {
            currentPage * pageSize
        }

        return (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .firstOrNull { getPosition(it) == targetPosition }
            ?: getChildAt(0)
    }

    /**
     * Calculate snap offset for a position
     */
    fun getSnapOffset(position: Int): IntArray {
        val isSpeakerMode = isSpeakerModeOn()

        val targetX = if (isSpeakerMode) {
            if (position == 0) {
                0
            } else {
                val page = 1 + (position - 1) / pageSize
                page * getUsableWidth()
            }
        } else {
            (position / pageSize) * getUsableWidth()
        }

        return intArrayOf(targetX - offsetX, 0)
    }

    /**
     * Find first position of next page
     */
    fun findNextPageFirstPos(): Int {
        val isSpeakerMode = isSpeakerModeOn()
        val totalPages = getTotalPages(isSpeakerMode)
        val currentPage = getCurrentPage()
        val nextPage = (currentPage + 1).coerceAtMost(totalPages - 1)

        return if (isSpeakerMode) {
            when {
                nextPage == 0 -> 0
                nextPage >= 1 -> 1 + (nextPage - 1) * pageSize
                else -> 0
            }
        } else {
            nextPage * pageSize
        }
    }

    /**
     * Find first position of previous page
     */
    fun findPrePageFirstPos(): Int {
        val isSpeakerMode = isSpeakerModeOn()
        val currentPage = getCurrentPage()
        val prePage = (currentPage - 1).coerceAtLeast(0)

        return if (isSpeakerMode) {
            when {
                prePage == 0 -> 0
                prePage >= 1 -> 1 + (prePage - 1) * pageSize
                else -> 0
            }
        } else {
            prePage * pageSize
        }
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF {
        val offset = getSnapOffset(targetPosition)
        return PointF(offset[0].toFloat(), 0f)
    }

    override fun scrollToPosition(position: Int) {
        val isSpeakerMode = isSpeakerModeOn()
        offsetX = if (isSpeakerMode && position == 0) {
            0
        } else if (isSpeakerMode) {
            val page = 1 + (position - 1) / pageSize
            page * getUsableWidth()
        } else {
            (position / pageSize) * getUsableWidth()
        }

        requestLayout()
    }

    // ========== PageSnapHelper Inner Class ==========

    /**
     * SnapHelper for paged video grid scrolling
     * 
     * Handles fling gestures to snap to next/previous page
     * 
     * Features:
     * - Velocity-based page switching
     * - Smooth scroll animations
     * - Configurable fling sensitivity
     */
    class PageSnapHelper : SnapHelper() {

        companion object {
            /** Minimum fling velocity to trigger page change (px/s) */
            private const val MIN_FLING_VELOCITY = 1000
            /** Scroll speed in milliseconds per inch */
            private const val SCROLL_MS_PER_INCH = 50f
        }

        private var recyclerView: RecyclerView? = null

        override fun attachToRecyclerView(recyclerView: RecyclerView?) {
            this.recyclerView = recyclerView
            super.attachToRecyclerView(recyclerView)
        }

        /**
         * Calculate scroll distance to snap target view to snap position
         */
        override fun calculateDistanceToFinalSnap(
            layoutManager: RecyclerView.LayoutManager,
            targetView: View
        ): IntArray {
            val position = recyclerView?.getChildAdapterPosition(targetView) ?: RecyclerView.NO_POSITION
            return when {
                position == RecyclerView.NO_POSITION -> intArrayOf(0, 0)
                layoutManager is PagedVideoLayoutManager -> layoutManager.getSnapOffset(position)
                else -> intArrayOf(0, 0)
            }
        }

        /**
         * Find the view to snap to
         */
        override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
            return (layoutManager as? PagedVideoLayoutManager)?.findSnapView()
        }

        /**
         * Determine target snap position based on fling velocity
         * 
         * @param velocityX Horizontal velocity (positive = left swipe, negative = right swipe)
         * @return Target position to snap to, or NO_POSITION if velocity too low
         */
        override fun findTargetSnapPosition(
            layoutManager: RecyclerView.LayoutManager,
            velocityX: Int,
            velocityY: Int
        ): Int {
            val manager = layoutManager as? PagedVideoLayoutManager ?: return RecyclerView.NO_POSITION

            return when {
                velocityX > MIN_FLING_VELOCITY -> manager.findNextPageFirstPos()  // Swipe left -> next page
                velocityX < -MIN_FLING_VELOCITY -> manager.findPrePageFirstPos()  // Swipe right -> previous page
                else -> RecyclerView.NO_POSITION  // Velocity too low, no page change
            }
        }

        /**
         * Create smooth scroller for snapping animation
         */
        override fun createScroller(layoutManager: RecyclerView.LayoutManager): RecyclerView.SmoothScroller? {
            val context = recyclerView?.context ?: return null
            return object : LinearSmoothScroller(context) {
                override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
                    val (dx, dy) = calculateDistanceToFinalSnap(layoutManager, targetView)
                    val distance = maxOf(abs(dx), abs(dy))
                    if (distance > 0) {
                        val time = calculateTimeForDeceleration(distance)
                        action.update(dx, dy, time, mDecelerateInterpolator)
                    }
                }

                override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                    return SCROLL_MS_PER_INCH / displayMetrics.densityDpi
                }
            }
        }
    }
}
