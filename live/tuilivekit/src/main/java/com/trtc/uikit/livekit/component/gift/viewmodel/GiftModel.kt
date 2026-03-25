package com.trtc.uikit.livekit.component.gift.viewmodel

import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo

class GiftModel {
    var gift: Gift? = null
    var giftCount: Int = 0
    var sender: LiveUserInfo? = null
    var isFromSelf: Boolean = false

    val comboKey: String
        get() = "${sender?.userID ?: ""}_${gift?.giftID ?: ""}"

    val isAdvanced: Boolean
        get() = !gift?.resourceURL.isNullOrEmpty()

    fun addGiftCount(count: Int) {
        giftCount += count
    }

    fun reset() {
        gift = null
        giftCount = 0
        sender = null
        isFromSelf = false
    }
}
