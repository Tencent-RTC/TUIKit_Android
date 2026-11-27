package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarContent
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupMemberActionPolicy
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupMemberListViewModel
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun GroupMemberList(
    isVisible: Boolean,
    groupId: String,
    title: String = "",
    isSelectionMode: Boolean = false,
    isSingleSelect: Boolean = false,
    preSelectedMembers: List<String> = emptyList(),
    currentUserRole: GroupMemberRole = GroupMemberRole.MEMBER,
    refreshKey: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (List<GroupMember>) -> Unit = {},
    onMemberClick: (GroupMember) -> Unit = {},
    onSetAsAdmin: (GroupMember) -> Unit = {},
    onRemoveAdmin: (GroupMember) -> Unit = {},
    onRemoveMember: (GroupMember) -> Unit = {},
    onViewInfo: (GroupMember) -> Unit = {}
) {
    if (isVisible) {
        val viewModel: GroupMemberListViewModel = viewModel<GroupMemberListViewModel>(
            key = "GroupMemberList_${groupId}_$refreshKey"
        ) {
            GroupMemberListViewModel(groupId)
        }

        var selectedMembers by remember { mutableStateOf(preSelectedMembers.toSet()) }

        LaunchedEffect(isVisible, groupId) {
            if (isVisible) {
                selectedMembers = preSelectedMembers.toSet()
            }
        }

        FullScreenDialog(
            onDismissRequest = onDismiss,
        ) {
            GroupMemberPickerFullscreenContent(
                title = title,
                isSelectionMode = isSelectionMode,
                isSingleSelect = isSingleSelect,
                selectedMembers = selectedMembers,
                currentUserRole = currentUserRole,
                onSelectionChanged = { userID, isSelected ->
                    selectedMembers = if (isSingleSelect) {
                        if (isSelected) setOf(userID) else emptySet()
                    } else {
                        if (isSelected) selectedMembers + userID else selectedMembers - userID
                    }
                },
                onDismiss = onDismiss,
                onConfirm = { members ->
                    if (isSelectionMode) {
                        val selectedMemberItems = members.filter {
                            selectedMembers.contains(it.userID)
                        }
                        if (selectedMemberItems.isNotEmpty()) {
                            onConfirm(selectedMemberItems)
                            onDismiss()
                        }
                    }
                },
                onMemberClick = onMemberClick,
                onSetAsAdmin = onSetAsAdmin,
                onRemoveAdmin = onRemoveAdmin,
                onRemoveMember = onRemoveMember,
                onViewInfo = onViewInfo,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun GroupMemberPickerFullscreenContent(
    title: String,
    isSelectionMode: Boolean,
    isSingleSelect: Boolean,
    selectedMembers: Set<String>,
    currentUserRole: GroupMemberRole,
    onSelectionChanged: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<GroupMember>) -> Unit,
    onMemberClick: (GroupMember) -> Unit,
    onSetAsAdmin: (GroupMember) -> Unit,
    onRemoveAdmin: (GroupMember) -> Unit,
    onRemoveMember: (GroupMember) -> Unit,
    onViewInfo: (GroupMember) -> Unit,
    viewModel: GroupMemberListViewModel
) {
    val colors = LocalTheme.current.colors
    val members by viewModel.members.collectAsState()
    val listState = rememberLazyListState()

    var showActionSheet by remember { mutableStateOf(false) }
    var selectedGroupMemberForAction by remember { mutableStateOf<GroupMember?>(null) }
    var isLoadingMoreRequested by remember { mutableStateOf(false) }

    LaunchedEffect(members.size) {
        isLoadingMoreRequested = false
    }

    LaunchedEffect(listState, members.size) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            Pair(total > 0 && lastVisible >= total - 1, total)
        }.distinctUntilChanged().collect { (endReached, _) ->
            if (endReached && !isLoadingMoreRequested && members.isNotEmpty()) {
                isLoadingMoreRequested = true
                viewModel.loadMoreMembers()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorOperate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ChatSettingDialogTopBar(
                title = title,
                showConfirm = isSelectionMode,
                confirmEnabled = selectedMembers.isNotEmpty(),
                confirmText = stringResource(R.string.base_component_confirm),
                onBackClick = onDismiss,
                onConfirmClick = { onConfirm(members) }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState
            ) {
                items(items = members, key = { it.userID }) { member ->
                    GroupMemberItem(
                        groupMember = member,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedMembers.contains(member.userID),
                        onItemClick = {
                            if (isSelectionMode) {
                                onSelectionChanged(member.userID, !selectedMembers.contains(member.userID))
                            } else {
                                if (GroupMemberActionPolicy.hasActionPermission(currentUserRole, member.role)) {
                                    selectedGroupMemberForAction = member
                                    showActionSheet = true
                                } else {
                                    onMemberClick(member)
                                }
                            }
                        },
                        onSelectionChanged = { isSelected ->
                            onSelectionChanged(member.userID, isSelected)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        GroupMemberActionSheet(
            isVisible = showActionSheet,
            groupMember = selectedGroupMemberForAction,
            currentUserRole = currentUserRole,
            onDismiss = {
                showActionSheet = false
                selectedGroupMemberForAction = null
            },
            onSetAsAdmin = { member ->
                onSetAsAdmin(member)
                showActionSheet = false
                selectedGroupMemberForAction = null
            },
            onRemoveAdmin = { member ->
                onRemoveAdmin(member)
                showActionSheet = false
                selectedGroupMemberForAction = null
            },
            onRemoveMember = { member ->
                onRemoveMember(member)
                showActionSheet = false
                selectedGroupMemberForAction = null
            },
            onViewInfo = { member ->
                onViewInfo(member)
                showActionSheet = false
                selectedGroupMemberForAction = null
            }
        )
    }
}

@Composable
private fun GroupMemberItem(
    groupMember: GroupMember,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onSelectionChanged: (Boolean) -> Unit
) {
    val colors = LocalTheme.current.colors

    val roleText = when (groupMember.role) {
        GroupMemberRole.OWNER -> stringResource(R.string.chat_setting_member_type_owner)
        GroupMemberRole.ADMIN -> stringResource(R.string.chat_setting_member_type_administrator)
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .background(colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            content = AvatarContent.Image(
                url = groupMember.avatarURL,
                fallbackName = groupMember.displayName
            ),
            size = AvatarSize.M
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = groupMember.displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            roleText?.let {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.textColorLink,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(
                            color = colors.buttonColorPrimaryDisabled,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (isSelectionMode) {
            ChatSettingCheckbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged
            )
        }
    }
}
