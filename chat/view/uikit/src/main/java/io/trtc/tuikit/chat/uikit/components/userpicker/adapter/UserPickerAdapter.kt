package io.trtc.tuikit.chat.uikit.components.userpicker.adapter
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar

internal sealed class FlatItem {
    data class SectionHeader(val letter: String) : FlatItem()
    data class UserItem(val letter: String, val data: UserPickerData<Any?>) : FlatItem()
}

internal class UserPickerAdapter(
    private val context: Context,
    var showCheckbox: Boolean,
    private val selectedKeys: Set<String>,
    var lockedKeys: Set<String>,
    private val onItemClick: (UserPickerData<Any?>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_USER_ITEM = 1
        private const val PAYLOAD_SELECTION_STATE = "payload_selection_state"

        private const val ITEM_HEIGHT_DP = 60f
        private const val ITEM_HORIZONTAL_PADDING_DP = 16f
        private const val AVATAR_TEXT_SPACING_DP = 12f
        private const val CHECKBOX_SIZE_DP = 16f
        private const val CHECKBOX_END_MARGIN_DP = 16f
        private const val INDEX_BAR_RESERVED_DP = 26f
        private const val HEADER_HORIZONTAL_PADDING_DP = 16f
        private const val HEADER_VERTICAL_PADDING_DP = 6f
        private const val ITEM_TEXT_SIZE_SP = 18f
        private const val HEADER_TEXT_SIZE_SP = 14f
    }

    var items: List<FlatItem> = emptyList()

    fun getSelectionPayload(): Any {
        return PAYLOAD_SELECTION_STATE
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FlatItem.SectionHeader -> TYPE_SECTION_HEADER
            is FlatItem.UserItem -> TYPE_USER_ITEM
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION_HEADER -> SectionHeaderViewHolder(createSectionHeaderView(parent.context))
            TYPE_USER_ITEM -> UserItemViewHolder(createUserItemView(parent.context))
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val colors = getColors()

        when {
            holder is SectionHeaderViewHolder && item is FlatItem.SectionHeader -> {
                holder.textView.text = item.letter
                holder.textView.setTextColor(colors.textColorPrimary)
                holder.itemView.setBackgroundColor(colors.bgColorDialog)
            }

            holder is UserItemViewHolder && item is FlatItem.UserItem -> {
                bindUserItem(holder, item.data, colors)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION_STATE)) {
            val item = items[position]
            if (holder is UserItemViewHolder && item is FlatItem.UserItem) {
                bindSelectionState(holder, item.data, getColors())
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun bindUserItem(
        holder: UserItemViewHolder,
        data: UserPickerData<Any?>,
        colors: ColorTokens
    ) {
        val isLocked = lockedKeys.contains(data.key)

        holder.textView.text = data.label
        holder.textView.setTextColor(colors.textColorPrimary)
        holder.itemView.setBackgroundColor(colors.bgColorOperate)
        holder.avatar.setContent(
            Avatar.AvatarContent.Image(data.avatarUrl, data.label)
        )
        bindSelectionState(holder, data, colors)
        val clickListener = View.OnClickListener {
            if (!isLocked) onItemClick(data)
        }
        holder.itemView.setOnClickListener(clickListener)
        holder.rowLayout.setOnClickListener(clickListener)
        holder.checkBox.setOnClickListener(clickListener)
    }

    private fun bindSelectionState(
        holder: UserItemViewHolder,
        data: UserPickerData<Any?>,
        colors: ColorTokens
    ) {
        val isSelected = selectedKeys.contains(data.key)
        val isLocked = lockedKeys.contains(data.key)

        if (showCheckbox) {
            holder.checkBox.visibility = View.VISIBLE
            holder.checkBox.setCheckedState(isSelected, isLocked, colors)
        } else {
            holder.checkBox.visibility = View.GONE
        }

        val isEnabled = !isLocked
        holder.itemView.isEnabled = isEnabled
        holder.rowLayout.isEnabled = isEnabled
        holder.checkBox.isEnabled = isEnabled
    }

    private fun createSectionHeaderView(context: Context): View {
        val dm = context.resources.displayMetrics
        val hPad = dp2px(HEADER_HORIZONTAL_PADDING_DP, dm).toInt()
        val vPad = dp2px(HEADER_VERTICAL_PADDING_DP, dm).toInt()
        val colors = getColors()

        return FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(colors.bgColorDialog)
            setPaddingRelative(hPad, vPad, hPad, vPad)

            val textView = TextView(context).apply {
                tag = "sectionText"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_TEXT_SIZE_SP)
                setTextColor(colors.textColorPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                textDirection = View.TEXT_DIRECTION_LOCALE
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            }
            addView(textView)
        }
    }

    private fun createUserItemView(context: Context): View {
        val dm = context.resources.displayMetrics
        val itemHeightPx = dp2px(ITEM_HEIGHT_DP, dm).toInt()
        val hPad = dp2px(ITEM_HORIZONTAL_PADDING_DP, dm).toInt()
        val spacingPx = dp2px(AVATAR_TEXT_SPACING_DP, dm).toInt()
        val checkboxSizePx = dp2px(CHECKBOX_SIZE_DP, dm).toInt()
        val checkboxEndMarginPx = dp2px(CHECKBOX_END_MARGIN_DP, dm).toInt()
        val indexBarReservedPx = dp2px(INDEX_BAR_RESERVED_DP, dm).toInt()

        val container = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                itemHeightPx
            )
            setPaddingRelative(hPad, 0, hPad, 0)
        }

        val rowLayout = LinearLayout(context).apply {
            tag = "row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val checkBox = SelectionCheckBoxView(context).apply {
            tag = "checkBox"
            layoutParams = LinearLayout.LayoutParams(checkboxSizePx, checkboxSizePx).apply {
                marginEnd = checkboxEndMarginPx
            }
        }
        rowLayout.addView(checkBox)

        val avatar = Avatar(context).apply {
            tag = "avatar"
            setSize(Avatar.AvatarSize.M)
        }
        rowLayout.addView(
            avatar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(spacingPx, 1)
        }
        rowLayout.addView(spacer)

        val textView = TextView(context).apply {
            tag = "label"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, ITEM_TEXT_SIZE_SP)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.NORMAL
            )
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        rowLayout.addView(textView)

        val endSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(indexBarReservedPx, 1)
        }
        rowLayout.addView(endSpacer)

        container.addView(rowLayout)
        return container
    }

    internal class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewWithTag("sectionText")
    }

    internal class UserItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rowLayout: LinearLayout = view.findViewWithTag("row")
        val avatar: Avatar = view.findViewWithTag("avatar")
        val textView: TextView = view.findViewWithTag("label")
        val checkBox: SelectionCheckBoxView = view.findViewWithTag("checkBox")
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
