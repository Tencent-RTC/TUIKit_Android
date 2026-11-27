package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addnewchat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddNewChatViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddNewChatViewModelFactory
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ChatType
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupFlowStep
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPicker
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerData
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

private const val GROUP_MEMBER_MAX_COUNT = 100

@Composable
fun AddNewChatBottomSheet(
    modifier: Modifier = Modifier,
    chatType: ChatType = ChatType.SINGLE,
    onDismiss: () -> Unit,
    onCreateChat: (String) -> Unit = {},
    viewModelFactory: AddNewChatViewModelFactory = AddNewChatViewModelFactory()
) {
    val viewModel: AddNewChatViewModel = viewModel(
        factory = viewModelFactory
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = LocalTheme.current.colors

    LaunchedEffect(Unit) {
        viewModel.clearState()
    }

    LaunchedEffect(chatType) {
        viewModel.setChatType(chatType)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.createdConversationId) {
        uiState.createdConversationId?.let { conversationId ->
            onCreateChat(conversationId)
            onDismiss()
            viewModel.consumeCreatedConversationId()
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    fun handleBack() {
        when (uiState.groupFlowStep) {
            GroupFlowStep.CONTACT_SELECTION -> onDismiss()
            GroupFlowStep.GROUP_SETTINGS -> viewModel.clearGroupSettingsScreen()
            GroupFlowStep.GROUP_TYPE_SELECTION -> viewModel.clearGroupTypeSelectionScreen()
        }
    }

    FullScreenDialog(
        onDismissRequest = { handleBack() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgColorOperate)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Crossfade(targetState = uiState.groupFlowStep) { step ->
                when (step) {
                    GroupFlowStep.CONTACT_SELECTION -> {
                        BackHandler { handleBack() }
                        ContactSelectionStep(
                            viewModel = viewModel,
                            chatType = uiState.chatType,
                            selectedContacts = uiState.selectedContacts,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onBack = onDismiss,
                            onCreateChat = onCreateChat,
                            onDismiss = onDismiss
                        )
                    }

                    GroupFlowStep.GROUP_SETTINGS -> {
                        BackHandler { handleBack() }
                        GroupSettingsBottomSheet(
                            viewModel = viewModel,
                            onBack = { viewModel.clearGroupSettingsScreen() },
                            onShowGroupTypeSelection = {
                                viewModel.showGroupTypeSelectionScreen()
                            }
                        )
                    }

                    GroupFlowStep.GROUP_TYPE_SELECTION -> {
                        BackHandler { handleBack() }
                        GroupTypeSelectionBottomSheet(
                            viewModel = viewModel,
                            onBack = { viewModel.clearGroupTypeSelectionScreen() },
                            onTypeSelected = { groupTypeOption ->
                                viewModel.updateSelectedGroupType(groupTypeOption)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSelectionStep(
    viewModel: AddNewChatViewModel,
    chatType: ChatType,
    selectedContacts: List<ContactInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onCreateChat: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val contactDataSource by viewModel.contactDataSource.collectAsState()
    val colors = LocalTheme.current.colors

    val filteredDataSource = remember(contactDataSource, searchQuery) {
        val keyword = searchQuery.trim()
        if (keyword.isEmpty()) {
            contactDataSource
        } else {
            contactDataSource.filter { item ->
                item.extraData.matchesSearchQuery(keyword)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DialogNavBar(
            mode = DialogNavBarMode.BackTitle,
            title = stringResource(
                if (chatType == ChatType.GROUP) {
                    R.string.contact_list_create_group
                } else {
                    R.string.contact_list_create_c2c
                }
            ),
            onLeadingClick = onBack
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorSecondary
        )

        SearchBar(
            onQueryChanged = onSearchQueryChange,
            inputHeight = 36.dp,
            debounceMs = 300L,
            paddingVertical = 16.dp,
            expandTouchTargets = false,
            query = searchQuery
        )

        Box(modifier = Modifier.weight(1f)) {
            UserPicker(
                dataSource = filteredDataSource,
                defaultSelectedItems = selectedContacts.map { it.userID },
                maxCount = if (chatType == ChatType.SINGLE) 1 else GROUP_MEMBER_MAX_COUNT,
                showCheckbox = chatType != ChatType.SINGLE,
                onMaxCountExceed = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.contact_list_max_select_count, GROUP_MEMBER_MAX_COUNT),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onSelectedChanged = { visibleSelectedItems ->
                    if (chatType == ChatType.SINGLE) {
                        if (visibleSelectedItems.isNotEmpty()) {
                            onCreateChat(ConversationIDUtil.fromUser(visibleSelectedItems.first().key))
                            onDismiss()
                        }
                    } else {
                        updateSelectedContactsFromVisibleItems(
                            viewModel = viewModel,
                            visibleItems = filteredDataSource,
                            visibleSelectedItems = visibleSelectedItems
                        )
                    }
                }
            )
        }

        if (chatType == ChatType.GROUP) {
            SelectedContactsBottomBar(
                selectedContacts = selectedContacts,
                onConfirmClick = {
                    if (selectedContacts.isNotEmpty()) {
                        viewModel.startChat()
                    }
                }
            )
        }
    }
}

private fun updateSelectedContactsFromVisibleItems(
    viewModel: AddNewChatViewModel,
    visibleItems: List<UserPickerData<ContactInfo>>,
    visibleSelectedItems: List<UserPickerData<ContactInfo>>
) {
    val mergedSelectedContacts = ContactSelectionStateMerger.merge(
        currentSelected = viewModel.uiState.value.selectedContacts,
        visibleItems = visibleItems,
        visibleSelectedItems = visibleSelectedItems,
        selectedKeySelector = { it.userID },
        visibleKeySelector = { it.key },
        visibleToSelectedMapper = { it.extraData }
    )

    viewModel.setSelectedContacts(
        mergedSelectedContacts.map { contact ->
            UserPickerData(
                key = contact.userID,
                label = contact.displayName,
                avatarUrl = contact.avatarURL,
                extraData = contact
            )
        }
    )
}

@Composable
internal fun AddNewChatHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors

    Column(modifier = modifier.fillMaxWidth()) {
        DialogNavBar(
            mode = DialogNavBarMode.BackTitle,
            title = title,
            onLeadingClick = onBack
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorSecondary
        )
    }
}
