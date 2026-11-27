package io.trtc.tuikit.chat.uikit.components.emojipicker.ui
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.atomicx.theme.ThemeStore

class EmojiGridAdapter(
    val spanCount: Int,
    private val onEmojiClick: (Emoji) -> Unit,
    private val onEmojiLongClick: (Emoji) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_EMOJI = 1
        const val TYPE_SPACER = 2
    }

    private val items = mutableListOf<GridItem>()

    sealed class GridItem {
        data class Header(val title: String) : GridItem()
        data class EmojiItem(val emoji: Emoji) : GridItem()
        data class Spacer(val heightDp: Int) : GridItem()
    }

    fun submitItems(newItems: List<GridItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getSpanSize(position: Int): Int {
        if (position < 0 || position >= items.size) return 1
        return when (items[position]) {
            is GridItem.Header -> spanCount
            is GridItem.Spacer -> spanCount
            is GridItem.EmojiItem -> 1
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is GridItem.Header -> TYPE_HEADER
            is GridItem.EmojiItem -> TYPE_EMOJI
            is GridItem.Spacer -> TYPE_SPACER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.emoji_picker_item_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_EMOJI -> {
                val density = parent.context.resources.displayMetrics.density
                val padding = (4 * density).toInt()
                val container = SquareFrameLayout(parent.context).apply {
                    setPadding(padding, padding, padding, padding)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                val imageView = ImageView(parent.context).apply {
                    id = R.id.emoji_picker_image
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                container.addView(
                    imageView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                EmojiViewHolder(container)
            }
            TYPE_SPACER -> {
                val view = View(parent.context)
                SpacerViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

        when (val item = items[position]) {
            is GridItem.Header -> {
                val vh = holder as HeaderViewHolder
                vh.titleView.text = item.title
                vh.titleView.setTextColor(colors.textColorSecondary)
            }
            is GridItem.EmojiItem -> {
                val vh = holder as EmojiViewHolder

                Glide.with(context)
                    .load(item.emoji.emojiUrl)
                    .into(vh.emojiImage)

                vh.itemView.setOnClickListener {
                    onEmojiClick(item.emoji)
                }
                vh.itemView.setOnLongClickListener {
                    onEmojiLongClick(item.emoji)
                    true
                }
            }
            is GridItem.Spacer -> {
                val density = context.resources.displayMetrics.density
                holder.itemView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (item.heightDp * density).toInt()
                )
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.emoji_picker_header_title)
    }

    class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiImage: ImageView = itemView.findViewById(R.id.emoji_picker_image)
    }

    class SpacerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private class SquareFrameLayout(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        }
    }
}
