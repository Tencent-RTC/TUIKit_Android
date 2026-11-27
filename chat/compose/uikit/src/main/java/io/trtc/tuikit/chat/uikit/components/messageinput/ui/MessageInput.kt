package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Badge
import io.trtc.tuikit.chat.uikit.components.widgets.BadgeType
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.onEvent
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationController
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationControllers
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationPolicy
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationState
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.emojiSizeForTextSize
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiKeyToName
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiText
import io.trtc.tuikit.chat.uikit.components.emojipicker.ui.EmojiPicker
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputCoordinator
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputMode
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputModeEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputSurfaceState
import io.trtc.tuikit.chat.uikit.components.messageinput.state.OverlayEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelEffect
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelState
import io.trtc.tuikit.chat.uikit.components.messageinput.state.QuoteInfo
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.rememberKeyboardBridge
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.WindowSoftInputModeGuard
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.AUDIO_MIN_RECORD_TIME
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputViewModel
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputViewModelFactory
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.isGroupConversation
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerDialog
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.max

@Composable
fun MessageInput(
    conversationID: String,
    modifier: Modifier = Modifier,
    config: MessageInputConfigProtocol = ChatMessageInputConfig(),
    messageInputViewModelFactory: MessageInputViewModelFactory =
        MessageInputViewModelFactory(
            messageInputStore = MessageInputStore.create(conversationID),
            conversationID = conversationID,
            messageInputConfig = config
        ),
    typingStatusSender: ((Boolean) -> Unit)? = null,
) {
    val isChatbotConversation = remember(conversationID) {
        ChatbotConversationPolicy.isChatbotConversation(conversationID)
    }
    val effectiveConfig = remember(config, isChatbotConversation) {
        if (isChatbotConversation) ChatbotMessageInputConfig(config) else config
    }
    val chatbotController = remember(conversationID) {
        if (isChatbotConversation) {
            ChatbotConversationControllers.acquire(conversationID)
        } else {
            null
        }
    }
    DisposableEffect(chatbotController) {
        onDispose {
            chatbotController?.let { ChatbotConversationControllers.release(conversationID, it) }
        }
    }
    val messageInputViewModel = viewModel(
        modelClass = MessageInputViewModel::class,
        key = conversationID,
        factory = messageInputViewModelFactory
    )
    key(conversationID) {
        MessageInput(
            modifier = modifier,
            config = effectiveConfig,
            messageInputViewModel = messageInputViewModel,
            chatbotController = chatbotController,
            typingStatusSender = typingStatusSender
        )
    }
}

