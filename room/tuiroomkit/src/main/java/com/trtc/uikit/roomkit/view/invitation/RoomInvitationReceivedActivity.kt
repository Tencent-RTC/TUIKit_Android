package com.trtc.uikit.roomkit.view.invitation

import android.os.Bundle
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import io.trtc.tuikit.atomicx.common.FullScreenActivity

class RoomInvitationReceivedActivity : FullScreenActivity() {

    private val logger = RoomKitLogger.getLogger("RoomInvitationReceivedActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val roomID = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        val roomName = intent.getStringExtra(EXTRA_ROOM_NAME).orEmpty()
        val ownerName = intent.getStringExtra(EXTRA_OWNER_NAME).orEmpty()
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty()
        val callerAvatarUrl = intent.getStringExtra(EXTRA_CALLER_AVATAR_URL).orEmpty()
        val participantCount = intent.getIntExtra(EXTRA_PARTICIPANT_COUNT, 0)

        if (roomID.isEmpty()) {
            logger.error("onCreate: roomID is empty, finish")
            finish()
            return
        }
        val roomInvitationReceivedView = RoomInvitationReceivedView(this).also {
            it.bind(roomID, roomName, ownerName, callerName, callerAvatarUrl, participantCount)
        }
        setContentView(roomInvitationReceivedView)
    }

    @Suppress("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    companion object {
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_ROOM_NAME = "extra_room_name"
        const val EXTRA_OWNER_NAME = "extra_owner_name"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_AVATAR_URL = "extra_caller_avatar_url"
        const val EXTRA_PARTICIPANT_COUNT = "extra_participant_count"
    }
}
