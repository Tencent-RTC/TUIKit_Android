package com.trtc.uikit.roomkit

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.trtc.uikit.roomkit.view.RoomHomeView
import com.trtc.uikit.roomkit.view.RoomScheduleView
import com.trtc.uikit.roomkit.view.schedule.RoomScheduleInfoDialog
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo

/**
 * Home activity. Hosts [RoomHomeView] and registers the launcher used to open the schedule page;
 * on successful scheduling it shows [RoomScheduleInfoDialog] with the new room info.
 */
class RoomHomeActivity : FullScreenActivity() {

    private lateinit var roomHomeView: RoomHomeView
    private val scheduleInfoDialog by lazy { RoomScheduleInfoDialog(this) }

    private val scheduleRoomLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult

        val roomID = data.getStringExtra(RoomScheduleView.EXTRA_SCHEDULED_ROOM_ID).orEmpty()
        if (roomID.isEmpty()) return@registerForActivityResult

        val info = RoomInfo(
            roomID = roomID,
            roomName = data.getStringExtra(RoomScheduleView.EXTRA_SCHEDULED_ROOM_NAME).orEmpty(),
            password = data.getStringExtra(RoomScheduleView.EXTRA_SCHEDULED_PASSWORD).orEmpty(),
            scheduledStartTime = data.getLongExtra(RoomScheduleView.EXTRA_SCHEDULED_START_TIME, 0L),
            scheduledEndTime = data.getLongExtra(RoomScheduleView.EXTRA_SCHEDULED_END_TIME, 0L),
        )
        scheduleInfoDialog.show(info)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        roomHomeView = RoomHomeView(this).also {
            it.scheduleRoomLauncher = scheduleRoomLauncher
        }
        setContentView(roomHomeView)
    }
}
