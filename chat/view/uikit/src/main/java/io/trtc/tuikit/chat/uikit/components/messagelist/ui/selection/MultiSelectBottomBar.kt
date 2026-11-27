package io.trtc.tuikit.chat.uikit.components.messagelist.ui.selection
import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.theme.ThemeStore

class MultiSelectBottomBar(context: Context) : LinearLayout(context) {

    private val themeStore = ThemeStore.shared(context)
    private val density = context.resources.displayMetrics.density

    private val separateForwardItem: ActionItemView
    private val mergeForwardItem: ActionItemView
    private val deleteItem: ActionItemView

    var onForwardSeparate: () -> Unit = {}
    var onForwardMerge: () -> Unit = {}
    var onDelete: () -> Unit = {}

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val verticalPad = (12 * density).toInt()
        val horizontalPad = (16 * density).toInt()
        setPadding(horizontalPad, verticalPad, horizontalPad, verticalPad)

        val colors = themeStore.themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorBottomBar)

        separateForwardItem = ActionItemView(
            context = context,
            iconRes = R.drawable.message_list_multi_forward_separate_icon,
            labelRes = R.string.message_list_forward_by_separate
        ).apply { setOnClickListener { onForwardSeparate() } }

        mergeForwardItem = ActionItemView(
            context = context,
            iconRes = R.drawable.message_list_multi_forward_merge_icon,
            labelRes = R.string.message_list_forward_by_merge
        ).apply { setOnClickListener { onForwardMerge() } }

        deleteItem = ActionItemView(
            context = context,
            iconRes = R.drawable.message_list_multi_delete_icon,
            labelRes = R.string.message_list_multi_select_delete
        ).apply { setOnClickListener { onDelete() } }

        addView(separateForwardItem, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(mergeForwardItem, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(deleteItem, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    fun updateSelectedCount(count: Int) {
        val enabled = count > 0
        separateForwardItem.setEnabledState(enabled)
        mergeForwardItem.setEnabledState(enabled)
        deleteItem.setEnabledState(enabled)
    }

    private inner class ActionItemView(
        context: Context,
        iconRes: Int,
        labelRes: Int
    ) : LinearLayout(context) {

        private val iconView: ImageView
        private val labelView: TextView

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true

            val colors = themeStore.themeState.value.currentTheme.tokens.color
            val iconTint = colors.textColorSecondary

            iconView = ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(iconTint)
            }
            val iconSize = (40 * density).toInt()
            addView(iconView, LayoutParams(iconSize, iconSize))

            labelView = TextView(context).apply {
                setText(labelRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(iconTint)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            addView(labelView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * density).toInt()
            })
        }

        fun setEnabledState(enabled: Boolean) {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else 0.4f
        }
    }
}
