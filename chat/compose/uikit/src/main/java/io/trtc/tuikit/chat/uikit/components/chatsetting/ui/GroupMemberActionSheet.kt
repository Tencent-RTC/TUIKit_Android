package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupMemberActionPolicy
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole

@Composable
fun GroupMemberActionSheet(
    isVisible: Boolean,
    groupMember: GroupMember?,
    currentUserRole: GroupMemberRole,
    onDismiss: () -> Unit,
    onSetAsAdmin: (GroupMember) -> Unit = {},
    onRemoveAdmin: (GroupMember) -> Unit = {},
    onRemoveMember: (GroupMember) -> Unit = {},
    onViewInfo: (GroupMember) -> Unit = {}
) {
    if (!isVisible || groupMember == null) return

    val options = mutableListOf(
        ActionItem(text = stringResource(R.string.chat_setting_member_info), value = "info")
    )
    if (GroupMemberActionPolicy.canRemoveMember(currentUserRole, groupMember.role)) {
        options.add(
            ActionItem(
                text = stringResource(R.string.chat_setting_delete_member),
                value = "delete"
            )
        )
    }
    if (GroupMemberActionPolicy.canSetAdmin(currentUserRole, groupMember.role)) {
        options.add(
            ActionItem(
                text = stringResource(R.string.chat_setting_set_admin),
                value = "setAdmin"
            )
        )
    }
    if (GroupMemberActionPolicy.canRemoveAdmin(currentUserRole, groupMember.role)) {
        options.add(
            ActionItem(
                text = stringResource(R.string.chat_setting_remove_admin),
                value = "removeAdmin"
            )
        )
    }

    ActionSheet(
        isVisible = isVisible,
        options = options,
        onDismiss = onDismiss
    ) {
        when (it.value) {
            "info" -> onViewInfo(groupMember)
            "delete" -> onRemoveMember(groupMember)
            "setAdmin" -> onSetAsAdmin(groupMember)
            "removeAdmin" -> onRemoveAdmin(groupMember)
        }
    }
}
