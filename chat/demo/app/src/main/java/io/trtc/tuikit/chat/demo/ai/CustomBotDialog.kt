package io.trtc.tuikit.chat.demo.ai

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.app.R

/**
 * 填写用户在体验工作台创建的助手 ID。
 *
 * 确认前会向服务器核对该机器人确实存在，核对通过才保存并选中它。
 * 样式与 [QuickStartGuideDialog] 一致：渐变图标、圆角卡片、渐变主按钮。
 */
object CustomBotDialog {

    /** 官网《知识库 AI 助手》文档，AI 页横幅也跳这里。 */
    const val DOC_URL = "https://cloud.tencent.com/document/product/269/139511"

    private const val BRAND_PURPLE = 0xFF7B5CFF.toInt()
    private const val BRAND_BLUE = 0xFF2EC5FF.toInt()

    /** [onSaved] 在校验通过、已保存并选中自定义助手后回调（主线程）。 */
    fun show(context: Context, onSaved: () -> Unit) {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        val prefix = SupportAgentCatalog.BOT_USER_ID_PREFIX

        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

        // 前缀固定显示在输入框前面，客户只填后半部分；粘贴完整 ID 也能识别。
        fun stripPrefix(value: String): String = value.trim().removePrefix(prefix).trim()

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(colors.bgColorDialog)
            }
        }
        card.addView(
            TextView(context).apply {
                text = "+"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(BRAND_PURPLE, BRAND_BLUE),
                ).apply { shape = GradientDrawable.OVAL }
            },
            LinearLayout.LayoutParams(dp(56), dp(56)),
        )
        card.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_custom_bot_dialog_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.textColorPrimary)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) },
        )
        card.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_custom_bot_dialog_desc)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.25f)
                setTextColor(colors.textColorSecondary)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) },
        )

        // 输入区：圆角框内左侧固定前缀，聚焦时描边变为品牌紫。
        val fieldIdle = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(colors.bgColorDefault)
            setStroke(dp(1).coerceAtLeast(1), colors.strokeColorSecondary)
        }
        val fieldFocused = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(colors.bgColorDefault)
            setStroke(dp(2), BRAND_PURPLE)
        }
        val field = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = fieldIdle
        }
        field.addView(
            TextView(context).apply {
                text = prefix
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(BRAND_PURPLE)
            },
        )
        val input = EditText(context).apply {
            setText(stripPrefix(SupportAgentCatalog.customBotUserId))
            setSelection(text.length)
            hint = context.getString(R.string.demo_custom_bot_input_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setHintTextColor(colors.textColorTertiary)
            background = null
            setPadding(dp(6), 0, 0, 0)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setOnFocusChangeListener { _, hasFocus -> field.background = if (hasFocus) fieldFocused else fieldIdle }
        }
        field.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        card.addView(field, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(20) })

        val errorView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setLineSpacing(0f, 1.2f)
            setTextColor(colors.textColorError)
            visibility = View.GONE
        }
        card.addView(errorView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val confirmButton = TextView(context).apply {
            text = context.getString(R.string.demo_custom_bot_confirm)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(BRAND_PURPLE, BRAND_BLUE),
            ).apply { cornerRadius = dp(14).toFloat() }
        }
        card.addView(confirmButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(20) })

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancelButton = TextView(context).apply {
            text = context.getString(R.string.demo_custom_bot_cancel)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.textColorSecondary)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setOnClickListener { dialog.dismiss() }
        }
        val docLink = TextView(context).apply {
            text = context.getString(R.string.demo_custom_bot_dialog_doc_link)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.textColorLink)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setOnClickListener {
                DemoWebActivity.start(context, DOC_URL, context.getString(R.string.demo_custom_bot_dialog_title))
            }
        }
        footer.addView(cancelButton)
        footer.addView(
            View(context).apply { setBackgroundColor(colors.strokeColorSecondary) },
            LinearLayout.LayoutParams(dp(1).coerceAtLeast(1), dp(14)).apply { topMargin = dp(2) },
        )
        footer.addView(docLink)
        card.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        dialog.setContentView(card)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (context.resources.displayMetrics.widthPixels - dp(48)).coerceAtMost(dp(360))
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.5f)
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
            )
        }
        dialog.show()
        input.requestFocus()

        fun setConfirmEnabled(enabled: Boolean) {
            confirmButton.isEnabled = enabled
            confirmButton.alpha = if (enabled) 1f else 0.4f
        }

        setConfirmEnabled(stripPrefix(input.text.toString()).isNotEmpty())
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                errorView.visibility = View.GONE
                setConfirmEnabled(stripPrefix(s?.toString().orEmpty()).isNotEmpty())
            }
        })

        fun setSubmitting(submitting: Boolean) {
            input.isEnabled = !submitting
            setConfirmEnabled(!submitting && stripPrefix(input.text.toString()).isNotEmpty())
            cancelButton.isEnabled = !submitting
            dialog.setCancelable(!submitting)
        }

        confirmButton.setOnClickListener {
            val userId = prefix + stripPrefix(input.text.toString())
            errorView.visibility = View.GONE
            setSubmitting(true)
            SupportAgentCatalog.checkBot(userId) { result ->
                if (!dialog.isShowing) return@checkBot
                setSubmitting(false)
                when (result) {
                    SupportAgentCatalog.BotCheckResult.FOUND -> {
                        if (userId != SupportAgentCatalog.customBotUserId) {
                            SupportAgentCatalog.customBotUserId = userId
                        }
                        SupportAgentCatalog.selectedBotUserId = userId
                        dialog.dismiss()
                        onSaved()
                    }
                    SupportAgentCatalog.BotCheckResult.NOT_FOUND -> {
                        errorView.setText(R.string.demo_custom_bot_not_found)
                        errorView.visibility = View.VISIBLE
                    }
                    SupportAgentCatalog.BotCheckResult.NETWORK_ERROR -> {
                        errorView.setText(R.string.demo_custom_bot_network_error)
                        errorView.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
