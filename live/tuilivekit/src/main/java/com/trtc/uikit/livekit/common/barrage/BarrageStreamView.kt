package com.trtc.uikit.livekit.common.barrage

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.ColorRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.gift.LiveGiftBarrage
import com.trtc.uikit.livekit.common.theme.LevelBadgeDrawable
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.text.BreakIterator
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Final interaction positions in host-local pixels; notifications only crop the comment viewport. */
internal class LiveInteractionLayout(
    topBoundary: Int?,
    bottom: Int,
    defaultCommentHeight: Int,
    contentHeight: Int,
    isWelcomeVisible: Boolean,
    giftNoticeHeight: Int,
    safeBottom: Int,
    density: Float,
) {
    val commentHeight: Int
    val visibleCommentHeight: Int?
    val welcomeBottomOffset: Int
    val welcomeTop: Int
    val giftNoticeBottom: Int

    init {
        val spacing = (4 * density).toInt()
        val welcomeHeight = (28 * density).toInt()
        val welcomeReservation = if (isWelcomeVisible) welcomeHeight + spacing else 0
        val giftReservation = if (giftNoticeHeight > 0) giftNoticeHeight + spacing else 0
        commentHeight = topBoundary?.let { (bottom - it).coerceAtLeast(0) } ?: defaultCommentHeight
        visibleCommentHeight = topBoundary?.let {
            (bottom - it - welcomeReservation - giftReservation).coerceAtLeast(0)
        }
        val usedHeight = minOf(contentHeight.coerceAtLeast(0), visibleCommentHeight ?: commentHeight)
        welcomeBottomOffset = -(usedHeight + spacing)
        welcomeTop = bottom + welcomeBottomOffset - welcomeHeight
        giftNoticeBottom = minOf(safeBottom, bottom - spacing) - usedHeight - welcomeReservation
    }
}

