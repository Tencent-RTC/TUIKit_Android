package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarContent
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.GroupChatSettingConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.GroupChatSettingConfigProtocol
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingItemIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingSectionIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.GroupChatSettingItemContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.buildChatSettingItems
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupMemberActionPolicy
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermission
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermissionManager
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModelFactory
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.getGroupAvatarUrls
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupType

val LocalGroupViewModel =
    compositionLocalOf<GroupChatSettingViewModel> { error("No LocalGroupViewModel") }

val GroupMember.displayName
    get() = when {
        !nameCard.isNullOrEmpty() -> nameCard!!
        !friendRemark.isNullOrEmpty() -> friendRemark!!
        !nickname.isNullOrEmpty() -> nickname!!
        else -> userID
    }

@Composable
fun GroupChatSetting(
    groupID: String,
    modifier: Modifier = Modifier,
    onGroupMemberClick: (GroupMember) -> Unit = {},
    onGroupDeleted: () -> Unit = {},
    config: GroupChatSettingConfigProtocol = GroupChatSettingConfig(),
    groupChatSettingViewModelFactory: GroupChatSettingViewModelFactory = GroupChatSettingViewModelFactory(
        groupID
    )
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val viewModel = viewModel(
        GroupChatSettingViewModel::class,
        key = groupID,
        factory = groupChatSettingViewModelFactory
    )

    val groupType by viewModel.groupType.collectAsState()
    val groupName by viewModel.groupName.collectAsState()
    val selfRole by viewModel.currentUserRole.collectAsState()
    val joinOption by viewModel.joinGroupApprovalType.collectAsState()
    val inviteOption by viewModel.inviteToGroupApprovalType.collectAsState()
    val isNotDisturb by viewModel.isNotDisturb.collectAsState()
    val isPinned by viewModel.isPinned.collectAsState()
    val memberCount by viewModel.memberCount.collectAsState()
    val memberList by viewModel.memberList.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val selfNameCard by viewModel.selfNameCard.collectAsState()
    val chatBackgroundImageUri by viewModel.chatBackgroundImageUri.collectAsState()

    var showMemberList by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var showNoticeDialog by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var showJoinMethodSheet by remember { mutableStateOf(false) }
    var showInviteMethodSheet by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showManagementDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showQuitDialog by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }

    var addMemberCandidates by remember { mutableStateOf<List<ContactInfo>>(emptyList()) }
    var showAddMemberPicker by remember { mutableStateOf(false) }
    var removeMemberCandidates by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var showRemoveMemberPicker by remember { mutableStateOf(false) }
    var transferCandidates by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var showTransferPicker by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf<GroupMember?>(null) }
    var showTransferConfirm by remember { mutableStateOf(false) }

    val canEditGroupName = GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_NAME)
    val canEditGroupAvatar =
        GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_AVATAR)
    val canEditGroupNotice =
        GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_NOTICE)
    val canOpenGroupManagement =
        GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_MANAGEMENT)
    val canEditJoinOption =
        GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.SET_JOIN_GROUP_APPROVAL_TYPE)
    val canEditInviteOption = GroupPermissionManager.canPerformAction(
        groupType, selfRole, GroupPermission.SET_INVITE_TO_GROUP_APPROVAL_TYPE
    )
    val canEditSelfNameCard =
        GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_REMARK)
    val canToggleDoNotDisturb =
        GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.SET_DO_NOT_DISTURB)
    val canTogglePinned = GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.PIN_GROUP)
    val canAddMember = GroupPermissionManager.canPerformAction(
        groupType, selfRole, GroupPermission.ADD_GROUP_MEMBER
    ) && inviteOption != GroupInviteOption.FORBID
    val canRemoveMember =
        GroupPermissionManager.canPerformAction(groupType, selfRole, GroupPermission.REMOVE_GROUP_MEMBER)

    val itemAvailability = mapOf(
        ChatSettingItemIDs.GROUP_MANAGEMENT to canOpenGroupManagement,
        ChatSettingItemIDs.GROUP_DO_NOT_DISTURB to canToggleDoNotDisturb,
        ChatSettingItemIDs.GROUP_PIN to canTogglePinned,
        ChatSettingItemIDs.GROUP_TRANSFER_OWNER to GroupPermissionManager.canPerformAction(
            groupType, selfRole, GroupPermission.TRANSFER_OWNER
        ),
        ChatSettingItemIDs.GROUP_CLEAR_HISTORY to GroupPermissionManager.canPerformAction(
            groupType, selfRole, GroupPermission.CLEAR_HISTORY_MESSAGES
        ),
        ChatSettingItemIDs.GROUP_DELETE_AND_QUIT to GroupPermissionManager.canPerformAction(
            groupType, selfRole, GroupPermission.DELETE_AND_QUIT
        ),
        ChatSettingItemIDs.GROUP_DISMISS to GroupPermissionManager.canPerformAction(
            groupType, selfRole, GroupPermission.DISMISS_GROUP
        )
    )

    CompositionLocalProvider(LocalGroupViewModel provides viewModel) {
        val itemContext = remember(groupID) {
            GroupChatSettingItemContext(androidContext = context, groupID = groupID)
        }
        val items = buildChatSettingItems(
            itemContext = itemContext,
            defaults = buildList {
                    if (config.isShowHeader) {
                        add(
                            ChatSettingCustomItem<GroupChatSettingItemContext>(ChatSettingItemIDs.GROUP_HEADER) {
                                GroupHeaderSection(
                                    viewModel = viewModel,
                                    canEditGroupName = canEditGroupName,
                                    canEditGroupAvatar = canEditGroupAvatar,
                                    onAvatarClick = { showAvatarPicker = true },
                                    onGroupNameClick = { showGroupNameDialog = true },
                                    onGroupIdClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as? android.content.ClipboardManager
                                        clipboard?.setPrimaryClip(
                                            ClipData.newPlainText(viewModel.groupID, viewModel.groupID)
                                        )
                                        Toast.simple(context, context.getString(R.string.chat_setting_copied))
                                    }
                                )
                            }
                        )
                    }
                    if (config.isShowMemberPreview) {
                        add(
                            ChatSettingCustomItem<GroupChatSettingItemContext>(
                                ChatSettingItemIDs.GROUP_MEMBER_PREVIEW
                            ) {
                                GroupMemberPreviewSection(
                                    members = memberList,
                                    memberCount = memberCount,
                                    showAddButton = canAddMember,
                                    showRemoveButton = canRemoveMember,
                                    onHeaderClick = { showMemberList = true },
                                    onMemberClick = { member -> onGroupMemberClick(member) },
                                    onAddClick = {
                                        viewModel.loadAllGroupMembers {
                                            val existingIds =
                                                viewModel.memberList.value.map { it.userID }.toSet()
                                            addMemberCandidates = viewModel.friendList.value
                                                .filterNot { existingIds.contains(it.userID) }
                                            showAddMemberPicker = true
                                        }
                                    },
                                    onRemoveClick = {
                                        viewModel.loadAllGroupMembers {
                                            removeMemberCandidates =
                                                GroupMemberActionPolicy.filterRemovableMembers(
                                                    currentUserRole = selfRole,
                                                    members = viewModel.memberList.value,
                                                    roleSelector = GroupMember::role
                                                )
                                            showRemoveMemberPicker = true
                                        }
                                    }
                                )
                            }
                        )
                    }
                    if (config.isShowNotice) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_NOTICE,
                                ChatSettingSectionIDs.GROUP_SETTINGS,
                            ) {
                                SettingRowNavigate(
                                    title = stringResource(R.string.chat_setting_group_notice),
                                    value = notice.ifEmpty { stringResource(R.string.chat_setting_no_group_notice) },
                                    showArrow = false,
                                    customAccessoryResId = if (canEditGroupNotice) {
                                        R.drawable.chat_setting_group_name_edit_icon
                                    } else {
                                        null
                                    },
                                    onClick = if (canEditGroupNotice) {
                                        { showNoticeDialog = true }
                                    } else {
                                        null
                                    }
                                )
                            }
                        )
                    }
                    if (config.isShowManagement) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_MANAGEMENT,
                                ChatSettingSectionIDs.GROUP_SETTINGS,
                            ) {
                                SettingRowNavigate(
                                    title = stringResource(R.string.chat_setting_group_management),
                                    showArrow = canOpenGroupManagement,
                                    onClick = if (canOpenGroupManagement) {
                                        { showManagementDialog = true }
                                    } else {
                                        null
                                    }
                                )
                            }
                        )
                    }
                    if (config.isShowGroupType) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_TYPE,
                                ChatSettingSectionIDs.GROUP_SETTINGS,
                            ) {
                                SettingRowNavigate(
                                    title = stringResource(R.string.chat_setting_group_type),
                                    value = groupTypeText(groupType),
                                    showArrow = false
                                )
                            }
                        )
                    }
                    if (config.isShowJoinMethod) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_JOIN_METHOD,
                                ChatSettingSectionIDs.GROUP_SETTINGS,
                            ) {
                                SettingRowNavigate(
                                    title = stringResource(R.string.chat_setting_join_group_method),
                                    value = joinOptionText(joinOption),
                                    showArrow = canEditJoinOption,
                                    onClick = if (canEditJoinOption) {
                                        { showJoinMethodSheet = true }
                                    } else {
                                        null
                                    }
                                )
                            }
                        )
                    }
                    if (config.isShowInviteMethod) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_INVITE_METHOD,
                                ChatSettingSectionIDs.GROUP_SETTINGS,
                            ) {
                                SettingRowNavigate(
                                    title = stringResource(R.string.chat_setting_group_invited_method),
                                    value = inviteOptionText(inviteOption),
                                    showArrow = canEditInviteOption,
                                    onClick = if (canEditInviteOption) {
                                        { showInviteMethodSheet = true }
                                    } else {
                                        null
                                    }
                                )
                            }
                        )
                    }
                    if (config.isShowAlias) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_ALIAS,
                                ChatSettingSectionIDs.GROUP_ALIAS,
                            ) {
                                SettingRowNavigate(
                                    title = stringResource(R.string.chat_setting_my_alias_in_group),
                                    value = selfNameCard?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.chat_setting_not_set),
                                    showArrow = false,
                                    customAccessoryResId = if (canEditSelfNameCard) {
                                        R.drawable.chat_setting_group_name_edit_icon
                                    } else {
                                        null
                                    },
                                    onClick = if (canEditSelfNameCard) {
                                        { showAliasDialog = true }
                                    } else {
                                        null
                                    }
                                )
                            }
                        )
                    }
                    if (config.isShowDoNotDisturb) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_DO_NOT_DISTURB,
                                ChatSettingSectionIDs.GROUP_SWITCHES,
                            ) {
                                SettingRowToggle(
                                    title = stringResource(R.string.chat_setting_do_not_disturb),
                                    checked = isNotDisturb,
                                    onCheckedChange = { checked -> viewModel.setDoNotDisturb(checked) }
                                )
                            }
                        )
                    }
                    if (config.isShowPin) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_PIN,
                                ChatSettingSectionIDs.GROUP_SWITCHES,
                            ) {
                                SettingRowToggle(
                                    title = stringResource(R.string.chat_setting_pin),
                                    checked = isPinned,
                                    onCheckedChange = { checked -> viewModel.setPinChat(checked) }
                                )
                            }
                        )
                    }
                    if (config.isShowChatBackground) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_CHAT_BACKGROUND,
                                ChatSettingSectionIDs.GROUP_CHAT_BACKGROUND,
                            ) {
                                SettingRowNavigate(
                                    title = stringResource(R.string.chat_setting_chat_background),
                                    value = stringResource(
                                        if (chatBackgroundImageUri.isNullOrBlank()) {
                                            R.string.chat_setting_chat_background_default
                                        } else {
                                            R.string.chat_setting_chat_background_custom
                                        }
                                    ),
                                    showArrow = true,
                                    onClick = { showBackgroundPicker = true }
                                )
                            }
                        )
                    }
                    if (config.isShowTransferOwner) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_TRANSFER_OWNER,
                                ChatSettingSectionIDs.GROUP_ACTIONS,
                            ) {
                                SettingRowButton(
                                    title = stringResource(R.string.chat_setting_transfer_group_owner),
                                    style = SettingRowButtonStyle.LINK,
                                    onClick = {
                                        viewModel.loadAllGroupMembers {
                                            transferCandidates = viewModel.memberList.value
                                                .filter { it.role != GroupMemberRole.OWNER }
                                            showTransferPicker = true
                                        }
                                    }
                                )
                            }
                        )
                    }
                    if (config.isShowClearHistory) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_CLEAR_HISTORY,
                                ChatSettingSectionIDs.GROUP_ACTIONS,
                            ) {
                                SettingRowButton(
                                    title = stringResource(R.string.chat_setting_clear_history_messages),
                                    style = SettingRowButtonStyle.DANGER,
                                    onClick = { showClearHistoryDialog = true }
                                )
                            }
                        )
                    }
                    if (config.isShowDeleteAndQuit) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_DELETE_AND_QUIT,
                                ChatSettingSectionIDs.GROUP_ACTIONS,
                            ) {
                                SettingRowButton(
                                    title = stringResource(R.string.chat_setting_delete_and_quit),
                                    style = SettingRowButtonStyle.DANGER,
                                    onClick = { showQuitDialog = true }
                                )
                            }
                        )
                    }
                    if (config.isShowDismiss) {
                        add(
                            ChatSettingCustomItem(
                                ChatSettingItemIDs.GROUP_DISMISS,
                                ChatSettingSectionIDs.GROUP_ACTIONS,
                            ) {
                                SettingRowButton(
                                    title = stringResource(R.string.chat_setting_dismiss_group),
                                    style = SettingRowButtonStyle.DANGER,
                                    onClick = { showDismissDialog = true }
                                )
                            }
                        )
                    }
                },
            customizer = config.itemCustomizer,
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(color = colors.bgColorInput)
                .verticalScroll(rememberScrollState())
        ) {
            ChatSettingItemColumn(
                itemContext = itemContext,
                items = items,
                itemAvailability = itemAvailability
            )
        }

        GroupMemberList(
            isVisible = showMemberList,
            title = stringResource(R.string.chat_setting_group_members),
            isSelectionMode = false,
            groupId = groupID,
            currentUserRole = selfRole,
            onDismiss = { showMemberList = false },
            onMemberClick = { member -> onGroupMemberClick(member) },
            onSetAsAdmin = { member ->
                viewModel.setMemberRole(member.userID, GroupMemberRole.ADMIN)
            },
            onRemoveAdmin = { member ->
                viewModel.setMemberRole(member.userID, GroupMemberRole.MEMBER)
            },
            onRemoveMember = { member ->
                viewModel.deleteMember(listOf(member))
            },
            onViewInfo = { member -> onGroupMemberClick(member) }
        )

        AvatarSelector(
            isVisible = showAvatarPicker,
            title = stringResource(R.string.chat_setting_select_avatar),
            imageUrls = getGroupAvatarUrls(),
            onDismiss = { showAvatarPicker = false },
            onImageSelected = { _, url -> viewModel.setGroupAvatar(url) }
        )

        TextInputBottomSheet(
            isVisible = showGroupNameDialog,
            title = stringResource(R.string.chat_setting_modify_group_name),
            initialText = groupName,
            onDismiss = { showGroupNameDialog = false },
            onConfirm = { value ->
                if (value.isNotBlank()) {
                    viewModel.setGroupName(value)
                }
            }
        )

        TextInputBottomSheet(
            isVisible = showNoticeDialog,
            title = stringResource(R.string.chat_setting_group_notice),
            initialText = notice,
            maxLength = 300,
            multiline = true,
            onDismiss = { showNoticeDialog = false },
            onConfirm = { value ->
                if (value != notice) {
                    viewModel.setGroupNotice(value)
                }
            }
        )

        TextInputBottomSheet(
            isVisible = showAliasDialog,
            title = stringResource(R.string.chat_setting_modify_group_name_card),
            initialText = selfNameCard ?: "",
            onDismiss = { showAliasDialog = false },
            onConfirm = { value -> viewModel.setGroupNickname(value) }
        )

        ActionSheet(
            isVisible = showJoinMethodSheet,
            options = listOf(
                ActionItem(
                    text = stringResource(R.string.chat_setting_join_method_forbid),
                    value = GroupJoinOption.FORBID
                ),
                ActionItem(
                    text = stringResource(R.string.chat_setting_method_auth),
                    value = GroupJoinOption.AUTH
                ),
                ActionItem(
                    text = stringResource(R.string.chat_setting_method_auto),
                    value = GroupJoinOption.ANY
                )
            ),
            onDismiss = { showJoinMethodSheet = false },
            onActionSelected = { item ->
                (item.value as? GroupJoinOption)?.let { viewModel.setJoinGroupApproveType(it) }
            }
        )

        ActionSheet(
            isVisible = showInviteMethodSheet,
            options = listOf(
                ActionItem(
                    text = stringResource(R.string.chat_setting_invite_method_forbid),
                    value = GroupInviteOption.FORBID
                ),
                ActionItem(
                    text = stringResource(R.string.chat_setting_method_auth),
                    value = GroupInviteOption.AUTH
                ),
                ActionItem(
                    text = stringResource(R.string.chat_setting_method_auto),
                    value = GroupInviteOption.ANY
                )
            ),
            onDismiss = { showInviteMethodSheet = false },
            onActionSelected = { item ->
                (item.value as? GroupInviteOption)?.let { viewModel.setInviteGroupApproveType(it) }
            }
        )

        ChatBackgroundPickerDialog(
            isVisible = showBackgroundPicker,
            selectedImageUri = chatBackgroundImageUri,
            onDismiss = { showBackgroundPicker = false },
            onBackgroundSelected = { imageUri ->
                if (imageUri.isNullOrBlank()) {
                    viewModel.clearChatBackground()
                } else {
                    viewModel.setChatBackground(imageUri)
                }
            }
        )

        GroupManagementDialog(
            isVisible = showManagementDialog,
            groupId = groupID,
            onDismiss = { showManagementDialog = false }
        )

        ContactPickerDialog(
            isVisible = showAddMemberPicker,
            title = stringResource(R.string.chat_setting_add_group_members),
            contacts = addMemberCandidates,
            onDismiss = { showAddMemberPicker = false },
            onConfirm = { selected ->
                viewModel.addMember(selected.map(ContactInfo::userID))
            }
        )

        GroupMemberPickerDialog(
            isVisible = showRemoveMemberPicker,
            title = stringResource(R.string.chat_setting_delete_member),
            candidates = removeMemberCandidates,
            onDismiss = { showRemoveMemberPicker = false },
            onConfirm = { selected ->
                if (selected.isNotEmpty()) {
                    viewModel.deleteMember(selected)
                }
            }
        )

        GroupMemberPickerDialog(
            isVisible = showTransferPicker,
            title = stringResource(R.string.chat_setting_transfer_group_owner),
            candidates = transferCandidates,
            maxSelection = 1,
            onDismiss = { showTransferPicker = false },
            onConfirm = { selected ->
                val member = selected.firstOrNull() ?: return@GroupMemberPickerDialog
                transferTarget = member
                showTransferConfirm = true
            }
        )

        ChatSettingConfirmDialog(
            isVisible = showTransferConfirm,
            message = stringResource(R.string.chat_setting_tansfer_owner_tips),
            isConfirmDestructive = true,
            onDismiss = { showTransferConfirm = false },
            onCancel = { showTransferConfirm = false },
            onConfirm = {
                showTransferConfirm = false
                transferTarget?.let { viewModel.changeOwner(it.userID) }
            }
        )

        ChatSettingConfirmDialog(
            isVisible = showClearHistoryDialog,
            message = stringResource(R.string.chat_setting_clear_group_history_messages_tips),
            onDismiss = { showClearHistoryDialog = false },
            onCancel = { showClearHistoryDialog = false },
            onConfirm = {
                showClearHistoryDialog = false
                viewModel.clearChatHistory()
            }
        )

        ChatSettingConfirmDialog(
            isVisible = showQuitDialog,
            message = stringResource(R.string.chat_setting_delete_and_quit_tips),
            isConfirmDestructive = true,
            onDismiss = { showQuitDialog = false },
            onCancel = { showQuitDialog = false },
            onConfirm = {
                showQuitDialog = false
                viewModel.quitGroup(
                    onSuccess = { onGroupDeleted() },
                    onFailure = { _, desc -> Toast.error(context, desc) }
                )
            }
        )

        ChatSettingConfirmDialog(
            isVisible = showDismissDialog,
            message = stringResource(R.string.chat_setting_dismiss_group_tips),
            isConfirmDestructive = true,
            onDismiss = { showDismissDialog = false },
            onCancel = { showDismissDialog = false },
            onConfirm = {
                showDismissDialog = false
                viewModel.dismissGroup(
                    onSuccess = { onGroupDeleted() },
                    onFailure = { _, desc -> Toast.error(context, desc) }
                )
            }
        )
    }
}

