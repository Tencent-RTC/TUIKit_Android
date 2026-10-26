package com.trtc.uikit.roomkit.view.invitation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.CallRejectionReason
import io.trtc.tuikit.atomicxcore.api.room.RoomCall
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomListener
import io.trtc.tuikit.atomicxcore.api.room.RoomStore

@SuppressLint("StaticFieldLeak")
class RoomInvitationManager private constructor(private val context: Context) {

    private val logger = RoomKitLogger.getLogger("RoomInvitationManager")

    @Volatile
    private var isInvitationPending: Boolean = false

    private val roomListener = object : RoomListener() {
        override fun onCallReceived(
            roomInfo: RoomInfo,
            call: RoomCall,
            extensionInfo: String
        ) {
            logger.info(
                "onCallReceived: roomID=${roomInfo.roomID}, caller=${call.caller.userID}, " +
                        "isInvitationPending=$isInvitationPending"
            )

            // Already showing an invitation page → auto reject the new call.
            if (isInvitationPending) {
                logger.info("onCallReceived: another invitation is pending, auto reject")
                rejectCallInternal(roomInfo.roomID, CallRejectionReason.REJECTED)
                return
            }

            // Local user already inside another room → auto reject.
            val currentRoom = RoomStore.shared().state.currentRoom.value
            if (currentRoom?.roomID?.isNotEmpty() == true) {
                logger.info(
                    "onCallReceived: already in room ${currentRoom.roomID}, auto reject with IN_OTHER_ROOM"
                )
                rejectCallInternal(roomInfo.roomID, CallRejectionReason.IN_OTHER_ROOM)
                return
            }

            startInvitationReceivedActivity(roomInfo, call)
        }
    }

    init {
        RoomStore.shared().addRoomListener(roomListener)
        logger.info("RoomInvitationManager initialized, roomListener registered")
    }

    fun setInvitationPending(pending: Boolean) {
        logger.info("setInvitationPending: $pending")
        isInvitationPending = pending
    }

    private fun startInvitationReceivedActivity(roomInfo: RoomInfo, call: RoomCall) {
        val intent = Intent(context, RoomInvitationReceivedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(RoomInvitationReceivedActivity.EXTRA_ROOM_ID, roomInfo.roomID)
            putExtra(RoomInvitationReceivedActivity.EXTRA_ROOM_NAME, roomInfo.roomName)
            putExtra(RoomInvitationReceivedActivity.EXTRA_OWNER_NAME, roomInfo.roomOwner.userName)
            putExtra(RoomInvitationReceivedActivity.EXTRA_CALLER_NAME, call.caller.userName)
            putExtra(RoomInvitationReceivedActivity.EXTRA_CALLER_AVATAR_URL, call.caller.avatarURL)
            putExtra(RoomInvitationReceivedActivity.EXTRA_PARTICIPANT_COUNT, roomInfo.participantCount)
        }
        context.startActivity(intent)
    }

    private fun rejectCallInternal(roomID: String, reason: CallRejectionReason) {
        RoomStore.shared().rejectCall(
            roomID,
            reason,
            object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("auto rejectCall success: roomID=$roomID, reason=$reason")
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("auto rejectCall failed: roomID=$roomID, code=$code, desc=$desc")
                }
            }
        )
    }

    companion object {
        @Volatile
        private var instance: RoomInvitationManager? = null

        @JvmStatic
        @JvmOverloads
        fun getInstance(context: Context? = null): RoomInvitationManager {
            instance?.let { return it }
            val ctx = context ?: error(
                "RoomInvitationManager not initialized; " +
                        "first call must provide a Context (normally from RoomInitializer.onCreate)"
            )
            return synchronized(this) {
                instance ?: RoomInvitationManager(ctx.applicationContext).also { instance = it }
            }
        }
    }
}
