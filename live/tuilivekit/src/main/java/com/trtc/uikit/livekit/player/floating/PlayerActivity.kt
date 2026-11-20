package com.trtc.uikit.livekit.player.floating

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.livelist.LiveListView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.launch

open class PlayerActivity : AppCompatActivity() {

    protected open val autoEnterRoom: Boolean = true

    private val logger = LiveKitLog.getComponentLogger("PlayerActivity")
    private var liveListView: LiveListView? = null
    private var isLoading = false
    private var loadMorePending = false
    private var hasFirstDataLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureLoggedIn()) return
        val liveID = intent.getStringExtra(EXTRA_LIVE_ID).orEmpty()
        if (autoEnterRoom && liveID.isEmpty()) {
            FloatingPlayerManager.releaseSession(onlyUnclaimed = true)
            finish()
            return
        }
        initFullScreen()
        val view = object : LiveListView(this, liveID = liveID, autoEnterRoom = autoEnterRoom) {
            override fun onLoadMore() {
                fetchData()
            }

            override fun onCloseClick() {
                (liveListView?.parent as ViewGroup).removeView(liveListView)
                finish()
            }
        }
        liveListView = view
        setContentView(view)
        view.setData(LiveListStore.shared().liveState.liveList.value)
        fetchData(isRefresh = true)
    }

    override fun onResume() {
        super.onResume()
        ensureLoggedIn()
    }

    override fun onDestroy() {
        liveListView = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
        FloatingPlayerManager.releaseSession(onlyUnclaimed = true)
    }

    protected open fun onLoginRequired() {
        finish()
    }

    private fun ensureLoggedIn(): Boolean {
        if (isFinishing || isDestroyed) return false
        if (!LoginStore.shared.loginState.loginUserInfo.value?.userID.isNullOrEmpty()) return true
        FloatingPlayerManager.releaseSession(onlyUnclaimed = true)
        onLoginRequired()
        return false
    }

    private fun fetchData(isRefresh: Boolean = false) {
        val view = liveListView ?: return
        if (!ensureLoggedIn()) return
        if (isLoading) {
            if (!isRefresh) loadMorePending = true
            return
        }
        val store = LiveListStore.shared()
        val refresh = isRefresh || !hasFirstDataLoaded
        val cursor = store.liveState.liveListCursor.value
        if (!refresh && cursor.isEmpty()) return
        val oldSize = store.liveState.liveList.value.size
        isLoading = true
        store.fetchLiveList(if (refresh) "" else cursor, FETCH_COUNT, object : CompletionHandler {
            override fun onSuccess() {
                lifecycleScope.launch {
                    if (isFinishing || isDestroyed || liveListView !== view) return@launch
                    isLoading = false
                    if (!ensureLoggedIn()) return@launch
                    hasFirstDataLoaded = true
                    val list = store.liveState.liveList.value
                    if (refresh) {
                        view.appendData(list)
                    } else if (list.size > oldSize) {
                        view.appendData(list.subList(oldSize, list.size))
                    }
                    if (loadMorePending) {
                        loadMorePending = false
                        fetchData()
                    }
                }
            }

            override fun onFailure(code: Int, desc: String) {
                lifecycleScope.launch {
                    if (isFinishing || isDestroyed || liveListView !== view) return@launch
                    isLoading = false
                    loadMorePending = false
                    if (!ensureLoggedIn()) return@launch
                    logger.error("fetchLiveList failed, refresh:$refresh, code:$code, desc:$desc")
                    ErrorLocalized.onError(code)
                }
            }
        })
    }

    private fun initFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    companion object {
        const val EXTRA_LIVE_ID = "liveID"
        private const val FETCH_COUNT = 6
    }
}