@Composable
private fun GroupHeaderSection(
    viewModel: GroupChatSettingViewModel,
    canEditGroupName: Boolean,
    canEditGroupAvatar: Boolean,
    onAvatarClick: () -> Unit,
    onGroupNameClick: () -> Unit,
    onGroupIdClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val groupName by viewModel.groupName.collectAsState()
    val avatarURL by viewModel.avatar.collectAsState()
    val headerDisplayName = groupName.ifEmpty { viewModel.groupID }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            content = AvatarContent.Image(
                url = avatarURL.ifEmpty { null },
                fallbackName = headerDisplayName
            ),
            size = AvatarSize.L,
            onClick = {
                if (canEditGroupAvatar) {
                    onAvatarClick()
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canEditGroupName) {
                            Modifier.clickable { onGroupNameClick() }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text(
                    text = headerDisplayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.textColorPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (canEditGroupName) {
                    Icon(
                        painter = painterResource(R.drawable.chat_setting_group_name_edit_icon),
                        contentDescription = stringResource(R.string.chat_setting_edit),
                        tint = colors.textColorPrimary,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(15.dp)
                    )
                }
            }
            Text(
                text = "${stringResource(R.string.chat_setting_group_id)}: ${viewModel.groupID}",
                fontSize = 13.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorTertiary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { onGroupIdClick() }
            )
        }
    }
}

