package com.trtc.uikit.roomkit

import android.os.Bundle
import com.trtc.uikit.roomkit.view.RoomCreateView
import io.trtc.tuikit.atomicx.common.FullScreenActivity

/**
 * RoomCreateActivity - Room creation screen container activity.
 * Pure container that loads RoomCreateView for configuring and creating new rooms.
 */
class RoomCreateActivity : FullScreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(RoomCreateView(this))
    }
}
