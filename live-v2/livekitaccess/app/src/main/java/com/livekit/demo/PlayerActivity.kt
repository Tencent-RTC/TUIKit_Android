package com.livekit.demo

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.trtc.uikit.livekit.common.basic.ErrorLocalized
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.livelist.LiveListView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore

class PlayerActivity : AppCompatActivity() {

    companion object {
        private val LOGGER = LiveKitLogger.getComponentLogger("PlayerActivity")
        private const val FETCH_LIST_COUNT = 6
        private const val SDK_NOT_INITIALIZED = -1002
    }

    private lateinit var liveListView: LiveListView

    private var isLoading = false
    private var hasFirstDataLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoggedIn()) return
        initFullScreen()

        val liveID = intent.getStringExtra("liveID").orEmpty()

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
                val list = LiveListStore.shared().liveState.liveList.value
                liveListView.setData(list)
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
                callback.onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("fetchLiveList failed, code:$code, desc:$desc")
                ErrorLocalized.onError(code)
                callback.onFailure(code, desc)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        requireLoggedIn()
    }

    private fun initFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
