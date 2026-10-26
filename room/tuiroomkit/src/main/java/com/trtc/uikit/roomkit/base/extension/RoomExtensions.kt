package com.trtc.uikit.roomkit.base.extension

import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.room.DeviceRequestInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomUser

/**
 * Extension functions for RoomParticipant, UserProfile, RoomUser, and RoomInfo.
 * Provides utility methods for getting display names with fallback logic.
 */

fun RoomParticipant.getDisplayName(): String {
    return when {
        userName.isNotEmpty() -> userName
        else -> userID
    }
}

fun UserProfile.getDisplayName(): String {
    val nickname = this.nickname ?: ""
    return when {
        nickname.isNotEmpty() -> nickname
        else -> userID
    }
}

fun RoomUser.getDisplayName(): String {
    return when {
        userName.isNotEmpty() -> userName
        else -> userID
    }
}

fun RoomInfo.getDisplayName(): String {
    return when {
        roomName.isNotEmpty() -> roomName
        else -> roomID
    }
}

fun DeviceRequestInfo.getSenderDisplayName(): String {
    return when {
        senderUserName.isNotEmpty() -> senderUserName
        else -> senderUserID
    }
}