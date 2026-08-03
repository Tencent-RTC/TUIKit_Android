package com.trtc.uikit.roomkit.view.schedule

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class RoomScheduledRoomItemDecoration : RecyclerView.ItemDecoration() {

    companion object {
        private const val ITEM_BOTTOM_DP = 20f
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        if (parent.adapter == null) return

        val density = parent.context.resources.displayMetrics.density
        outRect.left = 0
        outRect.right = 0
        outRect.top = 0
        outRect.bottom = (ITEM_BOTTOM_DP * density).toInt()
    }
}
