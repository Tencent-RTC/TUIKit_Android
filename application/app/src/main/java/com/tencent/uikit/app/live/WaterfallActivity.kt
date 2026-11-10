package com.tencent.uikit.app.live

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tencent.uikit.app.BuildConfig
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.BaseActivity
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.log.LiveKitLog
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class WaterfallActivity : BaseActivity() {

    companion object {
        private val LOGGER = LiveKitLog.getComponentLogger("WaterfallActivity")
        private const val REFRESH_TIME_INTERVAL = 1000L
        private const val FETCH_LIST_COUNT = 6
        private const val SDK_NOT_INITIALIZED = -1002
    }

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: LiveListAdapter
    private var isLoading = false
    private var loadingTime = -REFRESH_TIME_INTERVAL
    private val refreshFallbackRunnable = Runnable {
        if (!isLoading) swipeRefreshLayout.isRefreshing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoggedIn()) return
        initLiveWindow()
        setContentView(R.layout.app_activity_waterfall)

        val root = findViewById<SwipeBackLayout>(R.id.app_live_root)
        root.onSwipeBack = { onBackPressedDispatcher.onBackPressed() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        findViewById<View>(R.id.btn_live_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<View>(R.id.btn_start_live).setOnClickListener {
            if (requireLoggedIn()) startActivity(Intent(this, PusherActivity::class.java))
        }
        findViewById<View>(R.id.btn_version).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.app_live_version_format, BuildConfig.VERSION_NAME))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        setupLiveList()
    }

    private fun setupLiveList() {
        swipeRefreshLayout = findViewById(R.id.app_swipe_refresh)
        swipeRefreshLayout.setColorSchemeResources(R.color.app_livelist_loading_color)
        swipeRefreshLayout.setOnRefreshListener(::refreshData)

        val recyclerView = findViewById<RecyclerView>(R.id.app_recycler_view)
        val gridLayoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.addItemDecoration(LiveListAdapter.GridDividerItemDecoration(this))
        adapter = LiveListAdapter(::onLiveItemClick)
        recyclerView.adapter = adapter
        adapter.setData(LiveListStore.shared().liveState.liveList.value)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var isSlidingUpward = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && isSlidingUpward &&
                    gridLayoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2 &&
                    LiveListStore.shared().liveState.liveListCursor.value.isNotEmpty()
                ) {
                    loadMoreData()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                isSlidingUpward = dy > 0
            }
        })
    }

    private fun refreshData() {
        if (!requireLoggedIn() || isLoading) return
        if (SystemClock.elapsedRealtime() - loadingTime < REFRESH_TIME_INTERVAL) {
            swipeRefreshLayout.removeCallbacks(refreshFallbackRunnable)
            swipeRefreshLayout.postDelayed(refreshFallbackRunnable, REFRESH_TIME_INTERVAL)
            return
        }
        loadingTime = SystemClock.elapsedRealtime()
        isLoading = true
        fetchLiveList(isRefresh = true, callback = object : CompletionHandler {
            override fun onSuccess() {
                adapter.setData(LiveListStore.shared().liveState.liveList.value)
                finishLoading()
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("refreshData failed, code:$code, desc:$desc")
                finishLoading()
            }
        })
    }

    private fun loadMoreData() {
        if (isLoading || !requireLoggedIn()) return
        if (LiveListStore.shared().liveState.liveListCursor.value.isEmpty()) return
        isLoading = true
        adapter.setLoading(true)
        fetchLiveList(isRefresh = false, callback = object : CompletionHandler {
            override fun onSuccess() {
                adapter.setData(LiveListStore.shared().liveState.liveList.value)
                finishLoading()
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("loadMoreData failed, code:$code, desc:$desc")
                finishLoading()
            }
        })
    }

    private fun finishLoading() {
        isLoading = false
        adapter.setLoading(false)
        swipeRefreshLayout.isRefreshing = false
    }

    private fun fetchLiveList(isRefresh: Boolean, callback: CompletionHandler) {
        if (!requireLoggedIn()) {
            callback.onFailure(SDK_NOT_INITIALIZED, "login first")
            return
        }
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

    private fun onLiveItemClick(liveInfo: LiveInfo) {
        if (!requireLoggedIn()) return
        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        if (liveInfo.liveOwner.userID == userID) {
            Toast.makeText(this, R.string.app_livelist_click_own_live_hint, Toast.LENGTH_SHORT).show()
            return
        }
        if (liveInfo.liveID.isEmpty()) return
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_LIVE_ID, liveInfo.liveID)
        })
    }

    override fun onResume() {
        super.onResume()
        if (::swipeRefreshLayout.isInitialized) refreshData()
    }

    override fun onDestroy() {
        if (::swipeRefreshLayout.isInitialized) {
            swipeRefreshLayout.removeCallbacks(refreshFallbackRunnable)
        }
        super.onDestroy()
    }
}
