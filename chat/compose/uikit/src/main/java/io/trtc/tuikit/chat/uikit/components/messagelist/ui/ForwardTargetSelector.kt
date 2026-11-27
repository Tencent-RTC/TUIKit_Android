package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel.ConversationListViewModel
import io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel.ConversationListViewModelFactory
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.MessageCheckBox
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarContent
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog

@Composable
fun ForwardTargetSelector(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onConfirm: (List<String>) -> Unit = {},
) {
    val colors = LocalTheme.current.colors
    val viewModelFactory = ConversationListViewModelFactory(ConversationListStore.create())
    val viewModel: ConversationListViewModel = viewModel(
        key = "ForwardTargetSelector",
        factory = viewModelFactory
    )
    val conversationList by viewModel.conversationList.collectAsState()
    val selectedConversations by viewModel.selectedConversations.collectAsState()
    val friendList by ContactStore.shared.state.friendList.collectAsState()
    val selectedContacts = remember { mutableStateListOf<ContactInfo>() }
    val listState = rememberLazyListState()
    var showContactPicker by remember { mutableStateOf(false) }
    var contactPickerPreSelectedKeys by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.clearSelection()
        ContactStore.shared.loadFriends(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    // 退出转发选择页时清空选中态：该 ViewModel 挂在宿主 Activity 上会被复用，
    // 若不在离开时清空，下次进入会残留上一次勾选的会话。
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearSelection() }
    }

    LaunchedEffect(conversationList, listState) {
        snapshotFlow {
            conversationList to listState.layoutInfo.visibleItemsInfo
        }.collect { (list, visibleItems) ->
            val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1
            if (lastVisibleIndex >= 0 && lastVisibleIndex >= list.size - 3) {
                viewModel.loadMoreConversation()
            }
        }
    }

    FullScreenDialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgColorOperate)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            DialogNavBar(
                mode = DialogNavBarMode.CancelTitleConfirm,
                title = stringResource(R.string.message_list_select_conversation),
                onLeadingClick = onDismiss,
                showConfirm = false,
                horizontalPadding = 16.dp
            )

            ContactEntryRow(
                onClick = {
                    if (friendList.isNotEmpty()) {
                        contactPickerPreSelectedKeys = collectPreSelectedUserIds(
                            selectedContacts = selectedContacts,
                            selectedConversations = viewModel.selectedConversations.value
                        )
                        showContactPicker = true
                    }
                }
            )

            RecentConversationsLabel()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState
            ) {
                items(conversationList, key = { it.conversationID }) { conversation ->
                    // 选中态必须在 item 内读取可观察状态（selectedConversations / selectedContacts）：
                    // LazyColumn 的 item 不会因父级重组而重组，读取普通 Map 会一直停留在首帧状态。
                    ConversationSelectorItem(
                        conversation = conversation,
                        isSelected = isConversationSelected(
                            conversation = conversation,
                            selectedConversations = selectedConversations,
                            selectedContacts = selectedContacts
                        ),
                        onToggle = {
                            toggleConversation(
                                conversation = conversation,
                                viewModel = viewModel,
                                selectedContacts = selectedContacts
                            )
                        }
                    )
                }
            }

            val footerItems = buildSelectedAvatarChips(
                selectedConversations = selectedConversations.toList(),
                selectedContacts = selectedContacts
            )
            if (footerItems.isNotEmpty()) {
                SelectedTargetsFooter(
                    selectedItems = footerItems,
                    onConfirm = {
                        onConfirm(
                            selectedConversationIDs(
                                selectedConversations = viewModel.selectedConversations.value,
                                selectedContacts = selectedContacts
                            )
                        )
                    }
                )
            }
        }
    }

    ForwardContactPickerDialog(
        visible = showContactPicker,
        contacts = friendList,
        preSelectedUserIds = contactPickerPreSelectedKeys,
        onDismiss = { showContactPicker = false },
        onConfirm = { returnedContacts ->
            applyContactPickerResult(
                returnedContacts = returnedContacts,
                preSelectedUserIds = contactPickerPreSelectedKeys,
                viewModel = viewModel,
                selectedContacts = selectedContacts
            )
        }
    )
}

@Composable
private fun ContactEntryRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.bgColorOperate)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.message_list_select_from_contacts),
            fontSize = 16.sp,
            color = colors.textColorPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Image(
            painter = painterResource(R.drawable.search_ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(colors.textColorSecondary)
        )
    }
}

