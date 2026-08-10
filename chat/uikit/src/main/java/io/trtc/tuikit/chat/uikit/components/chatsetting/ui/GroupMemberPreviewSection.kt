package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class GroupMemberPreviewSection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headerRow: SettingRowNavigate
    private val gridContainer: LinearLayout
    private val row1: LinearLayout
    private val row2: LinearLayout

    private var currentMembers: List<GroupMember> = emptyList()
    private var currentMemberCount: Int = 0
    private var showAddButton: Boolean = false
    private var showRemoveButton: Boolean = false
    private var viewScope: CoroutineScope? = null

    var onHeaderClick: (() -> Unit)? = null
    var onMemberClick: ((GroupMember) -> Unit)? = null
    var onAddClick: (() -> Unit)? = null
    var onRemoveClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        headerRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_members))
            setShowArrow(true)
            setOnClickListener { onHeaderClick?.invoke() }
        }
        addView(
            headerRow,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        gridContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            val horizontalPadding = dp2px(16f, resources.displayMetrics).toInt()
            val verticalPadding = dp2px(16f, resources.displayMetrics).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }
        row1 = createRow()
        row2 = createRow().apply {
            val topMargin = dp2px(12f, resources.displayMetrics).toInt()
            (layoutParams as LayoutParams).topMargin = topMargin
        }
        gridContainer.addView(row1)
        gridContainer.addView(row2)
        addView(
            gridContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun updateContent(
        members: List<GroupMember>,
        memberCount: Int,
        showAddButton: Boolean,
        showRemoveButton: Boolean
    ) {
        currentMembers = members
        currentMemberCount = memberCount
        this.showAddButton = showAddButton
        this.showRemoveButton = showRemoveButton
        headerRow.setValue(memberCount.toString())
        renderPreviewItems()
    }

    private fun createRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun renderPreviewItems() {
        row1.removeAllViews()
        row2.removeAllViews()

        val reserved = (if (showAddButton) 1 else 0) + (if (showRemoveButton) 1 else 0)
        val maxMemberSlots = (MAX_TOTAL_COUNT - reserved).coerceAtLeast(0)

        val items = mutableListOf<View>()
        currentMembers.take(maxMemberSlots).forEach { member ->
            items.add(createMemberItem(member))
        }
        if (showAddButton) {
            items.add(createActionItem(ACTION_ADD))
        }
        if (showRemoveButton) {
            items.add(createActionItem(ACTION_REMOVE))
        }

        items.forEachIndexed { index, item ->
            val target = if (index < COLUMNS_PER_ROW) row1 else row2
            target.addView(wrapInSlot(item))
        }

        val firstRowCount = minOf(items.size, COLUMNS_PER_ROW)
        repeat(COLUMNS_PER_ROW - firstRowCount) {
            row1.addView(createPlaceholderSlot())
        }

        val secondRowCount = (items.size - COLUMNS_PER_ROW).coerceAtLeast(0)
        if (secondRowCount > 0) {
            row2.visibility = View.VISIBLE
            repeat(COLUMNS_PER_ROW - secondRowCount) {
                row2.addView(createPlaceholderSlot())
            }
        } else {
            row2.visibility = View.GONE
        }
    }

    private fun wrapInSlot(item: View): View {
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(
                item,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            )
        }
    }

    private fun createPlaceholderSlot(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
    }

    private fun createMemberItem(member: GroupMember): View {
        val colors = getColors()
        val dm = resources.displayMetrics
        val itemWidth = dp2px(56f, dm).toInt()
        val avatarSize = dp2px(40f, dm).toInt()
        val displayName = member.displayName

        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(itemWidth, LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { onMemberClick?.invoke(member) }

            addView(
                Avatar(context).apply {
                    layoutParams = LayoutParams(avatarSize, avatarSize)
                    setContent(
                        Avatar.AvatarContent.Image(
                            url = member.avatarURL,
                            fallbackName = displayName
                        )
                    )
                }
            )

            addView(
                TextView(context).apply {
                    text = displayName
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(colors.textColorPrimary)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp2px(6f, dm).toInt()
                    }
                }
            )
        }
    }

    private fun createActionItem(actionType: ActionType): View {
        val colors = getColors()
        val dm = resources.displayMetrics
        val itemWidth = dp2px(56f, dm).toInt()
        val iconSize = dp2px(40f, dm).toInt()
        val iconCornerRadius = dp2px(8f, dm)
        val symbol = if (actionType == ACTION_ADD) "+" else "−"

        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(itemWidth, LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (actionType == ACTION_ADD) {
                    onAddClick?.invoke()
                } else {
                    onRemoveClick?.invoke()
                }
            }

            addView(
                FrameLayout(context).apply {
                    layoutParams = LayoutParams(iconSize, iconSize)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(colors.bgColorInput)
                        cornerRadius = iconCornerRadius
                    }
                    addView(
                        TextView(context).apply {
                            text = symbol
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                            setTextColor(colors.textColorSecondary)
                            gravity = Gravity.CENTER
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                    )
                }
            )

            addView(
                View(context).apply {
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dp2px(20f, dm).toInt()
                    )
                }
            )
        }
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        gridContainer.setBackgroundColor(colors.bgColorOperate)
        row1.setBackgroundColor(colors.bgColorOperate)
        row2.setBackgroundColor(colors.bgColorOperate)
        renderPreviewItems()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

    private enum class ActionType {
        ADD,
        REMOVE
    }

    private companion object {
        const val COLUMNS_PER_ROW = 5
        const val MAX_ROWS = 2
        const val MAX_TOTAL_COUNT = COLUMNS_PER_ROW * MAX_ROWS
        val ACTION_ADD = ActionType.ADD
        val ACTION_REMOVE = ActionType.REMOVE
    }
}
