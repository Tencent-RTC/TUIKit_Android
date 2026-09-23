package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.content.Context
import android.util.Log
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.onEvent
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationController
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputAppearance
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputButtonAppearance
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputFieldAppearance
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputFill
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.KeyboardBridge
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.WindowSoftInputModeGuard
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputCoordinator
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputMode
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputModeEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputSurfaceState
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputUiState
import io.trtc.tuikit.chat.uikit.components.messageinput.state.OverlayEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelEffect
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelHeightProvider
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelState
import io.trtc.tuikit.chat.uikit.components.messageinput.state.QuoteInfo
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.AUDIO_MIN_RECORD_TIME
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputViewModel
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputViewModelFactory
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessageInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), PanelHeightProvider {

    private var viewScope: CoroutineScope? = null
    private var viewModel: MessageInputViewModel? = null
    private var draftCollectorJob: kotlinx.coroutines.Job? = null
    private var chatbotStateJob: Job? = null
    private var typingStatusSender: ((Boolean) -> Unit)? = null
    private var chatbotController: ChatbotConversationController? = null

    private var keyboardBridge: KeyboardBridge? = null
    private val softInputModeGuard = WindowSoftInputModeGuard()
    private lateinit var coordinator: InputCoordinator

    private var lastRenderedState: InputUiState? = null
    private lateinit var panelAnimator: MessageInputPanelAnimator
    private lateinit var panelContentController: MessageInputPanelContentController
    private lateinit var textController: MessageInputTextController
    private lateinit var mentionController: MessageInputMentionController
    private val draftPolicy = MessageInputDraftPolicy()

    private var config: MessageInputConfigProtocol = ChatMessageInputConfig()
    private var isKeyboardBridgePausedForVoiceOverlay = false
    private var lastColors: ColorTokens? = null
    private var morePanelActions: List<MessageInputMenuAction> = emptyList()
    private val dismissedMorePanelRedDotIds = mutableSetOf<String>()

    private val hasBoundViews: Boolean
        get() = ::btnPressToTalk.isInitialized

    private lateinit var toolbarRow: LinearLayout
    private lateinit var topDivider: View
    private lateinit var quoteContainer: LinearLayout
    private lateinit var quoteSummaryView: TextView
    private lateinit var quoteCloseView: TextView
    private lateinit var voiceTranscriptionContainer: LinearLayout
    private lateinit var voiceTranscriptionEditText: AtomicEditText
    private lateinit var voiceTranscriptionCancel: TextView
    private lateinit var voiceTranscriptionSendAudio: TextView
    private lateinit var voiceTranscriptionSendText: TextView
    private lateinit var editText: AtomicEditText
    private lateinit var btnMore: FrameLayout
    private lateinit var btnMoreIcon: ImageView
    private lateinit var btnMoreRedDot: View
    private lateinit var inputContainer: LinearLayout
    private lateinit var inputSwitchContainer: FrameLayout
    private lateinit var btnEmoji: FrameLayout
    private lateinit var btnEmojiIcon: ImageView
    private lateinit var btnAudioRecord: FrameLayout
    private lateinit var btnAudioRecordIcon: ImageView
    private lateinit var spaceAfterAudioRecord: Space
    private lateinit var spaceAfterEmoji: Space
    private lateinit var spaceAfterSend: Space
    private lateinit var btnSend: FrameLayout
    private lateinit var btnSendIcon: ImageView
    private lateinit var btnSendText: TextView
    private lateinit var btnChatbotStop: FrameLayout
    private lateinit var btnChatbotStopIcon: ImageView
    private lateinit var btnPressToTalk: AudioRecorderView
    private lateinit var panelContainer: FrameLayout
    private var voiceTranscriptionAudioPath: String? = null
    private var voiceTranscriptionAudioDurationSecond: Int = 0
    private var voiceTranscriptionOverlay: MessageInputVoiceTranscriptionOverlay? = null

    private val density: Float
        get() = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        addView(createDefaultView(context))
        bindViews()
        setupListeners()
        setupTextWatcher()
    }

    private fun createDefaultView(context: Context): View {
        return LayoutInflater.from(context).inflate(R.layout.message_input_view, this, false)
    }

    fun setup(
        conversationID: String,
        config: MessageInputConfigProtocol = ChatMessageInputConfig(),
        typingStatusSender: ((Boolean) -> Unit)? = null
    ) {
        setupInternal(
            conversationID = conversationID,
            config = config,
            typingStatusSender = typingStatusSender,
            chatbotController = null
        )
    }

    internal fun setupWithChatbot(
        conversationID: String,
        config: MessageInputConfigProtocol,
        chatbotController: ChatbotConversationController,
        typingStatusSender: ((Boolean) -> Unit)? = null
    ) {
        setupInternal(
            conversationID = conversationID,
            config = ChatbotMessageInputConfig(config),
            typingStatusSender = typingStatusSender,
            chatbotController = chatbotController
        )
    }

    private fun setupInternal(
        conversationID: String,
        config: MessageInputConfigProtocol,
        typingStatusSender: ((Boolean) -> Unit)?,
        chatbotController: ChatbotConversationController?
    ) {
        this.config = config
        this.typingStatusSender = typingStatusSender
        this.chatbotController = chatbotController
        viewModel?.let { oldVm ->
            saveDraftIfNeeded(oldVm)
        }
        draftCollectorJob?.cancel()
        draftCollectorJob = null
        draftPolicy.reset()
        textController.resetInputText()

        val newViewModel = createBusinessViewModel(conversationID)
        viewModel = newViewModel
        handleIncomingDraft(newViewModel.conversationInfo.value?.draft)

        val scope = viewScope
        if (scope != null) {
            draftCollectorJob = scope.launch {
                newViewModel.conversationInfo.collectLatest { info ->
                    handleIncomingDraft(info?.draft)
                }
            }
        }
        applyConfig()
        bindChatbotState()
    }

    private fun createBusinessViewModel(conversationID: String): MessageInputViewModel {
        val store = MessageInputStore.create(conversationID)
        val owner = context.findViewModelStoreOwner()
            ?: error("MessageInputView requires a ViewModelStoreOwner host context.")
        val viewModelKey = "${MessageInputViewModel::class.java.name}:${System.identityHashCode(this)}:$conversationID"
        return ViewModelProvider(
            owner,
            MessageInputViewModelFactory(
                messageInputStore = store,
                conversationID = conversationID,
                messageInputConfig = config
            ).setTextSendCallbacks(
                canSend = { chatbotController?.isActive != true },
                onSent = { chatbotController?.onTextMessageSent() }
            )
        ).get(viewModelKey, MessageInputViewModel::class.java)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        softInputModeGuard.apply(context)
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        startRuntime()
    }

    private fun startRuntime() {
        val bridge = KeyboardBridge.create(context)
        keyboardBridge = bridge

        coordinator = InputCoordinator(bridge, this).also { it.density = density }
        bridge.listener = coordinator

        bridge.attach(this)

        viewScope?.launch {
            coordinator.state.collectLatest { state ->
                renderState(state)
            }
        }

        viewScope?.launch {
            coordinator.effects.collect { effect ->
                handleEffect(effect)
            }
        }

        viewScope?.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                updateColors(it.currentTheme.tokens.color)
            }
        }

        val currentVm = viewModel
        val scope = viewScope
        if (currentVm != null && scope != null && draftCollectorJob == null) {
            draftCollectorJob = scope.launch {
                currentVm.conversationInfo.collectLatest { info ->
                    handleIncomingDraft(info?.draft)
                }
            }
        }

        viewScope?.onEvent<Map<*, *>> { event ->
            if (mentionController.handleMessageListEvent(event)) return@onEvent
            val source = event["source"] as? String
            val eventType = event["event"] as? String
            if (source == MESSAGE_LIST_SOURCE && eventType == BLANK_AREA_CLICK_EVENT) {
                coordinator.dispatch(PanelEvent.RequestCollapse)
            }
            if (source == MESSAGE_LIST_SOURCE && eventType == QUOTE_MESSAGE_EVENT) {
                handleQuoteMessageEvent(event)
            }
        }

        applyConfig()
        bindChatbotState()
    }

    private fun bindChatbotState() {
        chatbotStateJob?.cancel()
        chatbotStateJob = null
        val controller = chatbotController ?: run {
            if (::coordinator.isInitialized) {
                updateSendButtonVisibility(coordinator.state.value)
            }
            return
        }
        val scope = viewScope ?: return
        chatbotStateJob = scope.launch {
            controller.state.collectLatest {
                if (::coordinator.isInitialized) {
                    updateSendButtonVisibility(coordinator.state.value)
                }
            }
        }
    }

    private fun handleIncomingDraft(draft: String?) {
        if (draftPolicy.shouldApplyIncomingDraft(textController.inputText, draft)) {
            textController.applyDraftIfEmpty(draft.orEmpty())
        }
    }

    private fun handleTextSubmitted() {
        draftPolicy.markDraftClearPending()
        viewModel?.setDraft(null)
    }

    private fun saveDraftIfNeeded(targetViewModel: MessageInputViewModel) {
        if (draftPolicy.shouldSaveDraft(textController.inputText, textController.hasUserEditedText)) {
            targetViewModel.setDraft(textController.inputText)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRuntime(saveDraft = true)
        softInputModeGuard.restore()
    }

    private fun handleQuoteMessageEvent(event: Map<*, *>) {
        val quoteInfo = MessageInputQuoteEventPolicy.resolveQuoteInfo(
            event = event,
            currentConversationID = viewModel?.conversationID
        ) ?: return

        coordinator.dispatch(OverlayEvent.SetQuote(quoteInfo))
        coordinator.dispatch(InputModeEvent.SwitchToText)
        MessageInputListenerBinder.postInputInteractEvent()
    }

    private fun stopRuntime(saveDraft: Boolean) {
        if (hasBoundViews) {
            btnPressToTalk.cancelRecording()
        }
        voiceTranscriptionOverlay?.dismiss()
        clearVoiceTranscriptionOverlayState()
        if (saveDraft) {
            viewModel?.let { saveDraftIfNeeded(it) }
        }
        detachKeyboardBridge()
        if (::panelAnimator.isInitialized) {
            panelAnimator.cancel()
        }
        if (::panelContentController.isInitialized) {
            panelContentController.cancelAnimations()
        }
        draftCollectorJob?.cancel()
        draftCollectorJob = null
        chatbotStateJob?.cancel()
        chatbotStateJob = null
        viewScope?.cancel()
        viewScope = null
    }

    private fun detachKeyboardBridge() {
        keyboardBridge?.listener = null
        keyboardBridge?.detach(this)
        keyboardBridge = null
        isKeyboardBridgePausedForVoiceOverlay = false
    }


    override fun getPreferredHeight(panel: PanelState): Int {
        return 0
    }


    private fun renderState(state: InputUiState) {
        val prev = lastRenderedState
        lastRenderedState = state
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(
                TAG,
                "renderState: panel=${prev?.panel}→${state.panel} " +
                        "mode=${prev?.inputMode}→${state.inputMode} " +
                        "kbH=${prev?.keyboardHeight}→${state.keyboardHeight} " +
                        "panelTarget=${prev?.panelTargetHeight}→${state.panelTargetHeight} " +
                        "currentAnim=${panelAnimator.currentAnimatedPanelHeight}"
            )
        }

        if (prev?.inputMode != state.inputMode) {
            renderInputMode(state.inputMode)
        }

        panelAnimator.drivePanelHeightAnimation(
            target = state.panelTargetHeight,
            keyboardHeight = state.keyboardHeight,
            isKeyboardAnimationSupported = keyboardBridge?.isAnimationSupported() == true,
            keyboardTargetHeight = keyboardBridge?.getKeyboardHeight() ?: 0
        )
        panelAnimator.syncContainerHeight(state.keyboardHeight)

        updateButtonIcons(state)
        updateSendButtonVisibility(state)
        updateInputHintVisibility(state)
        if (prev?.overlay?.quoteMessage != state.overlay.quoteMessage) {
            renderQuotePreview(state.overlay.quoteMessage)
        }
    }

    private fun renderQuotePreview(quoteInfo: QuoteInfo?) {
        if (quoteInfo == null) {
            quoteContainer.visibility = View.GONE
            quoteSummaryView.setTag(R.id.emoji_span_bind_token_tag, null)
            quoteSummaryView.text = ""
            return
        }
        quoteContainer.visibility = View.VISIBLE
        val rawText = buildQuoteSummaryText(quoteInfo)
        val bindToken = "quote|${quoteInfo.messageId}|${quoteSummaryView.textSize}"
        quoteSummaryView.setTag(R.id.emoji_span_bind_token_tag, bindToken)
        quoteSummaryView.text = EmojiSpanHelper.replaceEmojiKeysWithNames(rawText)
        EmojiSpanHelper.applyEmojiSpans(context, rawText, quoteSummaryView.textSize, quoteSummaryView) { spanned ->
            if (quoteSummaryView.getTag(R.id.emoji_span_bind_token_tag) == bindToken) {
                quoteSummaryView.text = spanned
            }
        }
    }

    private fun buildQuoteSummaryText(quoteInfo: QuoteInfo): String {
        val senderName = quoteInfo.senderName.takeIf { it.isNotBlank() }
        val summary = quoteInfo.summary.takeIf { it.isNotBlank() }.orEmpty()
        return when {
            senderName == null -> summary
            summary.isBlank() -> senderName
            else -> "$senderName：$summary"
        }
    }

    private fun handleEffect(effect: PanelEffect) {
        val current = coordinator.state.value
        val inPanel = current.surface == InputSurfaceState.PANEL
        Log.d(TAG, "handleEffect: $effect | currentSurface=${current.surface} currentPanel=${current.panel}")

        when (effect) {
            PanelEffect.ShowKeyboard -> {
                if (current.surface == InputSurfaceState.KEYBOARD) {
                    keyboardBridge?.showKeyboard(editText)
                } else {
                    Log.d(TAG, "  → skip ShowKeyboard: current=${current.surface}")
                }
            }

            PanelEffect.HideKeyboard -> {
                if (current.surface != InputSurfaceState.KEYBOARD) {
                    keyboardBridge?.hideKeyboard()
                } else {
                    Log.d(TAG, "  → skip HideKeyboard: current=KEYBOARD")
                }
            }

            PanelEffect.HideKeyboardKeepFocus -> {
                if (current.surface != InputSurfaceState.KEYBOARD) {
                    keyboardBridge?.hideKeyboardKeepFocus(editText)
                } else {
                    Log.d(TAG, "  → skip HideKeyboardKeepFocus: current=KEYBOARD")
                }
            }

            PanelEffect.RequestEditTextFocus -> {
                editText.requestFocus()
            }

            PanelEffect.ClearEditTextFocus -> {
                if (current.surface == InputSurfaceState.PANEL && current.panel == PanelState.MORE_PANEL) {
                    editText.clearFocus()
                } else {
                    Log.d(TAG, "  → skip ClearEditTextFocus: current=${current.surface}/${current.panel}")
                }
            }

            is PanelEffect.SetPanelContent -> {
                if (current.surface == InputSurfaceState.PANEL && current.panel == effect.panel) {
                    if (effect.panel == PanelState.MORE_PANEL) {
                        refreshMorePanelActions()
                    }
                    showPanelContent(effect.panel, crossfade = effect.crossfade)
                } else {
                    Log.d(TAG, "  → skip SetPanelContent(${effect.panel}): current=${current.panel}")
                }
            }

            PanelEffect.HidePanelContent -> {
                if (!inPanel) {
                    hidePanelContent()
                } else {
                    Log.d(TAG, "  → skip HidePanelContent: current=${current.panel} is still a panel")
                }
            }
        }
    }

    private fun hidePanelContent() {
        panelContentController.hidePanelContent()
    }

    private fun renderInputMode(mode: InputMode) {
        when (mode) {
            InputMode.TEXT -> {
                inputContainer.visibility = View.VISIBLE
                btnPressToTalk.visibility = View.GONE
                btnPressToTalk.resetState()
            }

            InputMode.VOICE -> {
                inputContainer.visibility = View.GONE
                btnPressToTalk.visibility = View.VISIBLE
                btnPressToTalk.resetState()
            }

            else -> Unit
        }
    }

    private fun showPanelContent(panel: PanelState, crossfade: Boolean = false) {
        panelContentController.showPanelContent(panel, crossfade)
    }

    private fun updateButtonIcons(state: InputUiState) {
        val appearance = config.appearance

        val isEmojiPanelOpen = state.panel == PanelState.EMOJI_PANEL
        btnEmojiIcon.setImageResource(
            MessageInputAppearanceApplier.resolveIcon(
                appearance = appearance?.emojiButton,
                isActive = isEmojiPanelOpen,
                defaultIcon = if (isEmojiPanelOpen) {
                    R.drawable.message_input_keyboard_icon
                } else {
                    R.drawable.message_input_emoji_icon
                }
            )
        )

        val isVoiceMode = state.inputMode == InputMode.VOICE
        btnAudioRecordIcon.setImageResource(
            MessageInputAppearanceApplier.resolveIcon(
                appearance = appearance?.audioRecordButton,
                isActive = isVoiceMode,
                defaultIcon = if (isVoiceMode) {
                    R.drawable.message_input_keyboard_icon
                } else {
                    R.drawable.message_input_audio_icon
                }
            )
        )

        btnMoreIcon.setImageResource(
            MessageInputAppearanceApplier.resolveIcon(
                appearance = appearance?.moreButton,
                isActive = state.panel == PanelState.MORE_PANEL,
                defaultIcon = R.drawable.message_input_more_icon
            )
        )
    }

    private fun updateSendButtonVisibility(state: InputUiState) {
        val hasText = ::textController.isInitialized && textController.inputText.isNotEmpty()
        val isTextMode = state.inputMode == InputMode.TEXT
        val visibility = MessageInputActionVisibilityPolicy.resolve(
            hasText = hasText,
            isTextMode = isTextMode,
            isShowMore = config.isShowMore,
            isChatbotActive = chatbotController?.isActive == true,
            keepSendVisible = config.keepSendButtonVisible
        )
        btnMore.visibility = if (visibility.showMore) View.VISIBLE else View.GONE
        btnSend.visibility = if (visibility.showSend) View.VISIBLE else View.GONE
        btnChatbotStop.visibility = if (visibility.showStop) View.VISIBLE else View.GONE
        spaceAfterSend.visibility = if (visibility.showSend && visibility.showMore) {
            View.VISIBLE
        } else {
            View.GONE
        }
        updateMoreButtonRedDotVisibility()
    }

    private fun updateMoreButtonRedDotVisibility() {
        if (!hasBoundViews) return
        val shouldShow = btnMore.visibility == View.VISIBLE &&
            morePanelActions.any { it.showRedDot && it.ID !in dismissedMorePanelRedDotIds }
        btnMoreRedDot.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun updateInputHintVisibility(state: InputUiState) {
        val shouldHideHint = MessageInputTextAffordancePolicy.shouldHideInputHint(
            surface = state.surface,
            keyboardHeight = state.keyboardHeight
        )
        val hintRes = if (config.enableLongPressToTalk) {
            R.string.message_input_edit_text_hint
        } else {
            R.string.message_input_edit_text_hint_text_only
        }
        val hint = config.appearance?.field?.hintText?.toString() ?: context.getString(hintRes)
        val nextHint = if (shouldHideHint) "" else hint
        if (editText.hint?.toString().orEmpty() != nextHint) {
            editText.hint = nextHint
            editText.requestLayout()
            editText.invalidate()
        }
    }


    private fun bindViews() {
        val previousAnimatedPanelHeight = if (::panelAnimator.isInitialized) {
            panelAnimator.currentAnimatedPanelHeight
        } else {
            0
        }
        val previousInputText = if (::textController.isInitialized) {
            textController.inputText
        } else {
            ""
        }
        toolbarRow = findViewById(R.id.message_input_toolbar_row)
        topDivider = findViewById(R.id.message_input_top_divider)
        quoteContainer = findViewById(R.id.message_input_quote_container)
        quoteSummaryView = findViewById(R.id.message_input_quote_summary)
        quoteCloseView = findViewById(R.id.message_input_quote_close)
        voiceTranscriptionContainer = findViewById(R.id.message_input_voice_transcription_container)
        voiceTranscriptionEditText = findViewById(R.id.message_input_voice_transcription_edit_text)
        voiceTranscriptionCancel = findViewById(R.id.message_input_voice_transcription_cancel)
        voiceTranscriptionSendAudio = findViewById(R.id.message_input_voice_transcription_send_audio)
        voiceTranscriptionSendText = findViewById(R.id.message_input_voice_transcription_send_text)
        editText = findViewById(R.id.message_input_edit_text)
        btnMore = findViewById(R.id.message_input_btn_more)
        btnMoreIcon = findViewById(R.id.message_input_btn_more_icon)
        btnMoreRedDot = findViewById(R.id.message_input_btn_more_red_dot)
        inputContainer = findViewById(R.id.message_input_container)
        inputSwitchContainer = findViewById(R.id.message_input_switch_container)
        btnEmoji = findViewById(R.id.message_input_btn_emoji)
        btnEmojiIcon = findViewById(R.id.message_input_btn_emoji_icon)
        btnAudioRecord = findViewById(R.id.message_input_btn_audio_record)
        btnAudioRecordIcon = findViewById(R.id.message_input_btn_audio_record_icon)
        spaceAfterAudioRecord = findViewById(R.id.message_input_space_after_audio_record)
        spaceAfterEmoji = findViewById(R.id.message_input_space_after_emoji)
        spaceAfterSend = findViewById(R.id.message_input_space_after_send)
        btnSend = findViewById(R.id.message_input_btn_send)
        btnSendIcon = findViewById(R.id.message_input_btn_send_icon)
        btnSendText = findViewById(R.id.message_input_btn_send_text)
        btnChatbotStop = findViewById(R.id.message_input_btn_chatbot_stop)
        btnChatbotStopIcon = findViewById(R.id.message_input_btn_chatbot_stop_icon)
        btnPressToTalk = findViewById<AudioRecorderView>(R.id.message_input_btn_press_to_talk).apply {
            config = AudioRecorderViewConfig(
                minDurationMs = AUDIO_MIN_RECORD_TIME,
                maxDurationMs = this@MessageInputView.config.audioMaxRecordDurationMs
                    .coerceAtLeast(AUDIO_MIN_RECORD_TIME)
            )
        }
        panelContainer = findViewById(R.id.message_input_panel_container)
        textController = MessageInputTextController(
            context = context,
            editText = editText,
            configProvider = { config },
            viewModelProvider = { viewModel },
            isCoordinatorInitialized = { ::coordinator.isInitialized },
            currentState = { coordinator.state.value },
            updateSendButtonVisibility = { state -> updateSendButtonVisibility(state) },
            clearQuote = { coordinator.dispatch(OverlayEvent.ClearQuote) },
            onMentionTrigger = { mentionController.showMentionMemberDialog() },
            onTypingContentChanged = { isTyping -> typingStatusSender?.invoke(isTyping) },
            initialInputText = previousInputText
        )
        mentionController = MessageInputMentionController(
            context = context,
            textController = textController,
            coordinatorProvider = { coordinator },
            viewModelProvider = { viewModel }
        )
        panelAnimator = MessageInputPanelAnimator(
            panelContainer = panelContainer,
            lastRenderedKeyboardHeight = { lastRenderedState?.keyboardHeight },
            initialAnimatedPanelHeight = previousAnimatedPanelHeight
        )
        panelContentController = MessageInputPanelContentController.attach(
            context = context,
            panelContainer = panelContainer,
            editText = editText,
            currentPanel = { lastRenderedState?.panel },
            onSendClick = { textController.sendCurrentText(onSubmitted = ::handleTextSubmitted) },
            onCustomEmojiClick = { group, emoji ->
                val faceIndex = EmojiManager.emojiGroupList.indexOfFirst { it.id == group.id }
                textController.sendFaceMessage(faceIndex.coerceAtLeast(0), emoji.key)
            },
            onMoreActionRedDotDismissed = { actionID ->
                dismissedMorePanelRedDotIds.add(actionID)
                updateMoreButtonRedDotVisibility()
            }
        )
    }

    private fun setupListeners() {
        MessageInputListenerBinder.bind(
            btnMore = btnMore,
            btnSend = btnSend,
            btnAudioRecord = btnAudioRecord,
            btnEmoji = btnEmoji,
            btnPressToTalk = btnPressToTalk,
            editText = editText,
            inputContainer = inputContainer,
            coordinatorProvider = { coordinator },
            viewModelProvider = { viewModel },
            isLongPressToTalkEnabled = { config.enableLongPressToTalk },
            sendCurrentText = { textController.sendCurrentText(onSubmitted = ::handleTextSubmitted) },
            showVoiceTranscriptionDraft = { audioPath, duration, text ->
                showVoiceTranscriptionDraft(audioPath, duration, text)
            },
            logDebug = { message -> Log.d(TAG, message) }
        )
        btnChatbotStop.setOnClickListener {
            chatbotController?.sendInterrupt(
                object : CompletionHandler {
                    override fun onSuccess() = Unit

                    override fun onFailure(code: Int, desc: String) {
                        AtomicToast.show(
                            context,
                            desc.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.message_input_send_failed),
                            style = AtomicToast.Style.ERROR
                        )
                    }
                }
            )
        }
        setupVoiceTranscriptionDraftListeners()
        setupQuotePreviewListeners()
    }

    private fun setupQuotePreviewListeners() {
        quoteCloseView.isClickable = true
        quoteCloseView.isFocusable = true
        quoteCloseView.setOnClickListener {
            if (::coordinator.isInitialized) {
                coordinator.dispatch(OverlayEvent.ClearQuote)
            }
        }
    }

    private fun setupVoiceTranscriptionDraftListeners() {
        voiceTranscriptionCancel.setOnClickListener {
            hideVoiceTranscriptionDraft()
        }
        voiceTranscriptionSendAudio.setOnClickListener {
            val audioPath = voiceTranscriptionAudioPath ?: return@setOnClickListener
            val duration = voiceTranscriptionAudioDurationSecond.coerceAtLeast(1)
            viewModel?.sendAudioMessage(audioPath, duration)
            hideVoiceTranscriptionDraft()
        }
        voiceTranscriptionSendText.setOnClickListener {
            val text = voiceTranscriptionEditText.text?.toString().orEmpty()
            if (text.isBlank()) {
                return@setOnClickListener
            }
            viewModel?.sendTextMessage(
                context = context,
                text = text,
                mentionList = emptyList(),
                onSuccess = { hideVoiceTranscriptionDraft() }
            )
        }
    }

    private fun showVoiceTranscriptionDraft(audioPath: String, audioDurationSecond: Int, text: String?) {
        if (text != null && voiceTranscriptionAudioPath != audioPath) {
            return
        }
        voiceTranscriptionOverlay?.let { overlay ->
            if (voiceTranscriptionAudioPath == audioPath) {
                overlay.updateResult(text)
            }
            return
        }
        if (text != null) {
            return
        }
        voiceTranscriptionAudioPath = audioPath
        voiceTranscriptionAudioDurationSecond = audioDurationSecond
        pauseKeyboardBridgeForVoiceTranscriptionOverlay()
        voiceTranscriptionOverlay = MessageInputVoiceTranscriptionOverlay(
            anchorView = this,
            audioPath = audioPath,
            audioDurationSecond = audioDurationSecond,
            text = text,
            onCancel = {
                clearVoiceTranscriptionOverlayState()
            },
            onSendAudio = { path, duration ->
                viewModel?.sendAudioMessage(path, duration)
                clearVoiceTranscriptionOverlayState()
            },
            onSendText = { currentText ->
                viewModel?.sendTextMessage(
                    context = context,
                    text = currentText,
                    mentionList = emptyList(),
                    onSuccess = { clearVoiceTranscriptionOverlayState() }
                )
            },
            onDismissed = {
                resumeKeyboardBridgeAfterVoiceTranscriptionOverlay()
                clearVoiceTranscriptionOverlayState()
            },
            onTranslate = { sourceText, targetLanguage, onSuccess, onFailure ->
                val vm = viewModel
                if (vm != null) {
                    vm.translateRecordText(sourceText, targetLanguage, onSuccess, onFailure)
                } else {
                    onFailure()
                }
            },
            onStartSpeak = { speakText, onStart, onComplete, onError ->
                val vm = viewModel
                if (vm != null) {
                    vm.startRecordTranslationSpeak(context, speakText, onStart, onComplete, onError)
                } else {
                    onError()
                }
            },
            onStopSpeak = {
                viewModel?.stopRecordTranslationSpeak()
            },
        ).also { it.show() }
    }

    private fun pauseKeyboardBridgeForVoiceTranscriptionOverlay() {
        if (isKeyboardBridgePausedForVoiceOverlay) {
            return
        }
        val bridge = keyboardBridge ?: return
        bridge.listener = null
        bridge.detach(this)
        isKeyboardBridgePausedForVoiceOverlay = true
    }

    private fun resumeKeyboardBridgeAfterVoiceTranscriptionOverlay() {
        if (!isKeyboardBridgePausedForVoiceOverlay) {
            return
        }
        val bridge = keyboardBridge
        if (bridge == null || !isAttachedToWindow || !::coordinator.isInitialized) {
            isKeyboardBridgePausedForVoiceOverlay = false
            return
        }
        bridge.listener = coordinator
        bridge.attach(this)
        isKeyboardBridgePausedForVoiceOverlay = false
    }

    private fun clearVoiceTranscriptionOverlayState() {
        voiceTranscriptionOverlay = null
        voiceTranscriptionAudioPath = null
        voiceTranscriptionAudioDurationSecond = 0
    }

    private fun hideVoiceTranscriptionDraft() {
        voiceTranscriptionOverlay?.dismiss()
        clearVoiceTranscriptionOverlayState()
        voiceTranscriptionEditText.setText("")
        voiceTranscriptionContainer.visibility = View.GONE
        toolbarRow.visibility = View.VISIBLE
        if (::coordinator.isInitialized) {
            coordinator.dispatch(PanelEvent.RequestCollapse)
        }
    }

    private fun refreshMorePanelActions() {
        val vm = viewModel ?: return
        morePanelActions = vm.getActions(context, config)
        dismissedMorePanelRedDotIds.clear()
        panelContentController.refreshMorePanelActions(morePanelActions, dismissedMorePanelRedDotIds)
        updateMoreButtonRedDotVisibility()
    }

    private fun applyConfig() {
        if (!hasBoundViews) return
        btnMore.visibility = if (config.isShowMore) View.VISIBLE else View.GONE
        btnAudioRecord.visibility = if (config.isShowAudioRecorder) View.VISIBLE else View.GONE
        btnEmoji.visibility = if (config.isShowEmoji) View.VISIBLE else View.GONE
        spaceAfterAudioRecord.visibility = btnAudioRecord.visibility
        spaceAfterEmoji.visibility = btnEmoji.visibility
        btnPressToTalk.config = btnPressToTalk.config.copy(
            minDurationMs = AUDIO_MIN_RECORD_TIME,
            maxDurationMs = config.audioMaxRecordDurationMs.coerceAtLeast(AUDIO_MIN_RECORD_TIME)
        )
        updateColors(
            lastColors ?: ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        )
        lastRenderedState?.let {
            updateInputHintVisibility(it)
            updateButtonIcons(it)
        }
        if (::coordinator.isInitialized) {
            updateSendButtonVisibility(coordinator.state.value)
        } else if (chatbotController?.isActive == true) {
            btnMore.visibility = View.GONE
            btnSend.visibility = View.GONE
            btnChatbotStop.visibility = View.VISIBLE
        } else {
            btnChatbotStop.visibility = View.GONE
        }
        refreshMorePanelActions()
    }

    private fun updateColors(colors: ColorTokens) {
        lastColors = colors
        val appearance = config.appearance

        background = MessageInputAppearanceApplier.fillDrawable(
            fill = appearance?.fill ?: MessageInputFill.Color(colors.bgColorOperate),
            cornerRadiusDp = null,
            density = density
        )
        topDivider.setBackgroundColor(colors.strokeColorSecondary)
        topDivider.visibility = if (appearance?.showTopDivider == false) {
            View.GONE
        } else {
            View.VISIBLE
        }
        quoteContainer.setBackgroundColor(colors.bgColorDefault)
        quoteSummaryView.setTextColor(colors.textColorSecondary)
        quoteCloseView.setTextColor(colors.textColorSecondary)
        btnMoreRedDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.textColorError)
        }

        applyInputFieldAppearance(appearance?.field, colors)
        applyToolbarButtonAppearance(appearance, colors)
        panelContentController.applyMorePanelAppearance(appearance?.morePanel)

        voiceTranscriptionEditText.setTextColor(colors.textColorButton)
        voiceTranscriptionEditText.setHintTextColor(colors.textColorButton)
        voiceTranscriptionContainer.setBackgroundColor(colors.bgColorOperate)
        voiceTranscriptionEditText.background = GradientDrawable().apply {
            setColor(colors.buttonColorPrimaryDefault)
            cornerRadius = 12f * density
        }
        val secondaryButtonBg = GradientDrawable().apply {
            setColor(colors.buttonColorSecondaryDefault)
            cornerRadius = 18f * density
        }
        voiceTranscriptionCancel.background = secondaryButtonBg.constantState?.newDrawable()?.mutate()
        voiceTranscriptionSendAudio.background = secondaryButtonBg.constantState?.newDrawable()?.mutate()
        voiceTranscriptionSendText.background = GradientDrawable().apply {
            setColor(colors.buttonColorPrimaryDefault)
            cornerRadius = 18f * density
        }
        voiceTranscriptionCancel.setTextColor(colors.textColorPrimary)
        voiceTranscriptionSendAudio.setTextColor(colors.textColorPrimary)
        voiceTranscriptionSendText.setTextColor(colors.textColorButton)
    }

    private fun applyInputFieldAppearance(
        field: MessageInputFieldAppearance?,
        colors: ColorTokens
    ) {
        inputSwitchContainer.background = MessageInputAppearanceApplier.fillDrawable(
            fill = field?.fill ?: MessageInputFill.Color(colors.bgColorInput),
            cornerRadiusDp = field?.cornerRadiusDp ?: DEFAULT_FIELD_CORNER_RADIUS_DP,
            density = density
        )
        field?.minHeightDp?.let { minHeightDp ->
            val minHeightPx = (minHeightDp * density).toInt()
            inputSwitchContainer.minimumHeight = minHeightPx
            inputContainer.minimumHeight = minHeightPx
            btnPressToTalk.layoutParams?.let { params ->
                params.height = minHeightPx
                btnPressToTalk.layoutParams = params
            }
        }
        field?.horizontalPaddingDp?.let { paddingDp ->
            val paddingPx = (paddingDp * density).toInt()
            inputContainer.setPaddingRelative(
                paddingPx,
                inputContainer.paddingTop,
                paddingPx,
                inputContainer.paddingBottom
            )
        }
        val textColor = field?.textColor ?: colors.textColorPrimary
        editText.setTextColor(textColor)
        editText.setHintTextColor(field?.hintColor ?: colors.textColorTertiary)
        btnPressToTalk.setTextColor(textColor)
    }

    private fun applyToolbarButtonAppearance(
        appearance: MessageInputAppearance?,
        colors: ColorTokens
    ) {
        val secondaryDefaults = MessageInputAppearanceApplier.ActionButtonDefaults(
            sizeDp = DEFAULT_TOOLBAR_BUTTON_SIZE_DP,
            iconTint = colors.textColorSecondary
        )
        MessageInputAppearanceApplier.applyActionButton(
            container = btnAudioRecord,
            iconView = btnAudioRecordIcon,
            labelView = null,
            appearance = appearance?.audioRecordButton,
            defaults = secondaryDefaults
        )
        MessageInputAppearanceApplier.applyActionButton(
            container = btnEmoji,
            iconView = btnEmojiIcon,
            labelView = null,
            appearance = appearance?.emojiButton,
            defaults = secondaryDefaults
        )
        MessageInputAppearanceApplier.applyActionButton(
            container = btnMore,
            iconView = btnMoreIcon,
            labelView = null,
            appearance = appearance?.moreButton,
            defaults = secondaryDefaults
        )

        val primaryDefaults = MessageInputAppearanceApplier.ActionButtonDefaults(
            sizeDp = DEFAULT_TOOLBAR_BUTTON_SIZE_DP,
            iconTint = colors.textColorButton,
            fill = MessageInputFill.Color(colors.buttonColorPrimaryDefault),
            cornerRadiusDp = MessageInputButtonAppearance.CORNER_RADIUS_PILL
        )
        btnSendIcon.setImageResource(
            MessageInputAppearanceApplier.resolveIcon(
                appearance = appearance?.sendButton,
                isActive = false,
                defaultIcon = R.drawable.message_input_send_arrow_icon
            )
        )
        MessageInputAppearanceApplier.applyActionButton(
            container = btnSend,
            iconView = btnSendIcon,
            labelView = btnSendText,
            appearance = appearance?.sendButton,
            defaults = primaryDefaults
        )
        btnChatbotStopIcon.setImageResource(R.drawable.message_input_chatbot_stop_icon)
        MessageInputAppearanceApplier.applyActionButton(
            container = btnChatbotStop,
            iconView = btnChatbotStopIcon,
            labelView = null,
            appearance = null,
            defaults = primaryDefaults
        )
    }

    private fun setupTextWatcher() {
        textController.setupTextWatcher()
    }

    companion object {
        private const val TAG = "MsgInput.View"
        private const val MESSAGE_LIST_SOURCE = "MessageList"
        private const val BLANK_AREA_CLICK_EVENT = "onBlankAreaClick"
        private const val QUOTE_MESSAGE_EVENT = "onQuoteMessage"
        private const val DEFAULT_FIELD_CORNER_RADIUS_DP = 4
        private const val DEFAULT_TOOLBAR_BUTTON_SIZE_DP = 28
    }
}

internal fun shouldRefreshSendButtonOnTextChanged(isCoordinatorInitialized: Boolean): Boolean {
    return isCoordinatorInitialized
}
