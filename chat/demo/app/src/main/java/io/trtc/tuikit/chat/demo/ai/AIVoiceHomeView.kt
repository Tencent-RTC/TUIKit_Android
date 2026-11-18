package io.trtc.tuikit.chat.demo.ai

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.AICallUiOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationSet
import android.view.animation.AlphaAnimation
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextSwitcher
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantAgentLoader
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantHandoffStatusLoader
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.AICallState
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui.VoiceOrbView
import io.trtc.tuikit.chat.uikit.pages.AIAssistantPageView
import io.trtc.tuikit.chat.uikit.pages.AIAssistantTrialPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.trtc.tuikit.chat.uikit.R as UikitR

/**
 * 体验 Demo 的 AI tab：默认展示「通话风格首屏」，点击才发起 AI 语音通话；
 * 「改用文字提问」/「记录」切到原来的文字会话页，挂断回来后也自动切到文字会话。
 * 设备不支持语音时直接展示文字会话。
 */
class AIVoiceHomeView(context: Context) : FrameLayout(context) {

    private val themeStore = ThemeStore.shared(context)
    private var scope: CoroutineScope? = null

    private val page = AIAssistantPageView(context)
    private val voiceSupported = page.isVoiceCallSupported

    private val landing = LinearLayout(context)
    private val agentPicker = AIAgentPicker(context) { page.onPageVisible() }
    private val historyPill = TextView(context)
    private val headline = TextView(context)
    private val subtitle = TextView(context)
    private val tryCard = LinearLayout(context)
    private val tryLabel = TextView(context)
    private val sampleSwitcher = TextSwitcher(context)
    private val orb = VoiceOrbView(context).apply { auroraStyle = true }
    private val orbRings = PulseRingsView(context, 0xFF8B7BFF.toInt(), 3, 3600L, startFraction = 0.6f, maxAlpha = 0.5f)
    private val callRings = PulseRingsView(context, 0xFF1DC98A.toInt(), 2, 2000L, startFraction = 0.64f, maxAlpha = 0.7f)
    private val callButton = FrameLayout(context)
    private val callLabel = TextView(context)
    private val useText = TextView(context)
    private val ctaBanner = LinearLayout(context)
    private val ctaTitle = TextView(context)
    private val ctaDesc = TextView(context)
    private val ctaClose = TextView(context)
    private var callBreath: ValueAnimator? = null

    private var samples = AIAgentProfile.of(context, null).samples
    private var sampleIndex = 0
    private val rotateSample = object : Runnable {
        override fun run() {
            if (samples.isEmpty()) return
            sampleIndex = (sampleIndex + 1) % samples.size
            sampleSwitcher.setText(samples[sampleIndex])
            postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    /** 发起通话的时间点。之后几秒内窗口失焦，就认为进了通话页，回来时切到文字会话。 */
    private var callStartedAt = 0L
    private var leftForCall = false

    /** 去系统设置开麦克风，回来时如果已授权就直接发起通话。 */
    private var pendingSettingsReturn = false

    /**
     * 只有真的离开过（窗口失焦）再回来才算「从设置返回」：关掉授权说明弹层时主窗口会先拿回一次焦点，
     * 早于设置页启动，不能把那一次当成返回。
     */
    private var leftForSettings = false

    /** 由 MainActivity 在切 tab 时设置；只有 AI tab 在前台时才拦截返回键。 */
    var isCurrentTab: Boolean = true
        set(value) {
            field = value
            updateBackCallback()
        }

    /** 文字模式下按返回键回到语音首屏，而不是直接退出 App。 */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showVoice()
        }
    }

