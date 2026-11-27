package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddContactAndGroupUiState
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddContactAndGroupViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddContactAndGroupViewModelFactory
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import java.util.UUID

enum class FlowStep {
    SEARCH,
    CONTACT_DETAIL,
    ADD_FRIEND_FORM,
    GROUP_JOIN_FORM
}

@Composable
fun AddContactAndGroupBottomSheet(
    addType: AddType,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialContactInfo: ContactInfo? = null,
    viewModelFactory: AddContactAndGroupViewModelFactory = AddContactAndGroupViewModelFactory()
) {
    val viewModelKey = remember { "AddContactAndGroup_${UUID.randomUUID()}" }
    val viewModel: AddContactAndGroupViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = LocalTheme.current.colors

    var currentStep by remember {
        mutableStateOf(if (initialContactInfo != null) FlowStep.CONTACT_DETAIL else FlowStep.SEARCH)
    }
    var selectedResult by remember { mutableStateOf<ContactInfo?>(initialContactInfo) }
    var addFriendWordingDraft by remember { mutableStateOf<String?>(null) }
    var addFriendRemarkDraft by remember { mutableStateOf<String?>(null) }
    var groupJoinMessageDraft by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (initialContactInfo == null) {
            viewModel.clearSearchResults()
        }
    }

    fun clearFormDrafts() {
        addFriendWordingDraft = null
        addFriendRemarkDraft = null
        groupJoinMessageDraft = null
    }

    fun handleBack() {
        when (currentStep) {
            FlowStep.SEARCH -> {
                viewModel.clearSearchResults()
                onDismiss()
            }
            FlowStep.CONTACT_DETAIL -> {
                if (initialContactInfo != null) onDismiss() else currentStep = FlowStep.SEARCH
            }
            FlowStep.ADD_FRIEND_FORM -> currentStep = FlowStep.CONTACT_DETAIL
            FlowStep.GROUP_JOIN_FORM -> {
                if (initialContactInfo != null) onDismiss() else currentStep = FlowStep.SEARCH
            }
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
        val dialogView = LocalView.current

        LaunchedEffect(uiState.requestResult) {
            uiState.requestResult?.let { result ->
                viewModel.clearRequestResult()
                if (result.isSuccess) {
                    onDismiss()
                    Toast.success(context, result.message)
                } else {
                    Toast.error(dialogView, result.message)
                }
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgColorOperate)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            FlowHeader(
                currentStep = currentStep,
                addType = addType,
                onBack = { handleBack() }
            )

            when (currentStep) {
                FlowStep.SEARCH -> {
                    SearchInterface(
                        addType = addType,
                        uiState = uiState,
                        viewModel = viewModel,
                        onResultClick = { result ->
                            val alreadyExists = if (addType == AddType.CONTACT) {
                                result.isFriend
                            } else {
                                uiState.isJoinGroupAlready
                            }
                            if (alreadyExists) return@SearchInterface
                            selectedResult = result
                            clearFormDrafts()
                            currentStep = if (addType == AddType.CONTACT) {
                                FlowStep.CONTACT_DETAIL
                            } else {
                                FlowStep.GROUP_JOIN_FORM
                            }
                        }
                    )
                }

                FlowStep.CONTACT_DETAIL -> {
                    selectedResult?.let { result ->
                        ContactDetail(
                            result = result,
                            addType = addType,
                            onAddContact = { currentStep = FlowStep.ADD_FRIEND_FORM }
                        )
                    }
                }

                FlowStep.ADD_FRIEND_FORM -> {
                    selectedResult?.let { result ->
                        AddFriendForm(
                            contactInfo = result,
                            wording = addFriendWordingDraft ?: defaultSelfWording(),
                            remark = addFriendRemarkDraft ?: result.displayName,
                            isAdding = uiState.isAddingContact,
                            onWordingChange = { addFriendWordingDraft = it },
                            onRemarkChange = { addFriendRemarkDraft = it },
                            onSend = { wording, remark ->
                                viewModel.addFriend(result, wording, remark, context::getString)
                            }
                        )
                    }
                }

                FlowStep.GROUP_JOIN_FORM -> {
                    selectedResult?.let { result ->
                        GroupJoinForm(
                            result = result,
                            message = groupJoinMessageDraft ?: defaultSelfWording(),
                            isJoining = uiState.isAddingContact,
                            onMessageChange = { groupJoinMessageDraft = it },
                            onSend = { message ->
                                viewModel.joinGroup(result, message)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowHeader(
    currentStep: FlowStep,
    addType: AddType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    val titleRes = when (currentStep) {
        FlowStep.SEARCH -> if (addType == AddType.CONTACT) {
            R.string.contact_list_add_contact
        } else {
            R.string.contact_list_join_group
        }
        FlowStep.CONTACT_DETAIL -> if (addType == AddType.CONTACT) {
            R.string.contact_list_add_contact
        } else {
            R.string.contact_list_contact_info
        }
        FlowStep.ADD_FRIEND_FORM -> R.string.contact_list_add_contact
        FlowStep.GROUP_JOIN_FORM -> R.string.contact_list_group_info
    }

    Column(modifier = modifier.fillMaxWidth()) {
        DialogNavBar(
            mode = DialogNavBarMode.BackTitle,
            title = stringResource(titleRes),
            onLeadingClick = onBack
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorSecondary
        )
    }
}

@Composable
private fun SearchInterface(
    addType: AddType,
    uiState: AddContactAndGroupUiState,
    viewModel: AddContactAndGroupViewModel,
    onResultClick: (ContactInfo) -> Unit
) {
    val colors = LocalTheme.current.colors

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            onQueryChanged = viewModel::updateSearchKeyword,
            query = uiState.searchKeyword,
            hint = stringResource(
                if (addType == AddType.CONTACT) {
                    R.string.contact_list_user_id
                } else {
                    R.string.contact_list_group_id
                }
            ),
            inputHeight = 36.dp,
            debounceMs = 0L,
            paddingVertical = 12.dp,
            expandTouchTargets = false,
            onSearch = {
                if (addType == AddType.CONTACT) {
                    viewModel.searchContact()
                } else {
                    viewModel.searchGroup()
                }
            }
        )

        if (addType == AddType.CONTACT && uiState.currentUserId.isNotEmpty() && uiState.searchKeyword.isEmpty()) {
            Text(
                text = stringResource(
                    R.string.contact_list_label_value_format,
                    stringResource(R.string.contact_list_my_user_id),
                    uiState.currentUserId
                ),
                color = colors.textColorSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.W400,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 80.dp, end = 16.dp)
            )
        }

        val info = if (addType == AddType.CONTACT) uiState.addFriendInfo else uiState.joinGroupInfo
        if (info != null) {
            SearchResult(
                result = info,
                addType = addType,
                isJoinGroupAlready = uiState.isJoinGroupAlready,
                onClick = onResultClick
            )
        } else if (!uiState.isSearching && uiState.searchKeyword.isNotEmpty()) {
            Text(
                text = stringResource(R.string.contact_list_no_information),
                color = colors.textColorSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.W400,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            )
        }
    }
}

@Composable
private fun SearchResult(
    result: ContactInfo,
    addType: AddType,
    isJoinGroupAlready: Boolean,
    onClick: (ContactInfo) -> Unit
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(result) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            modifier = Modifier.size(48.dp),
            url = result.avatarURL,
            name = result.displayName,
            size = AvatarSize.M
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.displayName,
                color = colors.textColorPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                fontWeight = FontWeight.W400
            )
            val idLabel = if (addType == AddType.GROUP) {
                stringResource(R.string.contact_list_group_id)
            } else {
                stringResource(R.string.contact_list_user_id)
            }
            val fullText = stringResource(R.string.contact_list_label_value_format, idLabel, result.userID)
            val valueStart = fullText.indexOf(result.userID)
            val idText = buildAnnotatedString {
                append(fullText)
                if (valueStart >= 0) {
                    addStyle(
                        style = SpanStyle(color = colors.textColorLink),
                        start = valueStart,
                        end = valueStart + result.userID.length
                    )
                }
            }
            Text(
                text = idText,
                color = colors.textColorSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                fontWeight = FontWeight.W400
            )
        }

        val alreadyTips = when (addType) {
            AddType.CONTACT -> if (result.isFriend) {
                stringResource(R.string.contact_list_already_is_friend)
            } else {
                null
            }
            AddType.GROUP -> if (isJoinGroupAlready) {
                stringResource(R.string.contact_list_already_in_group)
            } else {
                null
            }
        }
        alreadyTips?.let {
            Text(
                text = it,
                color = colors.textColorSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.W400
            )
        }
    }
}
