package com.tencent.uikit.app.live.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.livekit.player.PlayerView
import com.trtc.uikit.livekit.player.PlayerStatus
import com.trtc.uikit.livekit.player.floating.FloatingPlayerManager
import io.trtc.tuikit.atomicx.common.event.EventManager
import io.trtc.tuikit.atomicx.common.event.INotification
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.*

class LiveHomePreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private inner class Session(val info: LiveInfo) {
        val player = object : PlayerView(context, liveID = info.liveID) {
            override fun onCloseClicked() {
                if (currentID == info.liveID) closeCurrent()
            }
        }
        var job: Job? = null
        var joining = false
        var joined = false
        var failed = false
    }

    private val host get() = context as MainActivity
    private val main = Handler(Looper.getMainLooper())
    private val pager: ViewPager2
    private val refresh: SwipeRefreshLayout
    private val adapter: FeedAdapter
    private var scope: CoroutineScope? = null
    private var owner = ""
    private var direct = false
    private var auto = false
    private var active = false
    private var callActive = false
    private val isPlaybackActive get() = active && !callActive
    private var currentID: String? = null
    private var initialID: String? = null
    private var firstJoined = false
    private var coverSeed: android.graphics.drawable.Drawable? = null
    private var state = LiveHomeListStore.State()
    private var renderedRefresh = -1
    private val items = mutableListOf<LiveInfo>()
    private val sessions = linkedMapOf<String, Session>()
    private val ended = mutableSetOf<String>()
    private val holders = mutableMapOf<String, LiveHomePreviewItemView>()
    private var unbindFloatingDismiss: (() -> Unit)? = null
    private val notification = INotification { _, subKey, params ->
        when (subKey) {
            "join" -> {
                updateInteraction()
                if (isPlaybackActive) post { settle() }
            }
            "call" -> {
                callActive = params?.get("active") == true
                updateActive(active)
            }
            else -> if (params?.get("owner") == owner && params["action"] == "enter" && params["id"] == currentID) enter()
        }
    }

    init {
        inflate(context, R.layout.app_live_home_preview, this)
        pager = findViewById(R.id.home_feed_pager)
        refresh = findViewById(R.id.home_feed_refresh)
        adapter = FeedAdapter()
        pager.adapter = adapter
        pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pager.offscreenPageLimit = 1
        (pager.getChildAt(0) as RecyclerView).itemAnimator = null
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                post { settle() }
            }

            override fun onPageScrollStateChanged(scrollState: Int) {
                updateInteraction()
                if (scrollState == ViewPager2.SCROLL_STATE_IDLE) settle()
            }
        })
        refresh.setOnRefreshListener { emit("refresh") }
        refresh.setOnChildScrollUpCallback { _, _ -> pager.currentItem > 0 || host.liveJoinPending || sessions[currentID]?.joined == true }
        findViewById<View>(R.id.home_feed_close).setOnClickListener { closeCurrent() }
        findViewById<View>(R.id.home_feed_return).setOnClickListener { returnToPreview() }
        findViewById<View>(R.id.home_feed_retry).setOnClickListener {
            if (items.isEmpty()) emit("refresh") else {
                sessions[currentID]?.failed = false
                if (ended.contains(currentID)) emit("refresh") else enter()
            }
        }
        findViewById<View>(R.id.home_feed_more).setOnClickListener { emit("more") }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            listOf(R.id.home_feed_close, R.id.home_feed_return).forEach { id ->
                findViewById<View>(id).let { view ->
                    view.layoutParams = (view.layoutParams as LayoutParams).apply { topMargin = top + dp(6) }
                }
            }
            insets
        }
    }

    fun bind(ownerID: String, selectedID: String?, directlyEnter: Boolean) {
        owner = ownerID
        initialID = selectedID
        direct = directlyEnter
        auto = directlyEnter
        if (!isPlaybackActive && currentID == null) items.indexOfFirst { it.liveID == selectedID }.takeIf { it >= 0 }
            ?.let { pager.setCurrentItem(it, false) }
    }

    fun updateState(next: LiveHomeListStore.State) {
        state = next
        val oldID = currentID ?: initialID
        val locked =
            sessions[currentID]?.let { it.joining || it.joined } == true || host.liveJoinPending || pager.scrollState != ViewPager2.SCROLL_STATE_IDLE
        val feedItems = next.items.filterNot { isVoiceRoom(it.liveID) }
        val append = feedItems.size >= items.size && items.indices.all { items[it].liveID == feedItems[it].liveID }
        if (!locked || append) {
            val oldCount = items.size
            if (renderedRefresh != next.refreshRevision && !locked) {
                ended.clear()
                renderedRefresh = next.refreshRevision
            }
            val changed = feedItems.map { it.liveID } != items.map { it.liveID }
            items.clear(); items.addAll(feedItems)
            if (changed) {
                if (append) adapter.notifyItemRangeInserted(oldCount, items.size - oldCount)
                else adapter.notifyDataSetChanged()
                val index = items.indexOfFirst { it.liveID == oldID }.takeIf { it >= 0 } ?: 0
                if (!append || oldCount == 0) pager.setCurrentItem(index, false)
            }
            items.forEach { info -> holders[info.liveID]?.bind(info, owner) }
        }
        refresh.isRefreshing = next.loading && next.refresh && items.isNotEmpty()
        findViewById<View>(R.id.home_feed_more).visibility = if (next.error && items.isNotEmpty()) VISIBLE else GONE
        updateInteraction()
        if (isPlaybackActive) post { settle() }
    }

    fun updateActive(value: Boolean, keepJoined: Boolean = false) {
        active = value
        if (isPlaybackActive) post { settle() }
        else {
            sessions.values.toList().forEach { session ->
                if (!(keepJoined && session.info.liveID == currentID && session.joined)) release(session)
            }
        }
    }

    fun updateSeedCover(drawable: android.graphics.drawable.Drawable?) {
        coverSeed = drawable
        holders[initialID]?.seedCover(drawable)
    }

    fun returnToPreview() {
        auto = false
        sessions[currentID]?.let { session ->
            if (session.joining) release(session)
            else {
                session.joined = false; session.player.leaveChannel()
            }
        }
        emit("presentation", mapOf("fullScreen" to direct))
        updateState(state)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) post { settle() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        callActive = host.isCallActive
        EventManager.instance.unRegisterEvent(notification)
        EventManager.instance.registerEvent("app.home.live", "preview", notification)
        EventManager.instance.registerEvent("app.home.live", "join", notification)
        EventManager.instance.registerEvent("app.home.live", "call", notification)
        unbindFloatingDismiss = FloatingPlayerManager.addFloatingDismissListener { liveID ->
            if (!isPlaybackActive || currentID != liveID) return@addFloatingDismissListener
            val status = sessions[liveID]?.player?.status?.value
            if (status == PlayerStatus.PLAYING || status == PlayerStatus.IN_ROOM || status == PlayerStatus.CO_GUEST) {
                return@addFloatingDismissListener
            }
            post { settle() }
        }
    }

    override fun onDetachedFromWindow() {
        updateActive(false)
        unbindFloatingDismiss?.invoke()
        unbindFloatingDismiss = null
        scope?.cancel(); scope = null
        EventManager.instance.unRegisterEvent(notification)
        super.onDetachedFromWindow()
    }

    private fun settle() {
        if (!isPlaybackActive || !isAttachedToWindow || pager.scrollState != ViewPager2.SCROLL_STATE_IDLE || items.isEmpty() || width <= 0 || height <= 0) return
        val index = pager.currentItem.coerceIn(items.indices)
        val id = items[index].liveID
        if (currentID != id) {
            sessions[currentID]?.let { old ->
                old.player.setVolume(0)
                if (old.joined) {
                    old.player.leaveChannel(); old.joined = false
                }
            }
            currentID = id
            emit("current", mapOf("id" to id))
        }
        val near = (index - 1..index + 1).filter { it in items.indices }.map { items[it].liveID }.toSet()
        sessions.values.toList()
            .filter { it.info.liveID !in near || FloatingPlayerManager.isFloating(it.info.liveID) }
            .forEach { release(it) }
        val currentFloating = FloatingPlayerManager.isFloating(id)
        val current = if (currentFloating) null else prepare(index)
        if (!currentFloating && auto && current != null && !current.joined && !current.joining && !current.failed && !ended.contains(
                id
            )
        ) enter()
        listOf(index - 1, index + 1).filter { it in items.indices }
            .filterNot { FloatingPlayerManager.isFloating(items[it].liveID) }
            .forEach { prepare(it) }
        sessions.values.forEach {
            it.player.setVolume(
                if (it.info.liveID == id && !currentFloating && !it.failed && !ended.contains(id)) 100 else 0
            )
        }
        if (index >= items.size - 2 && state.canLoadMore && !state.loading) emit("more")
        updateInteraction()
    }

    private fun prepare(index: Int): Session? {
        val info = items[index]
        if (FloatingPlayerManager.isFloating(info.liveID)) {
            sessions[info.liveID]?.let { release(it) }
            return null
        }
        if (ended.contains(info.liveID)) return sessions[info.liveID]
        val session = sessions[info.liveID] ?: Session(info).also { next ->
            sessions[info.liveID] = next
            next.player.setJoinLiveEntranceEnabled(false)
            next.player.setLiveInfo(info)
            next.job = scope?.launch {
                next.player.status.collect { status ->
                    if (sessions[info.liveID] !== next) return@collect
                    if (status == PlayerStatus.END) {
                        ended.add(info.liveID); next.joined = false
                    }
                    updateInteraction()
                }
            }
        }
        attach(session)
        if (session.player.width <= 0) {
            val canvasHeight = rootView.height.coerceAtLeast(height)
            session.player.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(canvasHeight, View.MeasureSpec.EXACTLY)
            )
            session.player.layout(0, 0, width, canvasHeight)
        }
        session.player.setVolume(if (info.liveID == currentID) 100 else 0)
        if (!session.failed && !ended.contains(info.liveID)) session.player.startPlay()
        return session
    }

    private fun attach(session: Session) {
        val cell = holders[session.info.liveID] ?: return
        if (session.player.parent !== cell.playerContainer) {
            (session.player.parent as? ViewGroup)?.removeView(session.player)
            cell.playerContainer.addView(session.player, LayoutParams(-1, -1))
            // Kit detachment releases feature subscriptions; restore them when a recycled cell takes ownership.
            session.player.setLiveInfo(session.info)
        }
        cell.updateCanvas()
    }

    private fun enter() {
        val session = sessions[currentID] ?: return
        if (FloatingPlayerManager.isFloating(session.info.liveID)) return
        if (!isPlaybackActive || session.joining || session.joined || ended.contains(
                currentID
            ) || pager.scrollState != ViewPager2.SCROLL_STATE_IDLE
        ) return
        val self = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        if (self.isNotEmpty() && self == session.info.liveOwner.userID) {
            session.failed = true
            Toast.makeText(context, R.string.home_own_live, Toast.LENGTH_SHORT).show()
            updateInteraction()
            return
        }
        if (!host.beginLiveJoin()) return
        session.joining = true
        session.failed = false
        updateInteraction()
        session.player.joinChannel(object : LiveInfoCompletionHandler {
            override fun onSuccess(liveInfo: LiveInfo) {
                complete(true)
            }

            override fun onFailure(code: Int, desc: String) {
                complete(false)
            }

            private fun complete(success: Boolean) {
                main.post {
                    session.joining = false
                    host.endLiveJoin()
                    if (!isPlaybackActive || sessions[session.info.liveID] !== session || currentID != session.info.liveID) {
                        cleanup(session)
                        if (isPlaybackActive) settle()
                        return@post
                    }
                    if (session.player.status.value == PlayerStatus.END) {
                        ended.add(session.info.liveID)
                        session.joined = false
                    } else if (success) {
                        session.joined = true
                        firstJoined = true
                        coverSeed = null
                        auto = true
                        emit("presentation", mapOf("fullScreen" to true))
                    } else {
                        session.failed = true
                        if (!auto && !direct) Toast.makeText(context, R.string.home_join_failed, Toast.LENGTH_SHORT)
                            .show()
                    }
                    updateState(state)
                }
            }
        })
    }

    private fun closeCurrent() {
        if (direct) emit("close") else returnToPreview()
    }

    private fun release(session: Session) {
        sessions.remove(session.info.liveID)
        session.job?.cancel()
        session.player.setVolume(0)
        session.player.leaveChannel()
        session.player.stopPlay()
        (session.player.parent as? ViewGroup)?.removeView(session.player)
    }

    private fun cleanup(session: Session) {
        session.player.leaveChannel()
        session.player.stopPlay()
        session.job?.cancel()
        (session.player.parent as? ViewGroup)?.removeView(session.player)
    }

    private fun updateInteraction() {
        val current = sessions[currentID]
        val status = current?.player?.status?.value
        val busy = host.liveJoinPending || sessions.values.any { it.joining }
        pager.isUserInputEnabled = !busy && status != PlayerStatus.CO_GUEST
        refresh.isEnabled = !busy && current?.joined != true
        holders.forEach { (id, item) ->
            val session = sessions[id]
            item.updatePresentation(
                !auto && !direct, busy || session?.joining == true, session?.player?.status?.value == PlayerStatus.END,
                direct && !firstJoined && id == initialID
            )
        }
        findViewById<View>(R.id.home_feed_close).visibility =
            if (direct && status != PlayerStatus.IN_ROOM && status != PlayerStatus.CO_GUEST) VISIBLE else GONE
        // A room handoff temporarily has no joined player; it is not an exit/error state.
        val settledFailure =
            pager.scrollState == ViewPager2.SCROLL_STATE_IDLE && !busy && current?.joined != true && (current?.failed == true || status == PlayerStatus.END || ended.contains(
                currentID
            ))
        findViewById<View>(R.id.home_feed_return).visibility = if (!direct && auto && settledFailure) VISIBLE else GONE
        val showError = items.isEmpty() || ((auto || direct) && current?.failed == true)
        findViewById<View>(R.id.home_feed_status).visibility = if (showError) VISIBLE else GONE
        findViewById<View>(R.id.home_feed_loading).visibility = if (items.isEmpty() && state.loading) VISIBLE else GONE
        findViewById<TextView>(R.id.home_feed_message).apply {
            visibility = if (items.isEmpty() && state.loading) GONE else VISIBLE
            setText(
                if (current?.failed == true) R.string.home_join_failed else if (state.error) R.string.home_load_failed else R.string.home_empty
            )
        }
        findViewById<View>(R.id.home_feed_retry).visibility = if (items.isEmpty() && state.loading) GONE else VISIBLE
        emit("busy", mapOf("busy" to busy))
    }

    private fun emit(action: String, extra: Map<String, Any?> = emptyMap()) {
        EventManager.instance.notifyEvent("app.home.live", "feed", extra + mapOf("owner" to owner, "action" to action))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val stableIDs = mutableMapOf<String, Long>()
    private var nextStableID = 0L

    private inner class FeedAdapter : RecyclerView.Adapter<FeedHolder>() {
        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int) = stableIDs.getOrPut(items[position].liveID) { ++nextStableID }
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            FeedHolder(LiveHomePreviewItemView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -1)
            })

        override fun onBindViewHolder(holder: FeedHolder, position: Int) {
            val info = items[position]
            holders.entries.removeAll { it.value === holder.itemView && it.key != info.liveID }
            val item = holder.itemView as LiveHomePreviewItemView
            holders[info.liveID] = item
            item.bind(info, owner)
            if (info.liveID == initialID && !firstJoined) item.seedCover(coverSeed)
            sessions[info.liveID]?.let { attach(it) }
            updateInteraction()
        }

        override fun onViewRecycled(holder: FeedHolder) {
            holders.entries.removeAll { it.value === holder.itemView }
        }

        override fun onViewAttachedToWindow(holder: FeedHolder) {
            post { if (isPlaybackActive) settle() }
        }
    }

    private class FeedHolder(view: View) : RecyclerView.ViewHolder(view)
}
