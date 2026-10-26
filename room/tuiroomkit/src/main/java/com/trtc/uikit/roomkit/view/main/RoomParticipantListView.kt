package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.view.main.participantlist.StandardRoomParticipantListView
import com.trtc.uikit.roomkit.view.main.participantlist.WebinarRoomParticipantListView
import io.trtc.tuikit.atomicxcore.api.room.RoomType

/**
 * Room participant list view component.
 * Displays the list of participants and provides control buttons for room management.
 * Supports both STANDARD and WEBINAR modes with different UI layouts.
 */
class RoomParticipantListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    fun init(roomID: String, roomType: RoomType) {
        removeAllViews()
        val rootView = if (roomType == RoomType.WEBINAR) {
            WebinarRoomParticipantListView(context).apply {
                init(roomID)
            }
        } else {
            StandardRoomParticipantListView(context).apply {
                init(roomID)
            }
        }
        val params = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        addView(rootView, params)
    }
}