package io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.abs
import kotlin.math.roundToInt

private const val REACTION_GRID_SPAN_COUNT = 8
private const val PICKER_HEIGHT_SCREEN_RATIO = 0.6f
private const val PICKER_MIN_HEIGHT_DP = 400
private const val PICKER_MAX_HEIGHT_DP = 520
private const val DRAG_DISMISS_THRESHOLD_RATIO = 0.15f
private const val DRAG_ANIMATION_DURATION_MS = 180L
private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_EMOJI = 1

class ReactionEmojiPickerDialog(
    context: Context,
    private val message: MessageInfo,
    private val viewModel: MessageListViewModel
) : Dialog(context) {

    private val density = context.resources.displayMetrics.density
    private val themeStore = ThemeStore.shared(context)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                horizontalMargin = 0f
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, pickerHeight())
        }
        setContentView(buildContentView())
    }

    private fun buildContentView(): View {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        val items = buildPickerItems()
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, REACTION_GRID_SPAN_COUNT).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (items[position] is ReactionPickerItem.Header) {
                            REACTION_GRID_SPAN_COUNT
                        } else {
                            1
                        }
                    }
                }
            }
            adapter = ReactionPickerAdapter(items, colors) { emoji ->
                val isReacted = message.reactionList.any {
                    it.reactionID == emoji.key && it.reactedByMyself
                }
                if (isReacted) {
                    viewModel.removeMessageReaction(message, emoji.key)
                } else {
                    viewModel.addMessageReaction(message, emoji.key)
                    EmojiManager.reactionEmojiGroup?.let {
                        RecentEmojiManager.updateRecentEmoji(it.id, emoji.key)
                    }
                }
                dismiss()
            }
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }
        val dragHandle = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.message_list_reaction_collapse)
            setOnClickListener { dismiss() }
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.message_list_chevron_down)
                    imageTintList = ColorStateList.valueOf(colors.strokeColorPrimary)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                FrameLayout.LayoutParams(40.dp, 16.dp, Gravity.CENTER)
            )
        }
        val contentView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    20.dp.toFloat(), 20.dp.toFloat(),
                    20.dp.toFloat(), 20.dp.toFloat(),
                    0f, 0f,
                    0f, 0f
                )
                setColor(colors.bgColorDialog)
            }
            setPadding(16.dp, 0, 16.dp, 24.dp)
            addView(
                dragHandle,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    32.dp
                )
            )
            addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }
        attachDragToDismiss(dragHandle, contentView)
        return contentView
    }

    private fun attachDragToDismiss(dragHandle: View, contentView: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startTranslationY = 0f
        var movedBeyondTouchSlop = false
        dragHandle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    contentView.animate().cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startTranslationY = contentView.translationY
                    movedBeyondTouchSlop = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!movedBeyondTouchSlop &&
                        (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)
                    ) {
                        movedBeyondTouchSlop = true
                    }
                    contentView.translationY = if (
                        movedBeyondTouchSlop &&
                        deltaY > 0f &&
                        abs(deltaY) >= abs(deltaX)
                    ) {
                        startTranslationY + deltaY
                    } else {
                        startTranslationY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!movedBeyondTouchSlop) {
                        view.performClick()
                    } else {
                        val dismissThreshold =
                            contentView.height * DRAG_DISMISS_THRESHOLD_RATIO
                        if (contentView.translationY >= dismissThreshold) {
                            contentView.animate()
                                .translationY(contentView.height.toFloat())
                                .setDuration(DRAG_ANIMATION_DURATION_MS)
                                .withEndAction { dismiss() }
                                .start()
                        } else {
                            animateDragBack(contentView)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    animateDragBack(contentView)
                    true
                }
                else -> false
            }
        }
    }

    private fun animateDragBack(contentView: View) {
        contentView.animate()
            .translationY(0f)
            .setDuration(DRAG_ANIMATION_DURATION_MS)
            .start()
    }

    private fun pickerHeight(): Int {
        val screenHeight = context.resources.displayMetrics.heightPixels
        return (screenHeight * PICKER_HEIGHT_SCREEN_RATIO)
            .roundToInt()
            .coerceIn(PICKER_MIN_HEIGHT_DP.dp, PICKER_MAX_HEIGHT_DP.dp)
    }

    private fun buildPickerItems(): List<ReactionPickerItem> {
        val allEmojis = EmojiManager.reactionEmojiListForPicker()
        val emojiMap = allEmojis.associateBy { it.key }
        val recentEmojis = EmojiManager.reactionEmojiGroup
            ?.let { RecentEmojiManager.getRecentEmojiList(it.id) }
            .orEmpty()
            .mapNotNull { emojiMap[it] }
        val items = mutableListOf<ReactionPickerItem>()
        if (recentEmojis.isNotEmpty()) {
            items += ReactionPickerItem.Header(
                context.getString(R.string.message_list_reaction_recent_used)
            )
            items += recentEmojis.map { ReactionPickerItem.EmojiItem(it) }
        }
        if (allEmojis.isNotEmpty()) {
            items += ReactionPickerItem.Header(
                context.getString(R.string.message_list_reaction_all_emojis)
            )
            items += allEmojis.map { ReactionPickerItem.EmojiItem(it) }
        }
        return items
    }

    private inner class ReactionPickerAdapter(
        private val items: List<ReactionPickerItem>,
        private val colors: ColorTokens,
        private val onItemClick: (Emoji) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is ReactionPickerItem.Header -> VIEW_TYPE_HEADER
                is ReactionPickerItem.EmojiItem -> VIEW_TYPE_EMOJI
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                val titleView = TextView(parent.context).apply {
                    textSize = 12f
                    setTextColor(colors.textColorSecondary)
                    setPadding(0, 8.dp, 0, 8.dp)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                HeaderViewHolder(titleView)
            } else {
                val container = FrameLayout(parent.context).apply {
                    foregroundGravity = Gravity.CENTER
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        44.dp
                    )
                    foreground = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 12.dp.toFloat()
                    }
                }
                val imageView = ImageView(parent.context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                container.addView(
                    imageView,
                    FrameLayout.LayoutParams(
                        32.dp,
                        32.dp,
                        Gravity.START or Gravity.CENTER_VERTICAL
                    )
                )
                EmojiViewHolder(container, imageView)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ReactionPickerItem.Header -> {
                    (holder as HeaderViewHolder).titleView.text = item.title
                }
                is ReactionPickerItem.EmojiItem -> {
                    val emojiHolder = holder as EmojiViewHolder
                    val emoji = item.emoji
                    val drawable = EmojiManager.getCachedEmojiDrawable(emoji.key)
                    if (drawable != null) {
                        emojiHolder.imageView.setImageDrawable(drawable)
                    } else {
                        Glide.with(emojiHolder.imageView)
                            .load(emoji.emojiUrl)
                            .into(emojiHolder.imageView)
                    }
                    emojiHolder.itemView.setOnClickListener { onItemClick(emoji) }
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class HeaderViewHolder(
        val titleView: TextView
    ) : RecyclerView.ViewHolder(titleView)

    private class EmojiViewHolder(
        itemView: View,
        val imageView: ImageView
    ) : RecyclerView.ViewHolder(itemView)

    private sealed class ReactionPickerItem {
        data class Header(val title: String) : ReactionPickerItem()
        data class EmojiItem(val emoji: Emoji) : ReactionPickerItem()
    }

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
