package com.trtc.uikit.livekit.common.gift

import androidx.annotation.MainThread
import com.trtc.uikit.livekit.common.LiveKitResources
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Settles received gift combos into local barrages for both live entries. */
@MainThread
internal class GiftBarrageStore {
    private data class ComboKey(val senderID: String, val giftID: String)

    private data class PendingBarrage(
        val barrage: LiveGiftBarrage,
        var timer: Job? = null,
    )

    private val mutableGiftBarrages = MutableSharedFlow<LiveGiftBarrage>()
    val giftBarrages = mutableGiftBarrages.asSharedFlow()

    private var liveID = ""
    private var sdkStore: GiftStore? = null
    private var listener: GiftListener? = null
    private var scope: CoroutineScope? = null
    private var collectionID = UUID.randomUUID()
    private var isCollectionEnabled = false
    private var isApplicationActive = false
    private val pendingBarrages = mutableMapOf<ComboKey, PendingBarrage>()

    fun activate(liveID: String) {
        deactivate()
        if (liveID.isEmpty()) return
        this.liveID = liveID
        isCollectionEnabled = true
        sdkStore = GiftStore.create(liveID).also {
            it.setLanguage(LiveKitResources.giftLanguageCode)
        }
        updateCollection()
    }

    fun deactivate() {
        isCollectionEnabled = false
        isApplicationActive = false
        updateCollection()
        sdkStore = null
        liveID = ""
    }

    fun setCollectionEnabled(enabled: Boolean) {
        if (isCollectionEnabled == enabled) return
        isCollectionEnabled = enabled
        updateCollection()
    }

    fun setApplicationActive(active: Boolean) {
        if (isApplicationActive == active) return
        isApplicationActive = active
        updateCollection()
    }

    private fun updateCollection() {
        collectionID = UUID.randomUUID()
        listener?.let { sdkStore?.removeGiftListener(it) }
        listener = null
        scope?.cancel()
        scope = null
        pendingBarrages.clear()
        val store = sdkStore ?: return
        if (!isCollectionEnabled || !isApplicationActive) return

        val currentCollection = collectionID
        val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = currentScope
        val listener = object : GiftListener() {
            override fun onReceiveGift(
                liveID: String,
                gift: Gift,
                count: Int,
                sender: LiveUserInfo,
            ) {
                // Snapshot SDK values before dispatch; this listener keeps its original generation.
                val barrage = LiveGiftBarrage(
                    giftID = gift.giftID,
                    giftName = gift.name,
                    giftIconURL = gift.iconURL,
                    count = count,
                    senderID = sender.userID,
                    senderName = sender.userName,
                    senderLevel = sender.level,
                )
                currentScope.launch {
                    if (collectionID == currentCollection) receive(liveID, barrage)
                }
            }
        }
        this.listener = listener
        store.addGiftListener(listener)
    }

    private fun receive(eventLiveID: String, barrage: LiveGiftBarrage) {
        if (eventLiveID != liveID || barrage.count <= 0) return
        val currentScope = scope ?: return
        val key = ComboKey(barrage.senderID, barrage.giftID)
        val previous = pendingBarrages[key]
        previous?.timer?.cancel()
        val first = previous?.barrage ?: barrage.copy(count = 0)
        val total = (first.count.toLong() + barrage.count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val pending = PendingBarrage(first.copy(count = total))
        pendingBarrages[key] = pending
        pending.timer = currentScope.launch {
            delay(COMBO_INTERVAL_MS)
            if (pendingBarrages[key] !== pending) return@launch
            pendingBarrages.remove(key)
            mutableGiftBarrages.emit(pending.barrage)
        }
    }

    private companion object {
        const val COMBO_INTERVAL_MS = 2_000L
    }
}

/** A local snapshot of SDK gift events; it is never encoded or sent as a message. */
internal data class LiveGiftBarrage(
    val id: String = UUID.randomUUID().toString(),
    val giftID: String,
    val giftName: String,
    val giftIconURL: String,
    val count: Int,
    val senderID: String,
    val senderName: String,
    val senderLevel: Int,
)
