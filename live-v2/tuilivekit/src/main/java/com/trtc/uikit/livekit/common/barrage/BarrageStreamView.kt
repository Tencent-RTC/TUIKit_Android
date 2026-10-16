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
import android.graphics.text.LineBreaker
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.gift.player.LiveGiftComment
import com.trtc.uikit.livekit.common.theme.LevelBadgeDrawable
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.BreakIterator

class BarrageStreamView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        internal const val DEFAULT_WIDTH_RATIO = 0.8f
        private const val MAXIMUM_MESSAGE_COUNT = 100
        private const val BOTTOM_THRESHOLD_DP = 8
        private const val SCROLL_SETTLE_DELAY_MS = 320L
    }

    private val recyclerView: LiveMessageRecyclerView
    private val newMessageView: AtomicLabel
    private val layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
    private val messageAdapter = LiveMessageAdapter()

    private var subscribeJob: Job? = null
    private var unreadCount = 0
    private var isFollowingLatest = true
    private var isUserScrolling = false
    private var isUserTouching = false
    private var scrollRequest = 0
    private var compatibilityStore: BarrageUIStore? = null

    var onVisibleContentHeightChanged: ((Int) -> Unit)? = null

    internal data class Message(
        val id: String,
        val userName: String,
        val content: String,
        val level: Int,
        val isHost: Boolean = false,
        val gift: LiveGiftComment? = null,
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_live_message_list_view, this, true)
        recyclerView = findViewById(R.id.livekit_pusher_message_recycler)
        newMessageView = findViewById(R.id.livekit_pusher_message_new_count)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = messageAdapter
        recyclerView.itemAnimator = null
        val rowSizeListener = OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                recyclerView.post {
                    if (isAttachedToWindow) {
                        onVisibleContentHeightChanged?.invoke(visibleContentHeight())
                        if (isFollowingLatest && !isUserTouching && !isUserScrolling) {
                            scheduleScrollToLatest(animated = false)
                        }
                    }
                }
            }
        }
        recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.addOnLayoutChangeListener(rowSizeListener)
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                view.removeOnLayoutChangeListener(rowSizeListener)
            }
        })
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                val position = parent.getChildLayoutPosition(view)
                outRect.bottom = if (
                    position != RecyclerView.NO_POSITION && position < state.itemCount - 1
                ) {
                    dp(2)
                } else {
                    0
                }
            }
        })
        recyclerView.addOnScrollListener(createMessageScrollListener())
        recyclerView.onTouchStateChanged = { touching ->
            isUserTouching = touching
            if (touching) scrollRequest += 1
            else if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) updateFollowingState()
        }
        newMessageView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        newMessageView.setOnClickListener { scrollToLatest(animated = true) }
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

    internal fun bind(messages: List<Message>) {
        clearCompatibilitySubscription()
        updateMessages(messages.takeLast(MAXIMUM_MESSAGE_COUNT))
    }

    internal fun appendGiftComment(comment: LiveGiftComment) {
        compatibilityStore?.appendGiftComment(comment)
    }

    override fun onDetachedFromWindow() {
        scrollRequest += 1
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
            val anchor = if (isFollowingLatest) null else captureVisibleAnchor(messageAdapter.currentList)
            messageAdapter.maximumRowWidth = nextWidth
            messageAdapter.notifyItemRangeChanged(0, messageAdapter.itemCount, Unit)
            if (isFollowingLatest) scheduleScrollToLatest(animated = false)
            else restoreVisibleAnchor(anchor, messageAdapter.currentList)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun updateMessages(messages: List<Message>) {
        val previous = messageAdapter.currentList
        if (previous == messages) return
        val wasEmpty = previous.isEmpty()
        val wasFollowingLatest = isFollowingLatest
        val addedCount = numberOfNewMessages(previous, messages)
        val latestIsHost = messages.lastOrNull()?.isHost == true
        val anchor = if (wasFollowingLatest) null else captureVisibleAnchor(previous)

        messageAdapter.submitList(messages) {
            visibility = if (messages.isEmpty()) GONE else VISIBLE
            recyclerView.invalidateItemDecorations()
            requestLayout()
            post { onVisibleContentHeightChanged?.invoke(visibleContentHeight()) }
            if (messages.isEmpty()) {
                unreadCount = 0
                isFollowingLatest = true
                updateNewMessageView()
                return@submitList
            }
            if (!isUserTouching && !isUserScrolling &&
                (wasEmpty || wasFollowingLatest && isFollowingLatest || latestIsHost)
            ) {
                isFollowingLatest = true
                unreadCount = 0
                scheduleScrollToLatest(animated = !wasEmpty)
            } else if (addedCount > 0) {
                restoreVisibleAnchor(anchor, messages)
                unreadCount += addedCount
            }
            updateNewMessageView()
        }
    }

    private fun captureVisibleAnchor(messages: List<Message>): VisibleAnchor? {
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position !in messages.indices) return null
        val view = layoutManager.findViewByPosition(position) ?: return null
        return VisibleAnchor(messages[position].id, view.top - recyclerView.paddingTop)
    }

    private fun restoreVisibleAnchor(anchor: VisibleAnchor?, messages: List<Message>) {
        if (anchor == null) return
        val position = messages.indexOfFirst { it.id == anchor.id }
        if (position >= 0) layoutManager.scrollToPositionWithOffset(position, anchor.offset)
    }

    private fun numberOfNewMessages(previous: List<Message>, next: List<Message>): Int {
        if (previous.isEmpty()) return 0
        for (overlap in minOf(previous.size, next.size) downTo 1) {
            if (previous.takeLast(overlap).map(Message::id) == next.take(overlap).map(Message::id)) {
                return next.size - overlap
            }
        }
        return 0
    }

    private fun scheduleScrollToLatest(animated: Boolean) {
        scrollRequest += 1
        val request = scrollRequest
        recyclerView.post {
            if (request == scrollRequest && isFollowingLatest && !isUserTouching && !isUserScrolling) {
                scrollToLatest(animated)
            }
        }
        recyclerView.postDelayed({
            if (request == scrollRequest && isFollowingLatest && !isUserTouching && !isUserScrolling) {
                scrollToLatest(animated = false)
            }
        }, SCROLL_SETTLE_DELAY_MS)
    }

    private fun scrollToLatest(animated: Boolean) {
        val target = messageAdapter.itemCount - 1
        if (target < 0) return
        if (animated) recyclerView.smoothScrollToPosition(target) else recyclerView.scrollToPosition(target)
        isFollowingLatest = true
        unreadCount = 0
        updateNewMessageView()
    }

    private fun updateNewMessageView() {
        newMessageView.visibility = if (unreadCount == 0) GONE else VISIBLE
        newMessageView.text = resources.getQuantityString(
            R.plurals.livekit_pusher_new_messages,
            unreadCount,
            unreadCount,
        )
    }

    private fun isNearBottom(): Boolean {
        val target = messageAdapter.itemCount - 1
        if (target < 0 || layoutManager.findLastVisibleItemPosition() < target) return false
        val lastView = layoutManager.findViewByPosition(target) ?: return false
        return lastView.bottom - recyclerView.height <= dp(BOTTOM_THRESHOLD_DP)
    }

    private fun createMessageScrollListener() = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                scrollRequest += 1
                isUserScrolling = true
            }
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                updateFollowingState()
                isUserScrolling = false
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (isUserScrolling) updateFollowingState()
        }
    }

    private fun updateFollowingState() {
        isFollowingLatest = isNearBottom()
        if (isFollowingLatest) {
            unreadCount = 0
            updateNewMessageView()
        }
    }

    internal fun visibleContentHeight(): Int {
        if (visibility != VISIBLE || messageAdapter.itemCount == 0 || recyclerView.childCount == 0) return 0
        if (recyclerView.childCount < messageAdapter.itemCount) return recyclerView.height

        var contentTop = Int.MAX_VALUE
        var contentBottom = Int.MIN_VALUE
        repeat(recyclerView.childCount) { index ->
            val child = recyclerView.getChildAt(index)
            contentTop = minOf(contentTop, layoutManager.getDecoratedTop(child))
            contentBottom = maxOf(contentBottom, layoutManager.getDecoratedBottom(child))
        }
        return (contentBottom - contentTop).coerceIn(0, recyclerView.height)
    }

    internal fun canScrollMessages(direction: Int): Boolean = recyclerView.canScrollVertically(direction)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class VisibleAnchor(val id: String, val offset: Int)
}

