package com.tencent.uikit.app.live

import android.os.Bundle
import com.tencent.uikit.app.main.BaseActivity
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.livelist.LiveListView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore

class PlayerActivity : BaseActivity() {

    companion object {
        const val EXTRA_LIVE_ID = "liveID"
        private val LOGGER = LiveKitLog.getComponentLogger("PlayerActivity")
        private const val FETCH_LIST_COUNT = 6
        private const val SDK_NOT_INITIALIZED = -1002
    }

    private lateinit var liveListView: LiveListView
    private var isLoading = false
    private var hasFirstDataLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoggedIn()) return
        initLiveWindow()

        val liveID = intent.getStringExtra(EXTRA_LIVE_ID).orEmpty()
        if (liveID.isEmpty()) {
            finish()
            return
        }
        liveListView = object : LiveListView(this, liveID = liveID) {
            override fun onLoadMore() {
                loadMoreData()
            }
        }
        setContentView(liveListView)
        refreshData()
    }

    private fun refreshData() {
        if (isLoading) return
        fetchLiveList(isRefresh = true, callback = object : CompletionHandler {
            override fun onSuccess() {
                liveListView.setData(LiveListStore.shared().liveState.liveList.value)
                hasFirstDataLoaded = true
                isLoading = false
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("refreshData failed, code:$code, desc:$desc")
                isLoading = false
            }
        })
    }

    private fun loadMoreData() {
        if (isLoading) return
        val cursor = LiveListStore.shared().liveState.liveListCursor.value
        if (cursor.isEmpty() && hasFirstDataLoaded) return

        val oldSize = LiveListStore.shared().liveState.liveList.value.size
        fetchLiveList(isRefresh = false, callback = object : CompletionHandler {
            override fun onSuccess() {
                val newList = LiveListStore.shared().liveState.liveList.value
                if (newList.size > oldSize) {
                    liveListView.appendData(newList.subList(oldSize, newList.size))
                }
                hasFirstDataLoaded = true
                isLoading = false
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("loadMoreData failed, code:$code, desc:$desc")
                isLoading = false
            }
        })
    }

    private fun fetchLiveList(isRefresh: Boolean, callback: CompletionHandler) {
        if (!requireLoggedIn()) {
            callback.onFailure(SDK_NOT_INITIALIZED, "login first")
            return
        }
        isLoading = true
        val cursor = LiveListStore.shared().liveState.liveListCursor.value
        val fetchCursor = if (isRefresh || cursor.isEmpty()) "" else cursor
        LiveListStore.shared().fetchLiveList(fetchCursor, FETCH_LIST_COUNT, object : CompletionHandler {
            override fun onSuccess() {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) callback.onSuccess()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("fetchLiveList failed, code:$code, desc:$desc")
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    ErrorLocalized.onError(code)
                    callback.onFailure(code, desc)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        requireLoggedIn()
    }
}
