package io.trtc.tuikit.chat.demo.ai

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantAgent

/**
 * AI 聊天页标题的助手下拉菜单：服务器内置的助手 + 最后一项「自定义 AI 助手」。
 *
 * 选中项写入 [SupportAgentCatalog]，变化后回调 [onSelectionChanged] 让页面重新加载助手。
 */
class AIAgentPicker(
    private val context: Context,
    private val onSelectionChanged: () -> Unit,
) {

    private val themeStore = ThemeStore.shared(context)
    private val density = context.resources.displayMetrics.density
    private var popup: PopupWindow? = null

    fun dismiss() {
        popup?.dismiss()
    }

    // region menu

    /** 在 [anchor] 下方弹出助手菜单，水平居中对齐。 */
    fun show(anchor: View) {
        popup?.dismiss()
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(colors.bgColorDialog)
                setStroke(dp(1), colors.strokeColorSecondary)
            }
        }
        val window = PopupWindow(card, dp(MENU_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(12).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            setOnDismissListener { popup = null }
        }
        popup = window

        card.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_agent_picker_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(colors.textColorSecondary)
                setPadding(dp(16), dp(4), dp(16), dp(6))
            },
        )

        val customSelected = SupportAgentCatalog.isCustomSelected
        val boundId = SupportAgentCatalog.agents.firstOrNull()?.imUserId
        SupportAgentCatalog.directory.forEach { agent ->
            card.addView(
                buildRow(
                    colors = colors,
                    icon = builtInIcon(agent),
                    title = agent.displayName,
                    subtitle = null,
                    selected = !customSelected && agent.imUserId == boundId,
                    trailing = null,
                ) {
                    window.dismiss()
                    selectBuiltIn(agent)
                },
            )
        }

        card.addView(
            View(context).apply { setBackgroundColor(colors.strokeColorSecondary) },
            LinearLayout.LayoutParams(-1, dp(1).coerceAtLeast(1)).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
                marginStart = dp(16)
                marginEnd = dp(16)
            },
        )

        val customId = SupportAgentCatalog.customBotUserId
        val configured = customId.isNotEmpty()
        val editAction = TextView(context).apply {
            text = context.getString(if (configured) R.string.demo_ai_agent_custom_edit else R.string.demo_ai_agent_custom_go)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(BRAND_PURPLE)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(BRAND_PURPLE_SOFT)
            }
            setOnClickListener {
                window.dismiss()
                openCustomDialog()
            }
        }
        card.addView(
            buildRow(
                colors = colors,
                icon = customIcon(),
                title = context.getString(R.string.demo_ai_agent_custom_title),
                subtitle = if (configured) {
                    SupportAgentCatalog.customAgent?.displayName ?: customId
                } else {
                    context.getString(R.string.demo_ai_agent_custom_desc)
                },
                selected = customSelected,
                trailing = editAction,
            ) {
                window.dismiss()
                if (configured) selectCustom() else openCustomDialog()
            },
        )

        window.showAsDropDown(anchor, (anchor.width - MENU_WIDTH_DP * density).toInt() / 2, dp(4))
    }

    private fun buildRow(
        colors: ColorTokens,
        icon: View,
        title: String,
        subtitle: String?,
        selected: Boolean,
        trailing: View?,
        onClick: () -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(12), dp(10))
            if (selected) setBackgroundColor(BRAND_PURPLE_SOFT)
            setOnClickListener { onClick() }
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(
            TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (selected) BRAND_PURPLE else colors.textColorPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        if (!subtitle.isNullOrEmpty()) {
            texts.addView(
                TextView(context).apply {
                    text = subtitle
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(colors.textColorSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
        }
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        if (selected) {
            row.addView(
                TextView(context).apply {
                    text = "✓"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(BRAND_PURPLE)
                },
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) },
            )
        }
        trailing?.let { row.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) }) }
        return row
    }

    /** 内置助手：渐变圆底 + 名称首字。 */
    private fun builtInIcon(agent: AIAssistantAgent) = TextView(context).apply {
        text = agent.displayName.trim().take(1)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(BRAND_PURPLE, BRAND_BLUE),
        ).apply { shape = GradientDrawable.OVAL }
    }

    /** 自定义助手：虚线圆框 + 加号，和内置助手区分开。 */
    private fun customIcon() = TextView(context).apply {
        text = "+"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(BRAND_PURPLE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BRAND_PURPLE_SOFT)
            setStroke(dp(1), BRAND_PURPLE, dp(3).toFloat(), dp(2).toFloat())
        }
    }

    // endregion

    // region selection

    private fun selectBuiltIn(agent: AIAssistantAgent) {
        SupportAgentCatalog.selectedBotUserId = agent.imUserId
        onSelectionChanged()
    }

    private fun selectCustom() {
        val customId = SupportAgentCatalog.customBotUserId
        SupportAgentCatalog.selectedBotUserId = customId
        onSelectionChanged()
    }

    private fun openCustomDialog() {
        CustomBotDialog.show(context) { selectCustom() }
    }

    // endregion

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val MENU_WIDTH_DP = 292
        const val BRAND_PURPLE = 0xFF7B5CFF.toInt()
        const val BRAND_BLUE = 0xFF2EC5FF.toInt()
        const val BRAND_PURPLE_SOFT = 0x267B5CFF
    }
}
