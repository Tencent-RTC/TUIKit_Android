package com.trtc.uikit.livekit.features.endstatistics

import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason

object EndStatisticsDefine {

    data class AnchorEndStatisticsInfo(
        var roomId: String = "",
        var liveDurationMS: Long = 0L,
        var maxViewersCount: Long = 0L,
        var messageCount: Long = 0L,
        var likeCount: Long = 0L,
        var giftIncome: Long = 0L,
        var giftSenderCount: Long = 0L,
        var liveEndedReason: LiveEndedReason = LiveEndedReason.ENDED_BY_HOST
    )

    interface AnchorEndStatisticsViewListener {
        fun onCloseButtonClick()
    }

    interface AudienceEndStatisticsViewListener {
        fun onCloseButtonClick()
    }
}