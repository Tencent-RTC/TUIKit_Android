package com.trtc.uikit.roomkit.inroom.features.roomview

import android.graphics.RectF
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RoomParticipantLayout : RecyclerView.LayoutManager() {

    companion object {
        private const val TAG = "RoomParticipantLayout"
        const val PAGE_CAPACITY = 6
        const val ITEM_ASPECT_RATIO = 9f / 16f
        const val CORNER_RADIUS_DP = 12f

        private const val CONTENT_INSET_DP = 13f
        private const val ITEM_GAP_DP = 4f
        private const val FLOATING_INSET_DP = 8f
        private const val GRID_ROW_COUNT = 2
    }

    enum class FloatingSide { LEFT, RIGHT }

    data class FloatingWindowPosition(
        val side: FloatingSide = FloatingSide.RIGHT,
        val verticalFraction: Float = 0f,
    )

    var floatingPosition = FloatingWindowPosition()
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private var participantCount = 0
    private var hasScreenShare = false
    private var hasFloatingWindow = false

    private var density = 1f
    private var pageWidth = 0
    private var pageHeight = 0
    private var scrollOffsetX = 0
    private var needsFullReload = false
    private val floatingDragStartFrame = RectF()

    private val leadingItemCount: Int
        get() = if (hasScreenShare) 1 else 0

    val totalItemCount: Int
        get() = leadingItemCount + participantCount

    val pageCount: Int
        get() = leadingItemCount + (participantCount + PAGE_CAPACITY - 1) / PAGE_CAPACITY

    val currentScrollOffset: Int
        get() = scrollOffsetX

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        density = view.resources.displayMetrics.density
    }

    fun update(participantCount: Int, hasScreenShare: Boolean, hasFloatingWindow: Boolean) {
        val floating = hasScreenShare && hasFloatingWindow
        if (this.participantCount == participantCount &&
            this.hasScreenShare == hasScreenShare &&
            this.hasFloatingWindow == floating
        ) return
        this.participantCount = participantCount
        this.hasScreenShare = hasScreenShare
        this.hasFloatingWindow = floating
        scrollOffsetX = scrollOffsetX.coerceIn(0, maxScrollOffset())
        requestLayout()
    }

    fun markFullReload() {
        needsFullReload = true
        requestLayout()
    }

    fun itemCountInSection(section: Int): Int = if (section == 0) leadingItemCount else participantCount

    fun isScreenPosition(position: Int): Boolean = hasScreenShare && position == 0

    fun participantIndexForPosition(position: Int): Int = position - leadingItemCount

    fun pageIndexForPosition(position: Int): Int {
        if (isScreenPosition(position)) return 0
        return leadingItemCount + participantIndexForPosition(position) / PAGE_CAPACITY
    }

    fun currentPageIndex(): Int {
        if (pageWidth <= 0) return 0
        return (scrollOffsetX.toFloat() / pageWidth).roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    fun offsetForPage(index: Int): Int = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) * pageWidth

    fun targetPageForFling(velocityX: Int): Int {
        if (pageWidth <= 0) return 0
        val page = scrollOffsetX.toFloat() / pageWidth
        val target = if (velocityX > 0) floor(page).toInt() + 1 else ceil(page).toInt() - 1
        return target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    override fun canScrollHorizontally(): Boolean = true

    override fun canScrollVertically(): Boolean = false

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        pageWidth = width - paddingLeft - paddingRight
        pageHeight = height - paddingTop - paddingBottom
        scrollOffsetX = scrollOffsetX.coerceIn(0, maxScrollOffset())
        Log.d(TAG, "layout page=${currentPageIndex()} offset=$scrollOffsetX items=$totalItemCount children=$childCount fullReload=$needsFullReload")
        if (needsFullReload) {
            Log.w(TAG, "detachAndScrapAttachedViews")
            detachAndScrapAttachedViews(recycler)
            needsFullReload = false
        }
        fill(recycler)
    }

    override fun onItemsChanged(recyclerView: RecyclerView) {
        Log.w(TAG, "onItemsChanged")
        super.onItemsChanged(recyclerView)
        requestLayout()
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        Log.d(TAG, "onItemsAdded position=$positionStart count=$itemCount")
        super.onItemsAdded(recyclerView, positionStart, itemCount)
        requestLayout()
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        Log.d(TAG, "onItemsRemoved position=$positionStart count=$itemCount")
        super.onItemsRemoved(recyclerView, positionStart, itemCount)
        needsFullReload = true
        requestLayout()
    }

    override fun onItemsMoved(recyclerView: RecyclerView, from: Int, to: Int, itemCount: Int) {
        Log.d(TAG, "onItemsMoved $from -> $to count=$itemCount")
        super.onItemsMoved(recyclerView, from, to, itemCount)
        requestLayout()
    }

    override fun onItemsUpdated(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount)
        requestLayout()
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        val target = (scrollOffsetX + dx).coerceIn(0, maxScrollOffset())
        val consumed = target - scrollOffsetX
        if (consumed == 0) return 0
        scrollOffsetX = target
        fill(recycler)
        return consumed
    }

    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int = pageWidth * pageCount

    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int = pageWidth

    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int = scrollOffsetX

    private fun fill(recycler: RecyclerView.Recycler) {
        if (pageWidth <= 0 || pageHeight <= 0) return

        for (index in childCount - 1 downTo 0) {
            val view = getChildAt(index) ?: continue
            val position = getPosition(view)
            val bounds = if (position in 0 until totalItemCount) itemBounds(position) else null
            if (bounds == null || !isVisible(bounds)) {
                Log.d(TAG, "recycleOffscreen position=$position page=${currentPageIndex()} offset=$scrollOffsetX")
                removeAndRecycleView(view, recycler)
            }
        }

        for (position in 0 until totalItemCount) {
            val bounds = itemBounds(position) ?: continue
            if (!isVisible(bounds)) continue
            val view = findViewByPosition(position) ?: recycler.getViewForPosition(position).also {
                Log.d(TAG, "createView position=$position page=${currentPageIndex()} offset=$scrollOffsetX")
                addView(it)
            }
            layoutItem(view, bounds)
        }
    }

    private fun isVisible(bounds: RectF): Boolean =
        bounds.right >= scrollOffsetX && bounds.left <= scrollOffsetX + pageWidth

    private fun layoutItem(view: View, bounds: RectF) {
        val itemWidth = bounds.width().roundToInt()
        val itemHeight = bounds.height().roundToInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(itemWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(itemHeight, View.MeasureSpec.EXACTLY),
        )
        val left = (bounds.left - scrollOffsetX).roundToInt() + paddingLeft
        val top = bounds.top.roundToInt() + paddingTop
        view.layout(left, top, left + itemWidth, top + itemHeight)
        (view as? RoomParticipantCell)?.onLaidOut()
    }

    private fun maxScrollOffset(): Int = ((pageCount - 1).coerceAtLeast(0)) * pageWidth

    private fun itemBounds(position: Int): RectF? {
        val logicalWidth = pageWidth / density
        val logicalHeight = pageHeight / density
        if (logicalWidth <= 0f || logicalHeight <= 0f) return null
        if (isScreenPosition(position)) {
            return contentBounds(logicalWidth, logicalHeight).scaled(density)
        }
        val participantIndex = participantIndexForPosition(position)
        if (participantIndex !in 0 until participantCount) return null
        val pageOffset = participantIndex / PAGE_CAPACITY
        val itemOffset = participantIndex % PAGE_CAPACITY
        val itemCountOnPage = min(PAGE_CAPACITY, participantCount - pageOffset * PAGE_CAPACITY)
        val rects = participantRects(
            count = itemCountOnPage,
            multiplePages = participantCount > PAGE_CAPACITY,
            width = logicalWidth,
            height = logicalHeight,
        )
        val rect = rects.getOrNull(itemOffset) ?: return null
        rect.offset((leadingItemCount + pageOffset) * logicalWidth, 0f)
        return rect.scaled(density)
    }

    fun floatingFrame(): RectF {
        if (!hasFloatingWindow || pageWidth <= 0 || pageHeight <= 0) return RectF()
        return floatingBounds(pageWidth / density, pageHeight / density, floatingPosition).scaled(density)
    }

    fun handleFloatingPan(view: View, action: Int, translationX: Float, translationY: Float) {
        when (action) {
            MotionEvent.ACTION_DOWN -> floatingDragStartFrame.set(
                view.left.toFloat(), view.top.toFloat(), view.right.toFloat(), view.bottom.toFloat(),
            )
            MotionEvent.ACTION_MOVE -> {
                val clamped = clampedFloatingTranslation(translationX, translationY)
                layoutFloating(view, floatingDragStartFrame.left + clamped.first, floatingDragStartFrame.top + clamped.second)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val clamped = clampedFloatingTranslation(translationX, translationY)
                val left = floatingDragStartFrame.left + clamped.first
                val top = floatingDragStartFrame.top + clamped.second
                layoutFloating(view, left, top)
                floatingPosition = settledFloatingPosition(
                    left,
                    top,
                    floatingDragStartFrame.width().roundToInt(),
                    floatingDragStartFrame.height().roundToInt(),
                )
            }
        }
    }

    private fun layoutFloating(view: View, left: Float, top: Float) {
        val width = floatingDragStartFrame.width().roundToInt()
        val height = floatingDragStartFrame.height().roundToInt()
        view.layout(left.roundToInt(), top.roundToInt(), left.roundToInt() + width, top.roundToInt() + height)
    }

    private fun clampedFloatingTranslation(translationX: Float, translationY: Float): Pair<Float, Float> {
        if (pageWidth <= 0) return 0f to 0f
        val inset = floatingInset(pageWidth / density, pageHeight / density) * density
        val maxLeft = max(pageWidth - floatingDragStartFrame.width() - inset, inset)
        val maxTop = max(pageHeight - floatingDragStartFrame.height() - inset, inset)
        val left = min(max(floatingDragStartFrame.left + translationX, inset), maxLeft)
        val top = min(max(floatingDragStartFrame.top + translationY, inset), maxTop)
        return left - floatingDragStartFrame.left to top - floatingDragStartFrame.top
    }

    private fun settledFloatingPosition(left: Float, top: Float, width: Int, height: Int): FloatingWindowPosition {
        val inset = floatingInset(pageWidth / density, pageHeight / density) * density
        val maxTop = max(pageHeight - height - inset, inset)
        val clampedTop = min(max(top, inset), maxTop)
        val fraction = if (maxTop > inset) (clampedTop - inset) / (maxTop - inset) else 0f
        val side = if (left + width / 2f < pageWidth / 2f) FloatingSide.LEFT else FloatingSide.RIGHT
        return FloatingWindowPosition(side, fraction)
    }

    private fun RectF.scaled(scale: Float): RectF = RectF(left * scale, top * scale, right * scale, bottom * scale)

    private fun contentBounds(width: Float, height: Float): RectF {
        val edge = min(CONTENT_INSET_DP, min(width, height) / 8f)
        return RectF(edge, edge, width - edge, height - edge)
    }

    private fun floatingInset(width: Float, height: Float): Float = min(FLOATING_INSET_DP, min(width, height) / 4f)

    private fun participantRects(count: Int, multiplePages: Boolean, width: Float, height: Float): List<RectF> {
        if (multiplePages) {
            return gridRects(PAGE_CAPACITY, 3, GRID_ROW_COUNT, width, height).take(count)
        }
        return when (count) {
            1 -> listOf(contentBounds(width, height))
            2 -> gridRects(2, 2, 1, width, height)
            3, 4 -> gridRects(count, 2, GRID_ROW_COUNT, width, height)
            else -> gridRects(count, 3, GRID_ROW_COUNT, width, height)
        }
    }

    private fun floatingBounds(width: Float, height: Float, position: FloatingWindowPosition): RectF {
        val inset = floatingInset(width, height)
        val preferredWidth = min(width * 0.24f, 120f)
        val widthFittingHeight = (height - inset * 2) * ITEM_ASPECT_RATIO
        val itemWidth = min(preferredWidth, widthFittingHeight)
        val itemHeight = itemWidth / ITEM_ASPECT_RATIO
        val minTop = inset
        val maxTop = height - inset - itemHeight
        val top = if (maxTop <= minTop) {
            minTop
        } else {
            minTop + (maxTop - minTop) * position.verticalFraction.coerceIn(0f, 1f)
        }
        val left = if (position.side == FloatingSide.LEFT) inset else width - inset - itemWidth
        return RectF(left, top, left + itemWidth, top + itemHeight)
    }

    private fun gridRects(count: Int, columns: Int, rows: Int, width: Float, height: Float): List<RectF> {
        val maxSpacing = min(width, height) / 8f
        val edgeInset = min(CONTENT_INSET_DP, maxSpacing)
        val itemGap = min(ITEM_GAP_DP, maxSpacing)
        val widthPerColumn = (width - edgeInset * 2 - itemGap * (columns - 1)) / columns
        val heightPerRow = (height - edgeInset * 2 - itemGap * (rows - 1)) / rows
        val itemWidth = max(min(widthPerColumn, heightPerRow * ITEM_ASPECT_RATIO), 0f)
        val itemHeight = itemWidth / ITEM_ASPECT_RATIO
        val gridTop = (height - itemHeight * rows - itemGap * (rows - 1)) / 2

        return (0 until count).map { index ->
            val rowIndex = index / columns
            val columnIndex = index % columns
            val itemsInRow = min(columns, count - rowIndex * columns)
            val rowWidth = itemWidth * itemsInRow + itemGap * (itemsInRow - 1)
            val rowLeft = (width - rowWidth) / 2
            val left = rowLeft + columnIndex * (itemWidth + itemGap)
            val top = gridTop + rowIndex * (itemHeight + itemGap)
            RectF(left, top, left + itemWidth, top + itemHeight)
        }
    }
}
