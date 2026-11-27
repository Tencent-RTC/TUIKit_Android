package com.tencent.uikit.app.live.home

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tencent.uikit.app.R
import com.trtc.uikit.livekit.player.floating.FloatingPlayerManager
import com.trtc.uikit.livekit.player.floating.PlayerActivity
import io.trtc.tuikit.atomicx.common.event.EventManager
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class LiveHomeListView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var owner = ""
    private val refresh: SwipeRefreshLayout
    private val list: RecyclerView
    private val adapter: HomeAdapter
    private var pendingScroll: android.os.Parcelable? = null
    private var state = LiveHomeListStore.State()

    init {
        inflate(context, R.layout.app_live_home_list, this)
        refresh = findViewById(R.id.home_list_refresh)
        list = findViewById(R.id.home_list_grid)
        adapter = HomeAdapter { info, card ->
            if (info.liveID.startsWith("voice_")) {
                val xy = IntArray(2); card.getLocationInWindow(xy)
                val rect = Rect(xy[0], xy[1], xy[0] + card.width, xy[1] + card.height)
                val visible = Rect()
                card.getGlobalVisibleRect(visible)
                val snapshot =
                    android.graphics.Bitmap.createBitmap(card.width, card.height, android.graphics.Bitmap.Config.ARGB_8888)
                card.draw(android.graphics.Canvas(snapshot))
                send(
                    "open", mapOf(
                        "id" to info.liveID, "rect" to rect, "visible" to visible, "snapshot" to snapshot,
                        "cover" to card.coverDrawable()
                    )
                )
            } else {
                FloatingPlayerManager.releaseSession(false)
                val intent = Intent(context, PlayerActivity::class.java)
                intent.putExtra("liveID", info.liveID)
                context.startActivity(intent)
            }
        }
        val manager = GridLayoutManager(context, 2)
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.getItemViewType(position) == 1) 2 else 1
        }
        list.layoutManager = manager
        list.adapter = adapter
        list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, layoutState: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                val dp = resources.displayMetrics.density
                outRect.set(
                    if (position % 2 == 0) 0 else (5 * dp).toInt(), 0, if (position % 2 == 0) (5 * dp).toInt() else 0,
                    (12 * dp).toInt()
                )
            }
        })
        refresh.setOnRefreshListener { send("refresh") }
        findViewById<View>(R.id.home_list_retry).setOnClickListener { send("refresh") }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && manager.findLastVisibleItemPosition() >= state.items.size - 2 && !state.loading && state.canLoadMore) send(
                    "more"
                )
            }
        })
    }

    fun bind(ownerID: String) {
        owner = ownerID
    }

    fun updateState(next: LiveHomeListStore.State) {
        state = next
        adapter.submitList(
            next.items + if (next.loading && next.items.isNotEmpty() && !next.refresh) listOf(null) else emptyList()
        ) {
            if (adapter.itemCount > 0) pendingScroll?.let {
                list.layoutManager?.onRestoreInstanceState(
                    it
                ); pendingScroll = null
            }
        }
        refresh.isRefreshing = next.loading && next.refresh && next.items.isNotEmpty()
        findViewById<View>(R.id.home_list_status).visibility = if (next.items.isEmpty()) VISIBLE else GONE
        findViewById<ProgressBar>(R.id.home_list_loading).visibility = if (next.loading) VISIBLE else GONE
        findViewById<TextView>(R.id.home_list_message).apply {
            visibility = if (next.loading) GONE else VISIBLE
            setText(if (next.error) R.string.home_load_failed else R.string.home_empty)
        }
        findViewById<View>(R.id.home_list_retry).visibility = if (next.loading) GONE else VISIBLE
    }

    fun saveScroll() = list.layoutManager?.onSaveInstanceState()
    fun restoreScroll(state: android.os.Parcelable?) {
        pendingScroll = state
    }

    fun firstVisibleID(): String? =
        (list.layoutManager as GridLayoutManager).findFirstVisibleItemPosition().takeIf { it in state.items.indices }
            ?.let { state.items[it].liveID }

    private fun send(action: String, extra: Map<String, Any?> = emptyMap()) {
        EventManager.instance.notifyEvent("app.home.live", "list", extra + mapOf("owner" to owner, "action" to action))
    }
}

private class HomeAdapter(private val select: (LiveInfo, LiveHomeItemView) -> Unit) :
    ListAdapter<LiveInfo?, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<LiveInfo?>() {
        override fun areItemsTheSame(a: LiveInfo, b: LiveInfo) = a.liveID == b.liveID
        override fun areContentsTheSame(a: LiveInfo, b: LiveInfo) = a == b
    }) {
    override fun getItemViewType(position: Int) = if (getItem(position) == null) 1 else 0
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
        if (type == 1) object : RecyclerView.ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.app_live_home_footer, parent, false)
        ) {}
        else object : RecyclerView.ViewHolder(
            LiveHomeItemView(parent.context).apply { layoutParams = ViewGroup.LayoutParams(-1, -2) }) {}

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val card = holder.itemView as LiveHomeItemView
        card.updateData(item)
        card.setOnClickListener { select(item, card) }
    }
}
