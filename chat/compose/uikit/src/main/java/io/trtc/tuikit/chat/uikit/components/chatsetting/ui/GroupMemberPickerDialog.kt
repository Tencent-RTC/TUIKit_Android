package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.runtime.Composable
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerData
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerDialog
import io.trtc.tuikit.atomicxcore.api.group.GroupMember

@Composable
fun GroupMemberPickerDialog(
    isVisible: Boolean,
    title: String,
    candidates: List<GroupMember>,
    preSelectedMemberIDs: List<String> = emptyList(),
    maxSelection: Int = Int.MAX_VALUE,
    onDismiss: () -> Unit,
    onConfirm: (List<GroupMember>) -> Unit
) {
    UserPickerDialog(
        visible = isVisible,
        title = title,
        dataSource = candidates.map { member ->
            UserPickerData(
                key = member.userID,
                label = member.displayName,
                avatarUrl = member.avatarURL,
                extraData = member
            )
        },
        maxCount = maxSelection,
        preSelectedKeys = preSelectedMemberIDs,
        allowEmptyConfirm = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
