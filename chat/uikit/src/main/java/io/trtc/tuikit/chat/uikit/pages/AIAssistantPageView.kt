package io.trtc.tuikit.chat.uikit.pages

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
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
 * 可复用的 AI 助手页面。助手账号由宿主通过 [AIAssistantAgentLoader] 异步提供。
 *
 * 消息列表 / 输入框走 UIKit 的 [ChatPageView]。右上角通话进入当前选中客服的会话。
 */
class AIAssistantPageView(context: Context) : FrameLayout(context) {

    private val themeStore = ThemeStore.shared(context)
    private val usageReporter = AIAssistantUsageReporter()
    private val isVoiceCallSupported =
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

    private val root: LinearLayout
    private val header: LinearLayout
    private val titleRow: LinearLayout
    private val title: TextView
    private val titleDropdown: ImageView
    private val btnCall: ImageView
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
        titleDropdown = header.findViewById(R.id.uikit_ai_title_dropdown)
        btnCall = header.findViewById(R.id.uikit_ai_call)
        divider = header.findViewById(R.id.uikit_ai_header_divider)
        title.text = context.getString(R.string.uikit_ai_page_title)
        titleRow.isClickable = false
        btnCall.visibility = View.GONE
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
    ) {
        this.agentLoader = agentLoader
        this.handoffStatusLoader = handoffStatusLoader
        btnCall.setOnClickListener { onCallClicked() }
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
            btnCall.visibility = View.GONE
            chatPageView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        btnCall.visibility = if (isVoiceCallSupported) View.VISIBLE else View.GONE
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
                ),
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
        AICallActivity.start(context, agent.imUserId)
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
        divider.setBackgroundColor(colors.strokeColorPrimary)
        emptyView.setTextColor(colors.textColorSecondary)
        transferButton.setTextColor(colors.textColorPrimary)
        transferButton.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(colors.bgColorOperate)
            setStroke(dp(1).coerceAtLeast(1), colors.strokeColorPrimary)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
