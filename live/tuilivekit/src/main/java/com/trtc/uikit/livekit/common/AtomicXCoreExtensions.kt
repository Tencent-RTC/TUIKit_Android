package com.trtc.uikit.livekit.common

import com.trtc.uikit.livekit.features.anchorview.store.BattleUser
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo

/**
 * Returns the user-facing name. Falls back to the user ID when the user name is empty.
 *
 * Aligned with iOS: atomic-x/ios/live/Sources/Common/Extension/AtomicXCoreExtension.swift
 */
val SeatUserInfo.displayName: String
    get() = userName.ifEmpty { userID }

val LiveUserInfo.displayName: String
    get() = userName.ifEmpty { userID }

val BattleUser.displayName: String
    get() = userName.ifEmpty { userId }
