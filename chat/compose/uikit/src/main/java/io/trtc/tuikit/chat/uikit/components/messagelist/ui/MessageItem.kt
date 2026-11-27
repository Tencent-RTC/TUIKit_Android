package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.AlertDialog
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.MergedMessageDetailActivity
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.DefaultMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.AsrTextBubble
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.CallUnreadDot
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.MessageCheckBox
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.MessageQuoteBubble
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.MessageReactionBar
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.MessageReadReceiptIndicator
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageReactionActionPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.TranslationBubble
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.shouldShowCallUnreadDot
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.FileUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.shouldShowReadReceiptIndicator
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import androidx.compose.material3.Text

@Composable
fun MessageItem(
    modifier: Modifier = Modifier,
    index: Int,
    message: MessageInfo,
    onUserLongPress: (String) -> Unit = {},
    onUserClick: (String?) -> Unit
) {
    val colors = LocalTheme.current.colors
    val config = LocalMessageListConfig.current
    val context = LocalContext.current
    val messageViewModel = LocalMessageListViewModel.current
    val isMultiSelectMode by messageViewModel.isMultiSelectMode.collectAsState()
    val selectedMessages by messageViewModel.selectedMessages.collectAsState()
    val timeString = messageViewModel.getMessageTimeString(index)
    val onBlankAreaClick = LocalBlankAreaClickHandler.current

    val isSelf = message.isSentBySelf
    val contentLayoutDirection = LocalLayoutDirection.current
    val isLeftAligned = MessageItemDisplayPolicy.resolveIsLeftAligned(
        alignment = config.alignment,
        isSelf = isSelf,
        isRtl = contentLayoutDirection == LayoutDirection.Rtl
    )
    val isGroupChat = message.conversationType == ConversationType.GROUP
    val isShowTimeMessage = config.isShowTimeMessage
    val isShowSystemMessage = config.isShowSystemMessage
    val isShowUnsupportMessage = config.isShowUnsupportMessage
    val cellSpacing = config.cellSpacing
    val avatarSpacing = config.avatarSpacing
    val isShowAvatar = if (isLeftAligned) {
        config.isShowLeftAvatar
    } else {
        config.isShowRightAvatar
    }
    val policyShowNickname = MessageItemDisplayPolicy.shouldShowNickname(
        alignment = config.alignment,
        isSelf = isSelf,
        isGroupChat = isGroupChat
    )
    val configShowNickname = if (isLeftAligned) {
        config.isShowLeftNickname
    } else {
        config.isShowRightNickname
    }
    val isShowNickname = policyShowNickname && configShowNickname

    val renderer = MessageRendererRegistry.getRenderer(message, config)
    val cellRenderer = MessageRendererRegistry.getCellRenderer(message, config)
    val renderConfig = renderer.renderConfig
    val renderContext = rememberMessageRenderContext(
        message = message,
        isMultiSelectMode = isMultiSelectMode,
        isSelected = selectedMessages.contains(message)
    )
    CompositionLocalProvider(
        LocalMessageRenderConfig provides renderConfig,
        LocalAudioPlayingState provides messageViewModel.audioPlayingState,
        LocalMessageRenderContext provides renderContext,
        LocalMessageRenderActions provides renderContext.actions
    ) {
        if (!isShowSystemMessage &&
            (message.messageType == MessageType.TIPS || message.status == MessageStatus.REVOKED)
        ) {
            return@CompositionLocalProvider
        }
        if (!isShowUnsupportMessage && renderer is DefaultMessageRenderer && cellRenderer == null) {
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
                cellRenderer.Render(message, renderContext)
            }
            return@CompositionLocalProvider
        }

        if (!renderConfig.showMessageMeta) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = cellSpacing)
            ) {
                if (!timeString.isNullOrEmpty() && isShowTimeMessage) {
                    MessageTimeDivider(timeString = timeString, cellSpacing = cellSpacing)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.messageListTouchTarget()) {
                        renderer.Render(message, renderContext)
                    }
                }
            }
            return@CompositionLocalProvider
        }

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
            maxRowWidth = maxRowWidth
        )
        val maxWidths = MessageItemDisplayPolicy.resolveMaxWidthsForMode(
            maxRowWidth = maxRowWidth,
            preferredBubbleMaxWidth = preferredBubbleMaxWidth,
            checkBoxVisible = isMultiSelectMode,
            statusReserve = statusReserve
        )
        val bubbleMaxWidth = maxWidths.bubbleMaxWidth
        val auxiliaryMaxWidth = maxWidths.auxiliaryMaxWidth

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onBlankAreaClick() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = cellSpacing, bottom = cellSpacing)
            ) {
                if (!timeString.isNullOrEmpty() && isShowTimeMessage) {
                    MessageTimeDivider(timeString = timeString, cellSpacing = cellSpacing)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    if (isMultiSelectMode) {
                        MessageCheckBox(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .messageListTouchTarget(),
                            checked = selectedMessages.contains(message)
                        )
                        Spacer(Modifier.width(MessageItemDisplayPolicy.MULTI_SELECT_CHECKBOX_MARGIN_END_DP.dp))
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = if (isLeftAligned) Arrangement.Start else Arrangement.End,
                            verticalAlignment = Alignment.Top
                        ) {
                            if (isLeftAligned && isShowAvatar) {
                                MessageAvatar(
                                    message = message,
                                    onLongPress = onUserLongPress,
                                    onClick = { onUserClick(message.from.userID) }
                                )
                                Spacer(Modifier.width(avatarSpacing))
                            }

                            Column(
                                horizontalAlignment = if (isLeftAligned) Alignment.Start else Alignment.End
                            ) {
                                if (isShowNickname) {
                                    CompositionLocalProvider(
                                        LocalLayoutDirection provides contentLayoutDirection
                                    ) {
                                        MessageNickname(
                                            message = message,
                                            onUserLongPress = onUserLongPress,
                                            onUserClick = { onUserClick(message.from.userID) }
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }

                                CompositionLocalProvider(
                                    LocalCurrentMessage provides message,
                                    LocalBubbleMaxWidth provides bubbleMaxWidth,
                                    LocalMessageContentMaxWidth provides maxWidths.contentMaxWidth
                                ) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        if (accessoryLayout.statusBeforeBubble) {
                                            MessageSideAccessory(
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
                                        CompositionLocalProvider(
                                            LocalLayoutDirection provides contentLayoutDirection
                                        ) {
                                            MessageContent(
                                                message = message,
                                                maxWidth = bubbleMaxWidth,
                                                onLongPress = { bounds ->
                                                    messageViewModel.showLongPressActionDialog(message, bounds)
                                                },
                                                onTap = {
                                                    if (message.messageType == MessageType.MERGED) {
                                                        MergedMessageDetailActivity.start(context, message, config)
                                                    } else if (message.messageType == MessageType.FILE) {
                                                        val filePayload =
                                                            message.messagePayload as? FileMessagePayload
                                                        val filePath = filePayload?.filePath
                                                        val fileName = filePayload?.fileName
                                                        if (filePath.isNullOrEmpty()) {
                                                            messageViewModel.downloadFile(message)
                                                        } else {
                                                            FileUtils.openFile(context, filePath, fileName)
                                                        }
                                                    } else if (message.messageType == MessageType.VIDEO) {
                                                        messageViewModel.showImage(context, message)
                                                    } else if (message.messageType == MessageType.AUDIO) {
                                                        messageViewModel.playAudioMessage(message)
                                                    } else if (message.messageType == MessageType.IMAGE) {
                                                        messageViewModel.showImage(context, message)
                                                    }
                                                },
                                                onRendered = {
                                                    if (message.messageType == MessageType.VIDEO) {
                                                        messageViewModel.downloadVideoSnapShot(message)
                                                    } else if (message.messageType == MessageType.IMAGE) {
                                                        messageViewModel.downloadThumbImage(message)
                                                    } else if (message.messageType == MessageType.AUDIO) {
                                                        messageViewModel.downloadSound(message)
                                                    }
                                                }
                                            )
                                        }
                                        if (!accessoryLayout.statusBeforeBubble) {
                                            MessageSideAccessory(
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

                                    val hasReactions = config.isSupportReaction &&
                                        message.reactionList.isNotEmpty() &&
                                        message.status == MessageStatus.SEND_SUCCESS
                                    if (hasReactions) {
                                        val reactionUsersBySnapshotKey by
                                            messageViewModel.reactionUsersBySnapshotKey.collectAsState()
                                        val reactionMsgId = message.msgID.orEmpty()
                                        LaunchedEffect(reactionMsgId, message.reactionList) {
                                            message.reactionList.forEach { reaction ->
                                                if (MessageReactionActionPolicy.needsFullUserFetch(reaction)) {
                                                    messageViewModel.ensureReactionUsersLoaded(message, reaction)
                                                }
                                            }
                                        }
                                        CompositionLocalProvider(
                                            LocalBubbleMaxWidth provides auxiliaryMaxWidth,
                                            LocalLayoutDirection provides contentLayoutDirection
                                        ) {
                                            MessageReactionBar(
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                                    .messageListTouchTarget(),
                                                reactionList = message.reactionList,
                                                isLeftAligned = isLeftAligned,
                                                onEmojiClick = if (!isMultiSelectMode) {
                                                    { reaction ->
                                                        if (reaction.reactedByMyself) {
                                                            messageViewModel.removeMessageReaction(
                                                                message,
                                                                reaction.reactionID
                                                            )
                                                        } else {
                                                            messageViewModel.addMessageReaction(
                                                                message,
                                                                reaction.reactionID
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    null
                                                },
                                                reactionUsersProvider = { reaction ->
                                                    if (reactionMsgId.isNotBlank() &&
                                                        MessageReactionActionPolicy.needsFullUserFetch(reaction)
                                                    ) {
                                                        val snapshotKey = MessageReactionActionPolicy
                                                            .reactionSnapshotKey(reactionMsgId, reaction)
                                                        reactionUsersBySnapshotKey[snapshotKey]
                                                            ?: reaction.partialUserList
                                                    } else {
                                                        reaction.partialUserList
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    val quoteInfo = message.quoteInfo
                                    if (quoteInfo != null) {
                                        val quoteHandler = LocalQuoteClickHandler.current
                                        CompositionLocalProvider(
                                            LocalLayoutDirection provides contentLayoutDirection
                                        ) {
                                            MessageQuoteBubble(
                                                quoteInfo = quoteInfo,
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .widthIn(max = bubbleMaxWidth)
                                                    .messageListTouchTarget(),
                                                onClick = if (!isMultiSelectMode) {
                                                    { quoteHandler(message, quoteInfo) }
                                                } else {
                                                    null
                                                }
                                            )
                                        }
                                    }
                                }

                                CompositionLocalProvider(
                                    LocalBubbleMaxWidth provides auxiliaryMaxWidth,
                                    LocalLayoutDirection provides contentLayoutDirection
                                ) {
                                    if (message.messageType == MessageType.AUDIO) {
                                        val processingAuxiliaryTextMessageIds by
                                            messageViewModel.processingAuxiliaryTextMessageIds.collectAsState()
                                        val hiddenAuxiliaryTextMessageIds by
                                            messageViewModel.hiddenAuxiliaryTextMessageIds.collectAsState()
                                        val msgID = message.msgID
                                        val isConverting = processingAuxiliaryTextMessageIds.contains(msgID)
                                        val isAsrTextHidden = hiddenAuxiliaryTextMessageIds.contains(msgID)
                                        val asrText = (message.messagePayload as? AudioMessagePayload)?.asrText
                                        val shouldShowAsrBubble =
                                            isConverting || (!asrText.isNullOrEmpty() && !isAsrTextHidden)

                                        if (shouldShowAsrBubble) {
                                            var showAsrMenu by remember { mutableStateOf(false) }
                                            AsrTextBubble(
                                                modifier = Modifier.messageListTouchTarget(),
                                                isSelf = message.isSentBySelf,
                                                isLoading = isConverting,
                                                asrText = asrText,
                                                showMenu = showAsrMenu,
                                                onLongPress = { showAsrMenu = true },
                                                onDismissMenu = { showAsrMenu = false },
                                                onHide = { messageViewModel.hideAsrText(message) },
                                                onForward = { messageViewModel.forwardAsrText(message) },
                                                onCopy = { messageViewModel.copyAsrText(message, context) }
                                            )
                                        }
                                    }

                                    if (message.messageType == MessageType.TEXT) {
                                        val processingAuxiliaryTextMessageIds by
                                            messageViewModel.processingAuxiliaryTextMessageIds.collectAsState()
                                        val hiddenAuxiliaryTextMessageIds by
                                            messageViewModel.hiddenAuxiliaryTextMessageIds.collectAsState()
                                        val msgID = message.msgID
                                        val isTranslating = processingAuxiliaryTextMessageIds.contains(msgID)
                                        val isTranslationHidden = hiddenAuxiliaryTextMessageIds.contains(msgID)
                                        val translatedTextMap =
                                            (message.messagePayload as? TextMessagePayload)?.translatedText
                                        val shouldShowTranslationBubble =
                                            isTranslating ||
                                                (!translatedTextMap.isNullOrEmpty() && !isTranslationHidden)

                                        if (shouldShowTranslationBubble) {
                                            var showTranslationMenu by remember { mutableStateOf(false) }
                                            val translatedText =
                                                messageViewModel.getTranslatedDisplayText(message)
                                            TranslationBubble(
                                                modifier = Modifier.messageListTouchTarget(),
                                                isSelf = message.isSentBySelf,
                                                isLoading = isTranslating,
                                                translatedText = translatedText,
                                                showMenu = showTranslationMenu,
                                                onLongPress = { showTranslationMenu = true },
                                                onDismissMenu = { showTranslationMenu = false },
                                                onHide = { messageViewModel.hideTranslation(message) },
                                                onForward = {
                                                    messageViewModel.forwardTranslatedText(message)
                                                },
                                                onCopy = {
                                                    messageViewModel.copyTranslatedText(message, context)
                                                }
                                            )
                                        }
                                    }
                                }

                                if (message.status == MessageStatus.VIOLATION) {
                                    CompositionLocalProvider(
                                        LocalLayoutDirection provides contentLayoutDirection
                                    ) {
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
                                MessageAvatar(
                                    message = message,
                                    onLongPress = onUserLongPress,
                                    onClick = { onUserClick(message.from.userID) }
                                )
                            }
                        }
                    }
                }
            }

            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .messageListTouchTarget()
                        .clickable(indication = null, interactionSource = null) {
                            messageViewModel.toggleMessageSelection(message)
                        }
                )
            }
        }
    }
}

@Composable
private fun MessageTimeDivider(
    timeString: String,
    cellSpacing: Dp
) {
    val colors = LocalTheme.current.colors
    val timeSpacing = (20.dp - cellSpacing).coerceAtLeast(0.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = timeSpacing, bottom = timeSpacing),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = timeString,
            fontSize = 14.sp,
            color = colors.textColorSecondary,
            fontWeight = FontWeight.W400
        )
    }
}

@Composable
internal fun MessageNickname(
    message: MessageInfo,
    onUserLongPress: (String) -> Unit = {},
    onUserClick: () -> Unit,
) {
    val colors = LocalTheme.current.colors
    val userID = message.from.userID
    val nicknameInteractionSource = remember { MutableInteractionSource() }
    Text(
        modifier = Modifier
            .messageListTouchTarget()
            .combinedClickable(
            interactionSource = nicknameInteractionSource,
            indication = null,
            onClick = onUserClick,
            onLongClick = { onUserLongPress(userID) }
        ),
        text = message.senderDisplayName,
        fontSize = 12.sp,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        color = colors.textColorSecondary
    )
}

@Composable
fun MessageAvatar(
    message: MessageInfo,
    onLongPress: (String) -> Unit = {},
    onClick: () -> Unit
) {
    val faceUrl = message.from.avatarURL
    val title = message.senderDisplayName.firstOrNull()?.uppercase() ?: ""
    val userID = message.from.userID
    Box(modifier = Modifier.messageListTouchTarget()) {
        Avatar(
            modifier = Modifier.size(40.dp),
            url = faceUrl,
            name = title,
            size = AvatarSize.S
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(userID) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongPress(userID) }
                    )
                }
        )
    }
}

@Composable
fun MessageContent(
    message: MessageInfo,
    maxWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    enableGesture: Boolean = true,
    onLongPress: (Rect?) -> Unit = {},
    onTap: () -> Unit = {},
    onRendered: () -> Unit = {}
) {
    var bubbleBounds by remember { mutableStateOf<Rect?>(null) }
    val messageViewModel = LocalMessageListViewModel.current
    val isMultiSelectMode by messageViewModel.isMultiSelectMode.collectAsState()
    val selectedMessages by messageViewModel.selectedMessages.collectAsState()
    val renderContext = rememberMessageRenderContext(
        message = message,
        isMultiSelectMode = isMultiSelectMode,
        isSelected = selectedMessages.contains(message)
    )
    Box(
        modifier = Modifier
            .then(if (maxWidth != androidx.compose.ui.unit.Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
            .messageListTouchTarget()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                bubbleBounds = Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
            }
            .pointerInput(message.msgID, enableGesture) {
                if (enableGesture) {
                    detectTapGestures(
                        onLongPress = { onLongPress(bubbleBounds) }
                    )
                }
            }
    ) {
        MessageBubbleSurface(
            message = message,
            isAggregation = false
        ) {
            CompositionLocalProvider(
                LocalCurrentMessage provides message,
                LocalInteractionHandler provides InteractionHandler(
                    onTap = onTap,
                    onLongPress = { onLongPress(bubbleBounds) },
                    onRendered = onRendered
                ),
                LocalMessageRenderContext provides renderContext,
                LocalMessageRenderActions provides renderContext.actions
            ) {
                val config = LocalMessageListConfig.current
                val renderer = MessageRendererRegistry.getRenderer(message, config)
                renderer.Render(message, renderContext)
            }
        }
    }
}

@Composable
private fun MessageSideAccessory(
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
            textLayoutDirection = textLayoutDirection
        )
    }
}

@Composable
fun MessageStatusContent(
    message: MessageInfo,
    modifier: Modifier = Modifier,
    textLayoutDirection: LayoutDirection = LocalLayoutDirection.current,
    enableInteraction: Boolean = true
) {
    val viewModel = LocalMessageListViewModel.current
    val config = LocalMessageListConfig.current
    val colors = LocalTheme.current.colors
    val activity = LocalActivity.current
    val readReceiptSignature = LocalReadReceiptSnapshots.current[message.msgID].orEmpty()
    Box(modifier = modifier.messageListTouchTarget(), contentAlignment = Alignment.Center) {
        if (message.isSentBySelf &&
            (message.status == MessageStatus.SEND_FAIL || message.status == MessageStatus.VIOLATION)
        ) {
            var showResendTips by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.clickable(
                    enabled = enableInteraction && message.status == MessageStatus.SEND_FAIL
                ) {
                    showResendTips = true
                }
            ) {
                Text(
                    modifier = Modifier
                        .size(MessageItemDisplayPolicy.FAIL_ICON_SIZE_DP.dp)
                        .background(color = colors.textColorError, shape = CircleShape)
                        .clip(CircleShape)
                        .wrapContentSize(),
                    text = "!",
                    color = colors.textColorButton,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            AlertDialog(
                isVisible = showResendTips,
                message = stringResource(R.string.message_list_resend_tips),
                onDismiss = { showResendTips = false },
                onCancel = { showResendTips = false },
                onConfirm = { viewModel.retrySendMessage(activity, message) }
            )
        } else if (message.status == MessageStatus.SENDING) {
            SendingIndicator()
        } else if (message.shouldShowReadReceiptIndicator(config.isShowReadReceipt)) {
            CompositionLocalProvider(LocalLayoutDirection provides textLayoutDirection) {
                MessageReadReceiptIndicator(
                    message = message,
                    receiptSignature = readReceiptSignature,
                    onClick = {
                        if (enableInteraction) {
                            viewModel.showReadReceiptDialog(message)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SendingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "sending")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing)
        ),
        label = "sendingRotation"
    )
    Canvas(
        modifier = Modifier
            .size(MessageItemDisplayPolicy.SENDING_INDICATOR_SIZE_DP.dp)
            .rotate(rotation)
    ) {
        val radius = size.minDimension / 2f
        val innerRadius = radius / 3.2f
        val thickness = radius / 10f
        drawCircle(
            brush = Brush.sweepGradient(
                0f to Color(0x1F888888),
                0.5f to Color(0x66888888),
                1f to Color(0xFF888888)
            ),
            radius = innerRadius + thickness / 2f,
            style = Stroke(width = thickness)
        )
    }
}
