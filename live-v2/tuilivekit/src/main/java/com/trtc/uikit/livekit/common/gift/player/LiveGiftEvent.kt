package com.trtc.uikit.livekit.common.gift.player

internal data class LiveGiftEvent(
    val giftID: String,
    val name: String,
    val iconURL: String,
    val resourceURL: String,
    val coins: Long,
    val count: Int,
    val senderID: String,
    val senderName: String,
    val senderAvatarURL: String,
    val senderLevel: Int,
) {
    val comboKey: String get() = "${senderID}_$giftID"

    val isAdvanced: Boolean get() = resourceURL.isNotEmpty()
}
