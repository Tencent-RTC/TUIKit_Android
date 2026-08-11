package com.trtc.uikit.roomkit.view.schedule

import android.os.Bundle
import io.trtc.tuikit.atomicx.common.FullScreenActivity

/**
 * Activity for the "create / edit" scheduled-room page.
 *
 * When started with [EXTRA_EDIT_ROOM_ID], enters edit mode and lets [RoomScheduleView] pre-fill
 * the form data. Otherwise starts in create mode.
 */
class RoomScheduleActivity : FullScreenActivity() {

    companion object {
        const val EXTRA_EDIT_ROOM_ID = "edit_room_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = RoomScheduleView(this).apply {
            init(intent.getStringExtra(EXTRA_EDIT_ROOM_ID).orEmpty())
        }
        setContentView(view)
    }
}
