package com.trtc.uikit.livekit.pusher.living.cohost

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import kotlin.math.min

internal class PusherCoHostCandidateListView(context: Context) : BottomSheetView(context) {
    internal var onInvite: ((String) -> Unit)? = null
    internal var onCancel: ((String) -> Unit)? = null
    internal var onRefresh: (() -> Unit)? = null
    internal var onLoadMore: (() -> Unit)? = null
    internal var onBack: (() -> Unit)? = null
    internal var onDidDismiss: (() -> Unit)? = null
    private val title: AppCompatTextView
    private val back: View
    private val refreshIndicator: android.widget.ProgressBar
    private var isRefreshing = false
    private val list: RecyclerView
    private val adapter = CandidatesAdapter()
    private var isAdding = false
    private var bottomInset = 0
    private var hostAvatarURL = ""
    private var state = PusherCoHostStore.State()
    private var rows: List<Row> = emptyList()
    override val sheetHeight: Int
        get() = min(
            dp(550) + bottomInset, (context.resources.displayMetrics.heightPixels - dp(80)).coerceAtLeast(0)
        )

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_cohost_candidate_view, contentView, true)
        title = contentView.findViewById(R.id.livekit_cohost_candidate_title)
        back = contentView.findViewById(R.id.livekit_cohost_back)
        refreshIndicator = contentView.findViewById(R.id.livekit_cohost_refresh)
        list = contentView.findViewById(R.id.livekit_cohost_candidates)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
    }

    fun bindHostAvatar(url: String) {
        if (hostAvatarURL != url) {
            hostAvatarURL = url
            if (rows.firstOrNull() == Row.Random) adapter.notifyItemChanged(0)
        }
    }

    fun show(isAdding: Boolean) {
        this.isAdding = isAdding
        back.isVisible = isAdding
        title.setText(if (isAdding) R.string.livekit_cohost_invite_title else R.string.livekit_cohost_interaction_title)
        (title.layoutParams as ConstraintLayout.LayoutParams).apply {
            leftMargin = dp(if (isAdding) 52 else 18)
            marginStart = leftMargin
            topMargin = dp(if (isAdding) 46 else 30)
            title.layoutParams = this
        }
        (list.layoutParams as ConstraintLayout.LayoutParams).apply {
            topMargin = dp(if (isAdding) 98 else 78)
            list.layoutParams = this
        }
        reload()
        list.scrollToPosition(0)
        show()
    }

    fun bind(state: PusherCoHostStore.State) {
        this.state = state
        if (!state.isLoading) isRefreshing = false
        refreshIndicator.isVisible = isRefreshing && state.isLoading
        reload()
    }

    override fun sheetWillPresent() {
        back.setOnClickListener { onBack?.invoke() }
        // A pull at the first item requests a fresh candidate page without replacing the scroll container.
        list.setOnTouchListener(object : View.OnTouchListener {
            var start = 0f
            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> start = event.y
                    android.view.MotionEvent.ACTION_UP -> if (event.y - start > dp(64) && !list.canScrollVertically(
                            -1
                        ) && !state.isLoading
                    ) {
                        isRefreshing = true; onRefresh?.invoke()
                    }
                }
                return false
            }
        })
        list.addOnScrollListener(scrollListener)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            contentView.setPadding(0, 0, 0, bottomInset)
            updateSheetHeight(false)
            insets
        }
        ViewCompat.requestApplyInsets(contentView)
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val last = (list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
            if (last >= rows.size - 3 && state.hasMore && !state.isLoading && state.loadError == null) list.post { if (isShowing && state.hasMore && !state.isLoading && state.loadError == null) onLoadMore?.invoke() }
        }
    }

    override fun sheetWillDismiss() {
        isRefreshing = false
        refreshIndicator.isVisible = false
        list.removeOnScrollListener(scrollListener)
        for (i in 0 until list.childCount) {
            (list.getChildViewHolder(list.getChildAt(i)) as? RandomHolder)?.view?.setPresentationEnabled(false)
        }
    }

    override fun sheetDidDismiss() {
        onDidDismiss?.invoke()
    }

    private fun reload() {
        val next = buildList {
            if (!isAdding) add(Row.Random)
            val groups = state.candidates.partition { if (isAdding) it.isMutual else it.isFollowing }
            listOf(groups.first, groups.second).forEachIndexed { index, users ->
                if (users.isNotEmpty()) {
                    add(
                        Row.Header(
                            if (index == 1) R.string.livekit_cohost_recommended else if (isAdding) R.string.livekit_cohost_mutual else R.string.livekit_cohost_following
                        )
                    )
                    users.forEach { add(Row.Person(it)) }
                    add(Row.Spacer(if (!isAdding && index == 0) 26 else 14))
                }
            }
            if (state.candidates.isEmpty() && !state.isLoading && state.loadError == null) add(Row.Empty)
            if (state.isLoading || state.loadError != null || state.hasMore) add(Row.Footer)
        }
        val same = rows == next
        rows = next
        if (same) adapter.notifyItemRangeChanged(0, rows.size) else adapter.notifyDataSetChanged()
    }

    private sealed interface Row {
        data object Random : Row
        data class Header(val title: Int) : Row
        data class Person(val user: PusherCoHostStore.User) : Row
        data class Spacer(val height: Int) : Row
        data object Empty : Row
        data object Footer : Row
    }

    private class RandomHolder(val view: PusherCoHostRandomConnectionView) : RecyclerView.ViewHolder(view)
    private class PersonHolder(view: View) : RecyclerView.ViewHolder(view) {
        var avatarURL: String? = null
        var user: PusherCoHostStore.User? = null
        val avatar: AppCompatImageView = view.findViewById(R.id.livekit_cohost_candidate_avatar)
        val name: AppCompatTextView = view.findViewById(R.id.livekit_cohost_candidate_name)
        val action: AppCompatTextView = view.findViewById(R.id.livekit_cohost_candidate_action)
    }

    private inner class CandidatesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            Row.Random -> 0
            is Row.Person -> 1
            else -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder = when (type) {
            0 -> RandomHolder(
                PusherCoHostRandomConnectionView(context).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(154))
                    setPadding(
                        0, 0, dp(2), dp(12)
                    )
                })

            1 -> PersonHolder(
                LayoutInflater.from(context).inflate(R.layout.livekit_pusher_cohost_candidate_item, parent, false)
            )

            else -> object : RecyclerView.ViewHolder(
                AppCompatTextView(context).apply {
                    includeFontPadding = false
                    textSize = 13f
                    setTextColor(
                        androidx.core.content.ContextCompat.getColor(context, R.color.livekit_cohost_secondary)
                    )
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(31))
                }) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                Row.Random -> {
                    val view = (holder as RandomHolder).view
                    view.bind(
                        hostAvatarURL, state.candidates
                    )
                    view.setPresentationEnabled(isShowing)
                }

                is Row.Person -> {
                    val item = holder as PersonHolder
                    val user = row.user
                    item.name.text = user.userName
                    item.user = user
                    if (item.avatarURL != user.avatarURL) {
                        item.avatarURL = user.avatarURL
                        ImageLoader.loadAvatar(
                            item.avatar, user.avatarURL.trim(), R.drawable.livekit_livelist_default_avatar
                        )
                    }
                    val connected = state.connected.any { it.liveID == user.liveID }
                    item.action.setText(
                        when {
                            connected -> R.string.livekit_cohost_connected
                            state.isBusy(
                                user.liveID
                            ) -> R.string.livekit_cohost_processing

                            state.isInvited(
                                user.liveID
                            ) -> R.string.livekit_cohost_cancel_invitation

                            else -> R.string.livekit_cohost_invite
                        }
                    )
                    item.action.isEnabled = !connected && !state.isBusy(user.liveID)
                    item.action.alpha = if (connected) .28f else if (item.action.isEnabled) 1f else .46f
                    item.action.setOnClickListener {
                        if (state.isInvited(user.liveID)) onCancel?.invoke(
                            user.liveID
                        ) else onInvite?.invoke(user.liveID)
                    }
                }

                else -> {
                    val text = holder.itemView as AppCompatTextView
                    val h = when (row) {
                        is Row.Spacer -> row.height
                        Row.Empty -> 60
                        Row.Footer -> 44
                        else -> 31
                    }
                    text.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
                    text.gravity = if (row is Row.Header) Gravity.CENTER_VERTICAL else Gravity.CENTER
                    text.setPadding(dp(2), 0, dp(2), 0)
                    text.text = when (row) {
                        is Row.Header -> context.getString(row.title)
                        Row.Empty -> context.getString(
                            R.string.livekit_cohost_empty
                        )

                        Row.Footer -> context.getString(
                            if (state.isLoading) R.string.livekit_cohost_processing else if (state.loadError != null) R.string.livekit_cohost_retry else R.string.livekit_cohost_load_more
                        )

                        else -> ""
                    }
                    text.isEnabled = !state.isLoading
                    text.setOnClickListener(
                        if (row == Row.Footer) View.OnClickListener { if (state.candidates.isEmpty() || state.loadError != null) onRefresh?.invoke() else onLoadMore?.invoke() } else null)
                }
            }
        }

        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            (holder as? RandomHolder)?.view?.setPresentationEnabled(isShowing)
            (holder as? PersonHolder)?.let { item ->
                val user = item.user
                if (user != null && item.avatarURL == null) {
                    item.avatarURL = user.avatarURL
                    ImageLoader.loadAvatar(
                        item.avatar, user.avatarURL.trim(), R.drawable.livekit_livelist_default_avatar
                    )
                }
            }
        }

        override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
            (holder as? RandomHolder)?.view?.setPresentationEnabled(false)
            (holder as? PersonHolder)?.let {
                Glide.with(
                    context
                ).clear(it.avatar)
                it.avatarURL = null
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            (holder as? PersonHolder)?.let {
                Glide.with(context).clear(it.avatar)
                it.avatarURL = null
            }
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