@Composable
private fun RecentConversationsLabel(
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    Text(
        text = stringResource(R.string.message_list_recent_conversations),
        fontSize = 12.sp,
        color = colors.textColorSecondary,
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgColorInput)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ConversationSelectorItem(
    conversation: ConversationInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    val title = conversation.title?.takeIf { it.isNotBlank() } ?: conversation.conversationID

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onToggle() }
            .background(color = colors.bgColorOperate)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MessageCheckBox(
            checked = isSelected,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Avatar(
            content = AvatarContent.Image(
                url = conversation.avatarURL,
                fallbackName = title
            ),
            size = AvatarSize.M,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            fontSize = 16.sp,
            color = colors.textColorPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SelectedTargetsFooter(
    selectedItems: List<AvatarChipInfo>,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val enabled = selectedItems.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            selectedItems.forEach { item ->
                Avatar(
                    content = AvatarContent.Image(
                        url = item.avatarUrl,
                        fallbackName = item.fallback
                    ),
                    size = AvatarSize.S
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(R.string.message_list_forward_button_text, selectedItems.size),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) colors.textColorButton else colors.textColorButtonDisabled,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (enabled) colors.buttonColorPrimaryDefault else colors.buttonColorPrimaryDisabled
                )
                .clickable(enabled = enabled, onClick = onConfirm)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

private data class AvatarChipInfo(
    val key: String,
    val avatarUrl: String,
    val fallback: String
)

private fun buildSelectedAvatarChips(
    selectedConversations: List<ConversationInfo>,
    selectedContacts: List<ContactInfo>
): List<AvatarChipInfo> {
    val conversationItems = selectedConversations.map { conversation ->
        AvatarChipInfo(
            key = "conv_${conversation.conversationID}",
            avatarUrl = conversation.avatarURL.orEmpty(),
            fallback = conversation.title?.takeIf { it.isNotBlank() } ?: conversation.conversationID
        )
    }
    val contactItems = selectedContacts.map { contact ->
        AvatarChipInfo(
            key = "contact_${contact.userID}",
            avatarUrl = contact.avatarURL.orEmpty(),
            fallback = contact.displayName
        )
    }
    return conversationItems + contactItems
}

private fun collectPreSelectedUserIds(
    selectedContacts: List<ContactInfo>,
    selectedConversations: Collection<ConversationInfo>
): List<String> {
    val ids = LinkedHashSet<String>()
    selectedContacts.forEach { ids.add(it.userID) }
    selectedConversations.forEach { conversation ->
        ConversationIDUtil.userIdOrNull(conversation.conversationID)?.let { ids.add(it) }
    }
    return ids.toList()
}

private fun applyContactPickerResult(
    returnedContacts: List<ContactInfo>,
    preSelectedUserIds: List<String>,
    viewModel: ConversationListViewModel,
    selectedContacts: MutableList<ContactInfo>
) {
    val returnedUserIds = returnedContacts.map { it.userID }.toSet()
    val deselectedUserIds = preSelectedUserIds.filter { it !in returnedUserIds }
    deselectedUserIds.forEach { userId ->
        val c2cId = ConversationIDUtil.fromUser(userId)
        viewModel.selectedConversations.value
            .firstOrNull { it.conversationID == c2cId }
            ?.let { viewModel.removeSelection(it) }
    }

    selectedContacts.clear()
    returnedContacts.forEach { contact ->
        val c2cId = ConversationIDUtil.fromUser(contact.userID)
        val existsAsConv = viewModel.selectedConversations.value
            .any { it.conversationID == c2cId }
        if (!existsAsConv) {
            selectedContacts.add(contact)
        }
    }
}

private fun isConversationSelected(
    conversation: ConversationInfo,
    selectedConversations: Collection<ConversationInfo>,
    selectedContacts: List<ContactInfo>
): Boolean {
    if (selectedConversations.any { it.conversationID == conversation.conversationID }) {
        return true
    }
    ConversationIDUtil.userIdOrNull(conversation.conversationID)?.let { userId ->
        return selectedContacts.any { it.userID == userId }
    }
    return false
}

private fun toggleConversation(
    conversation: ConversationInfo,
    viewModel: ConversationListViewModel,
    selectedContacts: MutableList<ContactInfo>
) {
    if (viewModel.isSelected(conversation)) {
        viewModel.removeSelection(conversation)
        return
    }
    ConversationIDUtil.userIdOrNull(conversation.conversationID)?.let { userId ->
        val matchingContact = selectedContacts.firstOrNull { it.userID == userId }
        if (matchingContact != null) {
            selectedContacts.remove(matchingContact)
            return
        }
    }
    viewModel.addSelection(conversation)
}

private fun selectedConversationIDs(
    selectedConversations: Collection<ConversationInfo>,
    selectedContacts: List<ContactInfo>
): List<String> {
    val conversationIds = selectedConversations.map { it.conversationID }
    val contactIds = selectedContacts.map { ConversationIDUtil.fromUser(it.userID) }
    return (conversationIds + contactIds).distinct()
}
