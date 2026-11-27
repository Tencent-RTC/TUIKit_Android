package io.trtc.tuikit.chat.uikit.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.LocalInputSurfaceAnimationState
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.MessageInput
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.rememberInputSurfaceAnimationState
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationPolicy
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageListController
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageList
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageListController
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.rememberMessageListController
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Composable
fun ChatPage(
    conversationID: String,
    locateMessage: MessageInfo? = null,
    messageListConfig: MessageListConfigProtocol = ChatMessageListConfig(),
    messageInputConfig: MessageInputConfigProtocol = ChatMessageInputConfig(),
    onUserClick: (String) -> Unit,
    onChatHeaderClick: (String) -> Unit,
    onBackClick: () -> Unit,
    header: (@Composable (conversation: ConversationInfo?, isMultiSelectMode: Boolean) -> Unit)? = null,
    onMultiSelectStateChanged: (Boolean) -> Unit = {},
    messageListController: MessageListController? = null,
    chatPageController: ChatPageController? = null,
    onTypingStatusChanged: (Boolean) -> Unit = {},
) {
    val conversationListStore = remember { ConversationListStore.create() }
    val scope = rememberCoroutineScope()
    val singleConversation = remember { MutableStateFlow<ConversationInfo?>(null) }
    val conversation = remember {
        combine(
            conversationListStore.state.conversationList,
            singleConversation
        ) { list, single ->
            list.firstOrNull { it.conversationID == conversationID } ?: single
        }.stateIn(scope, SharingStarted.Eagerly, null)
    }
    val conversationState = conversation.collectAsState()
    LaunchedEffect(Unit) {
        conversationListStore.loadConversations()
        conversationListStore.getConversationInfo(
            conversationID,
            object : GetConversationInfoCompletionHandler {
                override fun onSuccess(conversationInfo: ConversationInfo) {
                    singleConversation.value = conversationInfo
                }

                override fun onFailure(code: Int, desc: String) {
                }
            }
        )
    }

    val avatarUrl = conversationState.value?.avatarURL
    val groupID = remember(conversationID) {
        ConversationIDUtil.groupIdOrNull(conversationID)
    }
    val useDefaultHeader = header == null
    val joinedGroupList by GroupStore.shared.state.joinedGroupList.collectAsState()
    val joinedGroupName = remember(joinedGroupList, groupID) {
        groupID?.let { id ->
            joinedGroupList.firstOrNull { it.groupID == id }?.groupName?.takeIf { it.isNotBlank() }
        }
    }
    var resolvedGroupTitle by remember(conversationID) {
        mutableStateOf(
            joinedGroupName
                ?: conversationState.value?.title?.takeIf { it.isNotBlank() && it != groupID && it != conversationID }
                ?: ""
        )
    }
    DisposableEffect(groupID, useDefaultHeader) {
        var active = true
        if (useDefaultHeader && groupID != null) {
            GroupStore.shared.getGroupInfo(
                groupID,
                object : GetGroupInfoCompletionHandler {
                    override fun onSuccess(groupInfo: GroupInfo) {
                        if (!active) return
                        val fetchedName = groupInfo.groupName?.takeIf { it.isNotBlank() }
                        if (fetchedName != null) {
                            resolvedGroupTitle = fetchedName
                        }
                    }

                    override fun onFailure(code: Int, desc: String) = Unit
                }
            )
        }
        onDispose { active = false }
    }
    LaunchedEffect(joinedGroupName) {
        if (joinedGroupName != null) {
            resolvedGroupTitle = joinedGroupName
        }
    }
    LaunchedEffect(conversationState.value?.title, groupID) {
        if (groupID != null) {
            val validTitle = conversationState.value?.title
                ?.takeIf { it.isNotBlank() && it != groupID && it != conversationID }
            if (validTitle != null) {
                resolvedGroupTitle = validTitle
            }
        }
    }
    val displayName = if (groupID != null) {
        resolvedGroupTitle
    } else {
        conversationState.value?.title
    }
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val resolvedMessageListController = messageListController ?: rememberMessageListController()
    val inputSurfaceAnimationState = rememberInputSurfaceAnimationState()
    DisposableEffect(chatPageController, conversationID, context) {
        chatPageController?.bind(conversationID, context)
        onDispose {
            chatPageController?.unbind()
        }
    }
    CompositionLocalProvider(
        LocalMessageListController provides resolvedMessageListController,
        LocalInputSurfaceAnimationState provides inputSurfaceAnimationState
    ) {
    Scaffold(
        modifier = Modifier
            .background(color = colors.bgColorOperate)
            .fillMaxSize()
            .wrapContentHeight(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .background(color = colors.bgColorOperate)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                if (header != null) {
                    header(conversationState.value, isMultiSelectMode)
                } else {
                    ChatHeader(
                        avatarUrl = avatarUrl,
                        name = displayName ?: "",
                        onBackClick = onBackClick,
                        onAvatarClick = {
                            onChatHeaderClick(conversationID)
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (!isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = colors.bgColorOperate)
                ) {
                    val typingEnabled = messageListConfig.enableTyping &&
                        ConversationIDUtil.isC2C(conversationID) &&
                        !ChatbotConversationPolicy.isChatbotConversation(conversationID)
                    MessageInput(
                        modifier = Modifier.navigationBarsPadding(),
                        conversationID = conversationID,
                        config = messageInputConfig,
                        typingStatusSender = if (typingEnabled) {
                            { isTyping -> resolvedMessageListController.sendTypingStatus(isTyping) }
                        } else {
                            null
                        }
                    )
                }
            }

        }) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
        ) {
            MessageList(
                conversationID = conversationID,
                locateMessage = locateMessage,
                config = messageListConfig,
                controller = resolvedMessageListController,
                onTypingStatusChanged = onTypingStatusChanged,
                onMultiSelectStateChanged = {
                    isMultiSelectMode = it
                    onMultiSelectStateChanged(it)
                }
            ) {
                onUserClick(it)
            }
        }
    }
    }
}

@Composable
fun ChatHeader(
    avatarUrl: Any? = null,
    name: String,
    onBackClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {}
) {
    val colors = LocalTheme.current.colors

    Column(
        modifier = Modifier
            .background(color = colors.bgColorOperate)
            .fillMaxWidth()
            .padding(0.dp)
            .wrapContentHeight(unbounded = true)
            .height(IntrinsicSize.Min)

    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(modifier = Modifier.size(40.dp), onClick = onBackClick) {
                Icon(
                    modifier = Modifier
                        .size(16.dp),
                    painter = painterResource(R.drawable.uikit_ic_back),
                    tint = colors.textColorSecondary,
                    contentDescription = "back"
                )
            }
            Row(
                modifier = Modifier
                    .wrapContentSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onAvatarClick() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                Avatar(url = avatarUrl, name = name) {
                    onAvatarClick()
                }

                Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        modifier = Modifier,
                        color = colors.textColorPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        text = name
                    )

                }
            }

        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.strokeColorPrimary)

    }
}
