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
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.KeyboardBridge
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.WindowSoftInputModeGuard
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
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private var typingStatusSender: ((Boolean) -> Unit)? = null

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
    private lateinit var inputContainer: LinearLayout
    private lateinit var inputSwitchContainer: FrameLayout
    private lateinit var btnEmoji: FrameLayout
    private lateinit var btnEmojiIcon: ImageView
    private lateinit var btnAudioRecord: FrameLayout
    private lateinit var btnAudioRecordIcon: ImageView
    private lateinit var spaceAfterAudioRecord: Space
    private lateinit var spaceAfterEmoji: Space
    private lateinit var btnSend: FrameLayout
    private lateinit var btnSendIcon: ImageView
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
        this.config = config
        this.typingStatusSender = typingStatusSender
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
    }

    private fun createBusinessViewModel(conversationID: String): MessageInputViewModel {
        val store = MessageInputStore.create(conversationID)
        val owner = context.findViewModelStoreOwner()
            ?: error("MessageInputView requires a ViewModelStoreOwner host context.")
        val viewModelKey = "${MessageInputViewModel::class.java.name}:${System.identityHashCode(this)}:$conversationID"
        return ViewModelProvider(
            owner,
            MessageInputViewModelFactory(store, conversationID, config)
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
        val emojiIcon = if (state.panel == PanelState.EMOJI_PANEL) {
            R.drawable.message_input_keyboard_icon
        } else {
            R.drawable.message_input_emoji_icon
        }
        btnEmojiIcon.setImageResource(emojiIcon)

        val audioIcon = if (state.inputMode == InputMode.VOICE) {
            R.drawable.message_input_keyboard_icon
        } else {
            R.drawable.message_input_audio_icon
        }
        btnAudioRecordIcon.setImageResource(audioIcon)
    }

    private fun updateSendButtonVisibility(state: InputUiState) {
        val hasText = ::textController.isInitialized && textController.inputText.isNotEmpty()
        val isTextMode = state.inputMode == InputMode.TEXT

        if (hasText && isTextMode) {
            btnMore.visibility = View.GONE
            btnSend.visibility = View.VISIBLE
        } else {
            btnSend.visibility = View.GONE
            btnMore.visibility = if (config.isShowMore) View.VISIBLE else View.GONE
        }
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
        val nextHint = if (shouldHideHint) "" else context.getString(hintRes)
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
        inputContainer = findViewById(R.id.message_input_container)
        inputSwitchContainer = findViewById(R.id.message_input_switch_container)
        btnEmoji = findViewById(R.id.message_input_btn_emoji)
        btnEmojiIcon = findViewById(R.id.message_input_btn_emoji_icon)
        btnAudioRecord = findViewById(R.id.message_input_btn_audio_record)
        btnAudioRecordIcon = findViewById(R.id.message_input_btn_audio_record_icon)
        spaceAfterAudioRecord = findViewById(R.id.message_input_space_after_audio_record)
        spaceAfterEmoji = findViewById(R.id.message_input_space_after_emoji)
        btnSend = findViewById(R.id.message_input_btn_send)
        btnSendIcon = findViewById(R.id.message_input_btn_send_icon)
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
        val actions = vm.getActions(context, config)
        panelContentController.refreshMorePanelActions(actions)
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
        lastRenderedState?.let { updateInputHintVisibility(it) }
    }

    private fun updateColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        topDivider.setBackgroundColor(colors.strokeColorPrimary)
        quoteContainer.setBackgroundColor(colors.bgColorDefault)
        quoteSummaryView.setTextColor(colors.textColorSecondary)
        quoteCloseView.setTextColor(colors.textColorSecondary)

        val inputBg = GradientDrawable().apply {
            setColor(colors.bgColorInput)
            cornerRadius = 4f * density
        }
        inputSwitchContainer.background = inputBg

        editText.setTextColor(colors.textColorPrimary)
        editText.setHintTextColor(colors.textColorTertiary)
        btnPressToTalk.setTextColor(colors.textColorPrimary)
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

        btnAudioRecord.background = null
        btnEmoji.background = null
        btnMore.background = null

        btnAudioRecordIcon.setColorFilter(colors.textColorSecondary)
        btnEmojiIcon.setColorFilter(colors.textColorSecondary)
        btnMoreIcon.setColorFilter(colors.textColorSecondary)

        btnSendIcon.setImageResource(R.drawable.message_input_send_arrow_icon)
        val sendBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.buttonColorPrimaryDefault)
        }
        btnSend.background = sendBg
        btnSendIcon.setColorFilter(colors.textColorButton)
    }

    private fun setupTextWatcher() {
        textController.setupTextWatcher()
    }

    companion object {
        private const val TAG = "MsgInput.View"
        private const val MESSAGE_LIST_SOURCE = "MessageList"
        private const val BLANK_AREA_CLICK_EVENT = "onBlankAreaClick"
        private const val QUOTE_MESSAGE_EVENT = "onQuoteMessage"
    }
}

internal fun shouldRefreshSendButtonOnTextChanged(isCoordinatorInitialized: Boolean): Boolean {
    return isCoordinatorInitialized
}
