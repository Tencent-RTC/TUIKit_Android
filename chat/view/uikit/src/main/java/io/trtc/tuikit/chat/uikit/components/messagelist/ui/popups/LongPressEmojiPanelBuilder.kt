package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions.MessageReactionPanelPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.roundToInt

private const val MAX_QUICK_EMOJI_COUNT = 11

internal class LongPressEmojiPanelBuilder(
    private val context: Context,
    private val density: Float,
    private val colors: ColorTokens,
    private val message: MessageInfo,
    private val viewModel: MessageListViewModel,
    private val onDismiss: () -> Unit,
    private val onCollapse: () -> Unit,
    private val onShowAllEmoji: () -> Unit
) {

    fun totalHeight(): Int {
        return LongPressDimens.emojiPanelTopPadding(density) +
            LongPressDimens.emojiPanelHeaderHeight(density) +
            LongPressDimens.emojiPanelHeaderGap(density) +
            pageHeight() +
            LongPressDimens.emojiPanelBottomPadding(density)
    }

    fun build(cardWidth: Int): View {
        val panelHeight = totalHeight()
        val panelContentWidth = minOf(contentWidth(), cardWidth)
        val pageHeight = pageHeight()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(cardWidth, panelHeight)
            setPadding(0, LongPressDimens.emojiPanelTopPadding(density), 0, 0)
            addView(
                buildHeader(),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    LongPressDimens.emojiPanelHeaderHeight(density)
                ).apply {
                    bottomMargin = LongPressDimens.emojiPanelHeaderGap(density)
                }
            )
            addView(
                buildEmojiGrid(),
                LinearLayout.LayoutParams(panelContentWidth, pageHeight).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = LongPressDimens.emojiPanelBottomPadding(density)
                }
            )
            visibility = View.VISIBLE
        }
    }

    private fun buildHeader(): View {
        val collapseIconSize = LongPressDimens.emojiPanelCollapseIconSize(density)
        val hPadding = LongPressDimens.emojiPanelHorizontalPadding(density)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(hPadding, 0, hPadding, 0)
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.message_list_menu_reaction)
                    setTextColor(colors.textColorPrimary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.message_list_menu_reaction_collapse_icon)
                    imageTintList = ColorStateList.valueOf(colors.textColorPrimary)
                    contentDescription = context.getString(R.string.message_list_reaction_collapse)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isClickable = true
                    isFocusable = true
                    background = LongPressDrawables.createActionItemRipple(colors, density)
                    setOnClickListener { onCollapse() }
                },
                LinearLayout.LayoutParams(collapseIconSize, collapseIconSize)
            )
        }
    }

    private fun buildEmojiGrid(): View {
        val cellSize = cellSize()
        val hPadding = horizontalPadding()
        val quickEmojis = getQuickEmojis()
        val lastIndex = MessageReactionPanelPolicy.COLUMNS * MessageReactionPanelPolicy.ROWS - 1
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPadding, 0, hPadding, 0)
            for (rowIndex in 0 until MessageReactionPanelPolicy.ROWS) {
                addView(
                    buildEmojiRow(rowIndex, quickEmojis, lastIndex, cellSize),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        cellSize
                    )
                )
                if (rowIndex < MessageReactionPanelPolicy.ROWS - 1) {
                    addView(buildRowDivider())
                }
            }
        }
    }

    private fun buildRowDivider(): View {
        return View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(colors.strokeColorPrimary, 140))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = 3.dp
                bottomMargin = 3.dp
            }
        }
    }

    private fun buildEmojiRow(
        rowIndex: Int,
        quickEmojis: List<Emoji>,
        lastIndex: Int,
        cellSize: Int
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            for (col in 0 until MessageReactionPanelPolicy.COLUMNS) {
                val index = rowIndex * MessageReactionPanelPolicy.COLUMNS + col
                val cell = when {
                    index == lastIndex -> buildMoreCell(cellSize)
                    index < quickEmojis.size -> buildEmojiCell(quickEmojis[index], cellSize)
                    else -> View(context)
                }
                addView(
                    cell,
                    LinearLayout.LayoutParams(cellSize, cellSize)
                )
            }
        }
    }

    private fun buildEmojiCell(emoji: Emoji, cellSize: Int): View {
        val padding = 5.dp
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(padding, padding, padding, padding)
            background = LongPressDrawables.createEmojiCellRipple(colors, cellSize)
            val isReacted = message.reactionList.any {
                it.reactionID == emoji.key && it.reactedByMyself
            }
            val drawable = EmojiManager.getCachedEmojiDrawable(emoji.key)
            if (drawable != null) {
                setImageDrawable(drawable)
            } else {
                Glide.with(this)
                    .load(emoji.emojiUrl)
                    .into(this)
            }
            contentDescription = emoji.emojiName
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (isReacted) {
                    viewModel.removeMessageReaction(message, emoji.key)
                } else {
                    viewModel.addMessageReaction(message, emoji.key)
                    EmojiManager.reactionEmojiGroup?.let {
                        RecentEmojiManager.updateRecentEmoji(it.id, emoji.key)
                    }
                }
                onDismiss()
            }
        }
    }

    private fun buildMoreCell(cellSize: Int): View {
        val buttonSize = 24.dp
        val buttonBackgroundColor = colors.dropdownColorHover
        return FrameLayout(context).apply {
            background = LongPressDrawables.createEmojiCellRipple(colors, cellSize)
            contentDescription = context.getString(R.string.message_list_reaction_expand)
            isClickable = true
            isFocusable = true
            setOnClickListener { onShowAllEmoji() }
            addView(
                ImageView(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(buttonBackgroundColor)
                    }
                    setImageResource(R.drawable.message_list_menu_more_icon)
                    imageTintList = ColorStateList.valueOf(colors.textColorPrimary)
                    setPadding(5.dp, 5.dp, 5.dp, 5.dp)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER)
            )
        }
    }

    private fun getQuickEmojis(): List<Emoji> {
        val allEmojis = EmojiManager.reactionEmojiListForPicker()
        if (allEmojis.isEmpty()) {
            return emptyList()
        }
        val emojiMap = allEmojis.associateBy { it.key }
        val orderedKeys = mutableListOf<String>()
        orderedKeys += message.reactionList
            .filter { it.reactedByMyself }
            .map { it.reactionID }
        EmojiManager.reactionEmojiGroup?.let { group ->
            orderedKeys += RecentEmojiManager.getRecentEmojiList(group.id)
        }
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

    private fun horizontalPadding(): Int = LongPressDimens.emojiPanelHorizontalPadding(density)

    private fun cellSize(): Int = LongPressDimens.emojiCellSize(density)

    private fun pageHeight(): Int = LongPressDimens.emojiPanelPageHeight(density)

    private fun contentWidth(): Int = LongPressDimens.emojiPanelContentWidth(density)

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