@Composable
private fun GroupMemberPreviewSection(
    members: List<GroupMember>,
    memberCount: Int,
    showAddButton: Boolean,
    showRemoveButton: Boolean,
    onHeaderClick: () -> Unit,
    onMemberClick: (GroupMember) -> Unit,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
    ) {
        SettingRowNavigate(
            title = stringResource(R.string.chat_setting_group_members),
            value = memberCount.toString(),
            showArrow = true,
            onClick = onHeaderClick
        )

        val reserved = (if (showAddButton) 1 else 0) + (if (showRemoveButton) 1 else 0)
        val maxMemberSlots = (MAX_PREVIEW_COUNT - reserved).coerceAtLeast(0)

        val slots = mutableListOf<@Composable () -> Unit>()
        members.take(maxMemberSlots).forEach { member ->
            slots.add {
                MemberPreviewItem(member = member, onClick = { onMemberClick(member) })
            }
        }
        if (showAddButton) {
            slots.add { MemberPreviewActionItem(isAdd = true, onClick = onAddClick) }
        }
        if (showRemoveButton) {
            slots.add { MemberPreviewActionItem(isAdd = false, onClick = onRemoveClick) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
        ) {
            val firstRow = slots.take(PREVIEW_COLUMNS)
            val secondRow = slots.drop(PREVIEW_COLUMNS)
            Row(modifier = Modifier.fillMaxWidth()) {
                firstRow.forEach { slot ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        slot()
                    }
                }
                repeat(PREVIEW_COLUMNS - firstRow.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (secondRow.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    secondRow.forEach { slot ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            slot()
                        }
                    }
                    repeat(PREVIEW_COLUMNS - secondRow.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberPreviewItem(
    member: GroupMember,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .width(40.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(
            content = AvatarContent.Image(url = member.avatarURL, fallbackName = member.displayName),
            size = AvatarSize.M
        )
        Text(
            text = member.displayName,
            fontSize = 12.sp,
            color = colors.textColorPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun MemberPreviewActionItem(
    isAdd: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .width(40.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.bgColorInput, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isAdd) "+" else "−",
                fontSize = 22.sp,
                color = colors.textColorSecondary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

private const val PREVIEW_COLUMNS = 6
private const val MAX_PREVIEW_COUNT = PREVIEW_COLUMNS * 2

@Composable
private fun groupTypeText(groupType: GroupType): String {
    return when (groupType) {
        GroupType.WORK -> stringResource(R.string.chat_setting_group_type_work)
        GroupType.PUBLIC_GROUP -> stringResource(R.string.chat_setting_group_type_public)
        GroupType.MEETING -> stringResource(R.string.chat_setting_group_type_meeting)
        GroupType.AV_CHAT_ROOM -> stringResource(R.string.chat_setting_group_type_avchatroom)
        GroupType.COMMUNITY -> stringResource(R.string.chat_setting_group_type_community)
        else -> stringResource(R.string.chat_setting_group_type_work)
    }
}

@Composable
private fun joinOptionText(option: GroupJoinOption): String {
    return when (option) {
        GroupJoinOption.FORBID -> stringResource(R.string.chat_setting_join_method_forbid)
        GroupJoinOption.AUTH -> stringResource(R.string.chat_setting_method_auth)
        GroupJoinOption.ANY -> stringResource(R.string.chat_setting_method_auto)
        else -> stringResource(R.string.chat_setting_method_auto)
    }
}

@Composable
private fun inviteOptionText(option: GroupInviteOption): String {
    return when (option) {
        GroupInviteOption.FORBID -> stringResource(R.string.chat_setting_invite_method_forbid)
        GroupInviteOption.AUTH -> stringResource(R.string.chat_setting_method_auth)
        GroupInviteOption.ANY -> stringResource(R.string.chat_setting_method_auto)
        else -> stringResource(R.string.chat_setting_method_auto)
    }
}
