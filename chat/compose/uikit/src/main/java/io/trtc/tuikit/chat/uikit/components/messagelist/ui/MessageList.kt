package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.AlertDialog
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.common.onEvent
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationController
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationControllers
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationPolicy
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationState
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotForwardTargetPolicy
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageListConfig
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageProtocol
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSource
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotPlaceholderMessageFactory
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotUIRegistrations
import io.trtc.tuikit.chat.uikit.components.messagelist.MessageReadReceiptDialog
import io.trtc.tuikit.chat.uikit.components.messagelist.background.MessageListBackgroundLayer
import io.trtc.tuikit.chat.uikit.components.messagelist.background.MmkvChatBackgroundStore
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListBackground
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.listen.ListenPlaybackBar
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListBoundaryPagingPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListLocateCoordinator
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups.MessageLongPressMenu
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.selection.MessageListSelectionPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.MultiSelectBottomBar
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.ReactionEmojiPickerSheet
import io.trtc.tuikit.chat.uikit.components.messagelist.typing.TypingIndicatorController
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.HighlightManager
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModelFactory
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.LocalInputSurfaceAnimationState
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageEvent
import io.trtc.tuikit.atomicxcore.api.message.MessageForwardType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

val LocalMessageListViewModel =
    compositionLocalOf<MessageListViewModel> { error("No ViewModel provided") }
val LocalMessageListConfig = compositionLocalOf<MessageListConfigProtocol> { ChatMessageListConfig() }
val LocalCustomActions = compositionLocalOf<List<MessageCustomAction>> { emptyList() }
val LocalMessageRenderConfig = compositionLocalOf { MessageRenderConfig() }
val LocalHighlightManager = compositionLocalOf<HighlightManager> { error("No HighlightManager provided") }
val LocalBubbleMaxWidth = compositionLocalOf { 240.dp }
val LocalMessageContentMaxWidth = compositionLocalOf { Dp.Unspecified }
val LocalBlankAreaClickHandler = compositionLocalOf<() -> Unit> { {} }
val LocalMessageListWindowBounds = compositionLocalOf { Rect.Zero }

private const val MENTION_LOCATE_MAX_LOAD_COUNT = 20
private const val UNREAD_CLEAR_DEBOUNCE_MS = 300L
private const val LATEST_RELOAD_TIMEOUT_MS = 15_000L
private const val LATEST_RELOAD_FRAME_TIMEOUT_MS = 1_000L
private const val CHAT_SETTING_EVENT_SOURCE = "ChatSetting"
private const val EVENT_CHAT_BACKGROUND_CHANGED = "onChatBackgroundChanged"
private const val MESSAGE_INPUT_EVENT_SOURCE = "MessageInput"
private const val EVENT_INPUT_INTERACT = "onInputInteract"

internal class MessageListRenderActions(
    private val viewModel: () -> MessageListViewModel,
    private val androidContext: () -> Context
) : MessageRenderActions {
    override fun openImageViewer(message: MessageInfo) {
        viewModel().showImage(androidContext(), message)
    }

    override fun openVideoPlayer(message: MessageInfo) {
        viewModel().downloadOrShowVideo(androidContext(), message)
    }

    override fun playSound(message: MessageInfo) {
        viewModel().playAudioMessage(message)
    }

    override fun toggleSelection(message: MessageInfo) {
        viewModel().toggleMessageSelection(message)
    }

    override fun showLongPressMenu(message: MessageInfo, anchorBounds: Rect?) {
        viewModel().showLongPressActionDialog(message, anchorBounds)
    }
}

@Composable
internal fun rememberMessageListRenderActions(): MessageRenderActions {
    val viewModelRef = rememberUpdatedState(LocalMessageListViewModel.current)
    val contextRef = rememberUpdatedState(LocalContext.current)
    return remember {
        MessageListRenderActions(
            viewModel = { viewModelRef.value },
            androidContext = { contextRef.value }
        )
    }
}

