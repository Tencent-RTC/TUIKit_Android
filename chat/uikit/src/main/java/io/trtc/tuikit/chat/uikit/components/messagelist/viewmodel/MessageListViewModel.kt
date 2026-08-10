package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.messagelist.typing.TypingMessageProtocol
import io.trtc.tuikit.chat.uikit.components.common.MessageOfflinePushInfoFactory
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.AlbumPickerProcessingMessageStore
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.listen.ListenFromHereController
import io.trtc.tuikit.chat.uikit.components.messagelist.listen.ListenPlanResources
import io.trtc.tuikit.chat.uikit.components.messagelist.listen.ListenPlaybackState
import io.trtc.tuikit.chat.uikit.components.messagelist.listen.buildListenPlan
import io.trtc.tuikit.chat.uikit.components.messagelist.model.LoadingState
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListActionCallbacks
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListActionFactory
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageQuoteLocatePolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallMessageDisplayPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.AuxiliaryTextVisibilityStore
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageReadState
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryFormatter
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.TranslationTextParser
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationMarkType
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.FaceMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.ForwardMessageOption
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MediaQuality
import io.trtc.tuikit.atomicxcore.api.message.MergedForwardInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageEvent
import io.trtc.tuikit.atomicxcore.api.message.MessageForwardType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadDirection
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadOption
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.OfflinePushInfo
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

const val MESSAGE_AGGREGATION_TIME: Int = 300
const val FORWARD_MESSAGE_COUNT_LIMIT = 30
private const val FILE_URI_PREFIX = "file://"
private const val CONTENT_URI_PREFIX = "content://"
private const val HTTP_URI_PREFIX = "http://"
private const val HTTPS_URI_PREFIX = "https://"
private const val MESSAGE_LIST_EVENT_SOURCE = "MessageList"
private const val QUOTE_MESSAGE_EVENT = "onQuoteMessage"

