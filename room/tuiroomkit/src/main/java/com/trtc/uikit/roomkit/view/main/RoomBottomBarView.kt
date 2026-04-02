package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.view.main.bottombar.StandardRoomBottomBarView
import com.trtc.uikit.roomkit.view.main.bottombar.WebinarRoomBottomBarView
import io.trtc.tuikit.atomicxcore.api.room.RoomType

/**
 * RoomBottomBarView - Bottom action bar container view.
 * Dynamically creates StandardRoomBottomBarView or WebinarRoomBottomBarView based on roomType.
 */
class RoomBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    fun init(roomID: String, roomType: RoomType) {
        removeAllViews()
        val rootView = if (roomType == RoomType.WEBINAR) {
            WebinarRoomBottomBarView(context).apply { init(roomID) }
        } else {
            StandardRoomBottomBarView(context).apply { init(roomID) }
        }
        addView(rootView, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
}
