package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupTypeOption
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

internal class GroupTypeSelectionStepView(
    context: Context,
    currentType: GroupTypeOption,
    groupTypes: List<GroupTypeOption>,
    private val onTypeSelected: (GroupTypeOption) -> Unit
) : LinearLayout(context) {

    init {
        val colors = getColors()
        val dm = context.resources.displayMetrics

        orientation = VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(colors.bgColorTopBar)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }

        val cardSpacing = dp2px(12f, dm).toInt()
        val horizontalPadding = dp2px(16f, dm).toInt()
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(horizontalPadding, cardSpacing, horizontalPadding, cardSpacing)
        }

        groupTypes.forEachIndexed { index, option ->
            container.addView(
                createGroupTypeCard(
                    option = option,
                    isSelected = currentType.type == option.type,
                    topMargin = if (index == 0) 0 else cardSpacing
                )
            )
        }

        scrollView.addView(container)
        addView(scrollView)
    }

    private fun createGroupTypeCard(
        option: GroupTypeOption,
        isSelected: Boolean,
        topMargin: Int
    ): View {
        val colors = getColors()
        val dm = context.resources.displayMetrics
        val cardRadius = dp2px(12f, dm)
        val cardPadding = dp2px(16f, dm).toInt()
        return LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.topMargin = topMargin
            }
            setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
            background = GradientDrawable().apply {
                setColor(colors.bgColorOperate)
                cornerRadius = cardRadius
                if (isSelected) {
                    setStroke(dp2px(1.5f, dm).toInt(), colors.textColorLink)
                }
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onTypeSelected(option) }

            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addView(createGroupTypeIcon(option))
                addView(TextView(context).apply {
                    text = context.getString(option.displayNameResID)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(colors.textColorPrimary)
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = dp2px(10f, dm).toInt()
                    }
                })
            })

            addView(TextView(context).apply {
                text = context.getString(option.descriptionResID)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(colors.textColorSecondary)
                setLineSpacing(0f, 1.3f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    this.topMargin = dp2px(10f, dm).toInt()
                }
            })
        }
    }

    private fun createGroupTypeIcon(option: GroupTypeOption): View {
        val dm = context.resources.displayMetrics
        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp2px(40f, dm).toInt(),
                dp2px(40f, dm).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(GroupTypeIconMapper.iconResId(option))
        }
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
