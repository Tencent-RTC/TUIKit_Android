package com.trtc.uikit.roomkit.view.main.roomview

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Room video layout strategy selector
 * 
 * Selects appropriate LayoutManager based on participant count and screen share status.
 * Implements strategy pattern to dynamically switch between different layout modes.
 * 
 * Layout Modes:
 * - EMPTY: No participants (1x1 vertical grid, full screen)
 * - SINGLE: 1 participant (1x1 vertical grid, wrap content)
 * - DOUBLE: 2 participants (1x1 horizontal grid, wrap content)
 * - GRID: 3-6 participants (2xN vertical grid, wrap content)
 * - PAGING: 7+ participants (3x2 paged grid with horizontal scroll)
 * - SPEAKER_MODE: Screen share active (full screen + paged grid)
 * 
 * @param context Android context
 * @param recyclerView Target RecyclerView to configure
 * @param gridDecoration Grid decoration for managing dimensions
 */
class RoomVideoLayoutStrategy(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private val gridDecoration: RoomVideoGridDecoration
) {
    companion object {
        /** Grid configuration for paging mode */
        private const val PAGING_GRID_ROWS = 3
        private const val PAGING_GRID_COLUMNS = 2
    }

    private val pageSnapHelper = PagedVideoLayoutManager.PageSnapHelper()
    private var currentMode: LayoutMode = LayoutMode.EMPTY

    /**
     * Available layout modes
     */
    private enum class LayoutMode {
        /** No participants - empty state */
        EMPTY,
        /** Single participant - full viewport */
        SINGLE,
        /** Two participants - side by side */
        DOUBLE,
        /** 3-6 participants - simple grid */
        GRID,
        /** 7+ participants - paged grid */
        PAGING,
        /** Screen share active - speaker view + paged grid */
        SPEAKER_MODE
    }

    /**
     * Configure layout based on participant count and screen share status
     * 
     * @param count Total number of items (including screen share if present)
     * @param hasScreenShare Whether first item is screen share (triggers speaker mode)
     */
    fun configureForParticipantCount(count: Int, hasScreenShare: Boolean = false) {
        val newMode = determineLayoutMode(count, hasScreenShare)
        val modeChanged = currentMode != newMode
        currentMode = newMode

        when (newMode) {
            LayoutMode.EMPTY -> configureEmpty(modeChanged)
            LayoutMode.SINGLE -> configureSingle(modeChanged)
            LayoutMode.DOUBLE -> configureDouble(modeChanged)
            LayoutMode.GRID -> configureGrid(2, modeChanged)
            LayoutMode.PAGING -> configurePaging(modeChanged)
            LayoutMode.SPEAKER_MODE -> configureSpeakerMode(modeChanged)
        }
    }

    /**
     * Determine appropriate layout mode
     */
    private fun determineLayoutMode(count: Int, hasScreenShare: Boolean): LayoutMode {
        return when {
            count == 0 -> LayoutMode.EMPTY
            hasScreenShare -> LayoutMode.SPEAKER_MODE
            count == 1 -> LayoutMode.SINGLE
            count == 2 -> LayoutMode.DOUBLE
            count in 3..6 -> LayoutMode.GRID
            else -> LayoutMode.PAGING
        }
    }

    /**
     * Configure empty state (no participants)
     */
    private fun configureEmpty(createNew: Boolean) {
        detachPageSnap()
        if (createNew) {
            setGridLayout(1, GridLayoutManager.VERTICAL)
        }
        setSize(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        gridDecoration.setShouldApplySpacing(true)
        
        // Notify will be triggered after adapter updates data
    }

    /**
     * Configure single participant layout
     */
    private fun configureSingle(createNew: Boolean) {
        detachPageSnap()
        if (createNew) {
            setGridLayout(1, GridLayoutManager.VERTICAL)
        }
        setSize(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        gridDecoration.setShouldApplySpacing(true)
        
        // Notify will be triggered after adapter updates data
        // No need to post here, will call notifyAfterAdapterUpdate() externally
    }

    /**
     * Configure double participant layout (side by side)
     */
    private fun configureDouble(createNew: Boolean) {
        detachPageSnap()
        if (createNew) {
            setGridLayout(1, GridLayoutManager.HORIZONTAL)
        }
        setSize(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        gridDecoration.setShouldApplySpacing(true)
        
        // Notify will be triggered after adapter updates data
    }

    /**
     * Configure grid layout for 3-6 participants
     * @param spanCount Number of columns
     */
    private fun configureGrid(spanCount: Int, createNew: Boolean) {
        detachPageSnap()
        if (createNew) {
            setGridLayout(spanCount, GridLayoutManager.VERTICAL)
        }
        setSize(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        gridDecoration.setShouldApplySpacing(true)
        
        // Notify will be triggered after adapter updates data
    }

    /**
     * Configure paged layout for 7+ participants
     */
    private fun configurePaging(createNew: Boolean) {
        if (createNew) {
            val itemSize = gridDecoration.getItemSize()
            recyclerView.layoutManager = PagedVideoLayoutManager(
                PAGING_GRID_ROWS,
                PAGING_GRID_COLUMNS,
                itemSize.widthPx,
                itemSize.heightPx,
                itemSize.spacingPx
            )
        }

        attachPageSnap()
        recyclerView.isNestedScrollingEnabled = true
        gridDecoration.setShouldApplySpacing(false)

        // Clear virtual grid-page size in case we were previously in speaker mode
        getPagedLayoutManager()?.setGridPageSize(0, 0)

        val itemSize = gridDecoration.getItemSize()
        val pageWidth = itemSize.widthPx * PAGING_GRID_COLUMNS + itemSize.spacingPx * (PAGING_GRID_COLUMNS + 1)
        val pageHeight = itemSize.heightPx * PAGING_GRID_ROWS + itemSize.spacingPx * (PAGING_GRID_ROWS + 1)
        setSize(pageWidth, pageHeight)

        // Scroll to first page when entering paging mode
        if (createNew) {
            recyclerView.post { recyclerView.scrollToPosition(0) }
        }
    }

    /**
     * Configure speaker mode (screen share active)
     * Page 0: full-screen share; page 1+: grid of participants (same layout as PAGING mode)
     */
    private fun configureSpeakerMode(createNew: Boolean) {
        if (createNew) {
            val itemSize = gridDecoration.getItemSize()
            recyclerView.layoutManager = PagedVideoLayoutManager(
                PAGING_GRID_ROWS,
                PAGING_GRID_COLUMNS,
                itemSize.widthPx,
                itemSize.heightPx,
                itemSize.spacingPx
            )
        }

        attachPageSnap()
        recyclerView.isNestedScrollingEnabled = true
        gridDecoration.setShouldApplySpacing(false)

        // Fill the container so page 0 (screen share) can be truly full-screen
        setSize(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Keep grid pages (page 1+) visually identical to normal PAGING mode
        val itemSize = gridDecoration.getItemSize()
        val gridPageWidth = itemSize.widthPx * PAGING_GRID_COLUMNS + itemSize.spacingPx * (PAGING_GRID_COLUMNS + 1)
        val gridPageHeight = itemSize.heightPx * PAGING_GRID_ROWS + itemSize.spacingPx * (PAGING_GRID_ROWS + 1)
        getPagedLayoutManager()?.setGridPageSize(gridPageWidth, gridPageHeight)

        // Scroll to first page (full-screen speaker)
        if (createNew) {
            recyclerView.post { recyclerView.scrollToPosition(0) }
        }
    }
    
    /**
     * Get PagedVideoLayoutManager if current layout is paged
     * @return PagedVideoLayoutManager instance or null if not in paging/speaker mode
     */
    fun getPagedLayoutManager(): PagedVideoLayoutManager? {
        return recyclerView.layoutManager as? PagedVideoLayoutManager
    }
    
    /**
     * Get visible range for current layout mode
     * - For GridLayoutManager modes: returns all visible items
     * - For PagedVideoLayoutManager modes: delegates to layout manager
     * 
     * @return VisibleRange containing start/end positions and page index, or null if no items
     */
    fun getVisibleRange(): PagedVideoLayoutManager.VisibleRange? {
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (itemCount == 0) {
            return null
        }

        return when (currentMode) {
            LayoutMode.EMPTY -> null
            LayoutMode.SINGLE -> PagedVideoLayoutManager.VisibleRange(0, 0, 0)
            LayoutMode.DOUBLE -> PagedVideoLayoutManager.VisibleRange(0, 1, 0)
            LayoutMode.GRID -> PagedVideoLayoutManager.VisibleRange(0, itemCount - 1, 0)
            LayoutMode.PAGING, LayoutMode.SPEAKER_MODE -> {
                getPagedLayoutManager()?.getVisibleRange()
            }
        }
    }

    /**
     * Set GridLayoutManager for simple layouts
     */
    private fun setGridLayout(spanCount: Int, orientation: Int) {
        recyclerView.layoutManager = GridLayoutManager(context, spanCount, orientation, false)
        recyclerView.isNestedScrollingEnabled = false
    }

    /**
     * Set RecyclerView size
     */
    private fun setSize(width: Int, height: Int) {
        recyclerView.layoutParams = (recyclerView.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(width, height)).apply {
            this.width = width
            this.height = height
            this.gravity = Gravity.CENTER
        }
    }

    /**
     * Attach snap helper for paged scrolling
     */
    private fun attachPageSnap() = pageSnapHelper.attachToRecyclerView(recyclerView)

    /**
     * Detach snap helper
     */
    private fun detachPageSnap() = pageSnapHelper.attachToRecyclerView(null)
}
