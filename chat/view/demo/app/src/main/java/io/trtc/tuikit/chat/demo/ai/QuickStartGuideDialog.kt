package io.trtc.tuikit.chat.demo.ai

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R

/**
 * 从 GitHub 下载源码并填入自己 SDKAppID 的客户，还没有可对话的助手。
 * 这时点 AI 首屏的按钮先弹框，简要介绍 AI 助手，并引导客户按官网「接入 UIKit 快速体验（无需服务器）」配置。
 */
object QuickStartGuideDialog {

    private const val BRAND_PURPLE = 0xFF7B5CFF.toInt()
    private const val BRAND_BLUE = 0xFF2EC5FF.toInt()
    private const val BRAND_PURPLE_SOFT = 0x267B5CFF

    /** 需要引导时弹框并返回 true，调用方不再继续原来的操作。 */
    fun showIfNeeded(context: Context): Boolean {
        if (!SupportAgentCatalog.needsOwnAssistant) {
            return false
        }
        show(context)
        return true
    }

    private fun show(context: Context) {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

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

        // 渐变圆底 + 星标，和首屏助手选择器的风格一致。
        card.addView(
            TextView(context).apply {
                text = "✦"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(BRAND_PURPLE, BRAND_BLUE),
                ).apply { shape = GradientDrawable.OVAL }
            },
            LinearLayout.LayoutParams(dp(56), dp(56)),
        )
        card.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_quick_start_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.textColorPrimary)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) },
        )
        card.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_quick_start_intro)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.25f)
                setTextColor(colors.textColorSecondary)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) },
        )

        val steps = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(colors.bgColorDefault)
                setStroke(dp(1).coerceAtLeast(1), colors.strokeColorSecondary)
            }
        }
        listOf(
            R.string.demo_ai_quick_start_step1,
            R.string.demo_ai_quick_start_step2,
            R.string.demo_ai_quick_start_step3,
        ).forEachIndexed { index, res ->
            steps.addView(
                stepRow(context, colors, index + 1, context.getString(res), ::dp),
                LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(12) },
            )
        }
        card.addView(steps, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        card.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_quick_start_doc_hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setLineSpacing(0f, 1.2f)
                setTextColor(colors.textColorSecondary)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) },
        )

        card.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_quick_start_doc)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(BRAND_PURPLE, BRAND_BLUE),
                ).apply { cornerRadius = dp(14).toFloat() }
                setOnClickListener {
                    dialog.dismiss()
                    DemoWebActivity.start(
                        context,
                        CustomBotDialog.DOC_URL,
                        context.getString(R.string.demo_ai_quick_start_title),
                    )
                }
            },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(18) },
        )
        card.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_quick_start_cancel)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(colors.textColorSecondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(8))
                setOnClickListener { dialog.dismiss() }
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) },
        )

        dialog.setContentView(card)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (context.resources.displayMetrics.widthPixels - dp(48)).coerceAtMost(dp(360))
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.5f)
        }
        dialog.show()
    }

    /** 序号圆点 + 步骤说明。 */
    private fun stepRow(
        context: Context,
        colors: ColorTokens,
        number: Int,
        text: String,
        dp: (Int) -> Int,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        addView(
            TextView(context).apply {
                this.text = number.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(BRAND_PURPLE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(BRAND_PURPLE_SOFT)
                }
            },
            LinearLayout.LayoutParams(dp(22), dp(22)),
        )
        addView(
            TextView(context).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.2f)
                setTextColor(colors.textColorPrimary)
            },
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(10)
                topMargin = dp(1)
            },
        )
    }
}
