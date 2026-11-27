package io.trtc.tuikit.chat.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.google.gson.Gson
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.trtc.tuikit.chat.uikit.components.widgets.AlertDialog
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.common.onEvent
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationPolicy
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup.AddContactAndGroupBottomSheet
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupEvent
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.Event
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.chatsetting.ChatSettingActivity
import io.trtc.tuikit.chat.common.AppConstants
import io.trtc.tuikit.chat.common.MessageInputCallRedDotStore
import io.trtc.tuikit.chat.customMessages.CustomLinkMessage
import io.trtc.tuikit.chat.customMessages.CustomLinkMessageRenderer
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputActionIDs
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageListController
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.rememberMessageListController
import io.trtc.tuikit.chat.uikit.compose.R as UiKitR
import io.trtc.tuikit.chat.uikit.pages.ChatPage
import io.trtc.tuikit.chat.uikit.pages.rememberChatPageController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : BaseActivity() {

    val contactStore = ContactStore.shared

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversationID"
        private const val EXTRA_LOCATE_MESSAGE = "locateMessage"
        private const val DEMO_MESSAGE_INPUT_CUSTOM_LINK_ACTION_ID = "demo.messageInput.customLink"

        fun start(context: Context, conversationID: String, locateMessage: MessageInfo? = null) {
            context.startActivity(Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
                if (locateMessage != null) {
                    putExtra(EXTRA_LOCATE_MESSAGE, locateMessage)
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        val conversationID = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        @Suppress("DEPRECATION")
        val locateMessage = intent?.getParcelableExtra<MessageInfo>(EXTRA_LOCATE_MESSAGE)
        if (conversationID == null) {
            finish()
            return
        }
        val isChatbotConversation = ChatbotConversationPolicy.isChatbotConversation(conversationID)
        val messageListConfig = ChatMessageListConfig().setCustomMessageRenderer(
            businessID = CustomLinkMessage.BUSINESS_ID,
            renderer = CustomLinkMessageRenderer(),
            summaryProvider = { summaryContext ->
                val payload = summaryContext.message.messagePayload as? CustomMessagePayload
                CustomLinkMessage.from(payload?.customData)?.text
            }
        )
        val sdkAppID = LoginStore.shared.sdkAppID
        val messageInputConfig = ChatMessageInputConfig().customizeActions {
            val androidContext = editorContext.androidContext
            val conversationID = editorContext.conversationID
            add(
                MessageInputMenuAction(
                    ID = DEMO_MESSAGE_INPUT_CUSTOM_LINK_ACTION_ID,
                    title = androidContext.getString(R.string.compose_demo_custom_message_menu_title),
                    iconResID = android.R.drawable.ic_menu_send,
                    onClick = {
                        val customLinkMessage = CustomLinkMessage(
                            businessID = CustomLinkMessage.BUSINESS_ID,
                            text = androidContext.getString(R.string.compose_demo_custom_message_content),
                            link = androidContext.getString(R.string.compose_demo_custom_message_link)
                        )
                        val payload = SendMessagePayload.CustomSendMessagePayload(
                            customData = Gson().toJson(customLinkMessage),
                            description = customLinkMessage.text
                        )
                        MessageInputStore.create(conversationID).sendMessage(
                            payload = payload,
                            option = SendMessageOption(
                                needReadReceipt = AppBuilderConfig.enableReadReceipt
                            ),
                            completion = object : CompletionHandler {
                                override fun onSuccess() {}
                                override fun onFailure(code: Int, desc: String) {}
                            }
                        )
                    }
                )
            )
            replace(MessageInputActionIDs.VIDEO_CALL) { action ->
                withCallRedDotIfNeeded(sdkAppID, action)
            }
            replace(MessageInputActionIDs.AUDIO_CALL) { action ->
                withCallRedDotIfNeeded(sdkAppID, action)
            }
        }

        onEvent<Event.GroupDeleted> {
            if ("group_${it.groupID}" == conversationID) {
                finish()
            }
        }
        onEvent<Event.ContactDeleted> {
            if ("c2c_${it.contactID}" == conversationID) {
                finish()
            }
        }
        lifecycleScope.launch {
            GroupStore.shared.groupEventFlow.collectLatest { event ->
                when (event) {
                    is GroupEvent.OnGroupDismissed -> {
                        if ("group_${event.groupID}" == conversationID) {
                            finish()
                        }
                    }
                    else -> Unit
                }
            }
        }

        setContent {
            SetActivitySystemBarAppearance()

            var showAddFriendDialog by remember { mutableStateOf(false) }
            var contactInfo by remember { mutableStateOf<ContactInfo?>(null) }
            var showClearHistoryDialog by remember { mutableStateOf(false) }
            var isPeerTyping by remember { mutableStateOf(false) }
            var isMultiSelectMode by remember { mutableStateOf(false) }
            val chatPageController = rememberChatPageController()
            val messageListController = rememberMessageListController()

            BackHandler(enabled = isMultiSelectMode) {
                if (messageListController.isInMultiSelectMode()) {
                    messageListController.exitMultiSelectMode()
                }
            }

            fun handleChatSettingNavigation(userID: String? = null, groupID: String? = null) {
                if (userID.isNullOrEmpty() == false) {
                    contactStore.getContactInfo(listOf(userID), object : GetContactInfoCompletionHandler {
                        override fun onSuccess(contactInfoList: List<ContactInfo>) {
                            val info = contactInfoList.firstOrNull() ?: return
                            if (info.isFriend) {
                                ChatSettingActivity.start(context = this@ChatActivity, userID = userID)
                            } else {
                                showAddFriendDialog = true
                                contactInfo = info
                            }
                        }

                        override fun onFailure(code: Int, desc: String) {
                        }
                    })
                } else if (groupID.isNullOrEmpty() == false) {
                    ChatSettingActivity.start(context = this@ChatActivity, groupID = groupID)
                }
            }

            ChatPage(
                conversationID = conversationID,
                locateMessage = locateMessage,
                messageListConfig = messageListConfig,
                messageInputConfig = messageInputConfig,
                onUserClick = {
                    handleChatSettingNavigation(it)
                },
                onChatHeaderClick = {
                    val userID = getUserID(conversationID)
                    val groupID = getGroupID(conversationID)
                    handleChatSettingNavigation(userID, groupID)
                },
                onBackClick = { finish() },
                onTypingStatusChanged = { isPeerTyping = it },
                onMultiSelectStateChanged = { isMultiSelectMode = it },
                messageListController = messageListController,
                chatPageController = chatPageController,
                header = { conversation, isMultiSelectMode ->
                    DemoChatHeader(
                        conversation = conversation,
                        conversationID = conversationID,
                        isChatbotConversation = isChatbotConversation,
                        showSecurityBar = !isChatbotConversation,
                        isMultiSelectMode = isMultiSelectMode,
                        isPeerTyping = isPeerTyping,
                        onBackClick = { finish() },
                        onMoreClick = {
                            if (isChatbotConversation) {
                                showClearHistoryDialog = true
                            } else {
                                handleChatSettingNavigation(
                                    userID = getUserID(conversationID),
                                    groupID = getGroupID(conversationID)
                                )
                            }
                        }
                    )
                }
            )

            if (showAddFriendDialog && contactInfo != null) {
                AddContactAndGroupBottomSheet(
                    addType = AddType.CONTACT,
                    initialContactInfo = contactInfo,
                    onDismiss = { showAddFriendDialog = false }
                )
            }

            AlertDialog(
                isVisible = showClearHistoryDialog,
                message = stringResource(R.string.compose_demo_chatbot_clear_history_confirmation),
                cancelText = stringResource(R.string.compose_demo_about_cancel),
                confirmText = stringResource(R.string.compose_demo_clear),
                isConfirmDestructive = true,
                onDismiss = { showClearHistoryDialog = false },
                onCancel = { showClearHistoryDialog = false },
                onConfirm = {
                    showClearHistoryDialog = false
                    chatPageController.clearChatHistory(
                        object : CompletionHandler {
                            override fun onSuccess() = Unit

                            override fun onFailure(code: Int, desc: String) {
                                Toast.error(
                                    this@ChatActivity,
                                    desc.ifBlank {
                                        getString(R.string.compose_demo_chatbot_clear_history_failed)
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun withCallRedDotIfNeeded(
        sdkAppID: Int,
        action: MessageInputMenuAction
    ): MessageInputMenuAction {
        if (!MessageInputCallRedDotStore.shouldShow(sdkAppID, action.ID)) {
            return action
        }
        return action.copy(
            showRedDot = true,
            onClick = {
                MessageInputCallRedDotStore.markClicked(sdkAppID, action.ID)
                action.onClick()
            }
        )
    }

}

@Composable
private fun DemoChatHeader(
    conversation: ConversationInfo?,
    conversationID: String,
    isChatbotConversation: Boolean,
    showSecurityBar: Boolean,
    isMultiSelectMode: Boolean,
    isPeerTyping: Boolean,
    onBackClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val conversationListStore = remember { ConversationListStore.create() }
    val totalUnreadCount by conversationListStore.state.totalUnreadCount.collectAsState()
    var isSecurityBarVisible by remember(conversationID) { mutableStateOf(true) }

    val groupID = remember(conversationID) { getGroupID(conversationID) }
    val joinedGroupList by GroupStore.shared.state.joinedGroupList.collectAsState()
    val joinedGroupName = remember(joinedGroupList, groupID) {
        groupID?.let { id ->
            joinedGroupList.firstOrNull { it.groupID == id }?.groupName?.takeIf { it.isNotBlank() }
        }
    }
    var resolvedGroupTitle by remember(conversationID) {
        mutableStateOf(
            joinedGroupName
                ?: conversation?.title?.takeIf { it.isNotBlank() && it != groupID && it != conversationID }
                ?: ""
        )
    }
    DisposableEffect(groupID) {
        var active = true
        if (groupID != null) {
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
    LaunchedEffect(conversation?.title, groupID) {
        if (groupID != null) {
            val validTitle = conversation?.title
                ?.takeIf { it.isNotBlank() && it != groupID && it != conversationID }
            if (validTitle != null) {
                resolvedGroupTitle = validTitle
            }
        }
    }

    val defaultTitle = if (groupID != null) {
        resolvedGroupTitle
    } else {
        conversation?.title ?: conversationID
    }
    val title = if (isPeerTyping) {
        stringResource(R.string.compose_demo_chat_typing_indicator)
    } else {
        defaultTitle
    }

    Column(
        modifier = Modifier
            .background(color = colors.bgColorOperate)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
        ) {
            if (isMultiSelectMode) {
                val messageListController = LocalMessageListController.current
                Text(
                    text = stringResource(R.string.compose_demo_chat_header_cancel),
                    fontSize = 16.sp,
                    color = colors.textColorLink,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { messageListController?.exitMultiSelectMode() }
                )
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onBackClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(UiKitR.drawable.uikit_ic_back),
                        contentDescription = stringResource(R.string.compose_demo_chat_header_back),
                        tint = colors.textColorSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    if (totalUnreadCount > 0L) {
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(22.dp)
                                .background(color = colors.buttonColorOff, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (totalUnreadCount > 99L) "99+" else totalUnreadCount.toString(),
                                fontSize = 12.sp,
                                color = colors.textColorButton,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (!isMultiSelectMode) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textColorPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 72.dp)
                )

                Icon(
                    painter = painterResource(
                        if (isChatbotConversation) {
                            R.drawable.demo_ic_clear_chat_history
                        } else {
                            R.drawable.demo_ic_more
                        }
                    ),
                    contentDescription = stringResource(
                        if (isChatbotConversation) {
                            R.string.compose_demo_chatbot_clear_history
                        } else {
                            R.string.compose_demo_chat_header_more
                        }
                    ),
                    tint = colors.textColorSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(24.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onMoreClick() }
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.strokeColorSecondary)

        if (showSecurityBar && isSecurityBarVisible && !isMultiSelectMode) {
            SecurityWarningBar(
                onClose = { isSecurityBarVisible = false },
                onReport = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.REPORT_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        Toast.error(context, context.getString(R.string.compose_demo_open_browser_failed))
                    }
                }
            )
        }
    }
}

@Composable
private fun SecurityWarningBar(
    onClose: () -> Unit,
    onReport: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val warningText = stringResource(R.string.compose_demo_chat_security_warning)
    val reportText = stringResource(R.string.compose_demo_chat_security_warning_click_to_report)
    val linkColor = colors.textColorLink
    val annotatedText = remember(warningText, reportText, linkColor) {
        buildAnnotatedString {
            append(warningText)
            append(" ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "report",
                    styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                    linkInteractionListener = { onReport() }
                )
            ) {
                append(reportText)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.toastColorWarning)
            .padding(start = 20.dp, top = 16.dp, end = 17.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(R.drawable.demo_ic_security_warning),
            contentDescription = null,
            tint = colors.textColorWarning,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Text(
            text = annotatedText,
            fontSize = 14.sp,
            color = colors.textColorWarning,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 7.dp)
        )
        Icon(
            painter = painterResource(R.drawable.demo_ic_security_close),
            contentDescription = stringResource(R.string.compose_demo_close),
            tint = colors.textColorWarning,
            modifier = Modifier
                .size(22.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClose() }
        )
    }
}

fun getUserID(conversationID: String): String? {
    val c2cConversationIDPrefix = "c2c_"
    return if (conversationID.startsWith(c2cConversationIDPrefix)) {
        conversationID.replaceFirst(c2cConversationIDPrefix, "")
    } else {
        null
    }
}

fun getGroupID(conversationID: String): String? {
    val groupConversationIDPrefix = "group_"
    return if (conversationID.startsWith(groupConversationIDPrefix)) {
        conversationID.replaceFirst(groupConversationIDPrefix, "")
    } else {
        null
    }
}