/**
 * Message list scroller that fades its own top edge. Replaces the layer mask iOS applies to the
 * scroll viewport, which Android views cannot express through a background.
 */
internal class LiveMessageRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    var onTouchStateChanged: ((Boolean) -> Unit)? = null

    private val fadeHeight = 22 * resources.displayMetrics.density
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
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        fadePaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            fadeHeight.coerceAtMost(height.toFloat()),
            Color.TRANSPARENT,
            Color.BLACK,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        canvas.restoreToCount(layer)
    }
}

private class LiveMessageAdapter :
    ListAdapter<BarrageStreamView.Message, RecyclerView.ViewHolder>(MessageDiff) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).gift == null) VIEW_TYPE_TEXT else VIEW_TYPE_GIFT

    var maximumRowWidth: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_GIFT) {
            val row = LayoutInflater.from(parent.context)
                .inflate(R.layout.livekit_pusher_gift_message_row, parent, false)
            return GiftCommentRow(row)
        }
        val label = LayoutInflater.from(parent.context)
            .inflate(R.layout.livekit_pusher_live_message_item, parent, false) as AtomicLabel
        label.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        TextViewCompat.setLineHeight(label, label.toPixels(17))
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

    class MessageViewHolder(private val label: AtomicLabel) : RecyclerView.ViewHolder(label) {
        fun bind(message: BarrageStreamView.Message, maximumWidth: Int) {
            label.maxWidth = maximumWidth.coerceAtLeast(1)
            val text = makeText(message)
            label.text = text
            label.contentDescription = label.context.getString(
                R.string.livekit_pusher_message_sender_format, message.userName,
            ) + displayContent(message.content)
            val verticalPadding = verticalPadding(text)
            val horizontalPadding = label.resources.getDimension(R.dimen.livekit_comment_row_horizontal_inset).toInt()
            label.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        private fun makeText(message: BarrageStreamView.Message): SpannableStringBuilder {
            val text = SpannableStringBuilder()
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
                InlineBadgeSpan(drawable, label.toPixels(4)),
                start,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

        private fun verticalPadding(text: CharSequence): Int {
            val width = (label.maxWidth - (label.resources.getDimension(R.dimen.livekit_comment_row_horizontal_inset) * 2).toInt()).coerceAtLeast(1)
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, label.paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .setBreakStrategy(label.breakStrategy)
                    .setHyphenationFrequency(label.hyphenationFrequency)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, label.paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            }
            return label.toPixels(if (layout.lineCount > 1) 1 else 2)
        }
    }

    private object MessageDiff : DiffUtil.ItemCallback<BarrageStreamView.Message>() {
        override fun areItemsTheSame(
            oldItem: BarrageStreamView.Message,
            newItem: BarrageStreamView.Message,
        ): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: BarrageStreamView.Message,
            newItem: BarrageStreamView.Message,
        ): Boolean =
            oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_TEXT = 0
        const val VIEW_TYPE_GIFT = 1
    }
}

