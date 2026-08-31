package com.trtc.uikit.roomkit.view.main.roomview

import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

/**
 * Represents a video stream item in the grid
 * Wraps RoomParticipant with its associated stream type
 * 
 * This allows the same participant to have multiple streams (camera + screen share)
 * displayed as separate items in the RecyclerView
 * 
 * @property participant The room participant
 * @property streamType The type of video stream (CAMERA or SCREEN)
 * @property uniqueId Unique identifier combining userID and stream type for DiffUtil
 */
data class VideoStreamItem(
    val participant: RoomParticipant,
    val streamType: VideoStreamType
) {
    /**
     * Unique ID for this stream item
     * Used by RecyclerView adapter for stable IDs and DiffUtil comparison
     */
    val uniqueId: String = buildUniqueId(participant.userID, streamType)

    companion object {
        /**
         * Create a camera stream item
         */
        fun camera(participant: RoomParticipant) = VideoStreamItem(
            participant = participant,
            streamType = VideoStreamType.CAMERA
        )

        /**
         * Create a screen share stream item
         */
        fun screenShare(participant: RoomParticipant) = VideoStreamItem(
            participant = participant,
            streamType = VideoStreamType.SCREEN
        )

        fun buildUniqueId(userId: String, streamType: VideoStreamType): String =
            "${userId}_${streamType.name}"
    }
}
