package com.trtc.uikit.livekit.pusher.living.gift

import com.trtc.uikit.livekit.common.gift.player.GiftLanguage
import com.trtc.uikit.livekit.common.gift.player.LiveGiftComment
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftListener
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Aggregates gift statistics and settles local gift comments. Gift presentation is closed inside
 * [com.trtc.uikit.livekit.common.gift.GiftPlayView], which listens to [GiftStore] itself.
 */
internal class PusherGiftStore(private val liveID: String) {
    data class Statistics(
        val giftCount: Int = 0,
        val senderCount: Int = 0,
        val score: Long = 0,
    )

    data class State(
        val statistics: Statistics = Statistics(),
    )

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()
    private val mutableGiftComments = MutableSharedFlow<LiveGiftComment>(extraBufferCapacity = 64)
    val giftComments: SharedFlow<LiveGiftComment> = mutableGiftComments.asSharedFlow()

    private data class GiftCommentComboKey(val senderID: String, val giftID: String)

    private data class PendingGiftComment(
        val giftID: String,
        val giftName: String,
        val giftIconURL: String,
        val senderID: String,
        val senderName: String,
        val senderLevel: Int,
        var count: Int,
        var timer: Job? = null,
    )

    private val senderIDs = mutableSetOf<String>()
    private val pendingGiftComments = mutableMapOf<GiftCommentComboKey, PendingGiftComment>()
    private var sdkStore: GiftStore? = null
    private var listener: GiftListener? = null
    private var scope: CoroutineScope? = null
    private var isGiftCommentCollectionEnabled = false
    private var isApplicationActive = false

    fun activate() {
        if (liveID.isEmpty()) return
        deactivate(true)

        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        isGiftCommentCollectionEnabled = true
        isApplicationActive = false
        val sdkStore = GiftStore.create(liveID)
        this.sdkStore = sdkStore
        sdkStore.setLanguage(GiftLanguage.currentCode())
        val listener = object : GiftListener() {
            override fun onReceiveGift(
                liveID: String,
                gift: Gift,
                count: Int,
                sender: LiveUserInfo,
            ) {
                activeScope.launch { receive(liveID, gift, count, sender) }
            }
        }
        this.listener = listener
        sdkStore.addGiftListener(listener)
    }

    fun deactivate(resetStatistics: Boolean) {
        listener?.let { sdkStore?.removeGiftListener(it) }
        listener = null
        sdkStore = null
        cancelPendingGiftComments()
        scope?.cancel()
        scope = null
        isGiftCommentCollectionEnabled = false
        isApplicationActive = false
        if (!resetStatistics) return
        senderIDs.clear()
        mutableState.value = State()
    }

    fun setGiftCommentCollectionEnabled(enabled: Boolean) {
        if (isGiftCommentCollectionEnabled == enabled) return
        isGiftCommentCollectionEnabled = enabled
        if (!enabled) cancelPendingGiftComments()
    }

    fun setApplicationActive(active: Boolean) {
        if (isApplicationActive == active) return
        isApplicationActive = active
        if (!active) cancelPendingGiftComments()
    }

    private fun receive(eventLiveID: String, gift: Gift, count: Int, sender: LiveUserInfo) {
        if (eventLiveID != liveID || count <= 0) return

        val statistics = mutableState.value.statistics
        senderIDs.add(sender.userID)
        mutableState.value = State(
            statistics = Statistics(
                giftCount = saturatingAdd(statistics.giftCount.toLong(), count.toLong()).toIntSaturated(),
                senderCount = senderIDs.size,
                score = saturatingAdd(statistics.score, saturatingMultiply(gift.coins, count.toLong())),
            )
        )
        if (isGiftCommentCollectionEnabled && isApplicationActive) {
            accumulateGiftComment(gift, count, sender)
        }
    }

    private fun accumulateGiftComment(gift: Gift, count: Int, sender: LiveUserInfo) {
        val key = GiftCommentComboKey(sender.userID, gift.giftID)
        val pending = pendingGiftComments[key] ?: PendingGiftComment(
            giftID = gift.giftID,
            giftName = gift.name,
            giftIconURL = gift.iconURL,
            senderID = sender.userID,
            senderName = sender.userName,
            senderLevel = sender.level,
            count = 0,
        ).also { pendingGiftComments[key] = it }
        pending.count = saturatingAdd(pending.count.toLong(), count.toLong()).toIntSaturated()
        pending.timer?.cancel()
        pending.timer = scope?.launch {
            delay(GIFT_COMMENT_COMBO_INTERVAL_MS)
            flushGiftComment(key, pending)
        }
    }

    private fun flushGiftComment(key: GiftCommentComboKey, pending: PendingGiftComment) {
        if (
            !isGiftCommentCollectionEnabled ||
            !isApplicationActive ||
            pendingGiftComments[key] !== pending
        ) {
            return
        }
        pendingGiftComments.remove(key)
        mutableGiftComments.tryEmit(
            LiveGiftComment(
                giftID = pending.giftID,
                giftName = pending.giftName,
                giftIconURL = pending.giftIconURL,
                count = pending.count,
                senderID = pending.senderID,
                senderName = pending.senderName,
                senderLevel = pending.senderLevel,
            )
        )
    }

    private fun cancelPendingGiftComments() {
        pendingGiftComments.values.forEach { it.timer?.cancel() }
        pendingGiftComments.clear()
    }

    private fun saturatingAdd(lhs: Long, rhs: Long): Long {
        val sum = lhs + rhs
        return if (((lhs xor sum) and (rhs xor sum)) < 0) Long.MAX_VALUE else sum
    }

    private fun saturatingMultiply(lhs: Long, rhs: Long): Long {
        if (lhs == 0L || rhs == 0L) return 0
        val product = lhs * rhs
        return if (product / rhs != lhs) Long.MAX_VALUE else product
    }

    private fun Long.toIntSaturated(): Int =
        if (this > Int.MAX_VALUE) Int.MAX_VALUE else toInt()

    private companion object {
        const val GIFT_COMMENT_COMBO_INTERVAL_MS = 2_000L
    }
}
