package com.trtc.uikit.livekit.common.like

import android.os.Handler
import android.os.Looper
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LikeListener
import io.trtc.tuikit.atomicxcore.api.live.LikeStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo

class LikeUIStore(private val onReceiveLikes: (Int) -> Unit) {

    companion object {
        private const val SEND_INTERVAL_MS = 6_000L
    }
    private val logger = LiveKitLogger.Companion.getComponentLogger("LikeUIStore")
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

    private var pendingCount: Int = 0
    private var lastSendAt: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val flushRunnable = Runnable { flushPending() }

    fun setLiveID(liveID: String?) {
        if (liveID == currentLiveID) return
        handler.removeCallbacks(flushRunnable)
        flushPending()
        likeStore?.removeLikeListener(likeListener)
        currentLiveID = liveID?.takeIf { it.isNotEmpty() }
        likeStore = currentLiveID?.let { LikeStore.Companion.create(it) }
        likeStore?.addLikeListener(likeListener)
        pendingCount = 0
        lastSendAt = 0L
    }

    fun sendLike() {
        if (likeStore == null) return
        pendingCount++
        val now = System.currentTimeMillis()
        val elapsed = now - lastSendAt
        handler.removeCallbacks(flushRunnable)
        if (lastSendAt == 0L || elapsed > SEND_INTERVAL_MS) {
            flushPending()
        } else {
            handler.postDelayed(flushRunnable, SEND_INTERVAL_MS - elapsed + 1L)
        }
    }

    fun release() {
        setLiveID(null)
    }

    private fun flushPending() {
        val count = pendingCount
        val store = likeStore
        if (count <= 0 || store == null) return
        pendingCount = 0
        lastSendAt = System.currentTimeMillis()
        val liveID = currentLiveID
        store.sendLike(count, object : CompletionHandler {
            override fun onSuccess() = Unit

            override fun onFailure(code: Int, desc: String) {
                logger.error("sendLike failed, liveID:$liveID, count:$count, code:$code, desc:$desc")
            }
        })
    }
}