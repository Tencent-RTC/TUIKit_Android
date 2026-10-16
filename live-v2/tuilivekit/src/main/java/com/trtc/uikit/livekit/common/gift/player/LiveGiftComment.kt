package com.trtc.uikit.livekit.common.gift.player

import java.util.UUID

internal data class LiveGiftComment(
    val id: String = UUID.randomUUID().toString(),
    val giftID: String,
    val giftName: String,
    val giftIconURL: String,
    val count: Int,
    val senderID: String,
    val senderName: String,
    val senderLevel: Int,
)
