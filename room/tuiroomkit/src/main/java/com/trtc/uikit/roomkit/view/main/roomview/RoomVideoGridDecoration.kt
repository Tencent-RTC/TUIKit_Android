package com.trtc.uikit.roomkit.view.main.roomview

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Room video grid decoration for managing video item sizes and spacing
 * 
 * Centralizes size logic by applying through ItemDecoration instead of
 * passing parameters through Adapter/ViewHolder chain.
 * 
 * Features:
 * - Sets item width/height dynamically
 * - Adds spacing for GridLayoutManager
 * - No spacing for PagedVideoLayoutManager (handles internally)
 * 
 * @param itemWidthPx Initial item width in pixels
 * @param itemHeightPx Initial item height in pixels
 * @param spacingPx Initial spacing between items in pixels
 */
class RoomVideoGridDecoration(
    private var itemWidthPx: Int = 0,
    private var itemHeightPx: Int = 0,
    private var spacingPx: Int = 0
) : RecyclerView.ItemDecoration() {

    /** Flag to control whether spacing should be applied */
    private var shouldApplySpacing = true

    /**
     * Set whether spacing should be applied
     * @param shouldApply true to apply spacing (GridLayoutManager), false to skip (PagedVideoLayoutManager)
     */
    fun setShouldApplySpacing(shouldApply: Boolean) {
        shouldApplySpacing = shouldApply
    }

    /**
     * Update item dimensions
     * Call this when container size changes
     * 
     * @param widthPx New item width in pixels
     * @param heightPx New item height in pixels
     * @param spacingPx New spacing in pixels (optional, keeps current if not provided)
     */
    fun updateItemSize(widthPx: Int, heightPx: Int, spacingPx: Int = this.spacingPx) {
        itemWidthPx = widthPx
        itemHeightPx = heightPx
        this.spacingPx = spacingPx
    }

    /**
     * Get current item size configuration
     * @return ItemSize containing current width, height, and spacing
     */
    fun getItemSize(): ItemSize = ItemSize(itemWidthPx, itemHeightPx, spacingPx)

    /**
     * Data class holding item size configuration
     */
    data class ItemSize(val widthPx: Int, val heightPx: Int, val spacingPx: Int)

    /**
     * Apply item size and spacing offsets
     * Called before onBindViewHolder to ensure correct sizing
     */
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        // Set item size via layoutParams
        if (itemWidthPx > 0 && itemHeightPx > 0) {
            val lp = view.layoutParams as? RecyclerView.LayoutParams 
                ?: RecyclerView.LayoutParams(itemWidthPx, itemHeightPx)
            lp.width = itemWidthPx
            lp.height = itemHeightPx
            view.layoutParams = lp
        }

        // Add spacing based on flag and layout manager type
        if (shouldApplySpacing && parent.layoutManager !is PagedVideoLayoutManager && spacingPx > 0) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) {
                outRect.setEmpty()
                return
            }

            // Add uniform spacing around items
            outRect.set(spacingPx / 2, spacingPx / 2, spacingPx / 2, spacingPx / 2)
        } else {
            outRect.setEmpty()
        }
    }
}
