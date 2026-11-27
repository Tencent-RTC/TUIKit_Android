package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar

internal class GroupAvatarSelectorView(
    context: Context,
    displayedGroupName: String,
    selectedAvatarUrl: String?,
    avatarUrls: List<String>,
    private val onAvatarSelected: (String?) -> Unit
) : LinearLayout(context) {

    private companion object {
        const val GRID_ROWS = 2
        const val ITEM_SPACING_DP = 20f
        const val CORNER_RADIUS_DP = 8f
        const val STROKE_WIDTH_DP = 2f
    }

    private val wrapperViews = mutableMapOf<String?, FrameLayout>()

    init {
        val colors = getColors()
        val dm = context.resources.displayMetrics

        orientation = VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setBackgroundColor(colors.bgColorTopBar)

        addView(TextView(context).apply {
            text = context.getString(R.string.contact_list_group_avatar_text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(12f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(8f, dm).toInt()
            )
        })

        addView(
            createAvatarGridSection(
                displayedGroupName = displayedGroupName,
                selectedAvatarUrl = selectedAvatarUrl,
                avatarUrls = avatarUrls
            ).apply {
                setPadding(
                    dp2px(16f, dm).toInt(),
                    0,
                    dp2px(16f, dm).toInt(),
                    dp2px(16f, dm).toInt()
                )
            }
        )
    }

    fun updateSelection(selectedAvatarUrl: String?) {
        val colors = getColors()
        val dm = context.resources.displayMetrics
        val cornerRadius = dp2px(CORNER_RADIUS_DP, dm)
        val strokeWidth = dp2px(STROKE_WIDTH_DP, dm).toInt()
        wrapperViews.forEach { (avatarUrl, view) ->
            val isSelected = avatarUrl == selectedAvatarUrl
            view.foreground = if (isSelected) {
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    this.cornerRadius = cornerRadius
                    setStroke(strokeWidth, colors.textColorLink)
                }
            } else {
                null
            }
        }
    }

    private fun createAvatarGridSection(
        displayedGroupName: String,
        selectedAvatarUrl: String?,
        avatarUrls: List<String>
    ): View {
        val dm = context.resources.displayMetrics
        val itemSize = Avatar.AvatarSize.L.sizeDp.let { dp2px(it, dm).toInt() }
        val itemSpacing = dp2px(ITEM_SPACING_DP, dm).toInt()

        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val columnsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val candidates = listOf<String?>(null) + avatarUrls
        val columns = candidates.chunked(GRID_ROWS)
        columns.forEachIndexed { colIndex, colItems ->
            val column = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (colIndex > 0) marginStart = itemSpacing
                }
            }
            colItems.forEachIndexed { rowIndex, avatarUrl ->
                val avatarCard = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(itemSize, itemSize).apply {
                        if (rowIndex > 0) topMargin = itemSpacing
                    }
                    isClickable = true
                    isFocusable = true
                }
                val avatarView = Avatar(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setSize(Avatar.AvatarSize.L)
                    setShape(Avatar.AvatarShape.RoundRectangle)
                    if (avatarUrl.isNullOrEmpty()) {
                        setContent(
                            Avatar.AvatarContent.Text(
                                displayedGroupName.ifBlank {
                                    context.getString(R.string.contact_list_group_name)
                                }
                            )
                        )
                    } else {
                        setContent(Avatar.AvatarContent.Image(avatarUrl, displayedGroupName))
                    }
                }
                avatarCard.addView(avatarView)
                wrapperViews[avatarUrl] = avatarCard
                avatarCard.setOnClickListener {
                    onAvatarSelected(avatarUrl)
                    updateSelection(avatarUrl)
                }
                column.addView(avatarCard)
            }
            columnsContainer.addView(column)
        }

        scrollView.addView(columnsContainer)
        updateSelection(selectedAvatarUrl)
        return scrollView
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