class BarrageStreamView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        internal const val DEFAULT_WIDTH_RATIO = 0.8f
        private const val MAXIMUM_MESSAGE_COUNT = 100
    }

    private sealed class ScrollRequest {
        object None : ScrollRequest()
        object Earliest : ScrollRequest()
        data class Latest(val animated: Boolean) : ScrollRequest()
        data class Reading(val anchor: ReadingAnchor) : ScrollRequest()
    }

    private data class ReadingAnchor(val id: String, val topDistanceFromBottom: Int)

    private val recyclerView: LiveMessageRecyclerView
    private val newMessageView: AppCompatTextView
    private val messageAdapter = LiveMessageAdapter()
    private var requestedMessages: List<Message> = emptyList()
    private val layoutManager: LinearLayoutManager = object : LinearLayoutManager(context) {
        override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
            if (!state.isPreLayout) {
                isApplyingLayout = true
                prepareScrollTransaction()
            }
            super.onLayoutChildren(recycler, state)
        }

        override fun onLayoutCompleted(state: RecyclerView.State?) {
            super.onLayoutCompleted(state)
            if (state?.isPreLayout != true) completeScrollTransaction()
        }
    }.apply { stackFromEnd = true }
    private var subscribeJob: Job? = null
    private var compatibilityStore: BarrageUIStore? = null
    private var submissionGeneration = 0
    private var touchGeneration = 0
    private var pendingScroll: ScrollRequest = ScrollRequest.None
    private var animatedTarget = RecyclerView.NO_POSITION
    private var isApplyingLayout = false
    private var isFollowingLatest = true
    private var isUserScrolling = false
    private var isUserTouching = false
    private var unreadCount = 0
    private var maximumVisibleHeight: Int? = null
    private var hasExactContentHeight = false
    private var needsContentMeasurement = true
    private var lastReportedContentHeight = -1
    private var lastReportedVisibleHeight = -1

    /** Exact decorated extent when all rows are attached; otherwise at least the base viewport height.
     * Hosts only use min(contentHeight, visibleHeight). No off-screen row or Glide work is created to measure it.
     * Pure top cropping does not invalidate this extent or feed a changing clipped height back to the host.
     */
    internal var contentHeight: Int = 0
        private set
    internal var onContentHeightChanged: ((Int) -> Unit)? = null
    var onVisibleContentHeightChanged: ((Int) -> Unit)? = null

    internal val visibleHeight: Int
        get() = minOf(height, maximumVisibleHeight ?: height).coerceAtLeast(0)

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_live_message_list_view, this, true)
        recyclerView = findViewById(R.id.livekit_pusher_message_recycler)
        newMessageView = findViewById(R.id.livekit_pusher_message_new_count)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = messageAdapter
        recyclerView.itemAnimator = null
        messageAdapter.rowMetricsWillChange = { recordGeometryChange() }
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildLayoutPosition(view)
                outRect.bottom = if (position != RecyclerView.NO_POSITION && position < state.itemCount - 1) {
                    dimension(R.dimen.livekit_comment_row_spacing)
                } else 0
            }
        })
        recyclerView.addOnScrollListener(createMessageScrollListener())
        recyclerView.onTouchStateChanged = { touching ->
            isUserTouching = touching
            if (touching) {
                touchGeneration += 1
                pendingScroll = ScrollRequest.None
                animatedTarget = RecyclerView.NO_POSITION
            } else if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                updateFollowingState()
            }
        }
        newMessageView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        newMessageView.setOnClickListener {
            isUserScrolling = false
            recyclerView.stopScroll()
            requestLatest(animated = true)
        }
        visibility = GONE
    }

    @Suppress("UNUSED_PARAMETER")
    fun bind(liveID: String, owner: LiveUserInfo, includesPusherMessages: Boolean = true) {
        clearCompatibilitySubscription()
        if (liveID.isEmpty()) {
            updateMessages(emptyList())
            return
        }
        val store = BarrageUIStore(context)
        compatibilityStore = store
        store.activate(liveID, owner.userID.ifEmpty {
            LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        })
        subscribeJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            store.state.collectLatest { state ->
                updateMessages(state.comments.map { comment ->
                    Message(comment.id, comment.senderName, comment.text, comment.level, comment.isHost, comment.gift)
                })
            }
        }
    }

    internal data class Message(
        val id: String,
        val userName: String,
        val content: String,
        val level: Int,
        val isHost: Boolean = false,
        val gift: LiveGiftBarrage? = null,
        val isSystemAnnouncement: Boolean = false,
    )

    internal fun bind(messages: List<Message>) {
        clearCompatibilitySubscription()
        updateMessages(messages.takeLast(MAXIMUM_MESSAGE_COUNT))
    }

    internal fun appendGiftComment(comment: LiveGiftBarrage) {
        compatibilityStore?.appendGiftComment(comment)
    }

    internal fun setVisibleHeight(height: Int?) {
        val next = height?.coerceAtLeast(0)
        if (maximumVisibleHeight == next) return
        // Start padding expands the reachable first-row range. Preserve the actual screen anchor in the
        // same native layout transaction so padding changes cannot reposition either latest or history rows.
        if (pendingScroll == ScrollRequest.None) {
            captureReadingAnchor()?.let { pendingScroll = ScrollRequest.Reading(it) }
        }
        maximumVisibleHeight = next
        requestLayout()
    }

    internal fun prepareForReuse() {
        clearCompatibilitySubscription()
        requestedMessages = emptyList()
        submissionGeneration += 1
        touchGeneration += 1
        pendingScroll = ScrollRequest.None
        animatedTarget = RecyclerView.NO_POSITION
        isUserScrolling = false
        isUserTouching = false
        recyclerView.stopScroll()
        isFollowingLatest = true
        unreadCount = 0
        maximumVisibleHeight = null
        contentHeight = 0
        hasExactContentHeight = false
        needsContentMeasurement = true
        lastReportedContentHeight = -1
        lastReportedVisibleHeight = -1
        messageAdapter.submitList(emptyList())
        visibility = GONE
        updateNewMessageView()
        requestLayout()
    }

    override fun onDetachedFromWindow() {
        submissionGeneration += 1
        touchGeneration += 1
        pendingScroll = ScrollRequest.None
        animatedTarget = RecyclerView.NO_POSITION
        isUserScrolling = false
        isUserTouching = false
        isApplyingLayout = false
        recyclerView.stopScroll()
        clearCompatibilitySubscription()
        super.onDetachedFromWindow()
    }

    private fun clearCompatibilitySubscription() {
        subscribeJob?.cancel()
        subscribeJob = null
        compatibilityStore?.deactivate()
        compatibilityStore = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val nextWidth = MeasureSpec.getSize(widthMeasureSpec)
        if (nextWidth > 0 && messageAdapter.maximumRowWidth != nextWidth) {
            recordGeometryChange()
            messageAdapter.maximumRowWidth = nextWidth
            // Updating attached TextViews is sufficient; recycled rows use the adapter's latest width.
            repeat(recyclerView.childCount) { index ->
                (recyclerView.getChildAt(index) as? AppCompatTextView)?.maxWidth = nextWidth
            }
        }
        val clippedTop = (MeasureSpec.getSize(heightMeasureSpec) - (maximumVisibleHeight ?: MeasureSpec.getSize(
            heightMeasureSpec
        ))).coerceAtLeast(0)
        val padding = clippedTop + dimension(R.dimen.livekit_comment_fade_height)
        if (recyclerView.paddingTop != padding) recyclerView.setPadding(0, padding, 0, 0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (h != oldh) {
            recordGeometryChange()
            if (!hasExactContentHeight) needsContentMeasurement = true
        }
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        recyclerView.visibleTop = (height - visibleHeight).coerceAtLeast(0)
        super.onLayout(changed, left, top, right, bottom)
        updateNewMessageView()
        reportContentHeights()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y < height - visibleHeight) return false
        return super.dispatchTouchEvent(event)
    }

    private fun updateMessages(messages: List<Message>) {
        // The committed list can lag behind a pending diff; compare with the latest requested input.
        if (requestedMessages == messages) return
        requestedMessages = messages
        val previous = messageAdapter.currentList
        val previousIDs = previous.mapTo(HashSet(), Message::id)
        val added = messages.filter { it.id !in previousIDs }
        val hasNewHostComment = messages.lastOrNull()?.let { it.isHost && it.id !in previousIDs } == true
        val anchor = captureReadingAnchor(messages.mapTo(HashSet(), Message::id))
        val generation = ++submissionGeneration
        val touch = touchGeneration
        messageAdapter.submitList(messages) {
            if (generation != submissionGeneration) return@submitList
            visibility = if (messages.isEmpty()) GONE else VISIBLE
            needsContentMeasurement = true
            hasExactContentHeight = false
            recyclerView.invalidateItemDecorations()
            if (messages.isEmpty()) {
                pendingScroll = ScrollRequest.None
                contentHeight = 0
                isFollowingLatest = true
                unreadCount = 0
            } else if (touch == touchGeneration && !isUserTouching && !isUserScrolling && (previous.isEmpty() || isFollowingLatest || hasNewHostComment)) {
                pendingScroll = ScrollRequest.Latest(animated = previous.isNotEmpty() && added.isNotEmpty())
                isFollowingLatest = true
                unreadCount = 0
            } else {
                if (touch == touchGeneration) {
                    pendingScroll = if (anchor != null) ScrollRequest.Reading(anchor) else ScrollRequest.Earliest
                }
                unreadCount += added.size
            }
            updateNewMessageView()
            recyclerView.requestLayout()
        }
    }

    private fun requestLatest(animated: Boolean) {
        isFollowingLatest = true
        unreadCount = 0
        pendingScroll = ScrollRequest.Latest(animated)
        updateNewMessageView()
        recyclerView.requestLayout()
    }

    private fun recordGeometryChange() {
        if (isApplyingLayout) return
        needsContentMeasurement = true
        if (pendingScroll != ScrollRequest.None || isUserTouching || isUserScrolling) return
        if (isFollowingLatest) pendingScroll = ScrollRequest.Latest(animated = false)
        else captureReadingAnchor()?.let { pendingScroll = ScrollRequest.Reading(it) }
    }

    private fun prepareScrollTransaction() {
        // RecyclerView may run a measuring layout before its final layout; consume an intent only once.
        when (val request = pendingScroll) {
            ScrollRequest.None -> Unit
            ScrollRequest.Earliest -> {
                animatedTarget = RecyclerView.NO_POSITION
                if (messageAdapter.itemCount > 0) layoutManager.scrollToPositionWithOffset(0, 0)
            }

            is ScrollRequest.Latest -> {
                animatedTarget = RecyclerView.NO_POSITION
                val last = messageAdapter.itemCount - 1
                if (last >= 0) {
                    if (request.animated) animatedTarget = last else layoutManager.scrollToPosition(last)
                }
            }

            is ScrollRequest.Reading -> {
                animatedTarget = RecyclerView.NO_POSITION
                val position = messageAdapter.currentList.indexOfFirst { it.id == request.anchor.id }
                if (position >= 0) {
                    val desiredTop = recyclerView.height - request.anchor.topDistanceFromBottom
                    layoutManager.scrollToPositionWithOffset(position, desiredTop - recyclerView.paddingTop)
                } else if (messageAdapter.itemCount > 0) {
                    layoutManager.scrollToPositionWithOffset(0, 0)
                }
            }
        }
        pendingScroll = ScrollRequest.None
    }

    private fun completeScrollTransaction() {
        isApplyingLayout = false
        // Cropping may hide rows when measured. Refine the conservative extent once they all fit again.
        val canRefineExtent =
            !hasExactContentHeight && (contentHeight < recyclerView.height || recyclerView.childCount >= messageAdapter.itemCount)
        if (needsContentMeasurement || canRefineExtent) {
            updateContentExtent()
            needsContentMeasurement = false
        }
        reportContentHeights()
        val target = animatedTarget
        animatedTarget = RecyclerView.NO_POSITION
        if (target != RecyclerView.NO_POSITION && isFollowingLatest && !isUserTouching && !isUserScrolling) {
            recyclerView.smoothScrollToPosition(target)
        }
    }

    private fun captureReadingAnchor(retainedIDs: Set<String>? = null): ReadingAnchor? {
        val candidates = (0 until recyclerView.childCount).map { recyclerView.getChildAt(it) }.mapNotNull { view ->
            val id = messageAdapter.boundMessageID(recyclerView.getChildViewHolder(view)) ?: return@mapNotNull null
            if (retainedIDs != null && id !in retainedIDs) return@mapNotNull null
            id to view
        }
        val entry = candidates.lastOrNull {
            layoutManager.getDecoratedTop(it.second) < recyclerView.height && layoutManager.getDecoratedBottom(
                it.second
            ) > height - visibleHeight
        } ?: candidates.firstOrNull { layoutManager.getDecoratedBottom(it.second) > height - visibleHeight }
        ?: candidates.lastOrNull() ?: return null
        return ReadingAnchor(entry.first, recyclerView.height - layoutManager.getDecoratedTop(entry.second))
    }

    private fun updateContentExtent() {
        if (messageAdapter.itemCount == 0 || recyclerView.childCount == 0) {
            hasExactContentHeight = messageAdapter.itemCount == 0
            contentHeight = if (hasExactContentHeight) 0 else recyclerView.height
            return
        }
        val attachedIDs = HashSet<String>()
        var contentTop = Int.MAX_VALUE
        var contentBottom = Int.MIN_VALUE
        repeat(recyclerView.childCount) { index ->
            val child = recyclerView.getChildAt(index)
            messageAdapter.boundMessageID(recyclerView.getChildViewHolder(child))?.let(attachedIDs::add)
            contentTop = minOf(contentTop, layoutManager.getDecoratedTop(child))
            contentBottom = maxOf(contentBottom, layoutManager.getDecoratedBottom(child))
        }
        hasExactContentHeight = attachedIDs.size == messageAdapter.itemCount
        val attachedHeight = (contentBottom - contentTop).coerceAtLeast(0)
        contentHeight = if (hasExactContentHeight) attachedHeight else maxOf(recyclerView.height, attachedHeight)
    }

    private fun reportContentHeights() {
        if (contentHeight != lastReportedContentHeight) {
            lastReportedContentHeight = contentHeight
            onContentHeightChanged?.invoke(contentHeight)
        }
        val visible = visibleContentHeight()
        if (visible != lastReportedVisibleHeight) {
            lastReportedVisibleHeight = visible
            onVisibleContentHeightChanged?.invoke(visible)
        }
    }

    private fun updateNewMessageView() {
        newMessageView.visibility = if (unreadCount == 0 || visibleHeight < dimension(
                R.dimen.livekit_comment_new_message_height
            )
        ) GONE else VISIBLE
        newMessageView.text =
            resources.getQuantityString(R.plurals.livekit_pusher_new_messages, unreadCount, unreadCount)
    }

    private fun isNearBottom(): Boolean {
        val last = messageAdapter.itemCount - 1
        if (last < 0 || layoutManager.findLastVisibleItemPosition() < last) return last < 0
        val view = layoutManager.findViewByPosition(last) ?: return false
        return layoutManager.getDecoratedBottom(view) - recyclerView.height <= dimension(
            R.dimen.livekit_comment_bottom_threshold
        )
    }

    private fun createMessageScrollListener() = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (isApplyingLayout) return
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                touchGeneration += 1
                pendingScroll = ScrollRequest.None
                animatedTarget = RecyclerView.NO_POSITION
                isUserScrolling = true
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE && isUserScrolling) {
                updateFollowingState()
                isUserScrolling = false
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!isApplyingLayout && isUserScrolling) updateFollowingState()
        }
    }

    private fun updateFollowingState() {
        isFollowingLatest = isNearBottom()
        if (isFollowingLatest) unreadCount = 0
        updateNewMessageView()
    }

    internal fun visibleContentHeight(): Int = if (visibility == VISIBLE) minOf(contentHeight, visibleHeight) else 0
    internal fun canScrollMessages(direction: Int): Boolean = recyclerView.canScrollVertically(direction)
    private fun dimension(id: Int): Int = resources.getDimensionPixelSize(id)
}

