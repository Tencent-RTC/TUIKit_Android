package com.trtc.uikit.roomkit.base.util

import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant

fun UserProfile.getDisplayName(): String =
    nickname.orEmpty().ifBlank { userID }

fun RoomParticipant.getDisplayName(): String =
    nameCard.ifBlank { userName }.ifBlank { userID }