class MessageListViewModel(
    private val messageListStore: MessageListStore,
    val conversationID: String,
    var locateMessage: MessageInfo? = null,
    val messageListConfig: MessageListConfigProtocol = ChatMessageListConfig(),
    private val enableMediaPreviewBoundaryLoading: Boolean = true,
    private val reverseMediaPreviewMessageOrder: Boolean = true
) : ViewModel() {
    companion object {
        private const val TAG = "MessageListViewModel"
    }
    private val conversationListStore = ConversationListStore.create()
    val conversationListState = conversationListStore.state
    val messageListState = messageListStore.state
    val messageEvent = messageListStore.messageEventFlow.filter { event ->
        when (event) {
            is MessageEvent.OnReceiveNewMessage -> !TypingMessageProtocol.isTypingMessage(event.message)
        }
    }
    private val messageInputStore = MessageInputStore.create(conversationID)
    private val messageSummaryFormatter = MessageListMessageSummaryFormatter(messageListConfig)
    private val visibilityPolicy = MessageListVisibilityPolicy(messageListConfig)
    private val displayMapper = MessageListDisplayMapper(shouldDisplayMessage = ::shouldDisplayMessage)
    private val timeGroupingPolicy = MessageListTimeGroupingPolicy()
    private val resourceCoordinator = MessageResourceCoordinator { message, resourceType, completion ->
        downloadMessageMedia(
            message,
            resourceType,
            object : CompletionHandler {
                override fun onSuccess() {
                    completion.onComplete()
                }

                override fun onFailure(code: Int, desc: String) {
                    completion.onComplete()
                }
            }
        )
    }
    private val videoPlaybackController: MessageVideoPlaybackLauncher = MessageVideoPlaybackController(
        scope = viewModelScope
    )
    private val mediaPreviewMessagesOverride = MutableStateFlow<List<MessageInfo>?>(null)
    private val mediaPreviewMessageSource = createMediaPreviewMessageSource()
    private val mediaPreviewController = MessageMediaPreviewController(
        scope = viewModelScope,
        messageSource = mediaPreviewMessageSource,
        downloadResource = ::downloadMessageMedia,
        enableBoundaryLoading = enableMediaPreviewBoundaryLoading,
        reverseMessageOrderForPreview = false
    )
    private val forwardCoordinator by lazy {
        MessageForwardCoordinator(
            currentVisibleMessages = { messageList.value },
            offlinePushInfoFactory = { conversationID ->
                createOfflinePushInfoForMultiConversation("", conversationID)
            }
        )
    }
    private val hiddenResendingMessageIds = MutableStateFlow<Set<String>>(emptySet())

    val messageList: StateFlow<List<MessageInfo>> = combine(
        messageListState.messageList,
        AlbumPickerProcessingMessageStore.messagesByConversation,
        hiddenResendingMessageIds
    ) { list, processingMessagesByConversation, hiddenMessageIds ->
        val processingMessages = processingMessagesByConversation[conversationID].orEmpty()
        val visibleMessages = (list + processingMessages).filterNot { message ->
            message.msgID in hiddenMessageIds
        }
        displayMapper.map(visibleMessages)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun createMediaPreviewMessageSource(): MessageMediaPreviewMessageSource {
        return if (enableMediaPreviewBoundaryLoading) {
            MessageMediaPreviewDataManager(
                messageListStore = MessageListStore.create(conversationID)
            )
        } else {
            StaticMessageMediaPreviewMessageSource(
                messagesProvider = { mediaPreviewMessagesOverride.value ?: emptyList() },
                reverseMessageOrderForPreview = reverseMediaPreviewMessageOrder
            )
        }
    }

    val hasMoreOlderMessage = messageListState.hasOlderMessages
    val hasMoreNewerMessage = messageListState.hasNewerMessages

    private val pagingController = MessageListPagingController(
        messageListStore = messageListStore,
        hasOlderMessages = hasMoreOlderMessage,
        hasNewerMessages = hasMoreNewerMessage
    )
    val loadingState: StateFlow<LoadingState> = pagingController.loadingState

    private val selectionStateStore = MessageSelectionStateStore()
    val isMultiSelectMode: StateFlow<Boolean> = selectionStateStore.isMultiSelectMode

    val selectedMessages: StateFlow<Set<MessageInfo>> = selectionStateStore.selectedMessages

    private val dialogStateStore = MessageListDialogStateStore()
    val longPressActionMessage: StateFlow<MessageInfo?> = dialogStateStore.longPressActionMessage

    val onSingleMessageForward: StateFlow<MessageInfo?> = dialogStateStore.onSingleMessageForward

    val readReceiptMessage: StateFlow<MessageInfo?> = dialogStateStore.readReceiptMessage

    private val auxiliaryTextVisibilityStore = AuxiliaryTextVisibilityStore()

    private val audioController = MessageAudioController(
        onPlaybackError = { errorMessage ->
            Log.e(TAG, "play audio failed: $errorMessage")
        }
    )
    val audioPlayingState = audioController.audioPlayingState

    private val listenFromHereController = ListenFromHereController()
    val listenPlaybackState: StateFlow<ListenPlaybackState> = listenFromHereController.state

    private val _processingAuxiliaryTextMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val processingAuxiliaryTextMessageIds: StateFlow<Set<String>> =
        _processingAuxiliaryTextMessageIds.asStateFlow()
    private val pendingSuccessfulTranslationMessageIds = mutableSetOf<String>()
    private val pendingSuccessfulAsrMessageIds = mutableSetOf<String>()

    val hiddenAuxiliaryTextMessageIds: StateFlow<Set<String>> = auxiliaryTextVisibilityStore.hiddenMessageIds

    private val translationAtUserNamesByMessageId = mutableMapOf<String, List<String>?>()
    private val resolvingTranslationAtUserNameMessageIds = mutableSetOf<String>()
    private val _translationAtUserNameMessageIds = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val translationAtUserNameMessageIds: SharedFlow<String> = _translationAtUserNameMessageIds.asSharedFlow()

    private val _auxiliaryTextForwardContent = MutableStateFlow<String?>(null)
    val auxiliaryTextForwardContent: StateFlow<String?> = _auxiliaryTextForwardContent.asStateFlow()

    val reactionDetailMessage: StateFlow<MessageInfo?> = dialogStateStore.reactionDetailMessage

    val showEmojiPickerForMessage: StateFlow<MessageInfo?> = dialogStateStore.showEmojiPickerForMessage

    var forwardType = MessageForwardType.SEPARATE

    init {
        viewModelScope.launch {
            messageEvent.collect { event ->
                when (event) {
                    is MessageEvent.OnReceiveNewMessage -> {
                        locateMessage = null
                    }
                }
            }
        }
        viewModelScope.launch {
            messageList.collect { messages ->
                syncPendingAuxiliaryTextProcessing(messages)
            }
        }

        conversationListStore.getConversationInfo(conversationID, object : GetConversationInfoCompletionHandler {
            override fun onSuccess(conversationInfo: ConversationInfo) {}
            override fun onFailure(code: Int, desc: String) {}
        })
        messageListStore.loadMessages(
            MessageLoadOption(
                cursor = locateMessage,
                direction = if (locateMessage != null) MessageLoadDirection.BOTH else MessageLoadDirection.OLDER
            )
        )
    }

    private fun shouldDisplayMessage(message: MessageInfo): Boolean {
        if (TypingMessageProtocol.isTypingMessage(message)) return false
        return visibilityPolicy.shouldDisplay(message)
    }

    fun clearMessageReadCount() {
        conversationListStore.clearConversationUnreadCount(conversationID)
        conversationListStore.markConversation(
            listOf(conversationID),
            ConversationMarkType.unread,
            false
        )
    }

    fun loadMoreOlderMessage(completion: CompletionHandler? = null) {
        loadMoreMessages(MessageLoadDirection.OLDER, completion)
    }

    fun loadMoreNewerMessage(completion: CompletionHandler? = null) {
        loadMoreMessages(MessageLoadDirection.NEWER, completion)
    }

    fun reloadLatestMessages(completion: CompletionHandler? = null) {
        locateMessage = null
        messageListStore.loadMessages(
            MessageLoadOption(direction = MessageLoadDirection.OLDER),
            completion
        )
    }

    fun fetchConversationInfo(onResult: (ConversationInfo?) -> Unit) {
        conversationListStore.getConversationInfo(
            conversationID,
            object : GetConversationInfoCompletionHandler {
                override fun onSuccess(conversationInfo: ConversationInfo) {
                    onResult(conversationInfo)
                }

                override fun onFailure(code: Int, desc: String) {
                    onResult(null)
                }
            }
        )
    }

    fun loadMessagesAroundQuote(
        quoteInfo: MessageQuoteInfo,
        completion: CompletionHandler? = null
    ) {
        if (quoteInfo.sequence <= 0) {
            completion?.onFailure(-1, "Invalid quote message sequence")
            return
        }
        messageListStore.loadMessages(
            MessageLoadOption(
                cursor = MessageQuoteLocatePolicy.buildCursorMessage(quoteInfo),
                direction = MessageLoadDirection.BOTH
            ),
            completion
        )
    }

    internal fun findFilteredQuoteTargetId(quoteInfo: MessageQuoteInfo): String? {
        val rawMessages = messageListState.messageList.value
        val target = rawMessages.firstOrNull { quoteInfo.msgID.isNotBlank() && it.msgID == quoteInfo.msgID }
            ?: rawMessages.firstOrNull { quoteInfo.sequence > 0 && it.sequence == quoteInfo.sequence }
            ?: return null
        return target.msgID.takeIf { !visibilityPolicy.shouldDisplay(target) }
    }

    fun loadMessagesAroundMessage(
        message: MessageInfo,
        completion: CompletionHandler? = null
    ) {
        if (message.msgID.isBlank() && (message.sequence ?: 0L) <= 0L) {
            completion?.onFailure(-1, "Invalid message")
            return
        }
        messageListStore.loadMessages(
            MessageLoadOption(
                cursor = message,
                direction = MessageLoadDirection.BOTH
            ),
            completion
        )
    }

    fun loadOlderMessagesUntilSequence(
        sequence: Long,
        maxLoadCount: Int = 5,
        completion: CompletionHandler? = null
    ) {
        if (sequence <= 0) {
            completion?.onFailure(-1, "Invalid message sequence")
            return
        }
        if (messageList.value.any { it.sequence == sequence }) {
            completion?.onSuccess()
            return
        }
        if (maxLoadCount <= 0 || !hasMoreOlderMessage.value) {
            completion?.onFailure(-1, "Target message not found")
            return
        }

        loadMoreMessages(
            MessageLoadDirection.OLDER,
            object : CompletionHandler {
                override fun onSuccess() {
                    loadOlderMessagesUntilSequence(
                        sequence = sequence,
                        maxLoadCount = maxLoadCount - 1,
                        completion = completion
                    )
                }

                override fun onFailure(code: Int, desc: String) {
                    completion?.onFailure(code, desc)
                }
            }
        )
    }

    private fun loadMoreMessages(
        direction: MessageLoadDirection,
        completion: CompletionHandler? = null
    ) {
        pagingController.loadMoreMessages(direction, completion)
    }

    fun getMessageTimeString(index: Int): String? {
        return timeGroupingPolicy.timeStringForMessageAt(index, messageList.value)
    }

    fun getActions(
        context: Context,
        messageInfo: MessageInfo,
        config: MessageListConfigProtocol = messageListConfig,
    ): List<MessageCustomAction> {
        return MessageListActionFactory(
            config = config,
            latestMessageProvider = { message ->
                messageList.value.find { it.msgID == message.msgID } ?: message
            },
            auxiliaryTextVisibilityStore = auxiliaryTextVisibilityStore,
            callbacks = MessageListActionCallbacks(
                onEnterMultiSelectMode = { enterMultiSelectMode(messageInfo) },
                onForwardSingleMessage = dialogStateStore::showSingleMessageForward,
                onConvertVoiceToText = ::convertVoiceToText,
                onTranslateText = ::translateText,
                onQuoteMessage = ::quoteMessage,
                onListenFromHere = ::startListenFromHere
            )
        ).createDefaults(context, messageInfo)
    }

    fun startListenFromHere(message: MessageInfo) {
        val messages = messageList.value
        val targetId = message.msgID.takeIf { it.isNotBlank() } ?: return
        val startIndex = messages.indexOfFirst { it.msgID == targetId }
        if (startIndex < 0) {
            return
        }
        // messageList index 0 is the newest; reversing [0..startIndex] yields the
        // tapped -> newest range in chronological (oldest -> newest) order.
        val orderedRange = messages.subList(0, startIndex + 1).asReversed()
        val plan = buildListenPlan(orderedRange, buildListenPlanResources())
        listenFromHereController.start(plan)
    }

    fun stopListenFromHere() {
        listenFromHereController.stop()
    }

    private fun buildListenPlanResources(): ListenPlanResources {
        return ListenPlanResources(
            selfSpeaker = getLocalizedString(R.string.voice_message_listen_self_speaker),
            says = { name -> getLocalizedString(R.string.voice_message_listen_says, name) },
            sentImage = { name -> getLocalizedString(R.string.voice_message_listen_sent_image, name) },
            sentVideo = { name -> getLocalizedString(R.string.voice_message_listen_sent_video, name) },
            sentFile = { name -> getLocalizedString(R.string.voice_message_listen_sent_file, name) },
            sentMerged = { name, title ->
                getLocalizedString(R.string.voice_message_listen_sent_merged, name, title)
            }
        )
    }

    private fun isGroupChat(conversationID: String): Boolean {
        return ConversationIDUtil.isGroup(conversationID)
    }

    private fun quoteMessage(message: MessageInfo, summary: String) {
        EventBus.post(
            mapOf(
                "source" to MESSAGE_LIST_EVENT_SOURCE,
                "event" to QUOTE_MESSAGE_EVENT,
                "conversationID" to conversationID,
                "message" to message,
                "summary" to summary
            )
        )
    }

    fun enterMultiSelectMode(initialMessage: MessageInfo? = null) {
        selectionStateStore.enter(initialMessage)
    }

    fun exitMultiSelectMode() {
        selectionStateStore.exit()
    }

    fun toggleMessageSelection(message: MessageInfo) {
        selectionStateStore.toggle(message)
    }

    fun showLongPressActionDialog(message: MessageInfo) {
        dialogStateStore.showLongPressAction(message)
    }

    fun clearLongPressActionDialog() {
        dialogStateStore.clearLongPressAction()
    }

    fun showReadReceiptDialog(message: MessageInfo) {
        dialogStateStore.showReadReceipt(message)
    }

    fun clearReadReceiptDialog() {
        dialogStateStore.clearReadReceipt()
    }

    fun clearSingleMessageForward() {
        dialogStateStore.clearSingleMessageForward()
    }

    fun deleteSelectedMessages() {
        messageListStore.deleteMessages(selectedMessages.value.toList())
    }

    fun retrySendMessage(context: Context?, message: MessageInfo) {
        if (message.offlinePushInfo == null) {
            message.offlinePushInfo = createOfflinePushInfoForConversation(conversationID, message)
        }
        val payload = buildSendMessagePayload(message) ?: return
        val option = buildSendMessageOption(message)
        hideResendingMessage(message)
        messageListStore.deleteMessages(listOf(message), object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                Log.e(TAG, "delete failed message before resend failed, code: $code, desc: $desc")
            }
        })
        messageInputStore.sendMessage(payload, option, object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    private fun hideResendingMessage(message: MessageInfo) {
        val messageId = message.msgID.takeIf { it.isNotEmpty() } ?: return
        hiddenResendingMessageIds.value = hiddenResendingMessageIds.value + messageId
    }

    private fun buildSendMessagePayload(message: MessageInfo): SendMessagePayload? {
        return when (val payload = message.messagePayload) {
            is TextMessagePayload -> {
                val text = payload.text.takeIf { it.isNotEmpty() } ?: return null
                SendMessagePayload.TextSendMessagePayload(text = text)
            }

            is ImageMessagePayload -> {
                val imagePath = payload.originalImagePath?.takeIf { it.isNotBlank() } ?: return null
                SendMessagePayload.ImageSendMessagePayload(
                    imagePath = imagePath,
                    imageWidth = payload.originalImageWidth,
                    imageHeight = payload.originalImageHeight
                )
            }

            is VideoMessagePayload -> {
                val videoPath = payload.videoPath?.takeIf { it.isNotBlank() } ?: return null
                val snapshotPath = payload.videoSnapshotPath.orEmpty()
                SendMessagePayload.VideoSendMessagePayload(
                    videoFilePath = videoPath,
                    videoType = payload.videoType.orEmpty(),
                    duration = payload.videoDuration,
                    snapshotPath = snapshotPath,
                    snapshotWidth = payload.videoSnapshotWidth,
                    snapshotHeight = payload.videoSnapshotHeight
                )
            }

            is AudioMessagePayload -> {
                val audioPath = payload.audioPath?.takeIf { it.isNotBlank() } ?: return null
                SendMessagePayload.AudioSendMessagePayload(
                    audioFilePath = audioPath,
                    duration = payload.audioDuration
                )
            }

            is FileMessagePayload -> {
                val filePath = payload.filePath?.takeIf { it.isNotBlank() } ?: return null
                SendMessagePayload.FileSendMessagePayload(
                    filePath = filePath,
                    fileName = payload.fileName.orEmpty(),
                    fileSize = payload.fileSize
                )
            }

            is FaceMessagePayload -> {
                SendMessagePayload.FaceSendMessagePayload(
                    index = payload.faceIndex,
                    data = payload.faceData.orEmpty()
                )
            }

            is CustomMessagePayload -> {
                val customData = payload.customData?.takeIf { it.isNotEmpty() } ?: return null
                SendMessagePayload.CustomSendMessagePayload(
                    customData = customData,
                    description = payload.description.orEmpty(),
                    extensionInfo = payload.extensionInfo.orEmpty()
                )
            }

            else -> null
        }
    }

    private fun buildSendMessageOption(message: MessageInfo): SendMessageOption {
        return SendMessageOption(
            atUserList = message.atUserList.takeIf { it.isNotEmpty() },
            quotedMessage = null,
            needReadReceipt = message.needReadReceipt,
            isExtensionEnabled = message.isExtensionEnabled,
            onlineUserOnly = false,
            offlinePushInfo = message.offlinePushInfo
        )
    }

    fun sendReadReceipts(messages: List<MessageInfo>) {
        viewModelScope.launch(Dispatchers.IO) {
            val filtered = messages.filter { !it.isSentBySelf && it.needReadReceipt }
            if (filtered.isNotEmpty()) {
                messageListStore.sendMessageReadReceipts(filtered)
            }
        }
    }

    fun markVisibleCallMessagesRead(messages: List<MessageInfo>): List<String> {
        return messages.mapNotNull { message ->
            val callModel = CallMessageParser.parse(message) ?: return@mapNotNull null
            val shouldMarkRead = CallMessageDisplayPolicy.shouldShowOutsideUnreadDot(
                isSelf = message.isSentBySelf,
                isShowUnreadPoint = callModel.isShowUnreadPoint
            )
            if (!shouldMarkRead) {
                return@mapNotNull null
            }
            CallMessageReadState.markRead(message.msgID)
            message.msgID
        }
    }

    fun downloadFile(messageInfo: MessageInfo) {
        resourceCoordinator.request(messageInfo, MessageMediaFileType.FILE)
    }

    fun requestResourcesForMessage(messageInfo: MessageInfo) {
        when (messageInfo.messageType) {
            MessageType.IMAGE -> downloadThumbImage(messageInfo)
            MessageType.AUDIO -> downloadSound(messageInfo)
            MessageType.VIDEO -> downloadVideoSnapShot(messageInfo)
            else -> Unit
        }
    }

    fun downloadThumbImage(messageInfo: MessageInfo) {
        resourceCoordinator.request(messageInfo, MessageMediaFileType.THUMB_IMAGE)
    }

    fun downloadSound(messageInfo: MessageInfo) {
        resourceCoordinator.request(messageInfo, MessageMediaFileType.AUDIO)
    }

    fun downloadVideoSnapShot(messageInfo: MessageInfo) {
        resourceCoordinator.request(messageInfo, MessageMediaFileType.VIDEO_SNAPSHOT)
    }

    private fun downloadMessageMedia(
        messageInfo: MessageInfo,
        resourceType: MessageMediaFileType,
        completion: CompletionHandler
    ) {
        val quality = when (resourceType) {
            MessageMediaFileType.THUMB_IMAGE -> MediaQuality.THUMBNAIL
            MessageMediaFileType.LARGE_IMAGE -> MediaQuality.STANDARD
            MessageMediaFileType.ORIGINAL_IMAGE -> MediaQuality.ORIGINAL
            MessageMediaFileType.VIDEO_SNAPSHOT -> MediaQuality.THUMBNAIL
            MessageMediaFileType.VIDEO -> MediaQuality.ORIGINAL
            else -> null
        }
        MessageActionStore.create(messageInfo).downloadMedia(quality, completion)
    }

    fun showImage(context: Context, messageInfo: MessageInfo) {
        mediaPreviewController.showImage(context, messageInfo)
    }

    fun downloadOrShowVideo(context: Context, messageInfo: MessageInfo) {
        videoPlaybackController.downloadOrShowVideo(context, messageInfo)
    }

    fun setMediaPreviewMessages(messages: List<MessageInfo>?) {
        mediaPreviewMessagesOverride.value = messages
    }

    fun playAudioMessage(message: MessageInfo) {
        val messageId = message.msgID ?: return
        val targetPath = resolvePlayableAudioPath(message) ?: return
        audioController.toggle(messageId = messageId, filePath = targetPath)
    }

    fun initializeAudioPlayer() {
        audioController.ensureStarted()
    }

    private fun resolvePlayableAudioPath(message: MessageInfo): String? {
        val payload = message.messagePayload as? AudioMessagePayload ?: return null
        val soundPath = payload.audioPath?.takeIf { it.isNotBlank() } ?: return null
        return when {
            soundPath.startsWith(FILE_URI_PREFIX) ||
                soundPath.startsWith(CONTENT_URI_PREFIX) ||
                soundPath.startsWith(HTTP_URI_PREFIX) ||
                soundPath.startsWith(HTTPS_URI_PREFIX) -> {
                soundPath
            }

            File(soundPath).exists() -> {
                soundPath
            }

            else -> {
                null
            }
        }
    }

    fun clearAuxiliaryTextForward() {
        _auxiliaryTextForwardContent.value = null
    }

    fun sendAuxiliaryTextToConversations(
        text: String,
        conversationIDList: List<String>,
        completion: CompletionHandler? = null
    ) {
        viewModelScope.launch {
            var successCount = 0
            var failureCount = 0
            conversationIDList.forEach { conversationId ->
                val inputStore = MessageInputStore.create(conversationId)
                val payload = SendMessagePayload.TextSendMessagePayload(text = text)
                val placeholderMessage = MessageInfo().apply {
                    messageType = MessageType.TEXT
                    messagePayload = TextMessagePayload(text = text)
                }
                val option = SendMessageOption(
                    needReadReceipt = AppBuilderConfig.enableReadReceipt,
                    offlinePushInfo = createOfflinePushInfoForConversation(conversationId, placeholderMessage)
                )
                inputStore.sendMessage(payload, option, object : CompletionHandler {
                    override fun onSuccess() {
                        successCount++
                        if (successCount + failureCount == conversationIDList.size) {
                            if (failureCount == 0) {
                                completion?.onSuccess()
                            } else {
                                completion?.onFailure(-1, "Some messages failed to send")
                            }
                        }
                    }

                    override fun onFailure(code: Int, desc: String) {
                        failureCount++
                        if (successCount + failureCount == conversationIDList.size) {
                            completion?.onFailure(code, desc)
                        }
                    }
                })
            }
        }
    }

    fun convertVoiceToText(message: MessageInfo) {
        val msgId = message.msgID ?: return
        if (_processingAuxiliaryTextMessageIds.value.contains(msgId)) {
            return
        }
        auxiliaryTextVisibilityStore.unhide(msgId)
        _processingAuxiliaryTextMessageIds.value = _processingAuxiliaryTextMessageIds.value + msgId
        viewModelScope.launch {
            val actionStore = MessageActionStore.create(message)
            actionStore.convertVoiceToText("", object : CompletionHandler {
                override fun onSuccess() {
                    handleVoiceConvertSuccess(msgId)
                }

                override fun onFailure(code: Int, desc: String) {
                    pendingSuccessfulAsrMessageIds.remove(msgId)
                    _processingAuxiliaryTextMessageIds.value =
                        _processingAuxiliaryTextMessageIds.value - msgId
                    val appContext = ContextProvider.getApplicationContext() ?: return
                    AtomicToast.show(
                        appContext,
                        appContext.getString(R.string.message_list_convert_to_text_failed),
                        style = AtomicToast.Style.WARNING
                    )
                }
            })
        }
    }

    fun hideAsrText(message: MessageInfo) {
        message.msgID?.let { auxiliaryTextVisibilityStore.hide(it) }
    }

    fun copyAsrText(message: MessageInfo, context: Context) {
        val asrText = (message.messagePayload as? AudioMessagePayload)?.asrText ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ASR Text", asrText))
        AtomicToast.show(
            context,
            context.getString(R.string.uikit_copied),
            style = AtomicToast.Style.WARNING
        )
    }

    fun forwardAsrText(message: MessageInfo) {
        val asrText = (message.messagePayload as? AudioMessagePayload)?.asrText ?: return
        if (asrText.isNotBlank()) {
            _auxiliaryTextForwardContent.value = asrText
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun translateText(message: MessageInfo) {
        val msgId = message.msgID ?: return
        if (_processingAuxiliaryTextMessageIds.value.contains(msgId)) {
            return
        }
        val targetLanguage = AppBuilderConfig.translateTargetLanguage
        if (hasReusableTranslation(message)) {
            pendingSuccessfulTranslationMessageIds.remove(msgId)
            auxiliaryTextVisibilityStore.unhide(msgId)
            return
        }
        val originalText = (message.messagePayload as? TextMessagePayload)?.text
        if (originalText.isNullOrBlank()) {
            return
        }
        pendingSuccessfulTranslationMessageIds.remove(msgId)
        auxiliaryTextVisibilityStore.unhide(msgId)
        _processingAuxiliaryTextMessageIds.value = _processingAuxiliaryTextMessageIds.value + msgId
        viewModelScope.launch {
            val atUserNames = resolveAndCacheTranslationAtUserNames(message)
            val splitResult = TranslationTextParser.splitTextByEmojiAndAtUsers(originalText, atUserNames)
            val textArray =
                splitResult?.get(TranslationTextParser.KEY_SPLIT_STRING_TEXT) as? List<String> ?: emptyList()
            if (textArray.isEmpty()) {
                _processingAuxiliaryTextMessageIds.value =
                    _processingAuxiliaryTextMessageIds.value - msgId
                return@launch
            }
            val actionStore = MessageActionStore.create(message)
            actionStore.translateText(
                textArray,
                null,
                targetLanguage,
                object : CompletionHandler {
                    override fun onSuccess() {
                        handleTranslateSuccess(msgId)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        pendingSuccessfulTranslationMessageIds.remove(msgId)
                        _processingAuxiliaryTextMessageIds.value =
                            _processingAuxiliaryTextMessageIds.value - msgId
                        val appContext = ContextProvider.getApplicationContext() ?: return
                        AtomicToast.show(
                            appContext,
                            appContext.getString(R.string.message_list_translate_failed),
                            style = AtomicToast.Style.WARNING
                        )
                    }
                }
            )
        }
    }

    private fun handleVoiceConvertSuccess(messageId: String) {
        val latestMessage = messageList.value.firstOrNull { it.msgID == messageId }
        val nextState = AuxiliaryTextProcessingStatePolicy.afterVoiceConvertSuccess(
            processingIds = _processingAuxiliaryTextMessageIds.value,
            pendingSuccessIds = pendingSuccessfulAsrMessageIds,
            messageId = messageId,
            hasVisibleAsrText = latestMessage?.let { hasVisibleAsrText(it) } == true
        )
        pendingSuccessfulAsrMessageIds.clear()
        pendingSuccessfulAsrMessageIds.addAll(nextState.pendingSuccessIds)
        _processingAuxiliaryTextMessageIds.value = nextState.processingIds
    }

    private fun hasVisibleAsrText(message: MessageInfo): Boolean {
        return !((message.messagePayload as? AudioMessagePayload)?.asrText).isNullOrBlank()
    }

    private fun handleTranslateSuccess(messageId: String) {
        val latestMessage = messageList.value.firstOrNull { it.msgID == messageId }
        val nextState = TranslationProcessingStatePolicy.afterTranslationSuccess(
            processingIds = _processingAuxiliaryTextMessageIds.value,
            pendingSuccessIds = pendingSuccessfulTranslationMessageIds,
            messageId = messageId,
            hasVisibleTranslatedText = latestMessage?.let { getTranslatedDisplayText(it) != null } == true
        )
        pendingSuccessfulTranslationMessageIds.clear()
        pendingSuccessfulTranslationMessageIds.addAll(nextState.pendingSuccessIds)
        _processingAuxiliaryTextMessageIds.value = nextState.processingIds
    }

    private fun syncPendingAuxiliaryTextProcessing(messages: List<MessageInfo>) {
        syncPendingTranslationProcessing(messages)
        syncPendingAsrProcessing(messages)
    }

    private fun syncPendingTranslationProcessing(messages: List<MessageInfo>) {
        if (pendingSuccessfulTranslationMessageIds.isEmpty()) return

        val visibleTranslatedMessageIds = messages.mapNotNull { message ->
            val msgId = message.msgID ?: return@mapNotNull null
            if (pendingSuccessfulTranslationMessageIds.contains(msgId) &&
                getTranslatedDisplayText(message) != null
            ) {
                msgId
            } else {
                null
            }
        }.toSet()
        val nextState = TranslationProcessingStatePolicy.afterTranslatedTextVisible(
            processingIds = _processingAuxiliaryTextMessageIds.value,
            pendingSuccessIds = pendingSuccessfulTranslationMessageIds,
            visibleTranslatedMessageIds = visibleTranslatedMessageIds
        )
        pendingSuccessfulTranslationMessageIds.clear()
        pendingSuccessfulTranslationMessageIds.addAll(nextState.pendingSuccessIds)
        _processingAuxiliaryTextMessageIds.value = nextState.processingIds
    }

    private fun syncPendingAsrProcessing(messages: List<MessageInfo>) {
        if (pendingSuccessfulAsrMessageIds.isEmpty()) return

        val visibleAsrMessageIds = messages.mapNotNull { message ->
            val msgId = message.msgID ?: return@mapNotNull null
            if (pendingSuccessfulAsrMessageIds.contains(msgId) && hasVisibleAsrText(message)) {
                msgId
            } else {
                null
            }
        }.toSet()
        val nextState = AuxiliaryTextProcessingStatePolicy.afterAuxiliaryTextVisible(
            processingIds = _processingAuxiliaryTextMessageIds.value,
            pendingSuccessIds = pendingSuccessfulAsrMessageIds,
            visibleMessageIds = visibleAsrMessageIds
        )
        pendingSuccessfulAsrMessageIds.clear()
        pendingSuccessfulAsrMessageIds.addAll(nextState.pendingSuccessIds)
        _processingAuxiliaryTextMessageIds.value = nextState.processingIds
    }

    fun hideTranslation(message: MessageInfo) {
        message.msgID?.let { auxiliaryTextVisibilityStore.hide(it) }
    }

    fun copyTranslatedText(message: MessageInfo, context: Context) {
        val translatedText = getTranslatedDisplayText(message) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Translated Text", translatedText))
        AtomicToast.show(
            context,
            context.getString(R.string.uikit_copied),
            style = AtomicToast.Style.WARNING
        )
    }

    fun forwardTranslatedText(message: MessageInfo) {
        val translatedText = getTranslatedDisplayText(message) ?: return
        if (translatedText.isNotBlank()) {
            _auxiliaryTextForwardContent.value = translatedText
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getTranslatedDisplayText(message: MessageInfo): String? {
        val textPayload = message.messagePayload as? TextMessagePayload ?: return null
        val translatedTextMap = textPayload.translatedText
        if (translatedTextMap.isNullOrEmpty()) {
            return null
        }
        val originalText = textPayload.text.takeIf { it.isNotEmpty() } ?: return null
        val atUserNames = getCachedTranslationAtUserNames(message)
        if (message.atUserList.isNotEmpty() && atUserNames == null) {
            ensureTranslationAtUserNames(message)
            return null
        }
        val splitResult = TranslationTextParser.splitTextByEmojiAndAtUsers(originalText, atUserNames) ?: return null
        val resultArray =
            splitResult[TranslationTextParser.KEY_SPLIT_STRING_RESULT] as? List<String> ?: return null
        val textIndexArray =
            splitResult[TranslationTextParser.KEY_SPLIT_STRING_TEXT_INDEX] as? List<Int> ?: return null
        return TranslationTextParser.replacedStringWithArray(resultArray, textIndexArray, translatedTextMap)
    }

    private fun hasReusableTranslation(message: MessageInfo): Boolean {
        val translatedTextMap = (message.messagePayload as? TextMessagePayload)?.translatedText
        return TranslationTextParser.hasReusableTranslation(
            translatedTextMap = translatedTextMap
        )
    }

    private fun getCachedTranslationAtUserNames(message: MessageInfo): List<String>? {
        val msgId = message.msgID ?: return null
        return translationAtUserNamesByMessageId[msgId]
    }

    private fun ensureTranslationAtUserNames(message: MessageInfo) {
        val msgId = message.msgID ?: return
        if (translationAtUserNamesByMessageId.containsKey(msgId) ||
            resolvingTranslationAtUserNameMessageIds.contains(msgId)
        ) {
            return
        }
        resolvingTranslationAtUserNameMessageIds.add(msgId)
        viewModelScope.launch {
            val atUserNames = resolveAtUserNames(message)
            translationAtUserNamesByMessageId[msgId] = atUserNames
            resolvingTranslationAtUserNameMessageIds.remove(msgId)
            _translationAtUserNameMessageIds.tryEmit(msgId)
        }
    }

    private suspend fun resolveAndCacheTranslationAtUserNames(message: MessageInfo): List<String>? {
        val msgId = message.msgID
        if (!msgId.isNullOrBlank() && translationAtUserNamesByMessageId.containsKey(msgId)) {
            return translationAtUserNamesByMessageId[msgId]
        }
        val atUserNames = resolveAtUserNames(message)
        if (!msgId.isNullOrBlank()) {
            translationAtUserNamesByMessageId[msgId] = atUserNames
            _translationAtUserNameMessageIds.tryEmit(msgId)
        }
        return atUserNames
    }

    private suspend fun resolveAtUserNames(message: MessageInfo): List<String>? {
        val atUserList = message.atUserList
        if (atUserList.isEmpty()) {
            return null
        }
        val appContext = ContextProvider.getApplicationContext()
        val allMembersText = appContext?.getString(R.string.message_list_mention_all) ?: "All"
        val regularUserIDs = atUserList
            .filter { it != MentionInfo.AT_ALL_USER_ID }
            .distinct()
        val displayNamesByUserID = fetchAtUserDisplayNames(regularUserIDs)
        return TranslationTextParser.getAtUserNames(
            atUserList = atUserList,
            displayNamesByUserID = displayNamesByUserID,
            allMembersText = allMembersText
        )
    }

    private suspend fun fetchAtUserDisplayNames(userIDs: List<String>): Map<String, String> {
        if (userIDs.isEmpty()) {
            return emptyMap()
        }
        return suspendCancellableCoroutine { continuation ->
            V2TIMManager.getInstance().getUsersInfo(
                userIDs,
                object : V2TIMValueCallback<List<V2TIMUserFullInfo>?> {
                    override fun onSuccess(profiles: List<V2TIMUserFullInfo>?) {
                        if (!continuation.isActive) {
                            return
                        }
                        val displayNames = profiles.orEmpty().associate { profile ->
                            profile.userID to (profile.nickName?.takeIf { it.isNotBlank() } ?: profile.userID)
                        }
                        continuation.resume(displayNames)
                    }

                    override fun onError(code: Int, desc: String?) {
                        if (continuation.isActive) {
                            continuation.resume(emptyMap())
                        }
                    }
                }
            )
        }
    }

    fun showReactionDetail(message: MessageInfo) {
        dialogStateStore.showReactionDetail(message)
    }

    fun clearReactionDetail() {
        dialogStateStore.clearReactionDetail()
    }

    fun showEmojiPicker(message: MessageInfo) {
        dialogStateStore.showEmojiPicker(message)
    }

    fun clearEmojiPicker() {
        dialogStateStore.clearEmojiPicker()
    }

    fun forwardMessages(
        messageList: List<MessageInfo>,
        conversationIDList: List<String>,
        completion: CompletionHandler? = null
    ) {
        conversationIDList.forEach { tempConversationID ->
            val sortedMessageList = forwardCoordinator.prepareMessagesForForward(
                messages = messageList,
                conversationID = tempConversationID,
                needReadReceipt = AppBuilderConfig.enableReadReceipt
            )
            val mergedForwardInfo = when (forwardType) {
                MessageForwardType.MERGED -> MergedForwardInfo(
                    title = getForwardMessageTitle(sortedMessageList) ?: "",
                    abstractList = getAbstractList(sortedMessageList),
                    compatibleText = ""
                )
                else -> null
            }
            val sendMessageOption = SendMessageOption(
                needReadReceipt = AppBuilderConfig.enableReadReceipt,
                isExtensionEnabled = false,
                offlinePushInfo = createOfflinePushInfoForMultiConversation("", tempConversationID)
            )
            messageListStore.forwardMessages(
                messageList = sortedMessageList,
                option = ForwardMessageOption(
                    forwardType = forwardType,
                    mergedForwardInfo = mergedForwardInfo,
                    sendMessageOption = sendMessageOption
                ),
                conversationID = tempConversationID,
                completion = completion
            )
        }
    }

    private fun getForwardMessageTitle(messageList: List<MessageInfo>): String? {
        return if (isGroupConversation(conversationID)) {
            getLocalizedString(R.string.message_list_forward_chats)
        } else {
            val loginUserInfo = LoginStore.shared.loginState.loginUserInfo.value
            val selfName = loginUserInfo?.nickname ?: loginUserInfo?.userID ?: ""
            val chatName = conversationListState.conversationList.value
                .firstOrNull { it.conversationID == conversationID }
                ?.title?.takeIf { it.isNotBlank() }
                ?: run {
                    val firstMessage = messageList.firstOrNull()
                    if (firstMessage?.isSentBySelf == true) {
                        firstMessage.to
                    } else {
                        firstMessage?.senderDisplayName.orEmpty()
                    }
                }
            selfName +
                getLocalizedString(R.string.message_list_and_text) +
                chatName +
                getLocalizedString(R.string.message_list_forward_chats_c2c)
        }
    }

    private fun getAbstractList(messageList: List<MessageInfo>): List<String> {
        return messageList.take(3).map { message ->
            val userName = message.senderDisplayName
            getLocalizedString(
                R.string.message_list_forward_abstract_format,
                userName,
                getMessageAbstractText(message)
            )
        }
    }

    private fun getMessageAbstractText(message: MessageInfo): String {
        val appContext = ContextProvider.getApplicationContext() ?: return ""
        return EmojiSpanHelper.replaceEmojiKeysWithNames(
            messageSummaryFormatter.format(appContext, message, conversationID)
        )
    }

    private fun getLocalizedString(resId: Int, vararg formatArgs: Any): String {
        val appContext = ContextProvider.getApplicationContext() ?: return ""
        return if (formatArgs.isEmpty()) {
            appContext.getString(resId)
        } else {
            appContext.getString(resId, *formatArgs)
        }
    }

    private fun isGroupConversation(conversationID: String): Boolean {
        return ConversationIDUtil.isGroup(conversationID)
    }

    private fun createOfflinePushInfoForConversation(conversationID: String, message: MessageInfo): OfflinePushInfo {
        val loginUserInfo = LoginStore.shared.loginState.loginUserInfo.value
        val selfUserId = loginUserInfo?.userID.orEmpty()
        val selfName = loginUserInfo?.nickname ?: selfUserId

        val isGroup = isGroupConversation(conversationID)
        val groupId = ConversationIDUtil.groupId(conversationID)

        val chatName = if (conversationID == this.conversationID) {
            conversationListState.conversationList.value
                .firstOrNull { it.conversationID == conversationID }
                ?.title?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val title = if (isGroup) chatName ?: groupId else selfName
        val description = MessageOfflinePushInfoFactory.trimDescription(getMessageAbstractText(message))

        return MessageOfflinePushInfoFactory.create(
            title = title,
            description = description,
            isGroup = isGroup,
            senderId = if (isGroup) groupId else selfUserId,
            senderNickName = title,
            faceUrl = loginUserInfo?.avatarURL
        )
    }

    private fun createOfflinePushInfoForMultiConversation(
        description: String,
        conversationID: String
    ): OfflinePushInfo {
        val loginUserInfo = LoginStore.shared.loginState.loginUserInfo.value
        val selfUserId = loginUserInfo?.userID.orEmpty()
        val title = loginUserInfo?.nickname ?: selfUserId
        val isGroup = isGroupConversation(conversationID)
        val groupId = ConversationIDUtil.groupId(conversationID)

        return MessageOfflinePushInfoFactory.create(
            title = title,
            description = description,
            isGroup = isGroup,
            senderId = if (isGroup) groupId else selfUserId,
            senderNickName = title,
            faceUrl = loginUserInfo?.avatarURL
        )
    }

    fun addMessageReaction(message: MessageInfo, reactionID: String) {
        viewModelScope.launch {
            val actionStore = MessageActionStore.create(message)
            actionStore.addReaction(reactionID, object : CompletionHandler {
                override fun onSuccess() {}
                override fun onFailure(code: Int, desc: String) {}
            })
        }
    }

    fun removeMessageReaction(message: MessageInfo, reactionID: String) {
        viewModelScope.launch {
            val actionStore = MessageActionStore.create(message)
            actionStore.removeReaction(reactionID, object : CompletionHandler {
                override fun onSuccess() {}
                override fun onFailure(code: Int, desc: String) {}
            })
        }
    }

    fun getCurrentUserID(): String? {
        return LoginStore.shared.loginState.loginUserInfo.value?.userID
    }

    fun isMessageProcessingAuxiliaryText(msgId: String): Boolean {
        return _processingAuxiliaryTextMessageIds.value.contains(msgId)
    }

    fun isAsrTextHidden(msgId: String): Boolean {
        return auxiliaryTextVisibilityStore.isHidden(msgId)
    }

    fun isTranslationHidden(msgId: String): Boolean {
        return auxiliaryTextVisibilityStore.isHidden(msgId)
    }

    override fun onCleared() {
        mediaPreviewController.clear()
        audioController.release()
        listenFromHereController.stop()
        resourceCoordinator.clear()
        super.onCleared()
    }
}

internal data class TranslationProcessingState(
    val processingIds: Set<String>,
    val pendingSuccessIds: Set<String>
)

internal object TranslationProcessingStatePolicy {
    fun afterTranslationSuccess(
        processingIds: Set<String>,
        pendingSuccessIds: Set<String>,
        messageId: String,
        hasVisibleTranslatedText: Boolean
    ): TranslationProcessingState {
        return if (hasVisibleTranslatedText) {
            TranslationProcessingState(
                processingIds = processingIds - messageId,
                pendingSuccessIds = pendingSuccessIds - messageId
            )
        } else {
            TranslationProcessingState(
                processingIds = processingIds + messageId,
                pendingSuccessIds = pendingSuccessIds + messageId
            )
        }
    }

    fun afterTranslatedTextVisible(
        processingIds: Set<String>,
        pendingSuccessIds: Set<String>,
        visibleTranslatedMessageIds: Set<String>
    ): TranslationProcessingState {
        return TranslationProcessingState(
            processingIds = processingIds - visibleTranslatedMessageIds,
            pendingSuccessIds = pendingSuccessIds - visibleTranslatedMessageIds
        )
    }
}

internal object AuxiliaryTextProcessingStatePolicy {
    fun afterVoiceConvertSuccess(
        processingIds: Set<String>,
        pendingSuccessIds: Set<String>,
        messageId: String,
        hasVisibleAsrText: Boolean
    ): TranslationProcessingState {
        return if (hasVisibleAsrText) {
            TranslationProcessingState(
                processingIds = processingIds - messageId,
                pendingSuccessIds = pendingSuccessIds - messageId
            )
        } else {
            TranslationProcessingState(
                processingIds = processingIds + messageId,
                pendingSuccessIds = pendingSuccessIds + messageId
            )
        }
    }

    fun afterAuxiliaryTextVisible(
        processingIds: Set<String>,
        pendingSuccessIds: Set<String>,
        visibleMessageIds: Set<String>
    ): TranslationProcessingState {
        return TranslationProcessingState(
            processingIds = processingIds - visibleMessageIds,
            pendingSuccessIds = pendingSuccessIds - visibleMessageIds
        )
    }
}


