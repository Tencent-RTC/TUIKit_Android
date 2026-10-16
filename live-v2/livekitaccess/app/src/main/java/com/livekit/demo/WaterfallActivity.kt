package com.livekit.demo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.basic.ErrorLocalized
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.basic.imageloader.ImageLoader
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class WaterfallActivity : AppCompatActivity() {

    companion object {
        private val LOGGER = LiveKitLogger.getComponentLogger("WaterfallActivity")
        private const val REFRESH_TIME_INTERVAL = 1000L
        private const val FETCH_LIST_COUNT = 6
        private const val SDK_NOT_INITIALIZED = -1002
    }

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var adapter: LiveListAdapter


    private val handler = Handler(Looper.getMainLooper())
    private var logDownX = 0f
    private var logDownY = 0f
    private val logPress = Runnable {
        if (!isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved &&
            supportFragmentManager.findFragmentByTag(com.livekit.demo.logshare.LogShareFragment.TAG) == null) {
            com.livekit.demo.logshare.LogShareFragment().showNow(
                supportFragmentManager, com.livekit.demo.logshare.LogShareFragment.TAG)
        }
    }
    private var isLoading = false
    private var loadingTime = 0L

    private val refreshFallbackRunnable = Runnable {
        if (!isLoading) swipeRefreshLayout.isRefreshing = false
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoggedIn()) return
        initFullScreen()

        setContentView(R.layout.activity_waterfall)

        val titleView = findViewById<View>(R.id.tv_title)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(titleView) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBar.top)
            insets
        }

        findViewById<View>(R.id.btn_start_live).setOnClickListener {
            startActivity(Intent(this, PusherActivity::class.java))
        }

        findViewById<View>(R.id.btn_version).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.app_version_format, getVersionName(this)))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        setupLogShare()
        setupLiveList()
        refreshData()


        LOGGER.info("WaterfallActivity onCreate:${getVersionName(context = this)}")
    }

    fun getVersionName(context: Context): String? {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageInfo.versionName
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLogShare() {
        val title = findViewById<View>(R.id.tv_title)
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        title.setOnLongClickListener { logPress.run(); true }
        title.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    logDownX = event.x
                    logDownY = event.y
                    handler.removeCallbacks(logPress)
                    handler.postDelayed(logPress, 2000L)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.x - logDownX) > slop ||
                        kotlin.math.abs(event.y - logDownY) > slop) handler.removeCallbacks(logPress)
                }
                android.view.MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(logPress)
                    view.performClick()
                }
                android.view.MotionEvent.ACTION_CANCEL, android.view.MotionEvent.ACTION_POINTER_DOWN ->
                    handler.removeCallbacks(logPress)
            }
            true
        }
    }

    override fun onPause() {
        handler.removeCallbacks(logPress)
        super.onPause()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupLiveList() {
        swipeRefreshLayout = findViewById(R.id.app_swipe_refresh)
        recyclerView = findViewById(R.id.app_recycler_view)

        swipeRefreshLayout.setColorSchemeResources(R.color.app_livelist_loading_color)
        swipeRefreshLayout.setOnRefreshListener(::refreshData)

        gridLayoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.addItemDecoration(LiveListAdapter.GridDividerItemDecoration(this))

        adapter = LiveListAdapter(this).apply {
            setOnItemClickListener { _, liveInfo -> onLiveItemClick(liveInfo) }
        }
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var isSlidingUpward = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lastItemPosition = gridLayoutManager.findLastVisibleItemPosition()
                    if (isSlidingUpward
                        && lastItemPosition == adapter.itemCount - 1
                        && LiveListStore.shared().liveState.liveListCursor.value.isNotEmpty()
                    ) {
                        loadMoreData()
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                isSlidingUpward = dy > 0
            }
        })

        val list = LiveListStore.shared().liveState.liveList.value
        if (list.isNotEmpty()) {
            adapter.setData(list)
            adapter.notifyDataSetChanged()
        }
    }

    private fun refreshData() {
        if (!requireLoggedIn()) return
        if (isLoading) return
        if (System.currentTimeMillis() - loadingTime < REFRESH_TIME_INTERVAL) {
            swipeRefreshLayout.removeCallbacks(refreshFallbackRunnable)
            swipeRefreshLayout.postDelayed(refreshFallbackRunnable, REFRESH_TIME_INTERVAL)
            return
        }

        loadingTime = System.currentTimeMillis()
        isLoading = true
        fetchLiveList(isRefresh = true, callback = object : CompletionHandler {
            @SuppressLint("NotifyDataSetChanged")
            override fun onSuccess() {
                adapter.setData(LiveListStore.shared().liveState.liveList.value)
                adapter.setLoadState(LOADING_COMPLETE)
                adapter.notifyDataSetChanged()
                swipeRefreshLayout.isRefreshing = false
                swipeRefreshLayout.post { isLoading = false }
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("refreshData failed:error,errorCode:$code,desc:$desc")
                swipeRefreshLayout.post {
                    isLoading = false
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        })
    }

    private fun loadMoreData() {
        if (isLoading) return
        adapter.setLoadState(LOADING)
        isLoading = true

        fetchLiveList(isRefresh = false, callback = object : CompletionHandler {
            override fun onSuccess() {
                val liveInfoList = LiveListStore.shared().liveState.liveList.value
                if (liveInfoList.isEmpty()) {
                    swipeRefreshLayout.post {
                        adapter.setLoadState(LOADING_COMPLETE)
                        isLoading = false
                    }
                    return
                }
                swipeRefreshLayout.post {
                    val itemCount = adapter.itemCount
                    adapter.setData(liveInfoList)
                    adapter.notifyItemRangeInserted(itemCount, liveInfoList.size)
                    isLoading = false
                    adapter.setLoadState(LOADING_COMPLETE)
                }
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("loadMoreData failed, errorCode:$code,desc:$desc")
                swipeRefreshLayout.post {
                    adapter.setLoadState(LOADING_COMPLETE)
                    isLoading = false
                }
            }
        })
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
                callback.onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("fetchLiveList failed:code:$code,desc:$desc")
                ErrorLocalized.onError(code)
                callback.onFailure(code, desc)
            }
        })
    }

    private fun openPlayer(liveID: String) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("liveID", liveID)
        startActivity(intent)
    }

    private fun onLiveItemClick(liveInfo: LiveInfo) {
        val loginUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        if (loginUserID.isNotEmpty() && liveInfo.liveOwner.userID == loginUserID) {
            Toast.makeText(this, R.string.app_livelist_click_own_live_hint, Toast.LENGTH_SHORT).show()
            return
        }
        openPlayer(liveInfo.liveID)
    }

    override fun onResume() {
        super.onResume()
        if (!requireLoggedIn()) return
        refreshData()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::swipeRefreshLayout.isInitialized) {
            swipeRefreshLayout.removeCallbacks(refreshFallbackRunnable)
        }
        super.onDestroy()
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

class LiveListItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ivCoverImage: ImageView
    private val avatar: AtomicAvatar
    private val textRoomName: TextView
    private val textAnchorName: TextView
    private val textAudienceCountInfo: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.app_livelist_item, this, true)
        ivCoverImage = findViewById(R.id.cover_image)
        avatar = findViewById(R.id.iv_avatar)
        textRoomName = findViewById(R.id.tv_room_name)
        textAnchorName = findViewById(R.id.tv_anchor_name)
        textAudienceCountInfo = findViewById(R.id.tv_audience_count_info)
    }

    @SuppressLint("StringFormatMatches", "SetTextI18n")
    fun bind(liveInfo: LiveInfo) {
        ImageLoader.load(
            context,
            ivCoverImage,
            liveInfo.coverURL ?: LiveKitResources.defaultCoverURL,
            R.drawable.app_livelist_default_cover
        )
        avatar.setContent(
            AvatarContent.URL(
                liveInfo.liveOwner.avatarURL,
                R.drawable.app_livelist_default_avatar
            )
        )
        textRoomName.text = if (TextUtils.isEmpty(liveInfo.liveName)) liveInfo.liveID else liveInfo.liveName
        textAnchorName.text =
            if (TextUtils.isEmpty(liveInfo.liveOwner.userName)) liveInfo.liveOwner.userID else liveInfo.liveOwner.userName
        textAudienceCountInfo.text = "${liveInfo.totalViewerCount}"
    }
}

const val TYPE_ITEM = 1
const val TYPE_FOOTER = 2
const val LOADING = 1
const val LOADING_COMPLETE = 2

class LiveListAdapter(
    private val context: FragmentActivity
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun interface OnItemClickListener {
        fun onItemClick(view: View, liveInfo: LiveInfo)
    }

    private var loadState = LOADING_COMPLETE
    private val liveInfoList = mutableListOf<LiveInfo>()
    private var onItemClickListener: OnItemClickListener? = null

    fun setData(newLiveInfoList: List<LiveInfo>) {
        liveInfoList.clear()
        liveInfoList.addAll(newLiveInfoList)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position + 1 == itemCount) TYPE_FOOTER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_FOOTER -> FootViewHolder(
                LayoutInflater.from(context)
                    .inflate(R.layout.app_livelist_refresh_footer, parent, false)
            )

            else -> LiveInfoViewHolder(LiveListItemView(context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FootViewHolder -> bindFootViewHolder(holder)
            is LiveInfoViewHolder -> bindLiveInfoViewHolder(holder, position)
        }
    }

    override fun getItemCount(): Int = liveInfoList.size + 1

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        (recyclerView.layoutManager as? GridLayoutManager)?.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (getItemViewType(position)) {
                        TYPE_FOOTER -> (recyclerView.layoutManager as GridLayoutManager).spanCount
                        else -> 1
                    }
                }
            }
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }

    private fun bindFootViewHolder(holder: FootViewHolder) {
        with(holder) {
            when (loadState) {
                LOADING -> {
                    pbProgressBar.visibility = View.VISIBLE
                    tvLoading.visibility = View.VISIBLE
                }

                else -> {
                    pbProgressBar.visibility = View.INVISIBLE
                    tvLoading.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun bindLiveInfoViewHolder(holder: LiveInfoViewHolder, position: Int) {
        val liveInfo = liveInfoList[position]
        val itemView = holder.itemView as LiveListItemView
        itemView.bind(liveInfo)
        itemView.apply {
            tag = liveInfo
            isEnabled = true
            setOnClickListener {
                onItemClickListener?.onItemClick(it, it.tag as LiveInfo)
            }
        }
    }

    fun setLoadState(newLoadState: Int) {
        loadState = newLoadState
        notifyItemChanged(itemCount - 1)
    }

    class LiveInfoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class GridDividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val dividerValue = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.set(dividerValue, dividerValue, dividerValue, dividerValue)
        }
    }

    private class FootViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pbProgressBar: ProgressBar = itemView.findViewById(R.id.pb_progress_bar)
        val tvLoading: TextView = itemView.findViewById(R.id.tv_loading_data)
    }
}
