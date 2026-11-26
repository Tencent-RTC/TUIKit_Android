package com.trtc.uikit.roomkit.base.util

import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.room.DeviceRequestInfo
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomUser

fun UserProfile.getDisplayName(): String =
    nickname.orEmpty().ifBlank { userID }

fun RoomParticipant.getDisplayName(): String =
    nameCard.ifBlank { userName }.ifBlank { userID }

fun RoomUser.getDisplayName(): String =
    userName.ifBlank { userID }

fun DeviceRequestInfo.getSenderName(): String =
    senderNameCard.ifBlank { senderUserName }.ifBlank { senderUserID }

fun ParticipantRole.canManage(target: ParticipantRole): Boolean = when (this) {
    ParticipantRole.OWNER -> true
    ParticipantRole.ADMIN -> target == ParticipantRole.GENERAL_USER
    ParticipantRole.GENERAL_USER -> false
}
