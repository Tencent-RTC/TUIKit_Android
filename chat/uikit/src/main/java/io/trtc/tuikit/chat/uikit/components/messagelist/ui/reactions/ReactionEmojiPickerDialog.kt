package io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.roundToInt

private const val REACTION_GRID_SPAN_COUNT = 6

class ReactionEmojiPickerDialog(
    context: Context,
    private val message: MessageInfo,
    private val viewModel: MessageListViewModel
) : Dialog(context) {

    private val density = context.resources.displayMetrics.density
    private val themeStore = ThemeStore.shared(context)
    private val emojiList: List<Emoji> by lazy {
        EmojiManager.reactionEmojiListForPicker()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(buildContentView())
    }

    private fun buildContentView(): View {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, REACTION_GRID_SPAN_COUNT)
            adapter = EmojiAdapter(emojiList) { emoji ->
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
        return LinearLayout(context).apply {
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
            setPadding(16.dp, 12.dp, 16.dp, 24.dp)
            addView(TextView(context).apply {
                text = context.getString(R.string.message_list_reaction_detail_title)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(colors.textColorPrimary)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                280.dp
            ).apply {
                topMargin = 12.dp
            })
        }
    }

    private inner class EmojiAdapter(
        private val items: List<Emoji>,
        private val onItemClick: (Emoji) -> Unit
    ) : RecyclerView.Adapter<EmojiViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val container = FrameLayout(parent.context).apply {
                foregroundGravity = Gravity.CENTER
                val size = 44.dp
                layoutParams = RecyclerView.LayoutParams(size, size)
                foreground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12.dp.toFloat()
                }
            }
            val imageView = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            container.addView(imageView, FrameLayout.LayoutParams(26.dp, 26.dp, Gravity.CENTER))
            return EmojiViewHolder(container, imageView)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            val emoji = items[position]
            val drawable = EmojiManager.getCachedEmojiDrawable(emoji.key)
            if (drawable != null) {
                holder.imageView.setImageDrawable(drawable)
            } else {
                Glide.with(holder.imageView)
                    .load(emoji.emojiUrl)
                    .into(holder.imageView)
            }
            holder.itemView.setOnClickListener { onItemClick(emoji) }
        }

        override fun getItemCount(): Int = items.size
    }

    private class EmojiViewHolder(
        itemView: View,
        val imageView: ImageView
    ) : RecyclerView.ViewHolder(itemView)

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
