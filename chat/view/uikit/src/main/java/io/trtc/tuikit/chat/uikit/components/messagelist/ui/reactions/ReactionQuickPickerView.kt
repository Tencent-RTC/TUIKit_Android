package io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import kotlin.math.roundToInt

private const val MAX_QUICK_EMOJI_COUNT = 6
private const val QUICK_PICKER_HORIZONTAL_PADDING = 8
private const val QUICK_PICKER_TOP_PADDING = 8
private const val QUICK_PICKER_BOTTOM_PADDING = 8
private const val QUICK_PICKER_ITEM_SIZE = 32
private const val QUICK_PICKER_ITEM_MARGIN = 0
private const val QUICK_PICKER_ITEM_PADDING = 4
private const val QUICK_PICKER_TOGGLE_ITEM_PADDING = 6
private const val QUICK_PICKER_DIVIDER_INSET = 14
private const val TOGGLE_ROTATION_DURATION = 200L

class ReactionQuickPickerView(
    context: Context
) : LinearLayout(context) {

    companion object {
        fun measuredRowWidth(context: Context): Int {
            val density = context.resources.displayMetrics.density
            val itemCount = MAX_QUICK_EMOJI_COUNT + 1
            fun Int.toPx(): Int = (this * density).roundToInt()
            return QUICK_PICKER_HORIZONTAL_PADDING.toPx() * 2 +
                itemCount * QUICK_PICKER_ITEM_SIZE.toPx() +
                itemCount * QUICK_PICKER_ITEM_MARGIN.toPx() * 2
        }
    }

    private val density = context.resources.displayMetrics.density
    private val colors: ColorTokens
        get() = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    private var toggleIconView: ImageView? = null
    private var isExpanded: Boolean = false
    private var onToggleExpanded: ((Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL
        clipToPadding = false
    }

    fun bind(
        message: MessageInfo,
        viewModel: MessageListViewModel,
        onHandled: () -> Unit,
        onToggleExpanded: ((Boolean) -> Unit)? = null
    ) {
        removeAllViews()
        toggleIconView = null
        isExpanded = false
        this.onToggleExpanded = onToggleExpanded
        if (message.status != MessageStatus.SEND_SUCCESS) {
            visibility = View.GONE
            return
        }

        val quickEmojis = getQuickEmojis(message)
        if (quickEmojis.isEmpty()) {
            visibility = View.GONE
            return
        }

        visibility = View.VISIBLE
        val itemCount = quickEmojis.size + 1
        addView(buildReactionRow(quickEmojis, message, viewModel, onHandled))
        addView(
            buildDivider(),
            LayoutParams(calculateDividerWidth(itemCount), 1).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
    }

    fun resetToggleToCollapsed() {
        if (!isExpanded) {
            return
        }
        isExpanded = false
        toggleIconView?.let { iconView ->
            iconView.animate()
                .rotation(0f)
                .setDuration(TOGGLE_ROTATION_DURATION)
                .start()
            iconView.contentDescription =
                context.getString(R.string.message_list_reaction_expand)
        }
    }

    private fun buildReactionRow(
        quickEmojis: List<Emoji>,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        onHandled: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                QUICK_PICKER_HORIZONTAL_PADDING.dp,
                QUICK_PICKER_TOP_PADDING.dp,
                QUICK_PICKER_HORIZONTAL_PADDING.dp,
                QUICK_PICKER_BOTTOM_PADDING.dp
            )
            quickEmojis.forEach { emoji ->
                addView(createEmojiItem(emoji, message, viewModel, onHandled))
            }
            addView(createToggleItem())
        }
    }

    private fun createEmojiItem(
        emoji: Emoji,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        onHandled: () -> Unit
    ): View {
        val isReacted = message.reactionList.any {
            it.reactionID == emoji.key && it.reactedByMyself
        }
        return ImageView(context).apply {
            layoutParams = createItemLayoutParams()
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                QUICK_PICKER_ITEM_PADDING.dp,
                QUICK_PICKER_ITEM_PADDING.dp,
                QUICK_PICKER_ITEM_PADDING.dp,
                QUICK_PICKER_ITEM_PADDING.dp
            )
            background = createEmojiItemBackground()
            val drawable = EmojiManager.getCachedEmojiDrawable(emoji.key)
            if (drawable != null) {
                setImageDrawable(drawable)
            } else {
                Glide.with(this)
                    .load(emoji.emojiUrl)
                    .into(this)
            }
            contentDescription = emoji.emojiName
            setOnClickListener {
                if (isReacted) {
                    viewModel.removeMessageReaction(message, emoji.key)
                } else {
                    viewModel.addMessageReaction(message, emoji.key)
                    EmojiManager.reactionEmojiGroup?.let {
                        RecentEmojiManager.updateRecentEmoji(it.id, emoji.key)
                    }
                }
                onHandled()
            }
        }
    }

    private fun createToggleItem(): View {
        return ImageView(context).apply {
            layoutParams = createItemLayoutParams()
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                QUICK_PICKER_TOGGLE_ITEM_PADDING.dp,
                QUICK_PICKER_TOGGLE_ITEM_PADDING.dp,
                QUICK_PICKER_TOGGLE_ITEM_PADDING.dp,
                QUICK_PICKER_TOGGLE_ITEM_PADDING.dp
            )
            setImageResource(R.drawable.message_list_reaction_expand_icon)
            ImageViewCompat.setImageTintList(
                this,
                ColorStateList.valueOf(colors.textColorSecondary)
            )
            background = createToggleItemBackground()
            contentDescription = context.getString(R.string.message_list_reaction_expand)
            toggleIconView = this
            setOnClickListener {
                handleToggleClick(this)
            }
        }
    }

    private fun handleToggleClick(iconView: ImageView) {
        isExpanded = !isExpanded
        val targetRotation = if (isExpanded) 180f else 0f
        iconView.animate()
            .rotation(targetRotation)
            .setDuration(TOGGLE_ROTATION_DURATION)
            .start()
        iconView.contentDescription = if (isExpanded) {
            context.getString(R.string.message_list_reaction_collapse)
        } else {
            context.getString(R.string.message_list_reaction_expand)
        }
        onToggleExpanded?.invoke(isExpanded)
    }

    private fun createItemLayoutParams(): LayoutParams {
        return LayoutParams(QUICK_PICKER_ITEM_SIZE.dp, QUICK_PICKER_ITEM_SIZE.dp).apply {
            marginStart = QUICK_PICKER_ITEM_MARGIN.dp
            marginEnd = QUICK_PICKER_ITEM_MARGIN.dp
        }
    }

    private fun createEmojiItemBackground(): Drawable {
        return createHoverableBackground((QUICK_PICKER_ITEM_SIZE / 2).dp.toFloat(), 28)
    }

    private fun createToggleItemBackground(): Drawable {
        return createHoverableBackground((QUICK_PICKER_ITEM_SIZE / 2).dp.toFloat(), 24)
    }

    private fun createHoverableBackground(cornerRadius: Float, rippleAlpha: Int): Drawable {
        val hoverColor = colors.dropdownColorHover
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(hoverColor)
        }
        val defaultDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(Color.TRANSPARENT)
        }
        val stateListDrawable = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), defaultDrawable)
        }
        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(colors.textColorPrimary, rippleAlpha)
            ),
            stateListDrawable,
            maskDrawable
        )
    }

    private fun buildDivider(): View {
        return View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(colors.strokeColorPrimary, 160))
        }
    }

    private fun calculateDividerWidth(itemCount: Int): Int {
        val rowWidth = QUICK_PICKER_HORIZONTAL_PADDING.dp * 2 +
            itemCount * QUICK_PICKER_ITEM_SIZE.dp +
            itemCount * QUICK_PICKER_ITEM_MARGIN.dp * 2
        return (rowWidth - QUICK_PICKER_DIVIDER_INSET.dp * 2).coerceAtLeast(0)
    }

    private fun getQuickEmojis(message: MessageInfo): List<Emoji> {
        val reactionGroup = EmojiManager.reactionEmojiGroup ?: return emptyList()
        val allEmojis = EmojiManager.reactionEmojiListForPicker()
        if (allEmojis.isEmpty()) {
            return emptyList()
        }

        val emojiMap = allEmojis.associateBy { it.key }
        val orderedKeys = mutableListOf<String>()
        orderedKeys += message.reactionList
            .filter { it.reactedByMyself }
            .map { it.reactionID }
        orderedKeys += RecentEmojiManager.getRecentEmojiList(reactionGroup.id)
            .filter { emojiMap.containsKey(it) }
            .take(MAX_QUICK_EMOJI_COUNT)
        orderedKeys += allEmojis.map { it.key }

        val result = mutableListOf<Emoji>()
        val usedKeys = mutableSetOf<String>()
        for (key in orderedKeys) {
            val emoji = emojiMap[key] ?: continue
            if (!usedKeys.add(key)) {
                continue
            }
            result.add(emoji)
            if (result.size >= MAX_QUICK_EMOJI_COUNT) {
                break
            }
        }
        return result
    }

    fun measuredRowWidth(): Int = measuredRowWidth(context)

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
