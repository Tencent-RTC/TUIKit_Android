package io.trtc.tuikit.chat.demo.ai

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantAgentLoader
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantHandoffStatusLoader
import io.trtc.tuikit.chat.uikit.pages.AIAssistantPageView
import io.trtc.tuikit.chat.uikit.pages.AIAssistantTrialPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 体验 Demo 的 AI tab：AI 文字会话页。
 * 标题下拉切换内置助手或自定义 AI 助手，不提供语音通话入口。
 */
class AIChatHomeView(context: Context) : FrameLayout(context) {

    private val themeStore = ThemeStore.shared(context)
    private var scope: CoroutineScope? = null

    private val page = AIAssistantPageView(context)
    private val agentPicker = AIAgentPicker(context) { page.onPageVisible() }
    private val ctaBanner = LinearLayout(context)
    private val ctaTitle = TextView(context)
    private val ctaDesc = TextView(context)
    private val ctaClose = TextView(context)

    init {
        buildCtaBanner()
        page.setup(
            // 页面只拿到下拉菜单选中的那一个助手；切换后通过 onPageVisible 重新拉取。
            agentLoader = AIAssistantAgentLoader { onResult ->
                SupportAgentCatalog.fetch { agents ->
                    onResult(agents)
                    showQuickStartGuideIfNeeded()
                }
            },
            handoffStatusLoader = AIAssistantHandoffStatusLoader(SupportAgentCatalog::fetchHandoffStatus),
            trialPrompt = AIAssistantTrialPrompt(
                quickQuestions = resources.getStringArray(R.array.demo_ai_quick_questions).toList(),
                quickQuestionsForAgent = { agent -> AIAgentProfile.of(context, agent).quickQuestions },
                headerBanner = ctaBanner,
                showCallButton = false,
                onTitleClick = { anchor -> agentPicker.show(anchor) },
            ),
        )
        ctaBanner.visibility = if (ctaDismissed) View.GONE else View.VISIBLE
        addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    fun onPageVisible() {
        page.onPageVisible()
    }

    /** 自有应用还没有可对话的助手时，每次进程只自动弹一次快速体验引导。 */
    private fun showQuickStartGuideIfNeeded() {
        if (quickStartGuideShown || !isAttachedToWindow) {
            return
        }
        if (QuickStartGuideDialog.showIfNeeded(context)) {
            quickStartGuideShown = true
        }
    }

    private fun buildCtaBanner() {
        ctaBanner.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            setOnClickListener { openCtaLink() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
                topMargin = dp(8)
                bottomMargin = dp(2)
            }
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        ctaTitle.apply {
            text = context.getString(R.string.demo_ai_cta_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        ctaDesc.apply {
            text = context.getString(R.string.demo_ai_cta_desc)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(ctaTitle)
        texts.addView(ctaDesc)
        ctaBanner.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val action = TextView(context).apply {
            text = context.getString(R.string.demo_ai_cta_action)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(BRAND_PURPLE, BRAND_BLUE),
            ).apply { cornerRadius = dp(14).toFloat() }
        }
        ctaBanner.addView(action, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        ctaClose.apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.demo_ai_cta_close)
            setOnClickListener {
                ctaDismissed = true
                ctaBanner.visibility = View.GONE
            }
        }
        ctaBanner.addView(ctaClose, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(2) })
    }

    private fun openCtaLink() {
        DemoWebActivity.start(
            context,
            CustomBotDialog.DOC_URL,
            context.getString(R.string.demo_ai_cta_title),
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { s ->
            s.launch {
                themeStore.themeState.collectLatest { applyColors(it.currentTheme.tokens.color) }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        agentPicker.dismiss()
        scope?.cancel()
        scope = null
    }

    private fun applyColors(colors: ColorTokens) {
        ctaTitle.setTextColor(colors.textColorPrimary)
        ctaDesc.setTextColor(colors.textColorSecondary)
        ctaClose.setTextColor(colors.textColorSecondary)
        ctaBanner.background = gradientBorder(colors.bgColorOperate)
    }

    /** 渐变描边卡片：外层渐变圆角矩形，内层内缩 1.5dp 填背景色。 */
    private fun gradientBorder(fill: Int): Drawable {
        val radius = dp(14).toFloat()
        val outer = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(BRAND_PURPLE, BRAND_BLUE, BRAND_PINK),
        ).apply { cornerRadius = radius }
        val inner = GradientDrawable().apply {
            cornerRadius = radius - dp(1)
            setColor(fill)
        }
        val stroke = (1.5f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        return LayerDrawable(arrayOf(outer, inner)).apply {
            setLayerInset(1, stroke, stroke, stroke, stroke)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** 本次进程内关掉过横幅就不再出现，重启 App 后恢复。 */
        var ctaDismissed = false

        /** 本次进程内已自动弹过快速体验引导。 */
        var quickStartGuideShown = false
        const val BRAND_PURPLE = 0xFF7B5CFF.toInt()
        const val BRAND_BLUE = 0xFF2EC5FF.toInt()
        const val BRAND_PINK = 0xFFFF6FD8.toInt()
    }
}