@Composable
private fun MessageInput(
    modifier: Modifier,
    config: MessageInputConfigProtocol,
    messageInputViewModel: MessageInputViewModel,
    chatbotController: ChatbotConversationController? = null,
    typingStatusSender: ((Boolean) -> Unit)? = null
) {
    val keyboardBridge = rememberKeyboardBridge()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val hostView = LocalView.current
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val coordinator = remember {
        InputCoordinator(object : InputCoordinator.RecordedKeyboardHeightProvider {
            override fun hasRecordedKeyboardHeight(): Boolean = keyboardBridge.hasRecordedKeyboardHeight()
            override fun getRecordedKeyboardHeight(): Int = keyboardBridge.getKeyboardHeight()
        })
    }
    coordinator.density = density.density

    val softInputModeGuard = remember { WindowSoftInputModeGuard() }
    DisposableEffect(softInputModeGuard) {
        softInputModeGuard.apply(context)
        onDispose { softInputModeGuard.restore() }
    }

    DisposableEffect(keyboardBridge, coordinator) {
        keyboardBridge.listener = coordinator
        onDispose {
            if (keyboardBridge.listener === coordinator) {
                keyboardBridge.listener = null
            }
        }
    }

    val uiState by coordinator.state.collectAsState()
    val conversationInfo by messageInputViewModel.conversationInfo.collectAsState()
    val groupCallPickerRequest by messageInputViewModel.groupCallPickerRequest.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var hasUserEditedText by remember { mutableStateOf(false) }
    val editTextState = remember(messageInputViewModel) { AndroidEditTextState() }
    var isShowMentionDialog by remember { mutableStateOf(false) }
    var isProgrammaticInsert by remember { mutableStateOf(false) }
    var voiceTranscriptionDraft by remember { mutableStateOf<VoiceTranscriptionDraft?>(null) }
    val isGroupChat = isGroupConversation(messageInputViewModel.conversationID)
    val draftPolicy = remember { MessageInputDraftPolicy() }
    val panelHeightDriver = remember { MessageInputPanelHeightDriver() }

    // Publish the input surface (keyboard / panel) height animation state so the message
    // list can suppress item placement animations while the bottom surface is resizing
    // every frame.
    val inputSurfaceAnimationState = LocalInputSurfaceAnimationState.current
    DisposableEffect(panelHeightDriver, coordinator, inputSurfaceAnimationState) {
        val animationState = inputSurfaceAnimationState
        if (animationState != null) {
            panelHeightDriver.onAnimationRunningChanged = { running ->
                animationState.isPanelHeightAnimating = running
            }
            coordinator.keyboardAnimationRunningCallback = { running ->
                animationState.isKeyboardAnimating = running
            }
        }
        onDispose {
            panelHeightDriver.onAnimationRunningChanged = null
            coordinator.keyboardAnimationRunningCallback = null
            animationState?.isPanelHeightAnimating = false
            animationState?.isKeyboardAnimating = false
        }
    }

    val pressToTalkController = remember(config.audioMaxRecordDurationMs) {
        PressToTalkController(
            context = context,
            minDurationMs = AUDIO_MIN_RECORD_TIME,
            maxDurationMs = config.audioMaxRecordDurationMs.coerceAtLeast(AUDIO_MIN_RECORD_TIME),
            onSendAudio = { path, duration ->
                messageInputViewModel.sendAudioMessage(path, duration)
            },
            onTranscribe = { path, duration ->
                voiceTranscriptionDraft = VoiceTranscriptionDraft(path, duration)
                coordinator.dispatch(InputModeEvent.SwitchToTextCollapsed)
                messageInputViewModel.convertLocalAudioToText(context, path) { text ->
                    if (voiceTranscriptionDraft?.audioPath == path) {
                        voiceTranscriptionDraft =
                            VoiceTranscriptionDraft(path, duration, text, hasResult = true)
                    }
                }
            }
        )
    }

    val chatbotState by remember(chatbotController) {
        chatbotController?.state ?: MutableStateFlow<ChatbotConversationState?>(null)
    }.collectAsState()
    val isChatbotActive = chatbotState != null && chatbotState !is ChatbotConversationState.Idle

    LaunchedEffect(chatbotController, messageInputViewModel) {
        messageInputViewModel.setTextSendCallbacks(
            canSend = { chatbotController?.isActive != true },
            onSent = { chatbotController?.onTextMessageSent() }
        )
    }

    LaunchedEffect(coordinator, keyboardBridge) {
        coordinator.effects.collect { effect ->
            val current = coordinator.state.value
            when (effect) {
                PanelEffect.ShowKeyboard -> {
                    if (current.surface == InputSurfaceState.KEYBOARD) {
                        val editText = editTextState.editTextView
                        softInputModeGuard.enforceNow()
                        if (editText != null) {
                            keyboardBridge.showKeyboard(editText)
                        } else {
                            editTextState.forceRequestFocus()
                        }
                    }
                }
                PanelEffect.HideKeyboard -> {
                    if (current.surface != InputSurfaceState.KEYBOARD) {
                        keyboardBridge.hideKeyboard()
                    }
                }
                PanelEffect.HideKeyboardKeepFocus -> {
                    if (current.surface != InputSurfaceState.KEYBOARD) {
                        val editText = editTextState.editTextView
                        if (editText != null) {
                            keyboardBridge.hideKeyboardKeepFocus(editText)
                        } else {
                            editTextState.hideKeyboardKeepFocus()
                        }
                    }
                }
                PanelEffect.RequestEditTextFocus -> editTextState.requestFocus()
                PanelEffect.ClearEditTextFocus -> {
                    if (current.surface == InputSurfaceState.PANEL && current.panel == PanelState.MORE_PANEL) {
                        editTextState.clearFocus()
                    }
                }
                is PanelEffect.SetPanelContent,
                PanelEffect.HidePanelContent -> Unit
            }
        }
    }

    DisposableEffect(Unit) {
        val job = coroutineScope.onEvent<Map<*, *>> { event ->
            val source = event["source"] as? String
            val eventType = event["event"] as? String
            if (source == MessageInputEvents.MESSAGE_LIST_SOURCE &&
                eventType == MessageInputEvents.USER_LONG_PRESS_EVENT
            ) {
                if (!isGroupChat) return@onEvent
                val userID = event["userID"] as? String ?: return@onEvent
                val message = event["message"] as? MessageInfo
                if (message?.isSentBySelf == true) return@onEvent
                val displayName = message?.senderDisplayName ?: userID
                isProgrammaticInsert = true
                editTextState.insertAtomicText(
                    MentionInfo(userID = userID, displayName = displayName).mentionText,
                    MentionInfo(userID = userID, displayName = displayName)
                )
                isProgrammaticInsert = false
                inputText = editTextState.getText()
                return@onEvent
            }
            if (source == MessageInputEvents.MESSAGE_LIST_SOURCE &&
                eventType == MessageInputEvents.BLANK_AREA_CLICK_EVENT
            ) {
                coordinator.dispatch(PanelEvent.RequestCollapse)
            }
            if (source == MessageInputEvents.MESSAGE_LIST_SOURCE &&
                eventType == MessageInputEvents.QUOTE_MESSAGE_EVENT
            ) {
                val quoteInfo = MessageInputQuoteEventPolicy.resolveQuoteInfo(
                    event = event,
                    currentConversationID = messageInputViewModel.conversationID
                ) ?: return@onEvent
                coordinator.dispatch(OverlayEvent.SetQuote(quoteInfo))
                coordinator.dispatch(InputModeEvent.SwitchToText)
                MessageInputEvents.postInputInteract()
            }
        }
        onDispose { job.cancel() }
    }

    LaunchedEffect(conversationInfo?.draft) {
        val draft = conversationInfo?.draft
        if (draftPolicy.shouldApplyIncomingDraft(inputText, draft)) {
            isProgrammaticInsert = true
            inputText = draft.orEmpty()
            editTextState.setText(draft.orEmpty())
            isProgrammaticInsert = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            panelHeightDriver.cancel()
            pressToTalkController.cancel()
            messageInputViewModel.stopRecordTranslationSpeak()
            if (draftPolicy.shouldSaveDraft(inputText, hasUserEditedText)) {
                messageInputViewModel.setDraft(inputText)
            }
            editTextState.dispose()
        }
    }

    SideEffect {
        panelHeightDriver.drive(
            scope = coroutineScope,
            target = uiState.panelTargetHeight,
            keyboardHeight = uiState.keyboardHeight,
            isKeyboardAnimationSupported = keyboardBridge.isAnimationSupported(),
            keyboardTargetHeight = keyboardBridge.getKeyboardHeight()
        )
    }

    val displayedPanelHeightPx = max(
        uiState.keyboardHeight,
        panelHeightDriver.heightAnimatable.value.toInt()
    )
    val finalPanelHeight = with(density) { displayedPanelHeightPx.toDp() }

    fun handleTextSubmitted() {
        draftPolicy.markDraftClearPending()
        messageInputViewModel.setDraft(null)
    }

    fun sendCurrentText() {
        val text = editTextState.getText()
        if (text.isEmpty()) return
        val mentionList = editTextState.getAtomicRanges<MentionInfo>().map { it.data }
        val quotedMessage = uiState.overlay.quoteMessage?.toMessageInfo()
        val accepted = messageInputViewModel.trySendTextMessage(
            context = activity ?: context,
            text = text,
            mentionList = mentionList,
            quotedMessage = quotedMessage,
            onSuccess = { coordinator.dispatch(OverlayEvent.ClearQuote) }
        )
        if (!accepted) return
        handleTextSubmitted()
        isProgrammaticInsert = true
        inputText = ""
        editTextState.setText("")
        isProgrammaticInsert = false
        typingStatusSender?.invoke(false)
    }

    val actionVisibility = MessageInputActionVisibilityPolicy.resolve(
        hasText = inputText.isNotEmpty() && uiState.inputMode == InputMode.TEXT,
        isTextMode = uiState.inputMode == InputMode.TEXT,
        isShowMore = config.isShowMore,
        isChatbotActive = isChatbotActive
    )
    val shouldHideHint = MessageInputTextAffordancePolicy.shouldHideInputHint(
        surface = uiState.surface,
        keyboardHeight = uiState.keyboardHeight
    )
    val hintRes = if (config.enableLongPressToTalk) {
        R.string.message_input_edit_text_hint
    } else {
        R.string.message_input_edit_text_hint_text_only
    }
    val inputHint = if (shouldHideHint) "" else stringResource(hintRes)
    val isMorePanelExpanded = uiState.panel == PanelState.MORE_PANEL
    val hasOngoingGroupCall by messageInputViewModel.hasOngoingGroupCall.collectAsState()
    LaunchedEffect(isMorePanelExpanded, messageInputViewModel.conversationID) {
        if (isMorePanelExpanded) {
            messageInputViewModel.refreshGroupCallStatus()
        }
    }
    val moreActionsSnapshot = remember(messageInputViewModel.conversationID) {
        MorePanelActionsSnapshot()
    }
    val moreActions = moreActionsSnapshot.resolve(
        expanded = isMorePanelExpanded,
        stateKey = hasOngoingGroupCall
    ) {
        messageInputViewModel.getActions(context, config)
    }
    var dismissedRedDotIds by remember(messageInputViewModel.conversationID) { mutableStateOf(emptySet<String>()) }
    val showMoreButtonRedDot = moreActions.any { it.showRedDot && it.ID !in dismissedRedDotIds }

    LaunchedEffect(uiState.surface, uiState.panel) {
        editTextState.setCursorVisible(
            uiState.surface == InputSurfaceState.KEYBOARD ||
                uiState.panel == PanelState.EMOJI_PANEL
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = colors.bgColorOperate)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.strokeColorSecondary)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (config.isShowAudioRecorder) {
                    ToolbarIconButton(
                        iconRes = if (uiState.inputMode == InputMode.VOICE) {
                            R.drawable.message_input_keyboard_icon
                        } else {
                            R.drawable.message_input_audio_icon
                        },
                        contentDescription = stringResource(R.string.message_input_audio),
                        onClick = {
                            if (uiState.inputMode == InputMode.VOICE) {
                                coordinator.dispatch(InputModeEvent.SwitchToText)
                            } else {
                                coordinator.dispatch(InputModeEvent.SwitchToVoice)
                            }
                            MessageInputEvents.postInputInteract()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.bgColorInput)
                        .onGloballyPositioned { pressToTalkController.updateBubbleAnchor(it.toScreenRect()) }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (uiState.surface != InputSurfaceState.KEYBOARD) {
                                coordinator.dispatch(PanelEvent.RequestKeyboard)
                            }
                            MessageInputEvents.postInputInteract()
                        }
                        .longPressRecordGesture(
                            enabled = {
                                MessageInputLongPressRecordingPolicy.shouldArmRecording(
                                    inputMode = uiState.inputMode,
                                    surface = uiState.surface,
                                    isLongPressToTalkEnabled = config.enableLongPressToTalk,
                                    hasInputText = inputText.isNotEmpty()
                                )
                            },
                            onRecordingStart = {
                                coordinator.dispatch(PanelEvent.RequestCollapse)
                                editTextState.clearFocus()
                                val started = pressToTalkController.start()
                                if (started) {
                                    MessageInputEvents.postInputInteract()
                                }
                                started
                            },
                            onRecordingStop = { x, y -> pressToTalkController.finish(x, y) },
                            onDragPosition = { x, y -> pressToTalkController.updateDrag(x, y) },
                            onTapWithoutLongPress = {
                                coordinator.dispatch(PanelEvent.RequestKeyboard)
                                MessageInputEvents.postInputInteract()
                            }
                        )
                ) {
                    val isVoiceMode = uiState.inputMode == InputMode.VOICE
                    Row(
                        modifier = if (isVoiceMode) {
                            Modifier
                                .matchParentSize()
                                .alpha(0f)
                                .clearAndSetSemantics { }
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AndroidEditText(
                            text = inputText,
                            onTextChange = { newText ->
                                if (!isProgrammaticInsert) {
                                    hasUserEditedText = true
                                    typingStatusSender?.invoke(newText.isNotEmpty())
                                    if (config.enableMention && isGroupChat &&
                                        MentionTriggerDetector.shouldTrigger(inputText, newText)
                                    ) {
                                        coordinator.dispatch(PanelEvent.RequestCollapse)
                                        isShowMentionDialog = true
                                    }
                                }
                                inputText = newText
                            },
                            modifier = Modifier.weight(1f),
                            state = editTextState,
                            hint = inputHint,
                            textColor = colors.textColorPrimary,
                            hintColor = colors.textColorTertiary,
                            textSize = 16.sp,
                            maxLines = 6,
                            onFocusChanged = { isFocused ->
                                if (isFocused && uiState.surface != InputSurfaceState.KEYBOARD) {
                                    coordinator.dispatch(PanelEvent.RequestKeyboard)
                                }
                                if (isFocused) {
                                    MessageInputEvents.postInputInteract()
                                }
                            },
                            onSendMessage = { sendCurrentText() },
                            onEmptyDeleteKey = {
                                val shouldClear = MessageInputQuoteEditingPolicy.shouldClearQuoteOnEmptyDelete(
                                    hasQuote = uiState.overlay.quoteMessage != null,
                                    inputText = inputText
                                )
                                if (shouldClear) {
                                    coordinator.dispatch(OverlayEvent.ClearQuote)
                                }
                                shouldClear
                            }
                        )
                    }
                    if (isVoiceMode) {
                        PressToTalkBar(
                            controller = pressToTalkController,
                            onTap = {
                                coordinator.dispatch(InputModeEvent.SwitchToText)
                                MessageInputEvents.postInputInteract()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (config.isShowEmoji) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ToolbarIconButton(
                        iconRes = if (uiState.panel == PanelState.EMOJI_PANEL) {
                            R.drawable.message_input_keyboard_icon
                        } else {
                            R.drawable.message_input_emoji_icon
                        },
                        contentDescription = stringResource(R.string.message_input_emoji),
                        onClick = {
                            if (uiState.panel == PanelState.EMOJI_PANEL) {
                                coordinator.dispatch(PanelEvent.RequestKeyboard)
                            } else {
                                coordinator.dispatch(PanelEvent.RequestEmojiPanel)
                            }
                            MessageInputEvents.postInputInteract()
                        }
                    )
                }

                if (actionVisibility.showMore) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ToolbarIconButton(
                        iconRes = R.drawable.message_input_more_icon,
                        contentDescription = stringResource(R.string.message_input_more),
                        showRedDot = showMoreButtonRedDot,
                        onClick = {
                            coordinator.dispatch(PanelEvent.RequestMorePanel)
                            MessageInputEvents.postInputInteract()
                        }
                    )
                }
                if (actionVisibility.showSend) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.buttonColorPrimaryDefault)
                            .clickable(
                                onClick = { sendCurrentText() },
                                role = Role.Button,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        InputToolbarImage(
                            iconRes = R.drawable.message_input_send_arrow_icon,
                            tint = colors.textColorButton,
                            contentDescription = stringResource(R.string.message_input_send)
                        )
                    }
                }
                if (actionVisibility.showStop) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.buttonColorPrimaryDefault)
                            .clickable(
                                onClick = {
                                    chatbotController?.sendInterrupt(object : CompletionHandler {
                                        override fun onSuccess() = Unit
                                        override fun onFailure(code: Int, desc: String) {
                                            Toast.error(
                                                context,
                                                desc.ifBlank { context.getString(R.string.message_input_send_failed) }
                                            )
                                        }
                                    })
                                },
                                role = Role.Button,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        InputToolbarImage(
                            iconRes = R.drawable.message_input_chatbot_stop_icon,
                            tint = colors.textColorButton,
                            contentDescription = stringResource(R.string.message_input_chatbot_stop)
                        )
                    }
                }
            }

            QuotePreviewBar(
                quoteInfo = uiState.overlay.quoteMessage,
                onClose = { coordinator.dispatch(OverlayEvent.ClearQuote) }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(finalPanelHeight)
            ) {
                AnimatedContent(
                    targetState = uiState.panel,
                    transitionSpec = {
                        val crossfade =
                            (initialState == PanelState.EMOJI_PANEL && targetState == PanelState.MORE_PANEL) ||
                                (initialState == PanelState.MORE_PANEL && targetState == PanelState.EMOJI_PANEL)
                        if (crossfade) {
                            fadeIn(tween(PANEL_ANIM_DURATION_MS, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(PANEL_ANIM_DURATION_MS, easing = FastOutSlowInEasing))
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "panelContent"
                ) { panel ->
                    when (panel) {
                        PanelState.EMOJI_PANEL -> {
                            EmojiPicker(
                                modifier = Modifier.fillMaxSize(),
                                onEmojiClick = { emojiGroup, emoji ->
                                    if (emojiGroup.isLittleEmoji) {
                                        editTextState.insertText(emoji.key)
                                        inputText = editTextState.getText()
                                    } else {
                                        val faceIndex = EmojiManager.emojiGroupList.indexOfFirst { it.id == emojiGroup.id }
                                        messageInputViewModel.sendFaceMessage(
                                            context = activity ?: context,
                                            faceIndex = faceIndex.coerceAtLeast(0),
                                            faceData = emoji.key,
                                            quotedMessage = uiState.overlay.quoteMessage?.toMessageInfo(),
                                            onSuccess = { coordinator.dispatch(OverlayEvent.ClearQuote) }
                                        )
                                    }
                                },
                                onSendClick = { sendCurrentText() },
                                onDeleteClick = { editTextState.deleteAtCursor() }
                            )
                        }
                        PanelState.MORE_PANEL -> {
                            MoreActionsPanel(
                                actions = moreActions,
                                modifier = Modifier.fillMaxSize(),
                                dismissedRedDotIds = dismissedRedDotIds,
                                onRedDotDismissed = { actionID ->
                                    dismissedRedDotIds = dismissedRedDotIds + actionID
                                }
                            )
                        }
                        else -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        val groupID = messageInputViewModel.conversationID.removePrefix("group_")
        MentionMemberDialog(
            isVisible = isShowMentionDialog,
            groupID = groupID,
            onDismiss = { isShowMentionDialog = false },
            onConfirm = { selectedMentions ->
                isShowMentionDialog = false
                if (selectedMentions.isNotEmpty()) {
                    isProgrammaticInsert = true
                    val currentText = editTextState.getText()
                    val cursor = editTextState.getSelectionStart()
                    if (cursor > 0 && MentionTriggerDetector.isTriggerChar(currentText.getOrNull(cursor - 1))) {
                        editTextState.deleteCharBeforeCursor()
                    }
                    selectedMentions.forEach {
                        editTextState.insertAtomicText(it.mentionText, it)
                    }
                    isProgrammaticInsert = false
                    inputText = editTextState.getText()
                }
            }
        )

        UserPickerDialog(
            visible = groupCallPickerRequest != null,
            title = groupCallPickerRequest?.title.orEmpty(),
            dataSource = groupCallPickerRequest?.candidates.orEmpty().map { it.toUserPickerData() },
            maxCount = groupCallPickerRequest?.maxSelection,
            allowEmptyConfirm = true,
            onDismiss = { messageInputViewModel.dismissGroupCallPicker() },
            onConfirm = { selected: List<GroupMember> ->
                messageInputViewModel.confirmGroupCallSelection(context, selected)
            }
        )

        AudioRecorderOverlay(
            visible = pressToTalkController.isRecording,
            uiState = pressToTalkController.uiState,
            maxDurationMs = config.audioMaxRecordDurationMs.coerceAtLeast(AUDIO_MIN_RECORD_TIME),
            bubbleAnchor = pressToTalkController.bubbleAnchor,
            onCancelTargetChanged = { pressToTalkController.updateCancelTarget(it) },
            onTranscribeTargetChanged = { pressToTalkController.updateTranscribeTarget(it) }
        )

        val isVoiceOverlayVisible = voiceTranscriptionDraft != null
        DisposableEffect(isVoiceOverlayVisible, keyboardBridge, coordinator) {
            if (isVoiceOverlayVisible) {
                keyboardBridge.listener = null
                keyboardBridge.detach(hostView)
            } else {
                keyboardBridge.listener = coordinator
                keyboardBridge.attach(hostView)
            }
            onDispose { }
        }

        VoiceTranscriptionOverlay(
            draft = voiceTranscriptionDraft,
            onCancel = { voiceTranscriptionDraft = null },
            onSendAudio = { path, duration ->
                messageInputViewModel.sendAudioMessage(path, duration)
                voiceTranscriptionDraft = null
            },
            onSendText = { text ->
                messageInputViewModel.sendTextMessage(
                    context = activity ?: context,
                    text = text,
                    mentionList = emptyList(),
                    onSuccess = { voiceTranscriptionDraft = null }
                )
            },
            onTranslate = { source, lang, onSuccess, onFailure ->
                messageInputViewModel.translateRecordText(source, lang, onSuccess, onFailure)
            },
            onStartSpeak = { text, onStart, onComplete, onError ->
                messageInputViewModel.startRecordTranslationSpeak(
                    context,
                    text,
                    onStart,
                    onComplete,
                    onError
                )
            },
            onStopSpeak = { messageInputViewModel.stopRecordTranslationSpeak() }
        )
    }
}

@Composable
private fun ToolbarIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    showRedDot: Boolean = false
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(
                onClick = onClick,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        InputToolbarImage(
            iconRes = iconRes,
            tint = colors.textColorSecondary,
            contentDescription = contentDescription
        )
        if (showRedDot) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd),
                type = BadgeType.Dot
            )
        }
    }
}

@Composable
private fun InputToolbarImage(
    iconRes: Int,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier.size(28.dp),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcAtop)
    )
}

@Composable
private fun QuotePreviewBar(
    quoteInfo: QuoteInfo?,
    onClose: () -> Unit
) {
    if (quoteInfo == null) return
    val colors = LocalTheme.current.colors
    val senderName = quoteInfo.senderName.takeIf { it.isNotBlank() }
    val summary = quoteInfo.summary.takeIf { it.isNotBlank() }.orEmpty()
    val rawText = when {
        senderName == null -> summary
        summary.isBlank() -> senderName
        else -> "$senderName：$summary"
    }
    val displayText = rememberEmojiKeyToName(rawText)
    val (annotated, inlineContent) = rememberEmojiText(
        text = displayText,
        emojiSize = emojiSizeForTextSize(13.sp),
        matchNames = true
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorDefault)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = annotated,
            inlineContent = inlineContent,
            color = colors.textColorSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    onClick = onClose,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                color = colors.textColorSecondary,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
