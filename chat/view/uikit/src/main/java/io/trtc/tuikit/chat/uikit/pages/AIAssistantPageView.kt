package io.trtc.tuikit.chat.uikit.pages

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantAgent
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantAgentLoader
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantControlMessages
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantHandoffStatusLoader
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantUsageReporter
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIConstants
import io.trtc.tuikit.chat.uikit.components.aiassistant.AgentWelcomeInserter
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.SystemOpusDecoder
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.AICallActivity
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.AICallUiOptions
import io.trtc.tuikit.chat.uikit.components.messageinput.asr.RealtimeAsrAudioCapture
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.CallMessageClickContext
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 体验 Demo 用来引导第一次打开 AI 首页的可选配置。不传时页面保持原样。
 *
 * 红点是否还在由宿主自己记在本地。[initialInputText] 只在发送红点还在、
 * 输入框为空且没有会话草稿时填入。
 */
class AIAssistantTrialPrompt(
    val initialInputText: String? = null,
    val shouldShowSendRedDot: () -> Boolean = { false },
    val onSendRedDotClick: () -> Unit = {},
    val shouldShowCallRedDot: () -> Boolean = { false },
    val onCallRedDotClick: () -> Unit = {},
    /** 标题栏左侧的返回入口文案，和 [onBack] 同时传才显示。 */
    val backLabel: String? = null,
    val onBack: (() -> Unit)? = null,
    /** 输入框上方可点击的推荐问题，点一下直接发送。 */
    val quickQuestions: List<String> = emptyList(),
    /** 按当前助手提供推荐问题，切换助手时刷新；返回 null 时使用 [quickQuestions]。 */
    val quickQuestionsForAgent: ((AIAssistantAgent) -> List<String>?)? = null,
    /** 插在标题栏下方的横幅，可见性由宿主控制。 */
    val headerBanner: View? = null,
    /** 通话页的外观与引导选项，不传时通话页保持原样。 */
    val callUiOptions: AICallUiOptions? = null,
    /** 按当前助手提供通话页选项（如示例话术），返回 null 时使用 [callUiOptions]。 */
    val callUiOptionsForAgent: ((AIAssistantAgent) -> AICallUiOptions?)? = null,
)

/**
 * 可复用的 AI 助手页面。助手账号由宿主通过 [AIAssistantAgentLoader] 异步提供。
 *
 * 消息列表 / 输入框走 UIKit 的 [ChatPageView]。右上角通话进入当前选中客服的会话。
 */
class AIAssistantPageView(context: Context) : FrameLayout(context) {

    private val themeStore = ThemeStore.shared(context)
    private val usageReporter = AIAssistantUsageReporter()
    /** 当前设备能否发起 AI 语音通话（实时识别 + Opus 解码都可用）。 */
    val isVoiceCallSupported =
        RealtimeAsrAudioCapture.isSupported() && SystemOpusDecoder.isSupported()
    private var scope: CoroutineScope? = null
    private var agentLoader: AIAssistantAgentLoader? = null
    private var handoffStatusLoader: AIAssistantHandoffStatusLoader? = null
    private var agents: List<AIAssistantAgent> = emptyList()
    private var selectedAgent: AIAssistantAgent? = null
    private var boundConversationID: String? = null
    private var fetchGeneration = 0
    private var callCheckGeneration = 0
    private var isCheckingHandoffStatus = false
    private var trialPrompt: AIAssistantTrialPrompt? = null
    private var quickQuestionRow: LinearLayout? = null

    private val root: LinearLayout
    private val header: LinearLayout
    private val titleRow: LinearLayout
    private val title: TextView
    private val backButton: TextView
    private val titleDropdown: ImageView
    private val btnCall: ImageView
    private val btnCallButton: FrameLayout
    private val callRedDot: View
    private val divider: View
    private val emptyView: TextView
    private val chatPageView: ChatPageView
    private val transferButton: TextView

