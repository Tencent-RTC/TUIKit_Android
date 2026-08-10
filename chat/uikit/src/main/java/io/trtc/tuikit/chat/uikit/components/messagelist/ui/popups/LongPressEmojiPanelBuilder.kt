package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions.MessageReactionPanelPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.roundToInt

internal class LongPressEmojiPanelBuilder(
    private val context: Context,
    private val density: Float,
    private val colors: ColorTokens,
    private val message: MessageInfo,
    private val viewModel: MessageListViewModel,
    private val onDismiss: () -> Unit
) {

    fun totalHeight(): Int {
        return pageHeight() + verticalPadding() * 2 + indicatorAreaHeight()
    }

    fun build(cardWidth: Int): View {
        val emojis = EmojiManager.reactionEmojiListForPicker()
        val pages = emojis.chunked(MessageReactionPanelPolicy.PAGE_SIZE)
        val pageCount = pages.size.coerceAtLeast(1)
        val showIndicator = pageCount > 1
        val indicatorHeight = if (showIndicator) LongPressPageIndicator(context, density, colors).areaHeight() else 0
        val pageHeight = pageHeight()
        val panelHeight = pageHeight + verticalPadding() * 2 + indicatorHeight
        val panelContentWidth = minOf(contentWidth(), cardWidth)
        val pager = ViewPager2(context).apply {
            layoutParams = ViewGroup.LayoutParams(panelContentWidth, pageHeight)
            offscreenPageLimit = 1
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int
                ): RecyclerView.ViewHolder {
                    val pageView = FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    return object : RecyclerView.ViewHolder(pageView) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val container = holder.itemView as FrameLayout
                    container.removeAllViews()
                    val pageItems = pages.getOrNull(position).orEmpty()
                    val page = buildEmojiPage(pageItems)
                    container.addView(
                        page,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP
                        )
                    )
                }

                override fun getItemCount(): Int = pageCount
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(cardWidth, panelHeight)
            setPadding(0, verticalPadding(), 0, verticalPadding())
            addView(
                pager,
                LinearLayout.LayoutParams(panelContentWidth, pageHeight).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
            if (showIndicator) {
                val pageIndicator = LongPressPageIndicator(context, density, colors)
                val indicator = pageIndicator.build(pageCount)
                pageIndicator.attach(pager, indicator)
                addView(
                    indicator,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        indicatorHeight
                    )
                )
            }
            visibility = View.VISIBLE
        }
    }

    private fun indicatorAreaHeight(): Int {
        return if (pageCount() > 1) {
            LongPressPageIndicator(context, density, colors).areaHeight()
        } else {
            0
        }
    }

    private fun pageCount(): Int {
        return MessageReactionPanelPolicy.pageCount(EmojiManager.reactionEmojiListForPicker().size)
    }

    private fun buildEmojiPage(items: List<Emoji>): View {
        val cellSize = cellSize()
        val hPadding = horizontalPadding()
        val rows = items.chunked(MessageReactionPanelPolicy.COLUMNS)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPadding, 0, hPadding, 0)
            for (rowIndex in 0 until MessageReactionPanelPolicy.ROWS) {
                val rowItems = rows.getOrNull(rowIndex).orEmpty()
                addView(
                    buildEmojiRow(rowItems, cellSize),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        cellSize
                    )
                )
            }
        }
    }

    private fun buildEmojiRow(items: List<Emoji>, cellSize: Int): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            for (col in 0 until MessageReactionPanelPolicy.COLUMNS) {
                val emoji = items.getOrNull(col)
                val cell = if (emoji != null) {
                    buildEmojiCell(emoji, cellSize)
                } else {
                    View(context)
                }
                addView(
                    cell,
                    LinearLayout.LayoutParams(cellSize, cellSize)
                )
            }
        }
    }

    private fun buildEmojiCell(emoji: Emoji, cellSize: Int): View {
        val padding = 4.dp
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

    private fun horizontalPadding(): Int = LongPressDimens.emojiPanelHorizontalPadding(density)

    private fun verticalPadding(): Int = LongPressDimens.emojiPanelVerticalPadding(density)

    private fun cellSize(): Int = LongPressDimens.emojiCellSize(density)

    private fun pageHeight(): Int = LongPressDimens.emojiPanelPageHeight(density)

    private fun contentWidth(): Int = LongPressDimens.emojiPanelContentWidth(density)

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