/** RecyclerView keeps native measurement, recycling and anchoring; this only clips the effective visible top. */
internal class LiveMessageRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {
    var onTouchStateChanged: ((Boolean) -> Unit)? = null
    var visibleTop: Int = 0
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouchStateChanged?.invoke(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchStateChanged?.invoke(false)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return super.dispatchDraw(canvas)
        val top = visibleTop.coerceIn(0, height).toFloat()
        if (top >= height) return
        val clipped = canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), height.toFloat())
        val layer = canvas.saveLayer(0f, top, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        val fade = resources.getDimension(R.dimen.livekit_comment_fade_height).coerceAtMost(height - top)
        fadePaint.shader =
            LinearGradient(0f, top, 0f, top + fade, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), fadePaint)
        canvas.restoreToCount(layer)
        canvas.restoreToCount(clipped)
    }
}

private class LiveMessageAdapter : ListAdapter<BarrageStreamView.Message, RecyclerView.ViewHolder>(MessageDiff) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).gift == null) VIEW_TYPE_TEXT else VIEW_TYPE_GIFT

    var maximumRowWidth: Int = 0
    var rowMetricsWillChange: (() -> Unit)? = null

    fun boundMessageID(holder: RecyclerView.ViewHolder): String? = when (holder) {
        is MessageViewHolder -> holder.boundMessageID
        is GiftCommentRow -> holder.boundMessageID
        else -> null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_GIFT) {
            val row =
                LayoutInflater.from(parent.context).inflate(R.layout.livekit_pusher_gift_message_row, parent, false)
            return GiftCommentRow(row).apply { metricsWillChange = { rowMetricsWillChange?.invoke() } }
        }
        val label = LayoutInflater.from(parent.context)
            .inflate(R.layout.livekit_pusher_live_message_item, parent, false) as AppCompatTextView
        label.typeface = commentTypeface()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            label.breakStrategy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                LineBreaker.BREAK_STRATEGY_SIMPLE
            } else {
                0 // BREAK_STRATEGY_SIMPLE on API 23–28, before LineBreaker was public.
            }
            label.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
        label.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        return MessageViewHolder(label)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is MessageViewHolder -> holder.boundMessageID = message.id
            is GiftCommentRow -> holder.boundMessageID = message.id
        }
        when (holder) {
            is GiftCommentRow -> message.gift?.let { holder.bind(it, maximumRowWidth) }
            is MessageViewHolder -> holder.bind(message, maximumRowWidth)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        (holder as? GiftCommentRow)?.loadIcon()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        (holder as? GiftCommentRow)?.clearIcon()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? GiftCommentRow)?.clearIcon()
        super.onViewRecycled(holder)
    }

    class MessageViewHolder(private val label: AppCompatTextView) : RecyclerView.ViewHolder(label) {
        var boundMessageID: String? = null
        fun bind(message: BarrageStreamView.Message, maximumWidth: Int) {
            label.maxWidth = maximumWidth.coerceAtLeast(1)
            val text = makeText(message)
            label.text = text
            label.contentDescription = if (message.isSystemAnnouncement) message.content else {
                label.context.getString(
                    R.string.livekit_pusher_message_sender_format, message.userName
                ) + displayContent(message.content)
            }
            val verticalPadding = label.dimension(R.dimen.livekit_comment_row_vertical_inset)
            val horizontalPadding = label.resources.getDimension(R.dimen.livekit_comment_row_horizontal_inset).toInt()
            label.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        private fun makeText(message: BarrageStreamView.Message): SpannableStringBuilder {
            val text = SpannableStringBuilder()
            if (message.isSystemAnnouncement) {
                text.append(message.content)
                text.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(label.context, R.color.livekit_pusher_message_name)),
                    0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                return text
            }
            appendBadge(text, LevelBadgeDrawable.forLevel(label.context, message.level))
            if (message.isHost) {
                appendBadge(text, LiveHostBadgeDrawable.create(label.context))
            }
            val displayName = displayCommentUserName(message.userName)
            val sender = label.context.getString(R.string.livekit_pusher_message_sender_format, displayName)
            val nameStart = text.length
            text.append(sender)
            text.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(label.context, R.color.livekit_pusher_message_name)),
                nameStart,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            val bodyStart = text.length
            text.append(displayContent(message.content))
            text.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(label.context, R.color.livekit_pusher_message_body)),
                bodyStart,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            return text
        }

        private fun displayContent(content: String): String {
            val iterator = BreakIterator.getCharacterInstance().apply { setText(content) }
            var end = iterator.first()
            repeat(200) {
                val next = iterator.next()
                if (next == BreakIterator.DONE) return content
                end = next
            }
            return if (end < content.length) content.substring(0, end) + "..." else content
        }

        private fun appendBadge(text: SpannableStringBuilder, drawable: Drawable) {
            val start = text.length
            text.append("\uFFFC")
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            text.setSpan(
                InlineBadgeSpan(
                    drawable, label.dimension(R.dimen.livekit_comment_badge_spacing),
                    label.dimension(R.dimen.livekit_comment_badge_height),
                ),
                start,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

    }

    private object MessageDiff : DiffUtil.ItemCallback<BarrageStreamView.Message>() {
        override fun areItemsTheSame(
            oldItem: BarrageStreamView.Message,
            newItem: BarrageStreamView.Message,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: BarrageStreamView.Message,
            newItem: BarrageStreamView.Message,
        ): Boolean = oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_TEXT = 0
        const val VIEW_TYPE_GIFT = 1
    }
}