private class InlineBadgeSpan(
    private val drawable: Drawable,
    private val trailingSpacing: Int,
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = drawable.bounds.width() + trailingSpacing

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val translationY = top + (bottom - top - drawable.bounds.height()) / 2f
        canvas.withTranslation(x, translationY) { drawable.draw(this) }
    }
}


private fun AtomicLabel.toPixels(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

/** Host tag drawn inline before the name of a message sent by the broadcaster. */
private class LiveHostBadgeDrawable private constructor(
    private val context: Context,
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.5f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            8.5f,
            context.resources.displayMetrics,
        )
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(dp(2f), 0f, dp(1f), color(R.color.livekit_pusher_message_badge_text_shadow))
    }
    private val rect = RectF()

    init {
        setBounds(0, 0, dp(29f).toInt(), dp(15f).toInt())
    }

    override fun getIntrinsicWidth(): Int = bounds.width()

    override fun getIntrinsicHeight(): Int = bounds.height()

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val radius = dp(7.5f)
        fillPaint.shader = null
        fillPaint.color = color(R.color.livekit_pusher_message_host_fill)
        fillPaint.setShadowLayer(
            dp(4f),
            0f,
            dp(1f),
            color(R.color.livekit_pusher_message_host_shadow),
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        val strokeColor = color(R.color.livekit_pusher_message_host_border)
        if (strokeColor != 0) {
            strokePaint.color = strokeColor
            val inset = strokePaint.strokeWidth / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, radius - inset, radius - inset, strokePaint)
            rect.inset(-inset, -inset)
        }

        textPaint.color = color(R.color.livekit_pusher_message_host_text)
        val metrics = textPaint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(
            context.getString(R.string.live_barrage_anchor),
            rect.centerX(),
            baseline,
            textPaint,
        )
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

    private fun dp(value: Float): Float = value * density

    companion object {
        fun create(context: Context): LiveHostBadgeDrawable = LiveHostBadgeDrawable(context)
    }
}

