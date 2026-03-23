package com.example.atomicxcore.components

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.atomicxcore.R
import com.google.android.material.button.MaterialButton
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore

/**
 * Co-host user list component
 *
 * Related APIs:
 * - LiveListStore.shared().fetchLiveList(cursor, count, completion) - Fetch the live room list
 * - LiveListStore.shared().state - Live list state (StateFlow)
 *
 * Features:
 * - Display a list of streamers available for co-hosting
 * - Show streamer avatar, nickname, and live room ID
 * - Support pull-to-refresh and load more
 * - Notify the consumer via callback when selected
 */
class CoHostUserListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var currentLiveID: String = ""
    private val liveList = mutableListOf<LiveInfo>()
    private var cursor: String = ""
    private val pageSize = 20
    private val adapter = HostListAdapter()
    private val recyclerView: RecyclerView
    private val emptyLabel: TextView
    private val loadingIndicator: ProgressBar

    /** Callback when a host is selected */
    var onSelectHost: ((LiveInfo) -> Unit)? = null

    /** Callback when the list is empty */
    var onEmptyList: (() -> Unit)? = null

    /** Callback when loading fails */
    var onLoadError: ((String) -> Unit)? = null

    /** Callback for dismissing the panel */
    var onDismiss: (() -> Unit)? = null

    init {
        val dp = resources.displayMetrics.density
        setBackgroundColor(Color.parseColor("#1F1F1F"))

        // List
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@CoHostUserListView.adapter
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = (4 * dp).toInt()
            }
        }
        addView(recyclerView)

        // Empty state
        emptyLabel = TextView(context).apply {
            text = context.getString(R.string.livePK_coHost_emptyList)
            textSize = 14f
            setTextColor(Color.parseColor("#80FFFFFF"))
            gravity = Gravity.CENTER
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        addView(emptyLabel)

        // Loading indicator
        loadingIndicator = ProgressBar(context).apply {
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        addView(loadingIndicator)
    }

    /**
     * Initialize and load the list
     */
    fun initialize(currentLiveID: String) {
        this.currentLiveID = currentLiveID
        loadList()
    }

    private fun loadList() {
        loadingIndicator.visibility = VISIBLE
        emptyLabel.visibility = GONE
        cursor = ""

        LiveListStore.shared().fetchLiveList(cursor, pageSize,
            com.example.atomicxcore.utils.completionHandler { code, message ->
                post {
                    loadingIndicator.visibility = GONE
                    if (code == 0) {
                        val state = LiveListStore.shared().liveState
                        cursor = state.liveListCursor.value
                        liveList.clear()
                        liveList.addAll(
                            (state.liveList.value)
                                .filter { it.liveID != currentLiveID }
                        )
                        emptyLabel.visibility = if (liveList.isEmpty()) VISIBLE else GONE
                        adapter.notifyDataSetChanged()
                        if (liveList.isEmpty()) onEmptyList?.invoke()
                    } else {
                        emptyLabel.visibility = VISIBLE
                        onLoadError?.invoke(message)
                    }
                }
            }
        )
    }

    /**
     * Refresh the list
     */
    fun refresh() {
        loadList()
    }

    // MARK: - RecyclerView Adapter

    private inner class HostListAdapter : RecyclerView.Adapter<HostListAdapter.ViewHolder>() {

        inner class ViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
            val avatarView: ImageView = container.findViewWithTag("avatar")
            val nameLabel: TextView = container.findViewWithTag("name")
            val liveIDLabel: TextView = container.findViewWithTag("liveID")
            val connectBtn: MaterialButton = container.findViewWithTag("connect")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.resources.displayMetrics.density
            val dp16 = (16 * dp).toInt()
            val dp12 = (12 * dp).toInt()
            val dp40 = (40 * dp).toInt()
            val dp64 = (64 * dp).toInt()
            val dp28 = (28 * dp).toInt()

            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, dp64
                )
                setPadding(dp16, 0, dp16, 0)
            }

            val avatarView = ImageView(parent.context).apply {
                tag = "avatar"
                layoutParams = LinearLayout.LayoutParams(dp40, dp40)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                setBackgroundColor(Color.parseColor("#1AFFFFFF"))
            }
            row.addView(avatarView)

            val textContainer = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp12
                }
            }

            val nameLabel = TextView(parent.context).apply {
                tag = "name"
                textSize = 15f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            textContainer.addView(nameLabel)

            val liveIDLabel = TextView(parent.context).apply {
                tag = "liveID"
                textSize = 12f
                setTextColor(Color.parseColor("#80FFFFFF"))
            }
            textContainer.addView(liveIDLabel)
            row.addView(textContainer)

            val dp8 = (8 * dp).toInt()
            val connectBtn = MaterialButton(parent.context).apply {
                tag = "connect"
                text = parent.context.getString(R.string.livePK_coHost_connect)
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#CCFF9800"))
                cornerRadius = (14 * dp).toInt()
                insetTop = 0
                insetBottom = 0
                minHeight = dp28
                minimumHeight = dp28
                setPadding(dp12, 0, dp12, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp28
                ).apply {
                    marginStart = dp8
                }
                isClickable = false // 点击整行触发
            }
            row.addView(connectBtn)

            return ViewHolder(row)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val liveInfo = liveList[position]
            val owner = liveInfo.liveOwner

            holder.nameLabel.text = if (owner?.userName.isNullOrEmpty()) (owner?.userID ?: "") else owner.userName
            holder.liveIDLabel.text = "ID: ${liveInfo.liveID}"

            if (!owner?.avatarURL.isNullOrEmpty()) {
                holder.avatarView.load(owner.avatarURL) {
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.ic_person)
                }
            } else {
                holder.avatarView.setImageResource(R.drawable.ic_person)
            }

            holder.container.setOnClickListener {
                onDismiss?.invoke()
                onSelectHost?.invoke(liveInfo)
            }
        }

        override fun getItemCount() = liveList.size
    }
}