private class InlineBadgeSpan(
    private val drawable: Drawable,
    private val trailingSpacing: Int,
    private val height: Int,
) : ReplacementSpan() {
    private val width =
        (drawable.intrinsicWidth * height.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1)).roundToInt()
    private val clipPath = android.graphics.Path().apply {
        addRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()), height / 2f, height / 2f,
            android.graphics.Path.Direction.CW
        )
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        expandInlineMetrics(paint, fm, height)
        return width + trailingSpacing
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float,
        top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val metrics = paint.fontMetrics
        val translationY = y + (metrics.ascent + metrics.descent - height) / 2f
        val save = canvas.save()
        canvas.translate(x, translationY)
        canvas.clipPath(clipPath)
        canvas.scale(
            width.toFloat() / drawable.bounds.width().coerceAtLeast(1),
            height.toFloat() / drawable.bounds.height().coerceAtLeast(1)
        )
        drawable.draw(canvas)
        canvas.restoreToCount(save)
    }
}

private fun expandInlineMetrics(paint: Paint, fm: Paint.FontMetricsInt?, height: Int) {
    if (fm == null) return
    val metrics = paint.fontMetricsInt
    val center = (metrics.ascent + metrics.descent) / 2f
    fm.ascent = minOf(metrics.ascent, floor(center - height / 2f).toInt())
    fm.descent = maxOf(metrics.descent, ceil(center + height / 2f).toInt())
    fm.top = minOf(metrics.top, fm.ascent)
    fm.bottom = maxOf(metrics.bottom, fm.descent)
}

