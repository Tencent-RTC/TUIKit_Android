package io.trtc.tuikit.chat.uikit.components.messagelist

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalAudioPlayingState
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalBubbleMaxWidth
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalCurrentMessage
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalHighlightManager
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageContentMaxWidth
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageListTouchTargetHitTester
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageListViewModel
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageRenderActions
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalQuoteClickHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalVisibleMessageIds
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRendererDisposeEffect
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageItemDisplayPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageListTouchTargetHitTester
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageNickname
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageQuoteLocatePolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRendererRegistry
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageStatusContent
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageStatusLayout
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.ProvideMessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListLocateCoordinator
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageMetadataTextStyle
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageListTouchTarget
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.rememberMessageListRenderActions
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.rememberMessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.rememberStatusReserveWidth
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.rememberVisibleMessageIds
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.CallUnreadDot
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.MessageQuoteBubble
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.shouldShowCallUnreadDot
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageAvatar as SharedMessageAvatar
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageContent as SharedMessageContent
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.DateTimeUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.FileUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MergedMessageDetailViewModel
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MergedMessageDetailViewModelFactory
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModelFactory
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MergedMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import kotlinx.coroutines.launch

val LocalMergedMessageDetailViewModel =
    compositionLocalOf<MergedMessageDetailViewModel> { error("No ViewModel provided") }

class MergedMessageDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MSG = "extra_msg"

        // MessageListConfigProtocol carries renderer instances and cannot go through an Intent,
        // so the host config is handed over in-process instead.
        @Volatile
        private var hostConfig: MessageListConfigProtocol? = null

        @JvmStatic
        @JvmOverloads
        fun start(
            context: Context,
            mergedMessage: MessageInfo,
            hostConfig: MessageListConfigProtocol? = null
        ) {
            this.hostConfig = hostConfig
            val intent = Intent(context, MergedMessageDetailActivity::class.java).apply {
                putExtra(EXTRA_MSG, mergedMessage)
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        internal fun peekHostConfig(): MessageListConfigProtocol? = hostConfig
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val message = intent.getParcelableExtra<MessageInfo>(EXTRA_MSG)

        if (message !is MessageInfo) {
            finish()
            return
        }
        val messageListStore = MessageListStore.create("")
        val viewModel by viewModels<MergedMessageDetailViewModel> {
            MergedMessageDetailViewModelFactory(messageListStore, message)
        }
        val messageListViewModel by viewModels<MessageListViewModel> {
            MessageListViewModelFactory(
                messageListStore = messageListStore,
                conversationID = "",
                locateMessage = null,
                enableMediaPreviewBoundaryLoading = false,
                reverseMediaPreviewMessageOrder = false
            )
        }

        setContent {
            DisposableEffect(Unit) {
                messageListViewModel.initializeAudioPlayer()
                onDispose {
                    messageListViewModel.destroyAudioPlayer()
                }
            }

            SetActivitySystemBarAppearance()

            CompositionLocalProvider(
                LocalMergedMessageDetailViewModel provides viewModel,
                LocalMessageListViewModel provides messageListViewModel,
                LocalAudioPlayingState provides messageListViewModel.audioPlayingState,
                LocalHighlightManager provides messageListViewModel.highlightManager
            ) {
                MergedMessageDetailScreen(
                    message = message,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun MergedMessageDetailScreen(
    message: MessageInfo,
    onBack: () -> Unit
) {
    val colors = LocalTheme.current.colors

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorOperate)
            .systemBarsPadding(),
        topBar = {
            MergedMessageTopBar(message, onBack = onBack)
        }
    ) { paddingValues ->
        MergedMessageList(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}

@Composable
fun MergedMessageTopBar(
    message: MessageInfo,
    onBack: () -> Unit
) {
    val colors = LocalTheme.current.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.bgColorOperate)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.uikit_ic_back),
                    contentDescription = stringResource(R.string.contact_list_back),
                    modifier = Modifier.size(16.dp),
                    tint = colors.textColorSecondary
                )
            }

            Text(
                text = (message.messagePayload as? MergedMessagePayload)?.title
                    ?: stringResource(R.string.message_list_merge_message),
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = colors.textColorPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .align(Alignment.Center)
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.strokeColorPrimary)
    }
}

