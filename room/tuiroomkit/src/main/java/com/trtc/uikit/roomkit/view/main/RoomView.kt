package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.view.main.roomview.StandardRoomView
import com.trtc.uikit.roomkit.view.main.roomview.webinar.WebinarRoomView
import com.trtc.uikit.roomkit.view.main.roomview.webinar.WebinarVideoViewAdapterImpl
import io.trtc.tuikit.atomicxcore.api.room.RoomType

/**
 * Main room view component displaying video grid.
 * Manages video rendering, layout strategies, and participant interactions in the room.
 */
class RoomView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attrs, defStyleAttr) {

    private var rootView: View? = null

    fun init(roomID: String, roomType: RoomType) {
        removeAllViews()
        val root = when (roomType) {
            RoomType.WEBINAR -> WebinarRoomView(context).apply {
                init(roomID)
                val adapter = WebinarVideoViewAdapterImpl(context)
                setVideoViewAdapter(adapter)
            }

            RoomType.STANDARD -> StandardRoomView(context).apply {
                init(roomID)
            }
        }
        rootView = root
        val params = LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
            topToTop = LayoutParams.PARENT_ID
        }
        addView(root, params)
        (root as? StandardRoomView)?.onOrientationSwitchClick = onOrientationSwitchClick
    }

    var onOrientationSwitchClick: (() -> Unit)? = null
        set(value) {
            field = value
            (rootView as? StandardRoomView)?.onOrientationSwitchClick = value
        }
}