    init {
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(root)

        header = LayoutInflater.from(context)
            .inflate(R.layout.uikit_ai_view_header, root, false) as LinearLayout
        titleRow = header.findViewById(R.id.uikit_ai_title_row)
        title = header.findViewById(R.id.uikit_ai_title)
        backButton = header.findViewById(R.id.uikit_ai_back)
        titleDropdown = header.findViewById(R.id.uikit_ai_title_dropdown)
        btnCall = header.findViewById(R.id.uikit_ai_call)
        btnCallButton = header.findViewById(R.id.uikit_ai_call_button)
        callRedDot = header.findViewById(R.id.uikit_ai_call_red_dot)
        divider = header.findViewById(R.id.uikit_ai_header_divider)
        title.text = context.getString(R.string.uikit_ai_page_title)
        titleRow.isClickable = false
        btnCallButton.visibility = View.GONE
        root.addView(header)

        emptyView = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(32), dp(48), dp(32), dp(48))
            text = context.getString(R.string.uikit_ai_agents_empty)
            visibility = View.GONE
        }
        root.addView(
            emptyView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        chatPageView = ChatPageView(context)
        chatPageView.visibility = View.GONE
        root.addView(
            chatPageView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        )

        transferButton = TextView(context).apply {
            text = context.getString(R.string.uikit_ai_transfer_to_human)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { sendTransferToHuman() }
        }
        chatPageView.setMessageListFooterOverlay(transferButton)
        chatPageView.setOnTextMessageSentListener(usageReporter::onMessageSent)
    }

    @JvmOverloads
    fun setup(
        agentLoader: AIAssistantAgentLoader,
        handoffStatusLoader: AIAssistantHandoffStatusLoader? = null,
        trialPrompt: AIAssistantTrialPrompt? = null,
    ) {
        this.agentLoader = agentLoader
        this.handoffStatusLoader = handoffStatusLoader
        this.trialPrompt = trialPrompt
        applyTrialPromptDecorations(trialPrompt)
        btnCallButton.setOnClickListener { onCallClicked() }
        titleRow.setOnClickListener { showAgentPicker() }
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
        if (isAttachedToWindow) {
            reloadAgents()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { s ->
            s.launch {
                themeStore.themeState.collectLatest { state ->
                    applyColors(state.currentTheme.tokens.color)
                }
            }
        }
        if (agentLoader != null) {
            reloadAgents()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fetchGeneration++
        callCheckGeneration++
        isCheckingHandoffStatus = false
        scope?.cancel()
        scope = null
    }

    /** Call when a persistent tab becomes visible again so its host-provided directory is refreshed. */
    fun onPageVisible() {
        if (isAttachedToWindow && agentLoader != null) {
            reloadAgents()
        }
    }

    /** 有可用的 AI 助手、并且设备支持时，返回 true。 */
    fun canStartVoiceCall(): Boolean = selectedAgent != null && isVoiceCallSupported

    /** 和点右上角通话按钮效果一致：会先查人工转接状态。 */
    fun startVoiceCall() {
        onCallClicked()
    }

    private fun applyTrialPromptDecorations(prompt: AIAssistantTrialPrompt?) {
        val back = prompt?.onBack
        val backLabel = prompt?.backLabel
        if (back != null && !backLabel.isNullOrEmpty()) {
            backButton.text = backLabel
            backButton.visibility = View.VISIBLE
            backButton.setOnClickListener { back() }
        } else {
            backButton.visibility = View.GONE
        }
        prompt?.headerBanner?.let { banner ->
            if (banner.parent == null) {
                root.addView(banner, root.indexOfChild(header) + 1)
            }
        }
        if (prompt?.quickQuestions.isNullOrEmpty() && prompt?.quickQuestionsForAgent == null) {
            return
        }
        (transferButton.parent as? ViewGroup)?.removeView(transferButton)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(transferButton)
        }
        quickQuestionRow = row
        setQuickQuestions(prompt?.quickQuestions.orEmpty())
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 0, dp(12), 0)
            addView(row)
            // 外层浮层是 wrap_content，这里给定宽度，横向滚动才生效
            layoutParams = LayoutParams(resources.displayMetrics.widthPixels - dp(12), LayoutParams.WRAP_CONTENT)
        }
        chatPageView.setMessageListFooterOverlay(scroller)
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    /** 用 [questions] 替换转人工按钮后面的推荐问题。 */
    private fun setQuickQuestions(questions: List<String>) {
        val row = quickQuestionRow ?: return
        for (i in row.childCount - 1 downTo 0) {
            if (row.getChildAt(i).tag == TAG_QUICK_QUESTION) row.removeViewAt(i)
        }
        questions.forEach { question ->
            row.addView(
                TextView(context).apply {
                    text = question
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    tag = TAG_QUICK_QUESTION
                    setOnClickListener { sendQuickQuestion(question) }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(8) },
            )
        }
        (row.parent as? HorizontalScrollView)?.scrollTo(0, 0)
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    private fun sendQuickQuestion(text: String) {
        val conversationID = boundConversationID ?: return
        MessageInputStore.create(conversationID).sendMessage(
            SendMessagePayload.TextSendMessagePayload(text),
            null,
            object : CompletionHandler {
                override fun onSuccess() {
                    usageReporter.onMessageSent()
                }

                override fun onFailure(code: Int, desc: String) {
                    Toast.makeText(context, R.string.uikit_ai_call_send_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun reloadAgents() {
        val loader = agentLoader ?: return
        val generation = ++fetchGeneration
        loader.load { loadedAgents ->
            post {
                if (generation != fetchGeneration) {
                    return@post
                }
                onAgentsLoaded(loadedAgents)
            }
        }
    }

    private fun onAgentsLoaded(agents: List<AIAssistantAgent>) {
        this.agents = agents
        if (agents.isEmpty()) {
            selectedAgent = null
            boundConversationID = null
            title.text = context.getString(R.string.uikit_ai_page_title)
            titleDropdown.visibility = View.GONE
            titleRow.isClickable = false
            btnCallButton.visibility = View.GONE
            updateCallRedDot()
            chatPageView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        btnCallButton.visibility = if (isVoiceCallSupported) View.VISIBLE else View.GONE
        updateCallRedDot()
        val currentId = selectedAgent?.id
        val next = agents.firstOrNull { it.id == currentId } ?: agents.first()
        bindAgent(next, agents)
    }

    private fun bindAgent(agent: AIAssistantAgent, agents: List<AIAssistantAgent>) {
        if (selectedAgent?.id != agent.id) {
            callCheckGeneration++
            isCheckingHandoffStatus = false
        }
        selectedAgent = agent
        title.text = agent.displayName
        trialPrompt?.let { prompt ->
            prompt.quickQuestionsForAgent?.let { setQuickQuestions(it(agent) ?: prompt.quickQuestions) }
        }
        val canSwitch = agents.size > 1
        titleDropdown.visibility = if (canSwitch) View.VISIBLE else View.GONE
        titleRow.isClickable = canSwitch
        if (boundConversationID == agent.conversationID && chatPageView.visibility == View.VISIBLE) {
            AgentWelcomeInserter.insertIfConversationEmpty(agent) {}
            return
        }
        AgentWelcomeInserter.insertIfConversationEmpty(agent) {
            if (selectedAgent?.id != agent.id ||
                selectedAgent?.conversationID != agent.conversationID
            ) {
                return@insertIfConversationEmpty
            }
            chatPageView.setup(
                conversationID = agent.conversationID,
                messageListConfig = ChatMessageListConfig(
                    isShowLeftAvatar = false,
                    isShowRightAvatar = false,
                ).addMessageExclusion { AIAssistantControlMessages.isControlMessage(it) }
                    // 回复可能来自人工客服，客户端分不清，等服务端下发「正在输入」再展示
                    .setEnableChatbotReplyPlaceholder(false)
                    .setCallMessageClickListener { clickContext ->
                        onCallMessageClicked(clickContext)
                        true
                    },
                messageInputConfig = ChatMessageInputConfig(
                    isShowAudioCall = false,
                    isShowVideoCall = false,
                    isShowMore = false,
                    isShowEmoji = false,
                    enableSendRecordedVoice = false,
                ).apply {
                    val prompt = trialPrompt
                    initialInputText = prompt?.initialInputText
                    shouldShowSendButtonRedDot = prompt?.shouldShowSendRedDot
                    onSendButtonRedDotClick = prompt?.onSendRedDotClick
                },
                onMultiSelectStateChanged = { isMultiSelect ->
                    chatPageView.setMessageListFooterOverlayVisible(!isMultiSelect)
                },
            )
            chatPageView.setMessageListFooterOverlayVisible(true)
            boundConversationID = agent.conversationID
            chatPageView.visibility = View.VISIBLE
        }
    }

    private fun onCallClicked() {
        if (callRedDot.visibility == View.VISIBLE) {
            callRedDot.visibility = View.GONE
            trialPrompt?.onCallRedDotClick?.invoke()
        }
        val agent = selectedAgent ?: return
        val statusLoader = handoffStatusLoader
        if (statusLoader == null) {
            startAiVoiceCall(agent)
            return
        }
        if (isCheckingHandoffStatus) {
            return
        }
        isCheckingHandoffStatus = true
        val generation = ++callCheckGeneration
        statusLoader.load { status ->
            post {
                if (generation != callCheckGeneration) {
                    return@post
                }
                isCheckingHandoffStatus = false
                if (!isAttachedToWindow || selectedAgent?.id != agent.id) {
                    return@post
                }
                when {
                    status.isSeatCallable -> {
                        AtomicCallEventPublisher.publishStartCall(
                            participantIds = listOf(status.seatImUserId.trim()),
                            mediaType = AtomicCallEventPublisher.MEDIA_TYPE_AUDIO,
                            chatGroupId = null,
                        )
                    }
                    status.isWaitingForSeat -> {
                        Toast.makeText(
                            context,
                            R.string.uikit_ai_call_handoff_waiting,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    else -> startAiVoiceCall(agent)
                }
            }
        }
    }

    private fun onCallMessageClicked(clickContext: CallMessageClickContext) {
        val agent = selectedAgent ?: return
        val statusLoader = handoffStatusLoader
        if (statusLoader == null) {
            Toast.makeText(
                context,
                R.string.uikit_ai_call_handoff_required,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (isCheckingHandoffStatus) {
            return
        }
        isCheckingHandoffStatus = true
        val generation = ++callCheckGeneration
        statusLoader.load { status ->
            post {
                if (generation != callCheckGeneration) {
                    return@post
                }
                isCheckingHandoffStatus = false
                if (!isAttachedToWindow || selectedAgent?.id != agent.id) {
                    return@post
                }
                when {
                    status.isSeatCallable -> {
                        clickContext.startCall(status.seatImUserId)
                    }
                    status.isWaitingForSeat -> {
                        Toast.makeText(
                            context,
                            R.string.uikit_ai_call_handoff_waiting,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            context,
                            R.string.uikit_ai_call_handoff_required,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun startAiVoiceCall(agent: AIAssistantAgent) {
        if (!isVoiceCallSupported) {
            return
        }
        usageReporter.onVoiceEntered()
        val options = trialPrompt?.let { it.callUiOptionsForAgent?.invoke(agent) ?: it.callUiOptions }
        AICallActivity.start(context, agent.imUserId, options)
    }

    private fun sendTransferToHuman() {
        val conversationID = boundConversationID ?: return
        MessageInputStore.create(conversationID).sendMessage(
            SendMessagePayload.TextSendMessagePayload(AIConstants.TRANSFER_TO_HUMAN_TEXT),
            null,
            object : CompletionHandler {
                override fun onSuccess() {
                    usageReporter.onMessageSent()
                }

                override fun onFailure(code: Int, desc: String) {
                    Toast.makeText(context, R.string.uikit_ai_call_send_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun showAgentPicker() {
        if (agents.size <= 1) {
            return
        }
        PopupMenuHelper(context).show(
            titleRow,
            agents.map { agent ->
                PopupMenuItem(
                    title = agent.displayName,
                    onClick = { bindAgent(agent, agents) },
                )
            },
        )
    }

    private fun applyColors(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorDefault)
        header.setBackgroundColor(colors.bgColorOperate)
        title.setTextColor(colors.textColorPrimary)
        titleDropdown.setColorFilter(colors.textColorPrimary)
        btnCall.setColorFilter(colors.textColorPrimary)
        callRedDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.textColorError)
        }
        updateCallRedDot()
        divider.setBackgroundColor(colors.strokeColorPrimary)
        emptyView.setTextColor(colors.textColorSecondary)
        transferButton.setTextColor(colors.textColorPrimary)
        transferButton.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(colors.bgColorOperate)
            setStroke(dp(1).coerceAtLeast(1), colors.strokeColorPrimary)
        }
        backButton.setTextColor(colors.textColorPrimary)
        quickQuestionRow?.let { row ->
            for (i in 0 until row.childCount) {
                val chip = row.getChildAt(i) as? TextView ?: continue
                if (chip.tag != TAG_QUICK_QUESTION) continue
                chip.setTextColor(colors.textColorLink)
                chip.background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(colors.bgColorOperate)
                    setStroke(dp(1).coerceAtLeast(1), colors.textColorLink)
                }
            }
        }
    }

    private fun updateCallRedDot() {
        val show = btnCallButton.visibility == View.VISIBLE &&
            trialPrompt?.shouldShowCallRedDot?.invoke() == true
        callRedDot.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val TAG_QUICK_QUESTION = "uikit_ai_quick_question"
    }
}