internal class GiftCommentRow(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val label = itemView as AtomicLabel
    private var comment: LiveGiftComment? = null
    private var icon: Drawable? = null
    private var showsIcon = false
    private var iconTarget: CustomTarget<Drawable>? = null

    init {
        label.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        TextViewCompat.setLineHeight(label, label.toPixels(22))
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

    fun bind(comment: LiveGiftComment, maximumWidth: Int) {
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
        val size = label.toPixels(22)
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
        fun append(value: String, @ColorRes color: Int) {
            val start = text.length
            text.append(value)
            text.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, color)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val badge = LevelBadgeDrawable.forLevel(context, comment.senderLevel)
        badge.setBounds(0, 0, badge.intrinsicWidth, badge.intrinsicHeight)
        text.append("\uFFFC")
        text.setSpan(InlineBadgeSpan(badge, label.toPixels(4)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(displayCommentUserName(sender), R.color.livekit_pusher_gift_message_sender)
        append(" $action ", R.color.livekit_pusher_gift_message_action)
        append(comment.giftName + " ", R.color.livekit_pusher_gift_message_highlight)
        if (showsIcon) {
            val start = text.length
            text.append("\uFFFC")
            text.setSpan(GiftIconSpan(icon, label.toPixels(22)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        fm?.let {
            val metrics = paint.fontMetricsInt
            val center = (metrics.ascent + metrics.descent) / 2
            it.ascent = minOf(metrics.ascent, center - size / 2)
            it.descent = maxOf(metrics.descent, it.ascent + size)
            it.top = minOf(metrics.top, it.ascent)
            it.bottom = maxOf(metrics.bottom, it.descent)
        }
        return size
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val drawable = drawable ?: return
        val scale = size.toFloat() / maxOf(1, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val width = (drawable.intrinsicWidth.coerceAtLeast(1) * scale).toInt()
        val height = (drawable.intrinsicHeight.coerceAtLeast(1) * scale).toInt()
        drawable.setBounds(0, 0, width, height)
        canvas.withTranslation(x + (size - width) / 2f, top + (bottom - top - height) / 2f) {
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
    if (previous == 0x200D || next == 0x200D || next in 0x1F3FB..0x1F3FF ||
        next in 0xE0020..0xE007F || type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()
    ) return true
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