private fun commentTypeface(): Typeface {
    val family = Typeface.create("sans-serif", Typeface.NORMAL)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(family, 600, false)
    } else {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}

private fun View.dimension(id: Int): Int = resources.getDimensionPixelSize(id)

/** Host tag drawn inline before the name of a message sent by the broadcaster. */
private class LiveHostBadgeDrawable private constructor(private val context: Context) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.livekit_pusher_message_host_fill)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.livekit_comment_host_border_width)
        color = color(R.color.livekit_pusher_message_host_border)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.livekit_comment_host_text_size)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = color(R.color.livekit_pusher_message_host_text)
    }
    private val title = context.getString(R.string.live_barrage_anchor)
    private val height = context.resources.getDimensionPixelSize(R.dimen.livekit_comment_badge_height)
    private val width = maxOf(
        context.resources.getDimensionPixelSize(R.dimen.livekit_comment_host_min_width),
        ceil(textPaint.measureText(title)).toInt() + context.resources.getDimensionPixelSize(
            R.dimen.livekit_comment_host_horizontal_inset
        ) * 2,
    )
    private val rect = RectF()

    init {
        setBounds(0, 0, width, height)
    }

    override fun getIntrinsicWidth(): Int = width
    override fun getIntrinsicHeight(): Int = height

    override fun draw(canvas: Canvas) {
        val inset = strokePaint.strokeWidth / 2f
        rect.set(bounds)
        rect.inset(inset, inset)
        val radius = rect.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        val metrics = textPaint.fontMetrics
        val baseline = (rect.centerY() - (metrics.ascent + metrics.descent) / 2f).roundToInt().toFloat()
        canvas.drawText(title, rect.centerX().roundToInt().toFloat(), baseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    private fun color(@ColorRes resource: Int): Int = ContextCompat.getColor(context, resource)

    companion object {
        fun create(context: Context): LiveHostBadgeDrawable = LiveHostBadgeDrawable(context)
    }
}

internal class GiftCommentRow(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val label = itemView as AppCompatTextView
    var boundMessageID: String? = null
    var metricsWillChange: (() -> Unit)? = null
    private var comment: LiveGiftBarrage? = null
    private var icon: Drawable? = null
    private var showsIcon = false
    private var iconTarget: CustomTarget<Drawable>? = null

    init {
        label.typeface = commentTypeface()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            label.breakStrategy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                LineBreaker.BREAK_STRATEGY_SIMPLE
            } else {
                0 // BREAK_STRATEGY_SIMPLE on API 23–28, before LineBreaker was public.
            }
            label.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
        label.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun bind(comment: LiveGiftBarrage, maximumWidth: Int) {
        if (this.comment?.giftIconURL != comment.giftIconURL) {
            clearIcon()
            showsIcon = comment.giftIconURL.isNotEmpty()
        }
        this.comment = comment
        label.maxWidth = maximumWidth.coerceAtLeast(1)
        render()
        if (label.isAttachedToWindow) loadIcon()
    }

    fun loadIcon() {
        val url = comment?.giftIconURL?.takeIf { it.isNotEmpty() } ?: return
        if (iconTarget != null) return
        showsIcon = true
        val size = label.dimension(R.dimen.livekit_comment_gift_icon_size)
        val target = object : CustomTarget<Drawable>(size, size) {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (iconTarget !== this) return
                icon = resource
                render()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (iconTarget !== this) return
                icon = null
                showsIcon = false
                render()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (iconTarget !== this) return
                icon = null
                render()
            }
        }
        iconTarget = target
        Glide.with(label.context.applicationContext).load(url).fitCenter().into(target)
    }

    fun clearIcon() {
        val target = iconTarget
        iconTarget = null
        icon = null
        showsIcon = comment?.giftIconURL?.isNotEmpty() == true
        // Drop spans before Glide releases the bitmap held by the previous target.
        render()
        if (target != null) Glide.with(label.context.applicationContext).clear(target)
    }

    private fun render() {
        val comment = comment ?: return
        val context = label.context
        val sender = comment.senderName.ifEmpty { comment.senderID }
            .ifEmpty { context.getString(R.string.livekit_common_viewer) }
        val action = context.getString(R.string.livekit_common_gift_sent)
        val count = context.getString(R.string.livekit_pusher_gift_count_format, comment.count.toString())
        val text = SpannableStringBuilder()
        metricsWillChange?.invoke()
        fun append(value: String, @ColorRes color: Int) {
            val start = text.length
            text.append(value)
            text.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, color)), start, text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val badge = LevelBadgeDrawable.forLevel(context, comment.senderLevel)
        badge.setBounds(0, 0, badge.intrinsicWidth, badge.intrinsicHeight)
        text.append("\uFFFC")
        text.setSpan(
            InlineBadgeSpan(
                badge, label.dimension(R.dimen.livekit_comment_badge_spacing),
                label.dimension(R.dimen.livekit_comment_badge_height)
            ),
            0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        append(displayCommentUserName(sender), R.color.livekit_pusher_gift_message_sender)
        append(" $action ", R.color.livekit_pusher_gift_message_action)
        append(comment.giftName + " ", R.color.livekit_pusher_gift_message_highlight)
        if (showsIcon) {
            val start = text.length
            text.append("\uFFFC")
            text.setSpan(
                GiftIconSpan(icon, label.dimension(R.dimen.livekit_comment_gift_icon_size)), start, text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(" ", R.color.livekit_pusher_gift_message_highlight)
        }
        val countStart = text.length
        append(count, R.color.livekit_pusher_gift_message_highlight)
        text.setSpan(StyleSpan(Typeface.BOLD), countStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.text = text
        label.contentDescription = "$sender $action ${comment.giftName} $count"
    }
}

private class GiftIconSpan(private val drawable: Drawable?, private val size: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        expandInlineMetrics(paint, fm, size)
        return size
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val drawable = drawable ?: return
        val scale = size.toFloat() / maxOf(1, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val width = (drawable.intrinsicWidth.coerceAtLeast(1) * scale).toInt()
        val height = (drawable.intrinsicHeight.coerceAtLeast(1) * scale).toInt()
        drawable.setBounds(0, 0, width, height)
        val metrics = paint.fontMetrics
        canvas.withTranslation(x + (size - width) / 2f, y + (metrics.ascent + metrics.descent - height) / 2f) {
            drawable.draw(this)
        }
    }
}

private fun displayCommentUserName(raw: String): String {
    val name = raw.replace(Regex("\\r\\n|[\\n\\r\\u000B\\u000C\\u0085\\u2028\\u2029]"), " ")
    val iterator = BreakIterator.getCharacterInstance().apply { setText(name) }
    var count = 0
    iterator.first()
    while (true) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) return name
        // Older Android Unicode tables split joined emoji, modifiers and flag pairs.
        if (next < name.length && continuesCommentCharacter(name, next)) continue
        count += 1
        if (count == 20) return if (next < name.length) name.substring(0, next) + "…" else name
    }
}

private fun continuesCommentCharacter(text: String, boundary: Int): Boolean {
    val next = Character.codePointAt(text, boundary)
    val previous = Character.codePointBefore(text, boundary)
    val type = Character.getType(next)
    if (previous == 0x200D || next == 0x200D || next in 0x1F3FB..0x1F3FF || next in 0xE0020..0xE007F || type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()) return true
    if (next !in 0x1F1E6..0x1F1FF) return false
    var position = boundary
    var regionalIndicators = 0
    while (position > 0) {
        val codePoint = Character.codePointBefore(text, position)
        if (codePoint !in 0x1F1E6..0x1F1FF) break
        regionalIndicators += 1
        position -= Character.charCount(codePoint)
    }
    return regionalIndicators % 2 == 1
}