@Composable
fun MergedMessageList(
    modifier: Modifier = Modifier,
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val viewModel = LocalMergedMessageDetailViewModel.current
    val messageListViewModel = LocalMessageListViewModel.current
    val messageList by viewModel.messageList.collectAsState()
    val layoutDirection = LocalLayoutDirection.current
    val mergedConfig: MessageListConfigProtocol = remember(layoutDirection) {
        val baseConfig = ChatMessageListConfig(
            alignment = if (layoutDirection == LayoutDirection.Rtl) {
                MessageAlignment.RIGHT
            } else {
                MessageAlignment.LEFT
            },
            isShowReadReceipt = false
        )
        val hostConfig = MergedMessageDetailActivity.peekHostConfig()
            ?: return@remember baseConfig
        MergedMessageDetailListConfig(delegateConfig = hostConfig, baseConfig = baseConfig)
    }
    val displayedMessages = remember(messageList, mergedConfig) {
        messageList.filter { shouldDisplayMessage(it, mergedConfig) }
    }
    LaunchedEffect(displayedMessages) {
        messageListViewModel.setMediaPreviewMessages(displayedMessages)
    }
    DisposableEffect(messageListViewModel, viewModel, mergedConfig) {
        messageListViewModel.setMediaPreviewMessagesRefresher { onCompleted ->
            viewModel.refreshMessageList { refreshedMessages ->
                // Update the preview source synchronously: the refresh caller reads media paths
                // back as soon as it is notified, which is before the effect above recomposes.
                messageListViewModel.setMediaPreviewMessages(
                    refreshedMessages.filter { shouldDisplayMessage(it, mergedConfig) }
                )
                onCompleted()
            }
        }
        onDispose {
            messageListViewModel.setMediaPreviewMessagesRefresher(null)
        }
    }

    val listState = rememberLazyListState()
    val visibleMessageIds = rememberVisibleMessageIds(listState)
    val locateCoordinator = remember(messageListViewModel.highlightManager) {
        MessageListLocateCoordinator(messageListViewModel.highlightManager)
    }
    val scope = rememberCoroutineScope()

    fun locateQuotedMessage(quoteInfo: MessageQuoteInfo) {
        if (MessageQuoteLocatePolicy.isOriginalMessageUnreachable(quoteInfo)) {
            Toast.info(context, context.getString(R.string.message_list_quote_original_unreachable))
            return
        }
        val targetMessageId = MessageQuoteLocatePolicy.findLoadedTargetMessageId(
            quoteInfo,
            displayedMessages
        )
        if (targetMessageId == null) {
            if (isFilteredQuoteTarget(quoteInfo, messageList, mergedConfig)) {
                Toast.info(context, context.getString(R.string.message_list_quote_original_filtered))
            } else {
                Toast.info(context, context.getString(R.string.message_list_quote_original_unreachable))
            }
            return
        }
        locateCoordinator.requestLocateMessage(targetMessageId)
        scope.launch {
            locateCoordinator.applyPending(displayedMessages, listState)
        }
    }

    val renderActions = rememberMessageListRenderActions()
    val touchTargetHitTester = remember { MessageListTouchTargetHitTester() }
    CompositionLocalProvider(
        LocalQuoteClickHandler provides { _, quoteInfo -> locateQuotedMessage(quoteInfo) },
        LocalMessageListConfig provides mergedConfig,
        LocalMessageRenderActions provides renderActions,
        LocalMessageListTouchTargetHitTester provides touchTargetHitTester,
        LocalVisibleMessageIds provides visibleMessageIds
    ) {
        Column(
            modifier = modifier
                .background(color = colors.bgColorOperate)
                .padding(vertical = 12.dp)
        ) {
            if (messageList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.textColorSecondary,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    state = listState,
                    reverseLayout = false,
                    verticalArrangement = Arrangement.Top
                ) {
                    itemsIndexed(
                        items = displayedMessages,
                        key = { _, item -> item.msgID }
                    ) { _, message ->
                        MessageItem(
                            modifier = Modifier.animateItem(),
                            message = message,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: MessageInfo,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTheme.current.colors
    val config = LocalMessageListConfig.current
    val contentLayoutDirection = LocalLayoutDirection.current
    val isSelf = message.isSentBySelf
    val isLeftAligned = MessageItemDisplayPolicy.resolveIsLeftAligned(
        alignment = config.alignment,
        isSelf = isSelf,
        isRtl = contentLayoutDirection == LayoutDirection.Rtl
    )
    val isShowAvatar = if (isLeftAligned) {
        config.isShowLeftAvatar
    } else {
        config.isShowRightAvatar
    }
    val isShowNickname = if (isLeftAligned) {
        config.isShowLeftNickname
    } else {
        config.isShowRightNickname
    }
    val cellSpacing = config.cellSpacing
    val avatarSpacing = config.avatarSpacing
    val renderer = MessageRendererRegistry.getRenderer(message, config)
    val cellRenderer = MessageRendererRegistry.getCellRenderer(message, config)
    val renderConfig = renderer.renderConfig
    val renderContext = rememberMessageRenderContext(
        message = message,
        isMultiSelectMode = false,
        isSelected = false
    )
    CompositionLocalProvider(
        LocalMessageRenderConfig provides renderConfig,
        LocalMessageRenderContext provides renderContext,
        LocalMessageRenderActions provides renderContext.actions
    ) {
        if (!shouldDisplayMessage(message, config)) {
            return@CompositionLocalProvider
        }
        MessageRendererDisposeEffect(
            message = message,
            renderer = renderer,
            cellRenderer = cellRenderer
        )
        if (cellRenderer != null) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = cellSpacing, bottom = cellSpacing)
                    .messageListTouchTarget()
            ) {
                ProvideMessageRenderContext(renderContext) {
                    cellRenderer.Render(message, renderContext)
                }
            }
            return@CompositionLocalProvider
        }
        if (!renderConfig.showMessageMeta) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = cellSpacing)
            ) {
                Box(modifier = Modifier.messageListTouchTarget()) {
                    ProvideMessageRenderContext(renderContext) {
                        renderer.Render(message, renderContext)
                    }
                }
            }
            return@CompositionLocalProvider
        }

        val inlineTimeString = DateTimeUtils.formatMessageListTime(
            timestampMs = message.timestamp?.times(1000),
            yesterdayLabel = stringResource(R.string.message_list_time_yesterday)
        )
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val showCallUnreadDot = message.shouldShowCallUnreadDot()
        val statusLayout = MessageItemDisplayPolicy.resolveStatusLayout(isLeftAligned)
        val accessoryLayout = if (showCallUnreadDot) {
            MessageItemDisplayPolicy.resolveStatusLayout(
                isLeftAligned = isLeftAligned,
                gap = MessageItemDisplayPolicy.CALL_UNREAD_DOT_MARGIN_DP.dp
            )
        } else {
            statusLayout
        }
        val statusReserve = rememberStatusReserveWidth(
            message = message,
            showCallUnreadDot = showCallUnreadDot,
            showMessageReadReceipt = config.isShowReadReceipt,
            layout = accessoryLayout
        )
        val maxRowWidth = MessageItemDisplayPolicy.resolveMaxRowWidth(
            screenWidth = screenWidth,
            horizontalPadding = config.horizontalPadding,
            avatarSpacing = avatarSpacing,
            showAvatar = isShowAvatar
        )
        val preferredBubbleMaxWidth = MessageItemDisplayPolicy.resolvePreferredBubbleMaxWidth(
            screenWidth = screenWidth,
            maxRowWidth = maxRowWidth,
            fillAvailableWidth = true
        )
        val maxWidths = MessageItemDisplayPolicy.resolveMaxWidthsForMode(
            maxRowWidth = maxRowWidth,
            preferredBubbleMaxWidth = preferredBubbleMaxWidth,
            checkBoxVisible = false,
            statusReserve = statusReserve,
            fillAvailableWidth = true
        )

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = cellSpacing, bottom = cellSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = if (isLeftAligned) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (isLeftAligned && isShowAvatar) {
                            Avatar(message = message, onClick = {})
                            Spacer(Modifier.width(avatarSpacing))
                        }
                        Column(
                            horizontalAlignment = if (isLeftAligned) Alignment.Start else Alignment.End
                        ) {
                            if (isShowNickname || !inlineTimeString.isNullOrEmpty()) {
                                CompositionLocalProvider(
                                    LocalLayoutDirection provides contentLayoutDirection
                                ) {
                                    MergedMessageHeader(
                                        message = message,
                                        showNickname = isShowNickname,
                                        timeString = inlineTimeString
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            CompositionLocalProvider(
                                LocalCurrentMessage provides message,
                                LocalBubbleMaxWidth provides maxWidths.bubbleMaxWidth,
                                LocalMessageContentMaxWidth provides maxWidths.contentMaxWidth,
                                LocalLayoutDirection provides contentLayoutDirection
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    if (accessoryLayout.statusBeforeBubble) {
                                        MergedMessageSideAccessory(
                                            message = message,
                                            layout = accessoryLayout,
                                            textLayoutDirection = contentLayoutDirection,
                                            modifier = if (showCallUnreadDot) {
                                                Modifier.align(Alignment.CenterVertically)
                                            } else {
                                                Modifier
                                            }
                                        )
                                    }
                                    MergedMessageContent(
                                        message = message,
                                        maxWidth = maxWidths.bubbleMaxWidth,
                                        enableGesture = false
                                    )
                                    if (!accessoryLayout.statusBeforeBubble) {
                                        MergedMessageSideAccessory(
                                            message = message,
                                            layout = accessoryLayout,
                                            textLayoutDirection = contentLayoutDirection,
                                            modifier = if (showCallUnreadDot) {
                                                Modifier.align(Alignment.CenterVertically)
                                            } else {
                                                Modifier
                                            }
                                        )
                                    }
                                }
                                val quoteInfo = message.quoteInfo
                                if (quoteInfo != null) {
                                    val quoteHandler = LocalQuoteClickHandler.current
                                    MessageQuoteBubble(
                                        quoteInfo = quoteInfo,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .widthIn(max = maxWidths.bubbleMaxWidth)
                                            .messageListTouchTarget(),
                                        onClick = { quoteHandler(message, quoteInfo) }
                                    )
                                }
                                if (message.status == MessageStatus.VIOLATION) {
                                    Text(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .messageListTouchTarget(),
                                        text = stringResource(R.string.message_list_violation_received),
                                        fontSize = 12.sp,
                                        color = colors.textColorError
                                    )
                                }
                            }
                        }
                        if (!isLeftAligned && isShowAvatar) {
                            Spacer(Modifier.width(avatarSpacing))
                            Avatar(message = message, onClick = {})
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MergedMessageHeader(
    message: MessageInfo,
    showNickname: Boolean,
    timeString: String?
) {
    val colors = LocalTheme.current.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showNickname) {
            Box(modifier = Modifier.weight(1f, fill = false)) {
                MessageNickname(message = message, onUserClick = {})
            }
        }
        if (!timeString.isNullOrEmpty()) {
            Text(
                text = timeString,
                style = messageMetadataTextStyle(),
                color = colors.textColorSecondary,
                maxLines = 1,
                modifier = if (showNickname) {
                    Modifier.padding(start = MessageItemDisplayPolicy.INLINE_MESSAGE_TIME_MARGIN_START_DP.dp)
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
fun Avatar(
    message: MessageInfo,
    onClick: () -> Unit
) {
    SharedMessageAvatar(
        message = message,
        onClick = onClick
    )
}

@Composable
fun MessageContent(
    message: MessageInfo,
    isAggregation: Boolean = false,
    enableGesture: Boolean = true
) {
    BoxWithConstraints {
        MergedMessageContent(
            message = message,
            maxWidth = maxWidth * 0.9f,
            enableGesture = enableGesture
        )
    }
}

@Composable
private fun MergedMessageContent(
    message: MessageInfo,
    maxWidth: Dp,
    enableGesture: Boolean
) {
    val context = LocalContext.current
    val messageListViewModel = LocalMessageListViewModel.current
    val mergedDetailViewModel = LocalMergedMessageDetailViewModel.current
    val config = LocalMessageListConfig.current
    SharedMessageContent(
        message = message,
        maxWidth = maxWidth,
        enableGesture = enableGesture,
        onTap = {
            if (message.messageType == MessageType.MERGED) {
                MergedMessageDetailActivity.start(context, message, config)
            } else if (message.messageType == MessageType.FILE) {
                val filePayload = message.messagePayload as? FileMessagePayload
                val filePath = filePayload?.filePath
                val fileName = filePayload?.fileName
                if (filePath.isNullOrEmpty()) {
                    messageListViewModel.downloadFile(message)
                } else {
                    FileUtils.openFile(context, filePath, fileName)
                }
            } else if (message.messageType == MessageType.VIDEO ||
                message.messageType == MessageType.IMAGE
            ) {
                messageListViewModel.showImage(context, message)
            } else if (message.messageType == MessageType.AUDIO) {
                messageListViewModel.playAudioMessage(message)
            }
        },
        onRendered = {
            if (message.messageType == MessageType.VIDEO) {
                mergedDetailViewModel.downloadVideoSnapShot(message)
            } else if (message.messageType == MessageType.IMAGE) {
                mergedDetailViewModel.downloadThumbImage(message)
            } else if (message.messageType == MessageType.AUDIO) {
                mergedDetailViewModel.downloadSound(message)
            }
        }
    )
}

@Composable
private fun MergedMessageSideAccessory(
    message: MessageInfo,
    layout: MessageStatusLayout,
    textLayoutDirection: LayoutDirection,
    modifier: Modifier = Modifier
) {
    val accessoryModifier = modifier.padding(
        start = layout.marginStart,
        end = layout.marginEnd
    )
    if (message.shouldShowCallUnreadDot()) {
        CallUnreadDot(
            message = message,
            modifier = accessoryModifier.messageListTouchTarget()
        )
    } else {
        MessageStatusContent(
            message = message,
            modifier = accessoryModifier,
            textLayoutDirection = textLayoutDirection,
            enableInteraction = false
        )
    }
}

private fun shouldDisplayMessage(
    message: MessageInfo,
    config: MessageListConfigProtocol
): Boolean {
    if (!config.isShowSystemMessage &&
        (message.messageType == MessageType.TIPS || message.status == MessageStatus.REVOKED)
    ) {
        return false
    }
    if (!config.isShowUnsupportMessage &&
        MessageRendererRegistry.isDefaultBuiltInRenderer(message, config)
    ) {
        return false
    }
    if (message.messageType == MessageType.CUSTOM) {
        val callModel = CallMessageParser.parse(message)
        if (callModel?.isExcludeFromHistory == true) {
            return false
        }
    }
    if (config.messageExclusionMatchers.any { it.matches(message) }) {
        return false
    }
    return true
}

private fun isFilteredQuoteTarget(
    quoteInfo: MessageQuoteInfo,
    rawMessages: List<MessageInfo>,
    config: MessageListConfigProtocol
): Boolean {
    val target = rawMessages.firstOrNull { quoteInfo.msgID.isNotBlank() && it.msgID == quoteInfo.msgID }
        ?: rawMessages.firstOrNull { quoteInfo.sequence > 0 && it.sequence == quoteInfo.sequence }
        ?: return false
    return !shouldDisplayMessage(target, config)
}
