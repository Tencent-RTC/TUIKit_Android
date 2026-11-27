package com.trtc.uikit.livekit.common.barrage.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R

internal class BarrageEmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val emojiList: RecyclerView
    private val gridLayoutManager: GridLayoutManager
    private val pageDownButton: AppCompatImageView
    private val deleteButton: AppCompatImageView
    private val sendButton: AppCompatTextView
    private val controlsGradient: View
    private var appliedNavigationBottom = -1

    var onSelect: ((BarrageEmoji) -> Unit)? = null
    var onDelete: (() -> Unit)? = null
    var onSend: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_barrage_emoji_panel, this, true)
        emojiList = findViewById(R.id.livekit_barrage_emoji_list)
        pageDownButton = findViewById(R.id.livekit_barrage_emoji_page_down)
        deleteButton = findViewById(R.id.livekit_barrage_emoji_delete)
        sendButton = findViewById(R.id.livekit_barrage_emoji_send)
        controlsGradient = findViewById(R.id.livekit_barrage_emoji_controls_gradient)
        gridLayoutManager = GridLayoutManager(context, 8)
        emojiList.layoutManager = gridLayoutManager
        emojiList.adapter = BarrageEmojiAdapter { emoji -> onSelect?.invoke(emoji) }
        emojiList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                val count = parent.adapter?.itemCount ?: 0
                if (position == RecyclerView.NO_POSITION || count == 0) return
                val spanCount = gridLayoutManager.spanCount
                val lastRowCount = count % spanCount
                val lastRowStart = count - if (lastRowCount == 0) spanCount else lastRowCount
                outRect.bottom = resources.getDimensionPixelSize(
                    if (position >= lastRowStart) {
                        R.dimen.livekit_barrage_emoji_section_bottom
                    } else {
                        R.dimen.livekit_barrage_emoji_vertical_spacing
                    },
                )
            }
        })
        pageDownButton.setOnClickListener { scrollToNextPage() }
        deleteButton.setOnClickListener { onDelete?.invoke() }
        sendButton.setOnClickListener { onSend?.invoke() }
        deleteButton.alpha = 0.45f
        sendButton.background?.mutate()
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            applyNavigationBottom(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(ev)
    }

    fun setSendEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
        sendButton.background?.alpha = if (enabled) 255 else 115
    }

    fun setDeleteEnabled(enabled: Boolean) {
        deleteButton.isEnabled = enabled
        deleteButton.alpha = if (enabled) 1f else 0.45f
    }

    fun resetScrollPosition() {
        emojiList.stopScroll()
        gridLayoutManager.scrollToPositionWithOffset(0, 0)
    }

    private fun applyNavigationBottom(navigationBottom: Int) {
        if (appliedNavigationBottom == navigationBottom) return
        appliedNavigationBottom = navigationBottom
        emojiList.updateLayoutParams<MarginLayoutParams> { bottomMargin = navigationBottom }
        controlsGradient.updateLayoutParams<LayoutParams> {
            height = resources.getDimensionPixelSize(R.dimen.livekit_barrage_emoji_controls_height) + navigationBottom
        }
        pageDownButton.updateLayoutParams<MarginLayoutParams> {
            bottomMargin =
                resources.getDimensionPixelSize(R.dimen.livekit_barrage_emoji_page_down_bottom) + navigationBottom
        }
        val controlsBottom =
            resources.getDimensionPixelSize(R.dimen.livekit_barrage_emoji_controls_bottom) + navigationBottom
        listOf(deleteButton, sendButton).forEach { view ->
            view.updateLayoutParams<MarginLayoutParams> { bottomMargin = controlsBottom }
        }
    }

    private fun scrollToNextPage() {
        val visibleHeight = emojiList.height - emojiList.paddingTop - emojiList.paddingBottom
        if (visibleHeight > 0 && emojiList.canScrollVertically(1)) {
            emojiList.smoothScrollBy(0, visibleHeight)
        }
    }
}

private class BarrageEmojiAdapter(
    private val onSelect: (BarrageEmoji) -> Unit,
) : RecyclerView.Adapter<BarrageEmojiAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: AppCompatImageView = view.findViewById(R.id.livekit_barrage_emoji_image)
        val fallback: AppCompatTextView = view.findViewById(R.id.livekit_barrage_emoji_fallback)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.livekit_barrage_emoji_item, parent, false)
        val holder = ViewHolder(view)
        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onSelect(BarrageEmoji.all[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val emoji = BarrageEmoji.all[position]
        val drawable = BarrageEmojiConverter.drawable(holder.itemView.context, emoji)
        holder.image.setImageDrawable(drawable)
        holder.image.visibility = if (drawable == null) View.GONE else View.VISIBLE
        holder.fallback.visibility = if (drawable == null) View.VISIBLE else View.GONE
        holder.fallback.text = if (drawable == null) emoji.token else ""
        holder.itemView.contentDescription = emoji.token
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.image.setImageDrawable(null)
        holder.itemView.contentDescription = null
    }

    override fun getItemCount(): Int = BarrageEmoji.all.size
}

internal class BarrageEmojiControlsGradientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (height <= 0) return
        val background = ContextCompat.getColor(context, R.color.livekit_barrage_composer_background)
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(background and 0x00FFFFFF, background, background),
            floatArrayOf(0f, 0.2f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
