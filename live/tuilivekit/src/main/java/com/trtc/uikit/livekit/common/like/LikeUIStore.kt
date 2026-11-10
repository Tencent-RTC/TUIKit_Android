package com.trtc.uikit.livekit.common.like

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.trtc.uikit.livekit.common.log.LiveKitLog
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LikeListener
import io.trtc.tuikit.atomicxcore.api.live.LikeStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo

class LikeUIStore(private val onReceiveLikes: (Int) -> Unit) {

    private val logger = LiveKitLog.Companion.getComponentLogger("LikeUIStore")
    private var currentLiveID: String? = null
    private var likeStore: LikeStore? = null
    private val likeListener = object : LikeListener() {
        override fun onReceiveLikesMessage(liveID: String, totalLikesReceived: Long, sender: LiveUserInfo) {
            handler.post {
                if (liveID == currentLiveID) {
                    onReceiveLikes(totalLikesReceived.coerceIn(1L, 6L).toInt())
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val likeSender = LikeSender()

    fun setLiveID(liveID: String?) {
        if (liveID == currentLiveID) return
        likeSender.deactivate()
        likeStore?.removeLikeListener(likeListener)
        currentLiveID = liveID?.takeIf { it.isNotEmpty() }
        likeStore = currentLiveID?.let { LikeStore.Companion.create(it) }
        likeStore?.addLikeListener(likeListener)
        val store = likeStore ?: return
        val targetLiveID = currentLiveID
        likeSender.activate { count ->
            store.sendLike(count, object : CompletionHandler {
                override fun onSuccess() = Unit
                override fun onFailure(code: Int, desc: String) {
                    logger.error("sendLike failed, liveID:$targetLiveID, count:$count, code:$code, desc:$desc")
                }
            })
        }
    }

    fun sendLike() = likeSender.sendLike()

    fun release() {
        setLiveID(null)
    }

}

/** Main-thread send batching only; each entry activation owns its SDK action. */
internal class LikeSender(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private val handler = Handler(Looper.getMainLooper())
    private var send: ((Int) -> Unit)? = null
    private var pendingCount = 0
    private var lastSendTime: Long? = null
    private var flushTask: Runnable? = null
    private var generation = 0L

    fun activate(send: (Int) -> Unit) {
        deactivate()
        this.send = send
    }

    fun sendLike() {
        if (send == null) return
        pendingCount++
        val elapsed = lastSendTime?.let { now() - it } ?: 6_000L
        if (elapsed >= 6_000L) {
            flush()
        } else if (flushTask == null) {
            val generation = generation
            flushTask = Runnable { if (this.generation == generation) flush() }.also {
                handler.postDelayed(it, 6_000L - elapsed)
            }
        }
    }

    fun deactivate(discardPending: Boolean = false) {
        generation++
        flushTask?.let(handler::removeCallbacks)
        flushTask = null
        val action = send
        val count = pendingCount
        send = null
        pendingCount = 0
        lastSendTime = null
        if (!discardPending && count > 0) action?.invoke(count)
    }

    private fun flush() {
        generation++
        flushTask?.let(handler::removeCallbacks)
        flushTask = null
        if (pendingCount == 0) return
        val count = pendingCount
        pendingCount = 0
        lastSendTime = now()
        send?.invoke(count)
    }
}
