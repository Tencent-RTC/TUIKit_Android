package com.trtc.uikit.livekit.features.endstatistics.store

import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import java.util.Locale

class EndStatisticsStore {
    private val internalState = EndStatisticsState()

    fun getState(): EndStatisticsState = internalState

    fun setRoomId(roomId: String) {
        internalState.roomId.value = roomId
    }

    fun setOwnerName(ownerName: String) {
        internalState.ownerName.value = ownerName
    }

    fun setOwnerAvatarUrl(ownerAvatarUrl: String) {
        internalState.ownerAvatarUrl.value = ownerAvatarUrl
    }

    fun setLiveDuration(duration: Long) {
        internalState.liveDurationMS.value = duration
    }

    fun setMaxViewersCount(count: Long) {
        internalState.maxViewersCount.value = count
    }

    fun setMessageCount(count: Long) {
        internalState.messageCount.value = count
    }

    fun setLikeCount(count: Long) {
        internalState.likeCount.value = count
    }

    fun setGiftIncome(count: Long) {
        internalState.giftIncome.value = count
    }

    fun setGiftSenderCount(count: Long) {
        internalState.giftSenderCount.value = count
    }

    fun setLiveEndedReason(reason: LiveEndedReason) {
        internalState.liveEndedReason.value = reason
    }

    fun formatSeconds(timeSeconds: Int): String {
        return if (timeSeconds > 0) {
            val hour = timeSeconds / 3600
            val min = timeSeconds % 3600 / 60
            val sec = timeSeconds % 60
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, min, sec)
        } else {
            "-- --"
        }
    }
}