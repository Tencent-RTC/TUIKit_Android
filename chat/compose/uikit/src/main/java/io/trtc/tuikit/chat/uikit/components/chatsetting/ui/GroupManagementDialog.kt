package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarContent
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.widgets.Switch
import io.trtc.tuikit.chat.uikit.components.widgets.SwitchSize
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermission
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermissionManager
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole

@Composable
fun GroupManagementDialog(
    isVisible: Boolean,
    groupId: String,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        FullScreenDialog(
            onDismissRequest = onDismiss,
        ) {
            GroupManagementFullscreenContent(
                groupId = groupId,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun GroupManagementFullscreenContent(
    groupId: String,
    onDismiss: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val groupViewModel = LocalGroupViewModel.current

    val isAllMuted by groupViewModel.isAllMuted.collectAsState()
    val currentUserRole by groupViewModel.currentUserRole.collectAsState()
    val groupType by groupViewModel.groupType.collectAsState()
    val silencedMembers by groupViewModel.silencedMembers.collectAsState()
    val adminMembers by groupViewModel.adminMembers.collectAsState()

    var showMuteMemberPicker by remember { mutableStateOf(false) }
    var muteCandidates by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var showAdminAddPicker by remember { mutableStateOf(false) }
    var adminAddCandidates by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var showAdminRemovePicker by remember { mutableStateOf(false) }
    var adminRemoveCandidates by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var unmuteTarget by remember { mutableStateOf<GroupMember?>(null) }
    var showUnmuteSheet by remember { mutableStateOf(false) }
    var removeAdminTarget by remember { mutableStateOf<GroupMember?>(null) }
    var showRemoveAdminSheet by remember { mutableStateOf(false) }

    val canManageAdmins = GroupPermissionManager.canPerformAction(
        groupType, currentUserRole, GroupPermission.SET_GROUP_MEMBER_ROLE
    )

    LaunchedEffect(Unit) {
        groupViewModel.loadAllGroupMembers()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorTopBar)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ChatSettingDialogTopBar(
                title = stringResource(R.string.chat_setting_group_management),
                dividerColor = colors.strokeColorPrimary,
                onBackClick = onDismiss
            )

            Spacer(modifier = Modifier.height(if (canManageAdmins) 10.dp else 20.dp))

            if (canManageAdmins) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgColorOperate)
                ) {
                    Text(
                        text = stringResource(R.string.chat_setting_group_admins),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W400,
                        color = colors.textColorSecondary,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 16.dp
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        adminMembers.forEach { member ->
                            AdminMemberItem(
                                member = member,
                                onClick = {
                                    removeAdminTarget = member
                                    showRemoveAdminSheet = true
                                }
                            )
                        }
                        AdminActionItem(
                            isAdd = true,
                            onClick = {
                                groupViewModel.loadAllGroupMembers {
                                    adminAddCandidates = groupViewModel.memberList.value
                                        .filter { it.role == GroupMemberRole.MEMBER }
                                    showAdminAddPicker = true
                                }
                            }
                        )
                        if (adminMembers.isNotEmpty()) {
                            AdminActionItem(
                                isAdd = false,
                                onClick = {
                                    groupViewModel.loadAllGroupMembers {
                                        adminRemoveCandidates = groupViewModel.adminMembers.value
                                        showAdminRemovePicker = true
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgColorOperate)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_setting_mute_all),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.textColorSecondary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isAllMuted,
                    size = SwitchSize.L,
                    onCheckedChange = { groupViewModel.toggleGroupAllMute() }
                )
            }

            Text(
                text = stringResource(R.string.chat_setting_mute_all_tips),
                fontSize = 14.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            )

            if (!isAllMuted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgColorOperate)
                        .clickable {
                            groupViewModel.loadAllGroupMembers {
                                muteCandidates = groupViewModel.memberList.value
                                    .filter { it.role != GroupMemberRole.OWNER && !it.isMuted }
                                showMuteMemberPicker = true
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W400,
                        color = colors.textColorLink
                    )
                    Text(
                        text = stringResource(R.string.chat_setting_select_muted_member),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = colors.textColorLink,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(silencedMembers, key = { it.userID }) { member ->
                        SilencedMemberItem(
                            groupMember = member,
                            onLongClick = {
                                unmuteTarget = member
                                showUnmuteSheet = true
                            }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    GroupMemberPickerDialog(
        isVisible = showMuteMemberPicker,
        title = stringResource(R.string.chat_setting_select_muted_member),
        candidates = muteCandidates,
        onDismiss = { showMuteMemberPicker = false },
        onConfirm = { members ->
            members.forEach { member ->
                groupViewModel.muteGroupMember(
                    member.userID,
                    7 * 24 * 60 * 60,
                    onFailure = { _, desc ->
                        Toast.error(
                            context,
                            context.getString(R.string.chat_setting_mute_operation_failed, desc)
                        )
                    }
                )
            }
        }
    )

    GroupMemberPickerDialog(
        isVisible = showAdminAddPicker,
        title = stringResource(R.string.chat_setting_select_admin_member),
        candidates = adminAddCandidates,
        onDismiss = { showAdminAddPicker = false },
        onConfirm = { members ->
            members.forEach { member ->
                groupViewModel.setMemberRole(
                    member.userID,
                    GroupMemberRole.ADMIN,
                    onFailure = { _, desc ->
                        Toast.error(
                            context,
                            context.getString(R.string.chat_setting_admin_operation_failed, desc)
                        )
                    }
                )
            }
        }
    )

    GroupMemberPickerDialog(
        isVisible = showAdminRemovePicker,
        title = stringResource(R.string.chat_setting_remove_admin),
        candidates = adminRemoveCandidates,
        onDismiss = { showAdminRemovePicker = false },
        onConfirm = { members ->
            members.forEach { member ->
                groupViewModel.setMemberRole(
                    member.userID,
                    GroupMemberRole.MEMBER,
                    onFailure = { _, desc ->
                        Toast.error(
                            context,
                            context.getString(R.string.chat_setting_admin_operation_failed, desc)
                        )
                    }
                )
            }
        }
    )

    ActionSheet(
        isVisible = showUnmuteSheet,
        options = listOf(
            ActionItem(text = stringResource(R.string.chat_setting_cancel_mute), value = "unmute")
        ),
        onDismiss = {
            showUnmuteSheet = false
            unmuteTarget = null
        },
        onActionSelected = {
            unmuteTarget?.let { member ->
                groupViewModel.muteGroupMember(
                    member.userID,
                    0,
                    onFailure = { _, desc ->
                        Toast.error(
                            context,
                            context.getString(R.string.chat_setting_mute_operation_failed, desc)
                        )
                    }
                )
            }
            showUnmuteSheet = false
            unmuteTarget = null
        }
    )

    ActionSheet(
        isVisible = showRemoveAdminSheet,
        options = listOf(
            ActionItem(text = stringResource(R.string.chat_setting_remove_admin), value = "removeAdmin")
        ),
        onDismiss = {
            showRemoveAdminSheet = false
            removeAdminTarget = null
        },
        onActionSelected = {
            removeAdminTarget?.let { member ->
                groupViewModel.setMemberRole(
                    member.userID,
                    GroupMemberRole.MEMBER,
                    onFailure = { _, desc ->
                        Toast.error(
                            context,
                            context.getString(R.string.chat_setting_admin_operation_failed, desc)
                        )
                    }
                )
            }
            showRemoveAdminSheet = false
            removeAdminTarget = null
        }
    )
}

@Composable
private fun AdminMemberItem(
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
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
        )
    }
}

@Composable
private fun AdminActionItem(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SilencedMemberItem(
    groupMember: GroupMember,
    onLongClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            )
            .background(colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            content = if (!groupMember.avatarURL.isNullOrEmpty()) {
                AvatarContent.Image(
                    url = groupMember.avatarURL,
                    fallbackName = groupMember.displayName
                )
            } else {
                AvatarContent.Text(groupMember.displayName)
            },
            size = AvatarSize.M
        )

        Text(
            text = groupMember.displayName,
            fontSize = 14.sp,
            fontWeight = FontWeight.W400,
            color = colors.textColorPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 13.dp)
        )
    }
}
