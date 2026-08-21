package io.trtc.tuikit.chat.uikit.components.messagelist.ui
import android.content.res.ColorStateList
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.common.findLifecycleOwner
import io.trtc.tuikit.chat.uikit.components.common.onEvent
import io.trtc.tuikit.chat.uikit.components.messagelist.background.MessageListBackgroundView
import io.trtc.tuikit.chat.uikit.components.messagelist.background.MmkvChatBackgroundStore
import io.trtc.tuikit.chat.uikit.components.messagelist.adapter.MessageListAdapter
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListBackground
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.listen.ListenPlaybackBar
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomActionContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.forward.ForwardTargetSelectorDialog
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListAlignmentController
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListBoundaryPagingPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListItemAnimatorFactory
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListLocateCoordinator
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListLayoutManager
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups.AuxiliaryTextLongPressPopup
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups.MessageLongPressPopup
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions.ReactionDetailDialog
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions.ReactionEmojiPickerDialog
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts.MessageListReadReceiptController
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts.MessageReadReceiptDialog
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.selection.MessageListSelectionController
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModelFactory
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.composeMessageLongPressActions
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageEvent
import io.trtc.tuikit.atomicxcore.api.message.MessageForwardType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessageListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private companion object {
        const val MENTION_LOCATE_MAX_LOAD_COUNT = 20
        const val UNREAD_CLEAR_DEBOUNCE_MS = 300L
    }

    private val backgroundView: MessageListBackgroundView
    private val recyclerView: RecyclerView
    private val loadingOlderView: ProgressBar
    private val loadingNewerView: ProgressBar
    private val floatingEntryCard: CardView
    private val floatingEntryIconView: ImageView
    private val floatingEntryTextView: TextView
    private val joinCallBannerContainer: FrameLayout
    private val multiSelectBarContainer: FrameLayout
    private val listenPlaybackBar: ListenPlaybackBar
    private lateinit var adapter: MessageListAdapter
    private lateinit var viewModel: MessageListViewModel
    private var viewScope: CoroutineScope? = null
    private var isAdapterDataObserverRegistered = false
    private val alignmentController: MessageListAlignmentController
    private val adapterDataObserver: RecyclerView.AdapterDataObserver

    private var onMultiSelectStateChanged: (Boolean) -> Unit = {}
    private var onUserClick: (String) -> Unit = {}

    fun exitMultiSelectMode() {
        if (::viewModel.isInitialized) {
            viewModel.exitMultiSelectMode()
        }
    }

    fun isInMultiSelectMode(): Boolean {
        return ::viewModel.isInitialized && viewModel.isMultiSelectMode.value
    }

    private var config: MessageListConfigProtocol = ChatMessageListConfig()
    private val themeStore = ThemeStore.shared(context)
    private val chatBackgroundStore by lazy { MmkvChatBackgroundStore(context) }
    private val joinCallBannerController: MessageListJoinCallBannerController
    private val locateCoordinator: MessageListLocateCoordinator
    private val readReceiptController: MessageListReadReceiptController
    private val selectionController: MessageListSelectionController
    private val floatingEntryStateController = MessageListFloatingEntryStateController()
    private val boundaryPagingPolicy = MessageListBoundaryPagingPolicy()
    private var isUserScrollSession = false
    private var suppressLatestAutoScrollFromMessageId: String? = null
    private val clearSuppressedLatestAutoScrollRunnable = Runnable {
        suppressLatestAutoScrollFromMessageId = null
    }
    private var scrollToLatestAfterReload = false
    private var currentConversationID: String? = null
    private var hostLifecycleOwner: LifecycleOwner? = null
    private var hostLifecycleObserver: DefaultLifecycleObserver? = null
    private var isHostResumed = false
    private var isResumedAndShown = false
    private var hasPendingDebouncedUnreadClear = false
    private val debounceClearUnreadRunnable = Runnable {
        hasPendingDebouncedUnreadClear = false
        if (isResumedAndShown && ::viewModel.isInitialized) {
            viewModel.clearConversationUnreadCount()
        }
    }

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        LayoutInflater.from(context).inflate(R.layout.message_list_view, this, true)
        backgroundView = findViewById(R.id.message_list_background_view)
        recyclerView = findViewById(R.id.message_list_recycler_view)
        recyclerView.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        loadingOlderView = findViewById(R.id.message_list_loading_older)
        loadingNewerView = findViewById(R.id.message_list_loading_newer)
        floatingEntryCard = findViewById(R.id.message_list_floating_entry_card)
        floatingEntryIconView = findViewById(R.id.message_list_floating_entry_icon)
        floatingEntryTextView = findViewById(R.id.message_list_floating_entry_text)
        joinCallBannerContainer = findViewById(R.id.message_list_join_call_banner)
        multiSelectBarContainer = findViewById(R.id.message_list_multi_select_bar)
        listenPlaybackBar = findViewById(R.id.message_list_listen_playback_bar)
        listenPlaybackBar.setOnCloseClickListener {
            if (::viewModel.isInitialized) {
                viewModel.stopListenFromHere()
            }
        }
        backgroundView.setDefaultBackgroundColor(
            themeStore.themeState.value.currentTheme.tokens.color.bgColorOperate
        )
        alignmentController = MessageListAlignmentController(recyclerView)
        adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                alignmentController.requestAlignment()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                alignmentController.requestAlignment()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                alignmentController.requestAlignment()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                alignmentController.requestAlignment()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                alignmentController.requestAlignment()
            }
        }
        joinCallBannerController = MessageListJoinCallBannerController(context, joinCallBannerContainer)
        locateCoordinator = MessageListLocateCoordinator(
            recyclerView = recyclerView,
            adapterProvider = { if (::adapter.isInitialized) adapter else null }
        )
        readReceiptController = MessageListReadReceiptController(
            recyclerView = recyclerView,
            adapterProvider = { if (::adapter.isInitialized) adapter else null },
            messagesProvider = { if (::viewModel.isInitialized) viewModel.messageList.value else emptyList() },
            isAttachedToWindowProvider = { isAttachedToWindow },
            onVisibleMessagesRead = { visibleMessages ->
                if (::viewModel.isInitialized) {
                    val markedMessageIds = viewModel.markVisibleCallMessagesRead(visibleMessages)
                    if (markedMessageIds.isNotEmpty()) {
                        postRefreshMessages(markedMessageIds)
                    }
                    viewModel.sendReadReceipts(visibleMessages)
                }
            }
        )
        selectionController = MessageListSelectionController(
            context = context,
            container = multiSelectBarContainer,
            selectedMessagesProvider = {
                if (::viewModel.isInitialized) viewModel.selectedMessages.value else emptySet()
            },
            onDeleteSelectedMessages = {
                if (::viewModel.isInitialized) {
                    viewModel.deleteSelectedMessages()
                }
            },
            onExitMultiSelectMode = {
                if (::viewModel.isInitialized) {
                    viewModel.exitMultiSelectMode()
                }
            },
            onForwardSelectedMessages = { selectedMessages, forwardType ->
                viewModel.forwardType = forwardType
                showForwardTargetSelector(
                    onConfirm = { conversationIDs ->
                        performMessageForward(
                            messages = selectedMessages,
                            conversationIDs = conversationIDs,
                            exitMultiSelect = true
                        )
                    },
                    onDismiss = {}
                )
            }
        )
        applyFloatingEntryLayout()
        applyFloatingEntryTheme()
        floatingEntryCard.setOnClickListener {
            handleFloatingEntryClick(floatingEntryStateController.currentEntry() ?: return@setOnClickListener)
        }

        val layoutManager = MessageListLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator = MessageListItemAnimatorFactory.create()
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            alignmentController.applyAlignmentImmediately()
            locateCoordinator.schedulePendingLocateHighlight()
        }

        val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val isMessageTouchTargetHit =
                        MessageListTouchTargetHitTester.isMessageTouchTargetHit(recyclerView, e)
                    if (MessageListInputDismissPolicy.shouldDismissInputForTap(isMessageTouchTargetHit)) {
                        postBlankAreaClickEvent()
                        collapseListenPlaybackBar()
                    }
                    return false
                }
            }
        )
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
        })
    }

    fun setup(
        conversationID: String,
        config: MessageListConfigProtocol = ChatMessageListConfig(),
        locateMessage: MessageInfo? = null,
        onMultiSelectStateChanged: (Boolean) -> Unit = {},
        onUserClick: (String) -> Unit = {}
    ) {
        this.config = config
        currentConversationID = conversationID
        this.onMultiSelectStateChanged = onMultiSelectStateChanged
        this.onUserClick = onUserClick
        locateCoordinator.reset(locateMessage?.msgID)
        alignmentController.reset()
        boundaryPagingPolicy.reset()
        isUserScrollSession = false
        clearSuppressedLatestAutoScroll()
        scrollToLatestAfterReload = false
        floatingEntryStateController.reset()
        refreshFloatingEntry()

        cleanupBinding()

        val messageListStore = MessageListStore.create(conversationID)
        val owner = context.findViewModelStoreOwner()
            ?: error("MessageListView requires a ViewModelStoreOwner host context.")
        val viewModelKey = buildViewModelKey(conversationID, locateMessage)
        viewModel = ViewModelProvider(
            owner,
            MessageListViewModelFactory(
                messageListStore = messageListStore,
                conversationID = conversationID,
                locateMessage = locateMessage,
                messageListConfig = config
            )
        ).get(viewModelKey, MessageListViewModel::class.java)

        val resolver = MessageRendererResolver(
            customRules = (config as? ChatMessageListConfig)?.customRenderRules.orEmpty()
        )
        val renderActions = createMessageRenderActions()

        adapter = MessageListAdapter(
            context = context,
            viewModel = viewModel,
            config = config,
            onItemLongClick = { message, anchorView ->
                showLongPressMenu(message, anchorView)
            },
            onAuxiliaryTextLongClick = { message, anchorView, actions ->
                showAuxiliaryTextLongPressMenu(message, anchorView, actions)
            },
            onUserClick = { userID ->
                onUserClick(userID)
            },
            onUserLongClick = { message, userID ->
                postUserLongPressEvent(message, userID)
            },
            onQuoteClick = { sourceMessage, quoteInfo ->
                locateQuotedMessage(sourceMessage, quoteInfo)
            },
            showMessageReadReceipt = config.isShowReadReceipt,
            resolver = resolver,
            renderActions = renderActions
        )
        recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(adapterDataObserver)
        isAdapterDataObserverRegistered = true
        recyclerView.addOnScrollListener(scrollListener)
        alignmentController.requestAlignment()
        recyclerView.addOnChildAttachStateChangeListener(locateCoordinator.childAttachStateChangeListener)
        applyMessageListBackground()
        joinCallBannerController.bind(conversationID)
        requestInitialMentionEntry(conversationID)

        if (isAttachedToWindow) {
            bindHostLifecycle()
            bindViewModel()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alignmentController.attach()
        if (!::viewModel.isInitialized) {
            return
        }
        currentConversationID?.let { joinCallBannerController.bind(it) }
        bindHostLifecycle()
        bindViewModel()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        refreshResumedAndShown()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        refreshResumedAndShown()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        refreshResumedAndShown()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unbindHostLifecycle()
        alignmentController.detach()
        locateCoordinator.cancel()
        readReceiptController.cancel()
        viewScope?.cancel()
        viewScope = null
        joinCallBannerController.release()
        if (::viewModel.isInitialized) {
            viewModel.stopListenFromHere()
        }
    }

    fun release() {
        cleanupBinding()
        alignmentController.detach()
        recyclerView.adapter = null
    }

    private fun bindViewModel() {
        if (viewScope != null || !::viewModel.isInitialized || !::adapter.isInitialized) {
            return
        }

        viewModel.initializeAudioPlayer()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope
        readReceiptController.scheduleRefresh()
        var previousProcessingAuxiliaryTextMessageIds = viewModel.processingAuxiliaryTextMessageIds.value
        var previousHiddenAuxiliaryTextMessageIds = viewModel.hiddenAuxiliaryTextMessageIds.value

        scope.launch {
            viewModel.messageList.collectLatest { list ->
                val currentLatestMessageId = adapter.currentList.firstOrNull()?.msgID
                val newLatestMessageId = list.firstOrNull()?.msgID
                val shouldSuppressLatestAutoScroll =
                    !currentLatestMessageId.isNullOrBlank() &&
                        currentLatestMessageId == suppressLatestAutoScrollFromMessageId &&
                        currentLatestMessageId != newLatestMessageId
                if (shouldSuppressLatestAutoScroll) {
                    clearSuppressedLatestAutoScroll()
                }
                if (!currentLatestMessageId.isNullOrBlank() &&
                    !newLatestMessageId.isNullOrBlank() &&
                    currentLatestMessageId != newLatestMessageId &&
                    !shouldSuppressLatestAutoScroll &&
                    locateCoordinator.isLatestMessageCompletelyVisible()
                ) {
                    locateCoordinator.requestScrollToLatestMessage(newLatestMessageId)
                }
                adapter.submitList(list) {
                    if (list.isNotEmpty()) {
                        alignmentController.markDataLoaded()
                    }
                    alignmentController.requestAlignment()
                    locateCoordinator.handlePendingLocateMessage(list)
                    locateCoordinator.scrollToPendingMessageIfReady()
                    locateCoordinator.scrollToLatestMessageIfReady()
                    if (scrollToLatestAfterReload) {
                        scrollToLatestAfterReload = false
                        recyclerView.scrollToPosition(0)
                        recyclerView.post { updateFloatingEntryForScroll() }
                    }
                    readReceiptController.postSyncVisibleReadReceipts()
                    updateFloatingEntryForScroll()
                }
            }
        }

        scope.launch {
            viewModel.loadingState.collectLatest { state ->
                loadingOlderView.visibility = if (state.isLoadingOlder) View.VISIBLE else View.GONE
                loadingNewerView.visibility = if (state.isLoadingNewer) View.VISIBLE else View.GONE
            }
        }

        scope.launch {
            viewModel.isMultiSelectMode.collectLatest { isMultiSelect ->
                onMultiSelectStateChanged(isMultiSelect)
                if (isMultiSelect) {
                    selectionController.show()
                } else {
                    selectionController.hide()
                }
                adapter.notifyDataSetChanged()
                alignmentController.requestAlignment()
            }
        }

        scope.launch {
            viewModel.selectedMessages.collectLatest { selected ->
                selectionController.updateSelectedCount(selected.size)
                adapter.notifyDataSetChanged()
            }
        }

        scope.launch {
            viewModel.processingAuxiliaryTextMessageIds.collectLatest { current ->
                val changedIds = MessageListAuxiliaryTextRefreshPolicy.changedMessageIds(
                    previous = previousProcessingAuxiliaryTextMessageIds,
                    current = current
                )
                previousProcessingAuxiliaryTextMessageIds = current
                postRefreshMessages(changedIds)
            }
        }

        scope.launch {
            viewModel.hiddenAuxiliaryTextMessageIds.collectLatest { current ->
                val changedIds = MessageListAuxiliaryTextRefreshPolicy.changedMessageIds(
                    previous = previousHiddenAuxiliaryTextMessageIds,
                    current = current
                )
                previousHiddenAuxiliaryTextMessageIds = current
                postRefreshMessages(changedIds)
            }
        }

        scope.launch {
            viewModel.translationAtUserNameMessageIds.collectLatest { messageId ->
                postRefreshMessages(listOf(messageId))
            }
        }

        scope.launch {
            viewModel.messageEvent.collect { event ->
                when (event) {
                    is MessageEvent.OnReceiveNewMessage -> {
                        scheduleDebouncedUnreadClear()
                        val isLatestVisible = locateCoordinator.isLatestMessageCompletelyVisible()
                        if (isLatestVisible) {
                            locateCoordinator.requestScrollToLatestMessage(event.message.msgID)
                        } else {
                            floatingEntryStateController.onNewMessage(
                                message = event.message,
                                isLatestCompletelyVisible = false
                            )
                            refreshFloatingEntry()
                        }
                    }
                }
            }
        }

        scope.launch {
            viewModel.audioPlayingState.collectLatest {
                adapter.notifyDataSetChanged()
            }
        }

        scope.launch {
            viewModel.listenPlaybackState.collectLatest { state ->
                if (state.isActive) {
                    listenPlaybackBar.render(state.isLoading, state.currentText)
                    listenPlaybackBar.visibility = View.VISIBLE
                } else {
                    listenPlaybackBar.collapse()
                    listenPlaybackBar.visibility = View.GONE
                }
            }
        }

        scope.launch {
            viewModel.onSingleMessageForward.collectLatest { message ->
                if (message != null) {
                    viewModel.forwardType = MessageForwardType.SEPARATE
                    showForwardTargetSelector(
                        onConfirm = { conversationIDs ->
                            performMessageForward(
                                messages = listOf(message),
                                conversationIDs = conversationIDs,
                                exitMultiSelect = false
                            )
                        },
                        onDismiss = {
                            viewModel.clearSingleMessageForward()
                        }
                    )
                }
            }
        }

        scope.launch {
            viewModel.auxiliaryTextForwardContent.collectLatest { text ->
                if (!text.isNullOrBlank()) {
                    showForwardTargetSelector(
                        onConfirm = { conversationIDs ->
                            performAuxiliaryTextForward(text, conversationIDs)
                        },
                        onDismiss = {
                            viewModel.clearAuxiliaryTextForward()
                        }
                    )
                }
            }
        }

        scope.launch {
            viewModel.readReceiptMessage.collectLatest { message ->
                if (message != null) {
                    MessageReadReceiptDialog(
                        context = context,
                        message = message,
                        onUserClick = onUserClick
                    ).apply {
                        setOnDismissListener {
                            viewModel.clearReadReceiptDialog()
                        }
                    }.show()
                }
            }
        }

        scope.launch {
            viewModel.reactionDetailMessage.collectLatest { message ->
                if (message != null) {
                    ReactionDetailDialog(
                        context = context,
                        message = message,
                        onReactionRemoved = {
                            adapter.notifyDataSetChanged()
                        }
                    ).apply {
                        setOnDismissListener {
                            viewModel.clearReactionDetail()
                        }
                    }.show()
                }
            }
        }

        scope.launch {
            viewModel.showEmojiPickerForMessage.collectLatest { message ->
                if (message != null) {
                    ReactionEmojiPickerDialog(
                        context = context,
                        message = message,
                        viewModel = viewModel
                    ).apply {
                        setOnDismissListener {
                            viewModel.clearEmojiPicker()
                        }
                    }.show()
                }
            }
        }

        scope.launch {
            themeStore.themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                backgroundView.setDefaultBackgroundColor(colors.bgColorOperate)
                applyFloatingEntryTheme()
                listenPlaybackBar.applyColors(colors)
                adapter.notifyDataSetChanged()
                alignmentController.requestAlignment()
            }
        }

        scope.onEvent<Map<*, *>> { event ->
            val source = event["source"] as? String
            val eventType = event["event"] as? String
            if (source == "MessageInput" && eventType == "onInputInteract") {
                if (::adapter.isInitialized && ::viewModel.isInitialized) {
                    handleBackToLatestClick()
                }
            }
            if (source == "ChatSetting" && eventType == "onChatBackgroundChanged") {
                val conversationID = event["conversationID"] as? String
                if (conversationID == currentConversationID && config.background == null) {
                    applyMessageListBackground()
                }
            }
        }
    }

    private fun cleanupBinding() {
        unbindHostLifecycle()
        readReceiptController.cancel()
        locateCoordinator.cancel()
        clearSuppressedLatestAutoScroll()
        isUserScrollSession = false
        scrollToLatestAfterReload = false
        viewScope?.cancel()
        viewScope = null
        if (::adapter.isInitialized && isAdapterDataObserverRegistered) {
            adapter.unregisterAdapterDataObserver(adapterDataObserver)
            isAdapterDataObserverRegistered = false
        }
        if (::adapter.isInitialized) {
            recyclerView.removeOnScrollListener(scrollListener)
        }
        recyclerView.removeOnChildAttachStateChangeListener(locateCoordinator.childAttachStateChangeListener)
        joinCallBannerController.release()
    }

    private fun bindHostLifecycle() {
        if (hostLifecycleObserver != null) {
            return
        }
        val owner = findViewTreeLifecycleOwner() ?: context.findLifecycleOwner() ?: return
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                isHostResumed = true
                refreshResumedAndShown()
            }

            override fun onPause(owner: LifecycleOwner) {
                isHostResumed = false
                refreshResumedAndShown()
            }
        }
        hostLifecycleOwner = owner
        hostLifecycleObserver = observer
        owner.lifecycle.addObserver(observer)
        refreshResumedAndShown()
    }

    private fun unbindHostLifecycle() {
        isHostResumed = false
        refreshResumedAndShown()
        val owner = hostLifecycleOwner
        val observer = hostLifecycleObserver
        hostLifecycleOwner = null
        hostLifecycleObserver = null
        if (owner != null && observer != null) {
            owner.lifecycle.removeObserver(observer)
        }
        flushDebouncedUnreadClear()
    }

    private fun refreshResumedAndShown() {
        val active = isHostResumed && isShown
        if (active == isResumedAndShown) {
            return
        }
        isResumedAndShown = active
        if (active) {
            cancelDebouncedUnreadClear()
            if (::viewModel.isInitialized) {
                viewModel.syncConversationAsRead()
            }
        } else {
            flushDebouncedUnreadClear()
        }
    }

    private fun scheduleDebouncedUnreadClear() {
        if (!isResumedAndShown || !::viewModel.isInitialized) {
            return
        }
        hasPendingDebouncedUnreadClear = true
        removeCallbacks(debounceClearUnreadRunnable)
        postDelayed(debounceClearUnreadRunnable, UNREAD_CLEAR_DEBOUNCE_MS)
    }

    private fun flushDebouncedUnreadClear() {
        removeCallbacks(debounceClearUnreadRunnable)
        if (!hasPendingDebouncedUnreadClear || !::viewModel.isInitialized) {
            hasPendingDebouncedUnreadClear = false
            return
        }
        hasPendingDebouncedUnreadClear = false
        viewModel.clearConversationUnreadCount()
    }

    private fun cancelDebouncedUnreadClear() {
        removeCallbacks(debounceClearUnreadRunnable)
        hasPendingDebouncedUnreadClear = false
    }

    private fun clearSuppressedLatestAutoScroll() {
        recyclerView.removeCallbacks(clearSuppressedLatestAutoScrollRunnable)
        suppressLatestAutoScrollFromMessageId = null
    }

    private fun buildViewModelKey(conversationID: String, locateMessage: MessageInfo?): String {
        return "${MessageListViewModel::class.java.name}:${System.identityHashCode(this)}:" +
            "$conversationID:${locateMessage?.msgID.orEmpty()}"
    }

    private fun postRefreshMessages(messageIds: List<String>) {
        recyclerView.post {
            if (!::adapter.isInitialized || !::viewModel.isInitialized) {
                return@post
            }
            val messages = viewModel.messageList.value
            messageIds.forEach { messageId ->
                val index = messages.indexOfFirst { it.msgID == messageId }
                if (index >= 0) {
                    adapter.notifyItemChanged(index)
                }
            }
        }
    }

    private fun applyMessageListBackground() {
        val persistedBackground = currentConversationID
            ?.let { chatBackgroundStore.getImageUri(it) }
            ?.let { MessageListBackground.Image(it) }
        backgroundView.setMessageListBackground(config.background ?: persistedBackground)
    }

    private fun requestInitialMentionEntry(conversationID: String) {
        if (!ConversationIDUtil.isGroup(conversationID)) {
            return
        }
        viewModel.fetchConversationInfo { conversationInfo ->
            if (currentConversationID != conversationID) {
                return@fetchConversationInfo
            }
            val mentionTarget = MessageListFloatingEntryPolicy.findOldestMentionTarget(
                conversationInfo?.groupAtInfoList.orEmpty()
            )
            floatingEntryStateController.onInitialMentionTarget(
                target = mentionTarget,
                visibility = mentionTarget?.let { mentionTargetVisibility(it) }
                    ?: MessageListMentionTargetVisibility.UNKNOWN
            )
            refreshFloatingEntry()
            if (mentionTarget != null) {
                recyclerView.post {
                    if (currentConversationID == conversationID) {
                        updateFloatingEntryForScroll()
                    }
                }
            }
        }
    }

    private fun updateFloatingEntryForScroll() {
        floatingEntryStateController.onMentionTargetVisibilityChanged(
            visibility = currentMentionTargetVisibility()
        )
        floatingEntryStateController.onScroll(
            distanceFromLatestPx = distanceFromLatestMessagePx(),
            viewportHeightPx = recyclerView.height,
            isLatestCompletelyVisible = locateCoordinator.isLatestMessageCompletelyVisible(),
            isReturnMessageCompletelyVisible = isBackToQuoteReturnMessageCompletelyVisible()
        )
        refreshFloatingEntry()
    }

    private fun isBackToQuoteReturnMessageCompletelyVisible(): Boolean {
        val returnMessageId = floatingEntryStateController.currentBackToQuoteReturnMessage()
            ?.msgID
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return isMessageCompletelyVisible(returnMessageId)
    }

    private fun distanceFromLatestMessagePx(): Int {
        if (locateCoordinator.isLatestMessageCompletelyVisible()) {
            return 0
        }
        return MessageListFloatingEntryPolicy.distanceFromBottomPx(
            scrollRangePx = recyclerView.computeVerticalScrollRange(),
            scrollExtentPx = recyclerView.computeVerticalScrollExtent(),
            scrollOffsetPx = recyclerView.computeVerticalScrollOffset()
        )
    }

    private fun currentMentionTargetVisibility(): MessageListMentionTargetVisibility {
        val target = floatingEntryStateController.currentMentionTarget()
            ?: return MessageListMentionTargetVisibility.UNKNOWN
        return mentionTargetVisibility(target)
    }

    private fun mentionTargetVisibility(target: MessageListMentionTarget): MessageListMentionTargetVisibility {
        if (!::viewModel.isInitialized) {
            return MessageListMentionTargetVisibility.UNKNOWN
        }
        val messages = viewModel.messageList.value
        val targetIndex = messages.indexOfFirst { it.sequence == target.sequence }
        if (targetIndex < 0) {
            return if (messages.isEmpty()) {
                MessageListMentionTargetVisibility.UNKNOWN
            } else {
                MessageListMentionTargetVisibility.HIDDEN
            }
        }
        val layoutManager = recyclerView.layoutManager as? MessageListLayoutManager
            ?: return MessageListMentionTargetVisibility.UNKNOWN
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        if (firstVisibleItem == RecyclerView.NO_POSITION || lastVisibleItem == RecyclerView.NO_POSITION) {
            return MessageListMentionTargetVisibility.UNKNOWN
        }
        return if (targetIndex in firstVisibleItem..lastVisibleItem) {
            MessageListMentionTargetVisibility.VISIBLE
        } else {
            MessageListMentionTargetVisibility.HIDDEN
        }
    }

    private fun refreshFloatingEntry() {
        val entry = floatingEntryStateController.currentEntry()
        if (entry == null) {
            floatingEntryCard.visibility = View.GONE
            return
        }

        floatingEntryTextView.text = floatingEntryText(entry)
        floatingEntryIconView.rotation = MessageListFloatingEntryPolicy.iconRotationDegrees(entry)
        floatingEntryCard.visibility = View.VISIBLE
    }

    private fun floatingEntryText(entry: MessageListFloatingEntry): String {
        return when (entry) {
            MessageListFloatingEntry.BackToLatest -> {
                context.getString(R.string.message_list_floating_back_to_latest)
            }
            is MessageListFloatingEntry.NewMessages -> {
                resources.getQuantityString(
                    R.plurals.message_list_floating_new_messages,
                    entry.count,
                    entry.count
                )
            }
            is MessageListFloatingEntry.Mention -> {
                when (entry.target.kind) {
                    MessageListMentionKind.AT_ALL -> context.getString(R.string.message_list_floating_at_all)
                    MessageListMentionKind.AT_ME -> context.getString(
                        R.string.message_list_floating_someone_mentioned_me
                    )
                    else -> context.getString(R.string.message_list_floating_someone_mentioned_me)
                }
            }
            is MessageListFloatingEntry.BackToQuote -> {
                context.getString(R.string.message_list_floating_back_to_quote)
            }
        }
    }

    private fun handleFloatingEntryClick(entry: MessageListFloatingEntry) {
        floatingEntryStateController.consume(entry)
        refreshFloatingEntry()
        when (entry) {
            MessageListFloatingEntry.BackToLatest -> {
                handleBackToLatestClick()
            }
            is MessageListFloatingEntry.NewMessages -> {
                handleNewMessagesClick(entry)
            }
            is MessageListFloatingEntry.Mention -> {
                locateMentionTarget(entry.target)
            }
            is MessageListFloatingEntry.BackToQuote -> {
                returnToQuoteSource(entry.returnMessage)
            }
        }
    }

    private fun handleNewMessagesClick(entry: MessageListFloatingEntry.NewMessages) {
        val firstMessageId = entry.firstMessage.msgID.takeIf { it.isNotBlank() } ?: return
        val isFirstNewMessageLoaded = viewModel.messageList.value.any { it.msgID == firstMessageId }
        when (MessageListNewMessagesNavigationPolicy.action(isFirstNewMessageLoaded)) {
            MessageListNewMessagesNavigationAction.LocateLoadedMessage -> {
                locateCoordinator.requestLocateMessage(firstMessageId)
            }
            MessageListNewMessagesNavigationAction.ReloadAroundMessage -> {
                boundaryPagingPolicy.reset()
                viewModel.loadMessagesAroundMessage(
                    message = entry.firstMessage,
                    completion = object : CompletionHandler {
                        override fun onSuccess() {
                            locateCoordinator.requestLocateMessage(firstMessageId)
                            recyclerView.post { updateFloatingEntryForScroll() }
                        }

                        override fun onFailure(code: Int, desc: String) = Unit
                    }
                )
            }
        }
    }

    private fun handleBackToLatestClick() {
        when (MessageListBackToLatestPolicy.action(viewModel.hasMoreNewerMessage.value)) {
            MessageListBackToLatestAction.ReloadLatestMessages -> {
                scrollToLatestAfterReload = true
                boundaryPagingPolicy.reset()
                viewModel.reloadLatestMessages(
                    object : CompletionHandler {
                        override fun onSuccess() = Unit

                        override fun onFailure(code: Int, desc: String) {
                            scrollToLatestAfterReload = false
                        }
                    }
                )
            }
            MessageListBackToLatestAction.ScrollLoadedLatest -> {
                recyclerView.scrollToPosition(0)
                recyclerView.post { updateFloatingEntryForScroll() }
            }
        }
    }

    private fun returnToQuoteSource(returnMessage: MessageInfo) {
        val returnMessageId = returnMessage.msgID.takeIf { it.isNotBlank() } ?: return
        if (viewModel.messageList.value.any { it.msgID == returnMessageId }) {
            locateCoordinator.requestScrollToMessage(returnMessageId)
            recyclerView.post { updateFloatingEntryForScroll() }
            return
        }
        viewModel.loadMessagesAroundMessage(
            message = returnMessage,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    locateCoordinator.requestScrollToMessage(returnMessageId)
                    recyclerView.post { updateFloatingEntryForScroll() }
                }

                override fun onFailure(code: Int, desc: String) = Unit
            }
        )
    }

    private fun locateMentionTarget(target: MessageListMentionTarget) {
        findMessageIdBySequence(target.sequence)?.let { messageId ->
            locateCoordinator.requestLocateMessage(messageId)
            return
        }

        viewModel.loadOlderMessagesUntilSequence(
            sequence = target.sequence,
            maxLoadCount = MENTION_LOCATE_MAX_LOAD_COUNT,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    findMessageIdBySequence(target.sequence)?.let { messageId ->
                        locateCoordinator.requestLocateMessage(messageId)
                    }
                }

                override fun onFailure(code: Int, desc: String) = Unit
            }
        )
    }

    private fun findMessageIdBySequence(sequence: Long): String? {
        return viewModel.messageList.value
            .firstOrNull { it.sequence == sequence }
            ?.msgID
            ?.takeIf { it.isNotBlank() }
    }

    private fun locateQuotedMessage(sourceMessage: MessageInfo, quoteInfo: MessageQuoteInfo) {
        if (MessageQuoteLocatePolicy.isOriginalMessageUnreachable(quoteInfo)) {
            showQuotedOriginalUnreachableToast()
            return
        }
        val messages = viewModel.messageList.value
        MessageQuoteLocatePolicy.findLoadedTargetMessageId(quoteInfo, messages)?.let { messageId ->
            navigateToQuotedMessage(messageId, sourceMessage)
            return
        }
        if (viewModel.findFilteredQuoteTargetId(quoteInfo) != null) {
            showQuotedOriginalFilteredToast()
            return
        }
        if (!MessageQuoteLocatePolicy.shouldLoadAround(quoteInfo, messages)) {
            return
        }
        viewModel.loadMessagesAroundQuote(
            quoteInfo = quoteInfo,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    if (viewModel.findFilteredQuoteTargetId(quoteInfo) != null) {
                        showQuotedOriginalFilteredToast()
                        return
                    }
                    val targetMessageId = MessageQuoteLocatePolicy.findLoadedTargetMessageId(
                        quoteInfo = quoteInfo,
                        messages = viewModel.messageList.value
                    ) ?: quoteInfo.msgID.takeIf { it.isNotBlank() }
                    targetMessageId?.let { messageId ->
                        navigateToQuotedMessage(messageId, sourceMessage)
                    }
                }

                override fun onFailure(code: Int, desc: String) = Unit
            }
        )
    }

    private fun showQuotedOriginalUnreachableToast() {
        AtomicToast.show(
            context,
            context.getString(R.string.message_list_quote_original_unreachable),
            style = AtomicToast.Style.INFO
        )
    }

    private fun showQuotedOriginalFilteredToast() {
        AtomicToast.show(
            context,
            context.getString(R.string.message_list_quote_original_filtered),
            style = AtomicToast.Style.INFO
        )
    }

    private fun navigateToQuotedMessage(targetMessageId: String, returnMessage: MessageInfo) {
        locateCoordinator.requestLocateMessage(targetMessageId)
        floatingEntryStateController.onQuoteNavigated(returnMessage)
        refreshFloatingEntry()
    }

    private fun isMessageCompletelyVisible(messageId: String): Boolean {
        if (!::viewModel.isInitialized) {
            return false
        }
        val index = viewModel.messageList.value.indexOfFirst { it.msgID == messageId }
        if (index < 0) {
            return false
        }
        val layoutManager = recyclerView.layoutManager as? MessageListLayoutManager ?: return false
        val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
        if (firstVisibleItem == RecyclerView.NO_POSITION || lastVisibleItem == RecyclerView.NO_POSITION) {
            return false
        }
        return index in firstVisibleItem..lastVisibleItem
    }

    private fun applyFloatingEntryTheme() {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        val contentColor = colors.textColorLink
        floatingEntryCard.radius = dpToPx(MessageListFloatingEntryStyle.CORNER_RADIUS_DP.toFloat()).toFloat()
        floatingEntryCard.cardElevation = dpToPx(MessageListFloatingEntryStyle.ELEVATION_DP.toFloat()).toFloat()
        floatingEntryCard.setCardBackgroundColor(colors.floatingColorDefault)
        floatingEntryTextView.setTextColor(contentColor)
        floatingEntryIconView.imageTintList = ColorStateList.valueOf(contentColor)
    }

    private fun applyFloatingEntryLayout() {
        floatingEntryCard.minimumWidth = dpToPx(MessageListFloatingEntryStyle.WIDTH_DP.toFloat())
        floatingEntryCard.minimumHeight = dpToPx(MessageListFloatingEntryStyle.HEIGHT_DP.toFloat())
        floatingEntryTextView.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            MessageListFloatingEntryStyle.TEXT_SIZE_SP.toFloat()
        )
        val layoutParams = floatingEntryCard.layoutParams as? ConstraintLayout.LayoutParams ?: return
        layoutParams.height = dpToPx(MessageListFloatingEntryStyle.HEIGHT_DP.toFloat())
        layoutParams.marginEnd = dpToPx(MessageListFloatingEntryStyle.MARGIN_END_DP.toFloat())
        layoutParams.bottomMargin = dpToPx(MessageListFloatingEntryStyle.MARGIN_BOTTOM_DP.toFloat())
        floatingEntryCard.layoutParams = layoutParams
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    private fun performMessageForward(
        messages: List<MessageInfo>,
        conversationIDs: List<String>,
        exitMultiSelect: Boolean
    ) {
        viewModel.forwardMessages(
            messageList = messages,
            conversationIDList = conversationIDs,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    if (exitMultiSelect) {
                        viewModel.exitMultiSelectMode()
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    AtomicToast.show(
                        context,
                        desc.ifBlank { context.getString(R.string.message_list_forward_failed_tip) },
                        style = AtomicToast.Style.WARNING
                    )
                }
            }
        )
    }

    private fun performAuxiliaryTextForward(
        text: String,
        conversationIDs: List<String>
    ) {
        viewModel.sendAuxiliaryTextToConversations(
            text = text,
            conversationIDList = conversationIDs,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    viewModel.clearAuxiliaryTextForward()
                }

                override fun onFailure(code: Int, desc: String) {
                    AtomicToast.show(
                        context,
                        desc.ifBlank { context.getString(R.string.message_list_forward_failed_tip) },
                        style = AtomicToast.Style.WARNING
                    )
                }
            }
        )
    }

    private fun showForwardTargetSelector(
        onConfirm: (List<String>) -> Unit,
        onDismiss: () -> Unit
    ) {
        ForwardTargetSelectorDialog(
            context = context,
            onConfirm = onConfirm
        ).apply {
            setOnDismissListener {
                onDismiss()
            }
        }.show()
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> isUserScrollSession = true
                RecyclerView.SCROLL_STATE_IDLE -> isUserScrollSession = false
            }
            if (MessageListInputDismissPolicy.shouldDismissInputForScroll(
                    isDragging = newState == RecyclerView.SCROLL_STATE_DRAGGING
                )
            ) {
                postBlankAreaClickEvent()
            }
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                locateCoordinator.schedulePendingLocateHighlight()
                updateFloatingEntryForScroll()
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            readReceiptController.runInScrollCallback {
                alignmentController.applyAlignmentImmediately()

                val layoutManager = recyclerView.layoutManager as? MessageListLayoutManager ?: return@runInScrollCallback
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (isUserScrollSession) {
                    if (boundaryPagingPolicy.shouldLoadNewer(firstVisibleItem)) {
                        loadMoreNewerMessageFromScroll()
                    }

                    if (boundaryPagingPolicy.shouldLoadOlder(lastVisibleItem, totalItemCount)) {
                        viewModel.loadMoreOlderMessage()
                    }
                }

                readReceiptController.syncVisibleReadReceipts()
                updateFloatingEntryForScroll()
            }
        }
    }

    private fun collapseListenPlaybackBar() {
        if (listenPlaybackBar.visibility == View.VISIBLE) {
            listenPlaybackBar.collapse()
        }
    }

    private fun loadMoreNewerMessageFromScroll() {
        val currentLatestMessageId = adapter.currentList.firstOrNull()?.msgID
        if (!currentLatestMessageId.isNullOrBlank()) {
            recyclerView.removeCallbacks(clearSuppressedLatestAutoScrollRunnable)
            suppressLatestAutoScrollFromMessageId = currentLatestMessageId
        }
        viewModel.loadMoreNewerMessage(
            object : CompletionHandler {
                override fun onSuccess() {
                    recyclerView.post(clearSuppressedLatestAutoScrollRunnable)
                }

                override fun onFailure(code: Int, desc: String) {
                    clearSuppressedLatestAutoScroll()
                }
            }
        )
    }

    private fun postBlankAreaClickEvent() {
        EventBus.post(
            mapOf(
                "source" to "MessageList",
                "event" to "onBlankAreaClick"
            )
        )
    }

    private fun postUserLongPressEvent(message: MessageInfo, userID: String) {
        EventBus.post(
            mapOf(
                "source" to "MessageList",
                "event" to "onUserLongPress",
                "userID" to userID,
                "message" to message
            )
        )
    }

    private fun createMessageRenderActions(): MessageRenderActions {
        return object : MessageRenderActions {
            override fun openImageViewer(message: MessageInfo) {
                viewModel.showImage(context, message)
            }

            override fun openVideoPlayer(message: MessageInfo) {
                viewModel.downloadOrShowVideo(context, message)
            }

            override fun playSound(message: MessageInfo) {
                viewModel.playAudioMessage(message)
            }

            override fun toggleSelection(message: MessageInfo) {
                viewModel.toggleMessageSelection(message)
            }

            override fun showLongPressMenu(message: MessageInfo, anchorView: View) {
                this@MessageListView.showLongPressMenu(message, anchorView)
            }
        }
    }

    private fun showLongPressMenu(
        message: MessageInfo,
        anchorView: View
    ) {
        val actions = composeMessageLongPressActions(
            actionContext = MessageCustomActionContext(
                androidContext = context,
                conversationID = viewModel.conversationID,
                message = message,
            ),
            defaults = viewModel.getActions(context, message, config),
            customizer = config.actionCustomizer,
            onDeleteRequested = { _, onConfirm ->
                showDeleteMessagesConfirmDialog(onConfirm)
            },
        )
        if (actions.isEmpty()) {
            return
        }

        MessageLongPressPopup(
            context = context,
            anchorView = anchorView,
            messageListView = this,
            message = message,
            viewModel = viewModel,
            config = config,
            actions = actions
        ).show()
    }

    private fun showAuxiliaryTextLongPressMenu(
        message: MessageInfo,
        anchorView: View,
        actions: List<MessageCustomAction>
    ) {
        if (actions.isEmpty()) {
            return
        }

        AuxiliaryTextLongPressPopup(
            context = context,
            anchorView = anchorView,
            messageListView = this,
            message = message,
            actions = actions
        ).show()
    }

    private fun showDeleteMessagesConfirmDialog(onConfirm: () -> Unit) {
        AtomicAlertDialog(context).apply {
            init {
                content = context.getString(R.string.message_list_delete_messages_tips)
                confirmButton(
                    context.getString(R.string.uikit_confirm),
                    type = AtomicAlertDialog.TextColorPreset.RED
                ) { _ ->
                    onConfirm()
                }
                cancelButton(context.getString(R.string.uikit_cancel))
            }
            show()
        }
    }
}

