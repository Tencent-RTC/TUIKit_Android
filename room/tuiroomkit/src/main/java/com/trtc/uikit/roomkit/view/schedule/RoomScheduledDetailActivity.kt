package com.trtc.uikit.roomkit.view.schedule

import android.os.Bundle
import io.trtc.tuikit.atomicx.common.FullScreenActivity

class RoomScheduledDetailActivity : FullScreenActivity() {

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = RoomScheduledDetailView(this).apply {
            init(intent.getStringExtra(EXTRA_ROOM_ID) ?: "")
        }
        setContentView(view)
    }
}