package com.tencent.uikit.app.live.home

import android.os.Handler
import android.os.Looper
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveHomeListStore {
    data class State(
        val items: List<LiveInfo> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false,
        val canLoadMore: Boolean = true, val error: Boolean = false, val refresh: Boolean = false,
        val revision: Int = 0, val refreshRevision: Int = 0
    )

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var cursor = ""
    private var token = 0L
    private var active = true

    fun independentCopy() = LiveHomeListStore().also {
        it.cursor = cursor
        it.mutable.value = mutable.value.copy(
            items = mutable.value.items.map { info -> info.copy() }, loading = false, refresh = false, error = false
        )
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            token++
            mutable.value = mutable.value.copy(loading = false, refresh = false)
        }
    }

    fun refresh() = fetch("")
    fun loadMore() {
        if (mutable.value.loaded && mutable.value.canLoadMore && cursor.isNotEmpty()) fetch(cursor)
    }

    private fun fetch(requestCursor: String) {
        if (!active || mutable.value.loading) return
        val request = ++token
        val refresh = requestCursor.isEmpty()
        mutable.value = mutable.value.copy(loading = true, error = false, refresh = refresh)
        val sdk = LiveListStore.shared()
        sdk.fetchLiveList(requestCursor, 6, object : CompletionHandler {
            override fun onSuccess() {
                val incoming = sdk.liveState.liveList.value.map { it.copy() }
                val next = sdk.liveState.liveListCursor.value
                main.post {
                    if (!active || request != token) return@post
                    val result = linkedMapOf<String, LiveInfo>()
                    if (!refresh) mutable.value.items.forEach { result[it.liveID] = it }
                    incoming.filter { it.liveID.isNotBlank() }.forEach { result[it.liveID] = it }
                    cursor = next
                    mutable.value = mutable.value.copy(
                        items = result.values.toList(), loading = false, loaded = true, canLoadMore = next.isNotEmpty(),
                        refresh = false, error = false, revision = mutable.value.revision + 1,
                        refreshRevision = mutable.value.refreshRevision + if (refresh) 1 else 0
                    )
                }
            }

            override fun onFailure(code: Int, desc: String) {
                main.post {
                    if (active && request == token) mutable.value = mutable.value.copy(
                        loading = false, error = true, refresh = false
                    )
                }
            }
        })
    }
}