@Composable
fun MessageList(
    conversationID: String,
    modifier: Modifier = Modifier,
    config: MessageListConfigProtocol = ChatMessageListConfig(),
    customActions: List<MessageCustomAction> = emptyList(),
    locateMessage: MessageInfo? = null,
    messageListViewModelFactory: MessageListViewModelFactory? = null,
    controller: MessageListController? = null,
    onTypingStatusChanged: (Boolean) -> Unit = {},
    onMultiSelectStateChanged: (Boolean) -> Unit = {},
    onUserClick: (String) -> Unit = {},
) {
    val isChatbotConversation = remember(conversationID) {
        ChatbotConversationPolicy.isChatbotConversation(conversationID)
    }
    val effectiveConfig = remember(config, isChatbotConversation) {
        if (isChatbotConversation) ChatbotMessageListConfig(config) else config
    }
    val chatbotController = remember(conversationID) {
        if (isChatbotConversation) {
            ChatbotUIRegistrations.ensureRegistered()
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
    val factory = messageListViewModelFactory ?: remember(conversationID, locateMessage, effectiveConfig) {
        MessageListViewModelFactory(
            messageListStore = MessageListStore.create(conversationID),
            conversationID = conversationID,
            locateMessage = locateMessage,
            messageListConfig = effectiveConfig
        )
    }
    val messageListViewModelKey = remember(
        conversationID,
        locateMessage?.msgID,
        locateMessage?.sequence,
        locateMessage?.timestamp
    ) {
        "${MessageListViewModel::class.java.name}:$conversationID:" +
            "${locateMessage?.msgID.orEmpty()}:${locateMessage?.sequence ?: 0L}:${locateMessage?.timestamp ?: 0L}"
    }
    val messageListViewModel = viewModel(
        modelClass = MessageListViewModel::class,
        key = messageListViewModelKey,
        factory = factory
    )
    val resolvedController = controller ?: rememberMessageListController()
    val currentConversationID = rememberUpdatedState(conversationID)

    DisposableEffect(messageListViewModel) {
        messageListViewModel.initializeAudioPlayer()
        messageListViewModel.syncConversationAsRead()
        resolvedController.viewModel = messageListViewModel
        onDispose {
            messageListViewModel.stopListenFromHere()
            messageListViewModel.destroyAudioPlayer()
            if (resolvedController.viewModel === messageListViewModel) {
                resolvedController.viewModel = null
            }
        }
    }

    val typingEnabled = effectiveConfig.enableTyping && ConversationIDUtil.isC2C(conversationID)
    DisposableEffect(conversationID, typingEnabled) {
        val typingController = if (typingEnabled) {
            TypingIndicatorController.obtain(conversationID)
        } else {
            null
        }
        resolvedController.typingController = typingController
        onDispose {
            typingController?.let { TypingIndicatorController.release(it) }
            if (resolvedController.typingController === typingController) {
                resolvedController.typingController = null
            }
        }
    }
    val typingController = resolvedController.typingController
    val typingStatusCallback = rememberUpdatedState(onTypingStatusChanged)
    LaunchedEffect(typingController) {
        if (typingController == null) {
            typingStatusCallback.value(false)
            return@LaunchedEffect
        }
        typingController.typingState.collectLatest { isTyping ->
            typingStatusCallback.value(isTyping)
        }
    }

    val userClickCallback: (String) -> Unit = remember(onUserClick) {
        { userID ->
            if (!ChatbotConversationPolicy.isChatbotID(userID)) {
                onUserClick(userID)
            }
        }
    }

    CompositionLocalProvider(
        LocalMessageListViewModel provides messageListViewModel,
        LocalMessageListConfig provides effectiveConfig,
        LocalCustomActions provides customActions,
        LocalMessageListController provides resolvedController,
        LocalHighlightManager provides messageListViewModel.highlightManager
    ) {
        key(messageListViewModelKey) {
            MessageListContent(
                modifier = modifier,
                conversationID = conversationID,
                messageListViewModel = messageListViewModel,
                onMultiSelectStateChanged = onMultiSelectStateChanged,
                onUserClick = userClickCallback,
                chatbotController = chatbotController,
                isCurrentConversation = { currentConversationID.value == conversationID }
            )
        }
    }
}

@Composable
private fun MessageListContent(
    modifier: Modifier,
    conversationID: String,
    messageListViewModel: MessageListViewModel,
    onMultiSelectStateChanged: (Boolean) -> Unit,
    onUserClick: (String) -> Unit,
    chatbotController: ChatbotConversationController?,
    isCurrentConversation: () -> Boolean,
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val config = LocalMessageListConfig.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderActions = rememberMessageListRenderActions()
    val touchTargetHitTester = remember { MessageListTouchTargetHitTester() }
    val readReceiptSnapshots = remember { mutableStateMapOf<String, String>() }

    // While the bottom input surface (IME or emoji/more panel) height is animating, the list
    // viewport resizes every frame. Running animateItem's placement animation against those
    // per-frame position changes makes messages overshoot and settle back (a visible hop).
    // Suppress the placement animation during those animations so items track the bottom edge
    // immediately; placement animations resume once the animation settles. The flag is driven
    // synchronously by the input surface (keyboard bridge / panel height driver) when
    // animations start and stop, because a snapshotFlow/debounced read lags one frame and
    // lets a hop slip through.
    val isInputSurfaceHeightAnimating = LocalInputSurfaceAnimationState.current?.isHeightAnimating == true

    val messageList by messageListViewModel.messageList.collectAsState()
    val chatbotState by remember(chatbotController) {
        chatbotController?.state ?: MutableStateFlow<ChatbotConversationState?>(null)
    }.collectAsState()
    val displayedMessages by remember(chatbotController) {
        derivedStateOf {
            val messages = messageList
            val state = chatbotState
            if (chatbotController != null &&
                state is ChatbotConversationState.Waiting &&
                chatbotController.shouldShowPlaceholder(messageListViewModel.messageListState.messageList.value)
            ) {
                listOf(
                    ChatbotPlaceholderMessageFactory.create(
                        conversationID = chatbotController.conversationID,
                        timestampSeconds = state.requestTimestampSeconds,
                        sender = resolveChatbotPlaceholderSender(
                            messageListViewModel.messageListState.messageList.value,
                            chatbotController.conversationID,
                            messageListViewModel
                        )
                    )
                ) + messages
            } else {
                messages
            }
        }
    }
    LaunchedEffect(chatbotController) {
        if (chatbotController != null) {
            messageListViewModel.messageListState.messageList.collectLatest { messages ->
                chatbotController.onMessagesChanged(messages)
            }
        }
    }

    val isMultiSelectMode by messageListViewModel.isMultiSelectMode.collectAsState()
    val selectedMessages by messageListViewModel.selectedMessages.collectAsState()
    val singleMessageToForward by messageListViewModel.onSingleMessageForward.collectAsState()
    val readReceiptMessage by messageListViewModel.readReceiptMessage.collectAsState()
    val longPressMenuRequest by messageListViewModel.longPressMenuRequest.collectAsState()
    val showReactionEmojiPickerForMessage by messageListViewModel.showEmojiPickerForMessage.collectAsState()
    val reactionActionErrorResId by messageListViewModel.reactionActionErrorResId.collectAsState()
    val loadingState by messageListViewModel.loadingState.collectAsState()
    val hasMoreNewerMessage by messageListViewModel.hasMoreNewerMessage.collectAsState()
    val listenPlaybackState by messageListViewModel.listenPlaybackState.collectAsState()
    val auxiliaryTextForwardContent by messageListViewModel.auxiliaryTextForwardContent.collectAsState()
    val shouldScrollToBottomAfterAsr by messageListViewModel.shouldScrollToBottomAfterAsr.collectAsState()
    val shouldScrollToBottomAfterTranslation by messageListViewModel.shouldScrollToBottomAfterTranslation.collectAsState()

    LaunchedEffect(reactionActionErrorResId) {
        reactionActionErrorResId?.let { resId ->
            Toast.error(context, context.getString(resId))
            messageListViewModel.clearReactionActionError()
        }
    }

    val isListInitialized = displayedMessages.isNotEmpty()
    var showForwardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var forwardingMessages by remember { mutableStateOf<List<MessageInfo>>(emptyList()) }
    var listenCollapsed by remember { mutableStateOf(false) }

    val chatBackgroundStore = remember { MmkvChatBackgroundStore(context) }
    var persistedBackground by remember(conversationID) {
        mutableStateOf(
            chatBackgroundStore.getImageUri(conversationID)?.let { MessageListBackground.Image(it) }
        )
    }
    val listBackground = config.background ?: persistedBackground

    LaunchedEffect(singleMessageToForward) {
        singleMessageToForward?.let { message ->
            forwardingMessages = listOf(message)
            messageListViewModel.forwardType = MessageForwardType.SEPARATE
            showForwardDialog = true
        }
    }

    LaunchedEffect(isMultiSelectMode) {
        onMultiSelectStateChanged(isMultiSelectMode)
    }

    val locateCoordinator = remember(messageListViewModel.highlightManager) {
        MessageListLocateCoordinator(messageListViewModel.highlightManager)
    }
    val floatingEntryState = remember { MessageListFloatingEntryStateController() }
    val boundaryPagingPolicy = remember { MessageListBoundaryPagingPolicy() }
    var floatingEntry by remember { mutableStateOf<MessageListFloatingEntry?>(null) }
    var latestReloadState by remember { mutableStateOf(MessageListLatestReloadState()) }

    val listState = remember(isListInitialized) {
        if (isListInitialized) {
            val locateId = messageListViewModel.locateMessage?.msgID
            if (!locateId.isNullOrBlank()) {
                locateCoordinator.reset(locateId)
                val index = displayedMessages.indexOfFirst { it.msgID == locateId }
                LazyListState(firstVisibleItemIndex = index.coerceAtLeast(0))
            } else {
                locateCoordinator.reset(null)
                LazyListState()
            }
        } else {
            LazyListState()
        }
    }
    val listStateRef = rememberUpdatedState(listState)
    val visibleMessageIds = rememberVisibleMessageIds(listState)
    val displayedMessagesRef = rememberUpdatedState(displayedMessages)
    val hasMoreNewerMessageRef = rememberUpdatedState(hasMoreNewerMessage)
    var listLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var isUserScrollSession by remember { mutableStateOf(false) }
    var isHostResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val resumedState = rememberUpdatedState(isHostResumed)

    DisposableEffect(lifecycleOwner, messageListViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isHostResumed = true
                    messageListViewModel.syncConversationAsRead()
                }
                Lifecycle.Event.ON_PAUSE -> isHostResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun hasMoreNewerForNavigation(): Boolean {
        return hasMoreNewerMessageRef.value || latestReloadState.retainHasMoreNewer
    }

    fun isAtLatestPosition(state: LazyListState = listState): Boolean {
        val hasMoreNewerMessages = hasMoreNewerForNavigation()
        return MessageListAutoScrollPolicy.isAtLatestPosition(
            hasItems = state.layoutInfo.totalItemsCount > 0,
            hasMoreNewerMessages = hasMoreNewerMessages,
            canScrollTowardLatest = state.canScrollBackward
        )
    }

    suspend fun scrollToLatest() {
        isProgrammaticScroll = true
        try {
            listState.scrollToItem(0)
            delay(80)
        } finally {
            isProgrammaticScroll = false
        }
    }

    fun refreshFloatingEntry() {
        val mentionTarget = floatingEntryState.currentMentionTarget()
        if (mentionTarget != null) {
            val visible = locateCoordinator.mentionTargetVisibility(
                sequence = mentionTarget.sequence,
                messages = displayedMessages,
                listState = listState
            )
            floatingEntryState.onMentionTargetVisibilityChanged(
                if (visible) {
                    MessageListMentionTargetVisibility.VISIBLE
                } else {
                    MessageListMentionTargetVisibility.HIDDEN
                }
            )
        }
        val returnMessageId = floatingEntryState.currentBackToQuoteReturnMessage()?.msgID
        floatingEntryState.onScroll(
            distanceFromLatestPx = locateCoordinator.distanceFromLatestPx(listState),
            viewportHeightPx = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
                .toFloat(),
            isAtLatest = isAtLatestPosition(),
            hasMoreNewerMessages = hasMoreNewerForNavigation(),
            isReturnMessageCompletelyVisible = returnMessageId?.let {
                locateCoordinator.isMessageCompletelyVisible(it, listState)
            } == true
        )
        floatingEntry = floatingEntryState.currentEntry()
    }
    val refreshFloatingEntryRef = rememberUpdatedState { refreshFloatingEntry() }

    fun removeRecalledNewMessagesFromFloatingEntry(messages: List<MessageInfo>) {
        if (!floatingEntryState.hasNewMessages()) {
            return
        }
        var removed = false
        messages.forEach { message ->
            if (message.status == MessageStatus.REVOKED &&
                floatingEntryState.onMessageRecalled(message.msgID)
            ) {
                removed = true
            }
        }
        if (removed) {
            floatingEntry = floatingEntryState.currentEntry()
        }
    }

    fun postBlankAreaClickEvent() {
        EventBus.post(
            mapOf(
                "source" to "MessageList",
                "event" to "onBlankAreaClick"
            )
        )
    }

    fun handleBlankAreaClick(isMessageTouchTargetHit: Boolean = false) {
        if (MessageListInputDismissPolicy.shouldDismissInputForTap(isMessageTouchTargetHit)) {
            postBlankAreaClickEvent()
            listenCollapsed = true
        }
    }

    fun handleBackToLatestClick() {
        if (latestReloadState.blocksNavigation) {
            return
        }
        when (MessageListBackToLatestPolicy.action(hasMoreNewerForNavigation())) {
            MessageListBackToLatestAction.ReloadLatestMessages -> {
                val startedState = latestReloadState.start()
                latestReloadState = startedState
                val requestId = startedState.requestId
                boundaryPagingPolicy.reset()
                messageListViewModel.reloadLatestMessages(
                    object : CompletionHandler {
                        override fun onSuccess() {
                            scope.launch {
                                latestReloadState = latestReloadState.onSuccess(requestId)
                            }
                        }

                        override fun onFailure(code: Int, desc: String) {
                            scope.launch {
                                val failedState = latestReloadState.onFailure(requestId)
                                if (failedState != latestReloadState) {
                                    latestReloadState = failedState
                                    refreshFloatingEntryRef.value()
                                }
                            }
                        }
                    }
                )
            }
            MessageListBackToLatestAction.ScrollLoadedLatest -> {
                scope.launch { scrollToLatest() }
            }
        }
    }
    val handleBackToLatestClickRef = rememberUpdatedState { handleBackToLatestClick() }

    LaunchedEffect(latestReloadState.phase, latestReloadState.requestId) {
        if (latestReloadState.phase != MessageListLatestReloadPhase.RELOADING) {
            return@LaunchedEffect
        }
        val requestId = latestReloadState.requestId
        delay(LATEST_RELOAD_TIMEOUT_MS)
        val failedState = latestReloadState.onFailure(requestId)
        if (failedState != latestReloadState) {
            latestReloadState = failedState
            refreshFloatingEntryRef.value()
        }
    }

    fun locateQuotedMessage(sourceMessage: MessageInfo, quoteInfo: MessageQuoteInfo) {
        if (MessageQuoteLocatePolicy.isOriginalMessageUnreachable(quoteInfo)) {
            Toast.info(context, context.getString(R.string.message_list_quote_original_unreachable))
            return
        }
        MessageQuoteLocatePolicy.findLoadedTargetMessageId(quoteInfo, displayedMessages)?.let { messageId ->
            locateCoordinator.requestLocateMessage(messageId)
            floatingEntryState.onQuoteNavigated(sourceMessage)
            scope.launch {
                locateCoordinator.applyPending(displayedMessages, listState)
                refreshFloatingEntry()
            }
            return
        }
        if (messageListViewModel.findFilteredQuoteTargetId(quoteInfo) != null) {
            Toast.info(context, context.getString(R.string.message_list_quote_original_filtered))
            return
        }
        if (!MessageQuoteLocatePolicy.shouldLoadAround(quoteInfo, displayedMessages)) {
            return
        }
        messageListViewModel.loadMessagesAroundQuote(
            quoteInfo = quoteInfo,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    if (messageListViewModel.findFilteredQuoteTargetId(quoteInfo) != null) {
                        Toast.info(context, context.getString(R.string.message_list_quote_original_filtered))
                        return
                    }
                    val targetMessageId = MessageQuoteLocatePolicy.findLoadedTargetMessageId(
                        quoteInfo = quoteInfo,
                        messages = messageListViewModel.messageList.value
                    ) ?: quoteInfo.msgID.takeIf { it.isNotBlank() }
                    targetMessageId?.let { messageId ->
                        locateCoordinator.requestLocateMessage(messageId)
                        floatingEntryState.onQuoteNavigated(sourceMessage)
                    }
                }

                override fun onFailure(code: Int, desc: String) = Unit
            }
        )
    }

    fun performMessageForward(
        messages: List<MessageInfo>,
        conversationIDs: List<String>,
        exitMultiSelect: Boolean
    ) {
        val partition = ChatbotForwardTargetPolicy.partition(conversationIDs)
        if (partition.rejectedChatbotTargets.isNotEmpty()) {
            Toast.warning(context, context.getString(R.string.message_list_chatbot_forward_not_supported))
        }
        val allowedConversationIDs = partition.allowedTargets
        if (allowedConversationIDs.isEmpty()) {
            return
        }
        messageListViewModel.forwardMessages(
            messageList = messages,
            conversationIDList = allowedConversationIDs,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    if (exitMultiSelect) {
                        messageListViewModel.exitMultiSelectMode()
                    }
                    messageListViewModel.clearSingleMessageForward()
                }

                override fun onFailure(code: Int, desc: String) {
                    Toast.warning(
                        context,
                        desc.ifBlank { context.getString(R.string.message_list_forward_failed_tip) }
                    )
                    messageListViewModel.clearSingleMessageForward()
                }
            }
        )
    }

    fun handleForwardSelection(forwardType: MessageForwardType) {
        val selected = selectedMessages.toList()
        when (MessageListSelectionPolicy.validateForwardSelection(selected, forwardType)) {
            MessageListSelectionPolicy.ForwardValidation.EMPTY_SELECTION -> return
            MessageListSelectionPolicy.ForwardValidation.CONTAINS_UNSENT_MESSAGE -> {
                Toast.warning(context, context.getString(R.string.message_list_forward_failed_tip))
            }
            MessageListSelectionPolicy.ForwardValidation.CONTAINS_CALL_MESSAGE -> {
                Toast.warning(context, context.getString(R.string.message_list_forward_call_message_tip))
            }
            MessageListSelectionPolicy.ForwardValidation.EXCEEDS_SEPARATE_FORWARD_LIMIT -> {
                Toast.error(context, context.getString(R.string.message_list_forward_oneByOne_limit_number_tip))
            }
            MessageListSelectionPolicy.ForwardValidation.ALLOWED -> {
                forwardingMessages = selected
                messageListViewModel.forwardType = forwardType
                showForwardDialog = true
            }
        }
    }

    fun handleFloatingEntryClick(entry: MessageListFloatingEntry) {
        floatingEntryState.consume(entry)
        floatingEntry = floatingEntryState.currentEntry()
        when (entry) {
            MessageListFloatingEntry.BackToLatest -> handleBackToLatestClick()
            is MessageListFloatingEntry.NewMessages -> {
                val firstMessageId = entry.firstMessage.msgID.takeIf { it.isNotBlank() } ?: return
                val isFirstNewMessageLoaded = displayedMessages.any { it.msgID == firstMessageId }
                when (MessageListNewMessagesNavigationPolicy.action(isFirstNewMessageLoaded)) {
                    MessageListNewMessagesNavigationAction.LocateLoadedMessage -> {
                        locateCoordinator.requestLocateMessage(firstMessageId)
                        scope.launch { locateCoordinator.applyPending(displayedMessages, listState) }
                    }
                    MessageListNewMessagesNavigationAction.ReloadAroundMessage -> {
                        boundaryPagingPolicy.reset()
                        messageListViewModel.loadMessagesAroundMessage(
                            message = entry.firstMessage,
                            completion = object : CompletionHandler {
                                override fun onSuccess() {
                                    locateCoordinator.requestLocateMessage(firstMessageId)
                                }

                                override fun onFailure(code: Int, desc: String) = Unit
                            }
                        )
                    }
                }
            }
            is MessageListFloatingEntry.Mention -> {
                val loadedId = displayedMessages.firstOrNull { it.sequence == entry.target.sequence }?.msgID
                if (!loadedId.isNullOrBlank()) {
                    locateCoordinator.requestLocateMessage(loadedId)
                    scope.launch { locateCoordinator.applyPending(displayedMessages, listState) }
                } else {
                    messageListViewModel.loadOlderMessagesUntilSequence(
                        sequence = entry.target.sequence,
                        maxLoadCount = MENTION_LOCATE_MAX_LOAD_COUNT,
                        completion = object : CompletionHandler {
                            override fun onSuccess() {
                                messageListViewModel.messageList.value
                                    .firstOrNull { it.sequence == entry.target.sequence }
                                    ?.msgID
                                    ?.let { locateCoordinator.requestLocateMessage(it) }
                            }

                            override fun onFailure(code: Int, desc: String) = Unit
                        }
                    )
                }
            }
            is MessageListFloatingEntry.BackToQuote -> {
                val returnMessageId = entry.returnMessage.msgID.takeIf { it.isNotBlank() } ?: return
                if (displayedMessages.any { it.msgID == returnMessageId }) {
                    locateCoordinator.requestScrollToMessage(returnMessageId)
                    scope.launch { locateCoordinator.applyPending(displayedMessages, listState) }
                } else {
                    messageListViewModel.loadMessagesAroundMessage(
                        message = entry.returnMessage,
                        completion = object : CompletionHandler {
                            override fun onSuccess() {
                                locateCoordinator.requestScrollToMessage(returnMessageId)
                            }

                            override fun onFailure(code: Int, desc: String) = Unit
                        }
                    )
                }
            }
        }
    }

    var lastTopMessageId by remember { mutableStateOf<String?>(null) }
    var lastKnownAtLatestPosition by remember { mutableStateOf(false) }
    LaunchedEffect(displayedMessages, latestReloadState.phase, latestReloadState.requestId) {
        removeRecalledNewMessagesFromFloatingEntry(displayedMessages)
        val previousTopMessageId = lastTopMessageId
        val wasAtLatestPosition = !previousTopMessageId.isNullOrBlank() && lastKnownAtLatestPosition
        val shouldSuppressAutoScrollForTargetNavigation =
            locateCoordinator.willApplyPendingTargetNavigation(displayedMessages)
        locateCoordinator.applyPending(displayedMessages, listState)
        if (latestReloadState.phase == MessageListLatestReloadPhase.SUCCEEDED) {
            val requestId = latestReloadState.requestId
            withTimeoutOrNull(LATEST_RELOAD_FRAME_TIMEOUT_MS) {
                withFrameNanos { }
            }
            if (latestReloadState.phase != MessageListLatestReloadPhase.SUCCEEDED ||
                latestReloadState.requestId != requestId
            ) {
                return@LaunchedEffect
            }
            displayedMessagesRef.value.firstOrNull()?.let { latestMessage ->
                lastTopMessageId = latestMessage.msgID
                scrollToLatest()
            }
            latestReloadState = latestReloadState.consumeSuccess()
            refreshFloatingEntryRef.value()
            return@LaunchedEffect
        }
        val newTopMessageId = displayedMessages.firstOrNull()?.msgID
        if (newTopMessageId.isNullOrEmpty()) {
            refreshFloatingEntry()
            return@LaunchedEffect
        }
        lastTopMessageId = newTopMessageId
        if (MessageListAutoScrollPolicy.shouldScrollAfterLatestChanged(
                previousLatestMessageId = previousTopMessageId,
                newLatestMessageId = newTopMessageId,
                wasAtLatestPosition = wasAtLatestPosition,
                suppressLatestAutoScroll = shouldSuppressAutoScrollForTargetNavigation
            )
        ) {
            scrollToLatest()
        }
        refreshFloatingEntry()
    }

    LaunchedEffect(messageListViewModel) {
        var unreadClearJob: Job? = null
        messageListViewModel.messageEvent.collect { event ->
            when (event) {
                is MessageEvent.OnReceiveNewMessage -> {
                    if (resumedState.value) {
                        unreadClearJob?.cancel()
                        unreadClearJob = launch {
                            delay(UNREAD_CLEAR_DEBOUNCE_MS)
                            if (resumedState.value) {
                                messageListViewModel.clearConversationUnreadCount()
                            }
                        }
                    }
                    if (!isAtLatestPosition(listStateRef.value)) {
                        floatingEntryState.onNewMessage(event.message, isAtLatest = false)
                        refreshFloatingEntryRef.value()
                    }
                }
            }
        }
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isUserScrollSession = true
                    if (MessageListInputDismissPolicy.shouldDismissInputForScroll(true)) {
                        postBlankAreaClickEvent()
                        listenCollapsed = true
                    }
                }
                is DragInteraction.Stop,
                is DragInteraction.Cancel -> {
                    yield()
                    if (!listState.isScrollInProgress) {
                        isUserScrollSession = false
                        lastKnownAtLatestPosition = isAtLatestPosition()
                        refreshFloatingEntry()
                    }
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                isUserScrollSession = false
                lastKnownAtLatestPosition = isAtLatestPosition()
                refreshFloatingEntry()
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collect { visibleItems ->
            lastKnownAtLatestPosition = isAtLatestPosition()
            val currentMessages = displayedMessagesRef.value
            val visibleMessages = visibleItems.mapNotNull { itemInfo ->
                currentMessages.getOrNull(itemInfo.index)
            }
            messageListViewModel.sendReadReceipts(visibleMessages)
            messageListViewModel.markVisibleCallMessagesRead(visibleMessages)
            MessageListReadReceiptSnapshot.syncVisible(visibleMessages, readReceiptSnapshots)
            if (!isProgrammaticScroll && isUserScrollSession) {
                val firstVisibleIndex = visibleItems.firstOrNull()?.index ?: -1
                val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1
                if (boundaryPagingPolicy.shouldLoadNewer(firstVisibleIndex)) {
                    messageListViewModel.loadMoreNewerMessage()
                }
                if (boundaryPagingPolicy.shouldLoadOlder(lastVisibleIndex, currentMessages.size)) {
                    messageListViewModel.loadMoreOlderMessage()
                }
            }
            refreshFloatingEntry()
        }
    }

    LaunchedEffect(listState, displayedMessages, isHostResumed) {
        while (true) {
            val visibleMessages = listState.layoutInfo.visibleItemsInfo.mapNotNull { itemInfo ->
                displayedMessages.getOrNull(itemInfo.index)
            }
            if (visibleMessages.isNotEmpty()) {
                messageListViewModel.sendReadReceipts(visibleMessages)
                messageListViewModel.markVisibleCallMessagesRead(visibleMessages)
            }
            MessageListReadReceiptSnapshot.syncVisible(visibleMessages, readReceiptSnapshots)
            delay(1000)
        }
    }

    LaunchedEffect(conversationID) {
        if (!ConversationIDUtil.isGroup(conversationID)) {
            return@LaunchedEffect
        }
        messageListViewModel.fetchConversationInfo { conversationInfo ->
            if (!isCurrentConversation()) {
                return@fetchConversationInfo
            }
            val mentionTarget = MessageListFloatingEntryPolicy.findOldestMentionTarget(
                conversationInfo?.groupAtInfoList.orEmpty()
            )
            floatingEntryState.onInitialMentionTarget(
                target = mentionTarget,
                visibility = MessageListMentionTargetVisibility.HIDDEN
            )
            refreshFloatingEntry()
        }
    }

    LaunchedEffect(shouldScrollToBottomAfterAsr) {
        if (shouldScrollToBottomAfterAsr) {
            delay(100)
            scrollToLatest()
            messageListViewModel.clearScrollToBottomAfterAsr()
        }
    }
    LaunchedEffect(shouldScrollToBottomAfterTranslation) {
        if (shouldScrollToBottomAfterTranslation) {
            delay(100)
            scrollToLatest()
            messageListViewModel.clearScrollToBottomAfterTranslation()
        }
    }

    DisposableEffect(conversationID) {
        val job = scope.onEvent<Map<*, *>> { event ->
            val source = event["source"] as? String
            val eventType = event["event"] as? String
            if (source == MESSAGE_INPUT_EVENT_SOURCE && eventType == EVENT_INPUT_INTERACT) {
                handleBackToLatestClickRef.value()
            }
            if (source == CHAT_SETTING_EVENT_SOURCE && eventType == EVENT_CHAT_BACKGROUND_CHANGED) {
                val changedConversationID = event["conversationID"] as? String
                if (changedConversationID == conversationID && config.background == null) {
                    persistedBackground = chatBackgroundStore.getImageUri(conversationID)
                        ?.let { MessageListBackground.Image(it) }
                }
            }
        }
        onDispose { job.cancel() }
    }

    var listWindowBounds by remember { mutableStateOf(Rect.Zero) }

    CompositionLocalProvider(
        LocalQuoteClickHandler provides { source, quote -> locateQuotedMessage(source, quote) },
        LocalBlankAreaClickHandler provides { handleBlankAreaClick() },
        LocalMessageListWindowBounds provides listWindowBounds,
        LocalMessageRenderActions provides renderActions,
        LocalMessageListTouchTargetHitTester provides touchTargetHitTester,
        LocalReadReceiptSnapshots provides readReceiptSnapshots,
        LocalVisibleMessageIds provides visibleMessageIds
    ) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                listWindowBounds = Rect(
                    left = pos.x,
                    top = pos.y,
                    right = pos.x + coords.size.width,
                    bottom = pos.y + coords.size.height
                )
            }
    ) {
        MessageListBackgroundLayer(background = listBackground)

        Column(modifier = Modifier.fillMaxSize()) {
            MessageListJoinCallBanner(conversationID = conversationID)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = config.horizontalPadding)
                        .onGloballyPositioned { coords ->
                            listLayoutCoordinates = coords
                        }
                        .pointerInput(touchTargetHitTester) {
                            detectTapGestures(onTap = { localOffset ->
                                val windowOffset = listLayoutCoordinates?.localToWindow(localOffset)
                                    ?: Offset.Zero
                                handleBlankAreaClick(
                                    isMessageTouchTargetHit = touchTargetHitTester.isHit(windowOffset)
                                )
                            })
                        },
                    state = listState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.Top
                ) {
                    itemsIndexed(
                        items = displayedMessages,
                        key = { _, item -> item.msgID }
                    ) { index, message ->
                        MessageItem(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(
                                        durationMillis = MessageListAnimationDefaults.ITEM_FADE_IN_DURATION_MS,
                                        easing = FastOutSlowInEasing
                                    ),
                                    placementSpec = if (isInputSurfaceHeightAnimating) {
                                        null
                                    } else {
                                        tween(
                                            durationMillis = MessageListAnimationDefaults.ITEM_PLACEMENT_DURATION_MS,
                                            easing = FastOutSlowInEasing
                                        )
                                    },
                                    fadeOutSpec = tween(
                                        durationMillis = MessageListAnimationDefaults.ITEM_FADE_OUT_DURATION_MS,
                                        easing = FastOutSlowInEasing
                                    )
                                ),
                            index = index,
                            message = message,
                            onUserLongPress = {
                                EventBus.post {
                                    mapOf(
                                        "source" to "MessageList",
                                        "event" to "onUserLongPress",
                                        "userID" to it,
                                        "message" to message
                                    )
                                }
                            },
                            onUserClick = {
                                onUserClick(it ?: "")
                            }
                        )
                    }
                }

                if (loadingState.isLoadingOlder) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(24.dp),
                        color = colors.textColorSecondary,
                        strokeWidth = 2.dp
                    )
                }
                if (loadingState.isLoadingNewer) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .size(24.dp),
                        color = colors.textColorSecondary,
                        strokeWidth = 2.dp
                    )
                }
            }

            if (isMultiSelectMode) {
                MultiSelectBottomBar(
                    selectedCount = selectedMessages.size,
                    onDelete = {
                        if (selectedMessages.isNotEmpty()) {
                            pendingDeleteConfirm = {
                                messageListViewModel.deleteSelectedMessages()
                                messageListViewModel.exitMultiSelectMode()
                            }
                            showDeleteDialog = true
                        }
                    },
                    onForwardSeparate = { handleForwardSelection(MessageForwardType.SEPARATE) },
                    onForwardMerge = { handleForwardSelection(MessageForwardType.MERGED) }
                )
            }
        }

        floatingEntry?.let { entry ->
            MessageListFloatingEntryCard(
                entry = entry,
                onClick = { handleFloatingEntryClick(it) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (isMultiSelectMode) 80.dp else 16.dp)
            )
        }

        if (listenPlaybackState.isActive) {
            ListenPlaybackBar(
                state = listenPlaybackState,
                onClose = { messageListViewModel.stopListenFromHere() },
                collapsed = listenCollapsed,
                onCollapsedChange = { listenCollapsed = it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = maxHeight * 0.4f)
            )
        }

        if (showForwardDialog) {
            ForwardTargetSelector(
                onDismiss = {
                    showForwardDialog = false
                    messageListViewModel.clearSingleMessageForward()
                },
                onConfirm = { conversationIDList ->
                    showForwardDialog = false
                    performMessageForward(
                        messages = forwardingMessages.toList(),
                        conversationIDs = conversationIDList,
                        exitMultiSelect = isMultiSelectMode
                    )
                }
            )
        }

        MessageReadReceiptDialog(
            isVisible = readReceiptMessage != null,
            message = readReceiptMessage,
            onDismiss = { messageListViewModel.clearReadReceiptDialog() },
            onUserClick = onUserClick
        )

        AlertDialog(
            isVisible = showDeleteDialog,
            onDismiss = {
                showDeleteDialog = false
                pendingDeleteConfirm = null
            },
            message = stringResource(R.string.message_list_delete_messages_tips),
            onCancel = {
                showDeleteDialog = false
                pendingDeleteConfirm = null
            },
            onConfirm = {
                pendingDeleteConfirm?.invoke()
                pendingDeleteConfirm = null
                showDeleteDialog = false
            }
        )

        longPressMenuRequest?.let { request ->
            MessageLongPressMenu(
                message = request.message,
                viewModel = messageListViewModel,
                config = config,
                anchorBounds = request.anchorBounds,
                onDismiss = { messageListViewModel.clearLongPressActionDialog() },
                onDeleteRequested = { onConfirm ->
                    pendingDeleteConfirm = onConfirm
                    showDeleteDialog = true
                }
            )
        }

        if (showReactionEmojiPickerForMessage != null) {
            ReactionEmojiPickerSheet(
                onDismiss = { messageListViewModel.clearEmojiPicker() },
                onEmojiClick = { emoji ->
                    val isReacted = showReactionEmojiPickerForMessage!!.reactionList.any {
                        it.reactionID == emoji.key && it.reactedByMyself
                    }
                    if (isReacted) {
                        messageListViewModel.removeMessageReaction(showReactionEmojiPickerForMessage!!, emoji.key)
                    } else {
                        messageListViewModel.addMessageReaction(showReactionEmojiPickerForMessage!!, emoji.key)
                        RecentEmojiManager.updateRecentEmoji(emoji.key)
                    }
                    messageListViewModel.clearEmojiPicker()
                }
            )
        }

        if (auxiliaryTextForwardContent != null) {
            ForwardTargetSelector(
                onDismiss = { messageListViewModel.clearAuxiliaryTextForward() },
                onConfirm = { conversationIDList ->
                    val textToForward = auxiliaryTextForwardContent!!
                    val partition = ChatbotForwardTargetPolicy.partition(conversationIDList)
                    if (partition.rejectedChatbotTargets.isNotEmpty()) {
                        Toast.warning(
                            context,
                            context.getString(R.string.message_list_chatbot_forward_not_supported)
                        )
                    }
                    val allowedConversationIDs = partition.allowedTargets
                    if (allowedConversationIDs.isEmpty()) {
                        messageListViewModel.clearAuxiliaryTextForward()
                        return@ForwardTargetSelector
                    }
                    messageListViewModel.sendAuxiliaryTextToConversations(
                        text = textToForward,
                        conversationIDList = allowedConversationIDs,
                        completion = object : CompletionHandler {
                            override fun onSuccess() {
                                messageListViewModel.clearAuxiliaryTextForward()
                            }

                            override fun onFailure(code: Int, desc: String) {
                                messageListViewModel.clearAuxiliaryTextForward()
                            }
                        }
                    )
                }
            )
        }
    }
    }
}

private fun resolveChatbotPlaceholderSender(
    sourceMessages: List<MessageInfo>,
    conversationID: String,
    viewModel: MessageListViewModel
): MessageSenderInfo? {
    val previousSender = sourceMessages.asReversed().firstOrNull { message ->
        val data = ChatbotMessageProtocol.parse(message)
        !message.isSentBySelf &&
            (data?.source == ChatbotMessageSource.FLOW ||
                data?.source == ChatbotMessageSource.ERROR)
    }?.from
    if (previousSender != null) {
        return previousSender
    }
    val conversation = viewModel.conversationListState.conversationList.value
        .firstOrNull { it.conversationID == conversationID }
        ?: return null
    return MessageSenderInfo(
        userID = ChatbotConversationPolicy.targetID(conversationID).orEmpty(),
        avatarURL = conversation.avatarURL,
        nickname = conversation.title
    )
}
