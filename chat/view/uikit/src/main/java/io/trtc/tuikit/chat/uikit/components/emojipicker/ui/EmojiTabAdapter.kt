package io.trtc.tuikit.chat.uikit.components.emojipicker.ui
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.EmojiGroup
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

class EmojiTabAdapter(
    emojiGroupList: List<EmojiGroup>,
    private val onTabClick: (Int) -> Unit
) : RecyclerView.Adapter<EmojiTabAdapter.TabViewHolder>() {

    private val emojiGroupList = mutableListOf<EmojiGroup>()

    var selectedIndex: Int = 0
        set(value) {
            val old = field
            field = value
            if (old != value) {
                if (old in 0 until itemCount) notifyItemChanged(old)
                if (value in 0 until itemCount) notifyItemChanged(value)
            }
        }

    init {
        this.emojiGroupList.addAll(emojiGroupList)
    }

    fun submitGroups(groups: List<EmojiGroup>) {
        emojiGroupList.clear()
        emojiGroupList.addAll(groups)
        if (selectedIndex >= groups.size) {
            selectedIndex = groups.lastIndex.coerceAtLeast(0)
        }
        notifyDataSetChanged()
    }

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.emoji_picker_tab_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.emoji_picker_item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val context = holder.itemView.context
        val emojiGroup = emojiGroupList[position]
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

        Glide.with(context)
            .load(emojiGroup.emojiGroupIconUrl)
            .into(holder.iconView)

        val bgDrawable = if (position == selectedIndex) {
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.setColor(colors.bgColorBubbleReciprocal)
            drawable.cornerRadius = dpToPx(context, 4f)
            drawable
        } else {
            null
        }
        holder.itemView.background = bgDrawable

        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onTabClick(adapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = emojiGroupList.size

    private fun dpToPx(context: android.content.Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