    init {
        buildLanding()
        buildCtaBanner()
        page.setup(
            // 页面只拿到下拉框选中的那一个助手；切换后通过 onPageVisible 重新拉取。
            agentLoader = AIAssistantAgentLoader { onResult ->
                SupportAgentCatalog.fetch { agents ->
                    agentPicker.setAgent(agents.firstOrNull())
                    applyProfile(AIAgentProfile.of(context, agents.firstOrNull()))
                    onResult(agents)
                }
            },
            handoffStatusLoader = AIAssistantHandoffStatusLoader(SupportAgentCatalog::fetchHandoffStatus),
            trialPrompt = AIAssistantTrialPrompt(
                backLabel = if (voiceSupported) context.getString(R.string.demo_ai_voice_back) else null,
                onBack = if (voiceSupported) ({ showVoice() }) else null,
                quickQuestions = resources.getStringArray(R.array.demo_ai_quick_questions).toList(),
                quickQuestionsForAgent = { agent -> AIAgentProfile.of(context, agent).quickQuestions },
                headerBanner = ctaBanner,
                callUiOptions = callUiOptions(samples),
                callUiOptionsForAgent = { agent -> callUiOptions(AIAgentProfile.of(context, agent).samples) },
            ),
        )
        addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(landing, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (voiceSupported) showVoice() else showText()
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    /** 切换助手后更新首屏副标题和轮播的示例问题。 */
    private fun applyProfile(profile: AIAgentProfile) {
        subtitle.text = profile.subtitle
        if (profile.samples == samples) {
            return
        }
        samples = profile.samples
        sampleIndex = 0
        sampleSwitcher.setCurrentText(samples.firstOrNull().orEmpty())
    }

    private fun callUiOptions(hints: List<String>) = AICallUiOptions(
        auroraStyle = true,
        plainTranscript = true,
        sampleHints = hints,
        showButtonLabels = true,
        showCallTimer = true,
    )

    fun onPageVisible() {
        page.onPageVisible()
    }

    // region layout

    private fun buildLanding() {
        landing.orientation = LinearLayout.VERTICAL
        landing.gravity = Gravity.CENTER_HORIZONTAL
        landing.isClickable = true

        val header = FrameLayout(context).apply {
            setPadding(dp(16), 0, dp(16), 0)
            clipChildren = false
            clipToPadding = false
        }
        header.addView(agentPicker, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        historyPill.apply {
            text = context.getString(R.string.demo_ai_voice_history)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setOnClickListener { onShowTextClicked() }
        }
        header.addView(historyPill, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        landing.clipChildren = false
        landing.addView(header, LinearLayout.LayoutParams(-1, dp(56)))

        landing.addView(Space(context), LinearLayout.LayoutParams(1, 0, 0.8f))

        val orbBox = FrameLayout(context)
        orbBox.addView(orbRings, FrameLayout.LayoutParams(-1, -1))
        orbBox.addView(orb, FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER))
        orbBox.setOnClickListener { onStartCallClicked() }
        landing.addView(orbBox, LinearLayout.LayoutParams(dp(264), dp(264)))

        headline.apply {
            text = context.getString(R.string.demo_ai_voice_headline)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        landing.addView(headline, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        subtitle.apply {
            text = context.getString(R.string.demo_ai_voice_subtitle)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
        }
        landing.addView(subtitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })

        tryCard.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        tryLabel.apply {
            text = context.getString(R.string.demo_ai_voice_try_label)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        tryCard.addView(tryLabel)
        sampleSwitcher.setFactory {
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(themeStore.themeState.value.currentTheme.tokens.color.textColorPrimary)
            }
        }
        sampleSwitcher.inAnimation = slide(fromY = 1f, toY = 0f, fromAlpha = 0f, toAlpha = 1f)
        sampleSwitcher.outAnimation = slide(fromY = 0f, toY = -1f, fromAlpha = 1f, toAlpha = 0f)
        sampleSwitcher.setCurrentText(samples.firstOrNull().orEmpty())
        tryCard.addView(sampleSwitcher, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        landing.addView(
            tryCard,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(18)
                marginStart = dp(28)
                marginEnd = dp(28)
            },
        )

        landing.addView(Space(context), LinearLayout.LayoutParams(1, 0, 1f))

        val callBox = FrameLayout(context)
        callBox.addView(callRings, FrameLayout.LayoutParams(-1, -1))
        callButton.apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF34E89E.toInt(), 0xFF0FB57A.toInt()),
            ).apply { shape = GradientDrawable.OVAL }
            elevation = dp(6).toFloat()
            contentDescription = context.getString(R.string.demo_ai_voice_start)
            setOnClickListener { onStartCallClicked() }
            addView(
                ImageView(context).apply {
                    setImageResource(UikitR.drawable.uikit_ai_ic_call)
                    setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER),
            )
        }
        callBox.addView(callButton, FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER))
        landing.addView(callBox, LinearLayout.LayoutParams(dp(116), dp(116)))

        callLabel.apply {
            text = context.getString(R.string.demo_ai_voice_start)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
        }
        landing.addView(callLabel, LinearLayout.LayoutParams(-2, -2))
        useText.apply {
            text = context.getString(R.string.demo_ai_voice_use_text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onShowTextClicked() }
        }
        landing.addView(useText, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10) })
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

    // endregion

    // region mode switch

    private fun showVoice() {
        if (!voiceSupported) {
            return
        }
        landing.visibility = View.VISIBLE
        page.visibility = View.GONE
        startLandingAnimations()
        updateBackCallback()
    }

    private fun showText() {
        landing.visibility = View.GONE
        page.visibility = View.VISIBLE
        ctaBanner.visibility = if (ctaDismissed) View.GONE else View.VISIBLE
        stopLandingAnimations()
        updateBackCallback()
    }

    private fun updateBackCallback() {
        backCallback.isEnabled = voiceSupported && isCurrentTab && page.visibility == View.VISIBLE
    }

    private fun startLandingAnimations() {
        if (!isAttachedToWindow || landing.visibility != View.VISIBLE) {
            return
        }
        orb.setState(AICallState.IDLE)
        removeCallbacks(rotateSample)
        postDelayed(rotateSample, SAMPLE_INTERVAL_MS)
        if (callBreath == null) {
            callBreath = ObjectAnimator.ofPropertyValuesHolder(
                callButton,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f),
            ).apply {
                duration = 1100L
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    private fun stopLandingAnimations() {
        removeCallbacks(rotateSample)
        callBreath?.cancel()
        callBreath = null
        callButton.scaleX = 1f
        callButton.scaleY = 1f
    }

    // endregion

    // region call

    /** 首屏的「记录」「改用文字提问」。 */
    private fun onShowTextClicked() {
        if (QuickStartGuideDialog.showIfNeeded(context)) {
            return
        }
        showText()
    }

    private fun onStartCallClicked() {
        if (QuickStartGuideDialog.showIfNeeded(context)) {
            return
        }
        if (!page.canStartVoiceCall()) {
            Toast.makeText(context, R.string.demo_ai_voice_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startCall()
        } else {
            showMicSheet(permanentlyDenied = isMicPermanentlyDenied())
        }
    }

    /**
     * 申请过、且系统不再给出理由弹窗，说明用户已经「拒绝且不再询问」，
     * 这时再走系统弹窗会被直接拒掉，只能引导去设置。
     */
    private fun isMicPermanentlyDenied(): Boolean {
        val activity = context as? Activity ?: return false
        return MMKV.defaultMMKV().decodeBool(KEY_MIC_ASKED, false) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    }

    private fun openAppSettings() {
        pendingSettingsReturn = true
        leftForSettings = false
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { pendingSettingsReturn = false }
    }

    private fun startCall() {
        callStartedAt = SystemClock.uptimeMillis()
        leftForCall = false
        page.startVoiceCall()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (pendingSettingsReturn && !hasWindowFocus) {
            leftForSettings = true
            return
        }
        if (hasWindowFocus && pendingSettingsReturn && leftForSettings) {
            pendingSettingsReturn = false
            leftForSettings = false
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted && page.canStartVoiceCall()) {
                startCall()
            }
            return
        }
        if (!hasWindowFocus) {
            if (callStartedAt != 0L && SystemClock.uptimeMillis() - callStartedAt < CALL_LEAVE_WINDOW_MS) {
                leftForCall = true
            }
            return
        }
        if (leftForCall) {
            // 挂断回来：接着用文字问
            leftForCall = false
            callStartedAt = 0L
            showText()
        }
    }

    /** 系统授权弹窗之前先说明为什么要麦克风，并给出不开麦的退路。 */
    private fun showMicSheet(permanentlyDenied: Boolean) {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(28))
            background = GradientDrawable().apply {
                setColor(colors.bgColorDialog)
                cornerRadii = floatArrayOf(
                    dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f,
                )
            }
        }
        sheet.addView(
            View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(3).toFloat()
                    setColor(colors.strokeColorPrimary)
                }
            },
            LinearLayout.LayoutParams(dp(40), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(18)
            },
        )
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x337B5CFF)
                }
                addView(
                    ImageView(context).apply {
                        setImageResource(UikitR.drawable.uikit_ai_ic_call_mic)
                        setColorFilter(BRAND_PURPLE, PorterDuff.Mode.SRC_IN)
                    },
                    FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER),
                )
            },
            LinearLayout.LayoutParams(dp(46), dp(46)),
        )
        titleRow.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_mic_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.textColorPrimary)
            },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(14) },
        )
        sheet.addView(titleRow)
        sheet.addView(
            TextView(context).apply {
                text = context.getString(
                    if (permanentlyDenied) R.string.demo_ai_mic_denied_desc else R.string.demo_ai_mic_desc,
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.3f)
                setTextColor(colors.textColorSecondary)
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) },
        )
        sheet.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_mic_tip)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(colors.textColorSecondary)
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) },
        )
        sheet.addView(
            TextView(context).apply {
                text = context.getString(
                    if (permanentlyDenied) R.string.demo_ai_mic_open_settings else R.string.demo_ai_mic_allow,
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(0xFF34E89E.toInt(), 0xFF0FB57A.toInt()),
                ).apply { cornerRadius = dp(14).toFloat() }
                setOnClickListener {
                    dialog.dismiss()
                    if (permanentlyDenied) {
                        openAppSettings()
                    } else {
                        MMKV.defaultMMKV().encode(KEY_MIC_ASKED, true)
                        startCall()
                    }
                }
            },
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(22) },
        )
        sheet.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_ai_mic_text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(colors.textColorLink)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(4))
                setOnClickListener {
                    dialog.dismiss()
                    showText()
                }
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) },
        )
        dialog.setContentView(sheet)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_InputMethod)
            setDimAmount(0.5f)
        }
        dialog.show()
    }

    private fun openCtaLink() {
        DemoWebActivity.start(
            context,
            CustomBotDialog.DOC_URL,
            context.getString(R.string.demo_ai_cta_title),
        )
    }

    // endregion

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (context as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(backCallback)
        updateBackCallback()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { s ->
            s.launch {
                themeStore.themeState.collectLatest { applyColors(it.currentTheme.tokens.color) }
            }
        }
        if (landing.visibility == View.VISIBLE) {
            startLandingAnimations()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        backCallback.remove()
        stopLandingAnimations()
        scope?.cancel()
        scope = null
    }

    private fun applyColors(colors: ColorTokens) {
        landing.setBackgroundColor(colors.bgColorOperate)
        historyPill.setTextColor(colors.textColorPrimary)
        historyPill.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), colors.strokeColorPrimary)
        }
        headline.setTextColor(colors.textColorPrimary)
        subtitle.setTextColor(colors.textColorSecondary)
        tryCard.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(colors.bgColorDefault)
            setStroke(dp(1), colors.strokeColorSecondary)
        }
        tryLabel.setTextColor(colors.textColorSecondary)
        for (i in 0 until sampleSwitcher.childCount) {
            (sampleSwitcher.getChildAt(i) as? TextView)?.setTextColor(colors.textColorPrimary)
        }
        callLabel.setTextColor(colors.textColorPrimary)
        useText.setTextColor(colors.textColorLink)
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

    private fun slide(fromY: Float, toY: Float, fromAlpha: Float, toAlpha: Float) = AnimationSet(true).apply {
        addAnimation(
            TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0f, TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, fromY, TranslateAnimation.RELATIVE_TO_SELF, toY,
            ),
        )
        addAnimation(AlphaAnimation(fromAlpha, toAlpha))
        duration = 380L
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_MIC_ASKED = "ai_voice_mic_permission_asked"

        /** 本次进程内关掉过横幅就不再出现，重启 App 后恢复。 */
        var ctaDismissed = false
        const val SAMPLE_INTERVAL_MS = 2600L
        const val CALL_LEAVE_WINDOW_MS = 4000L
        const val BRAND_PURPLE = 0xFF7B5CFF.toInt()
        const val BRAND_BLUE = 0xFF2EC5FF.toInt()
        const val BRAND_PINK = 0xFFFF6FD8.toInt()
    }
}
