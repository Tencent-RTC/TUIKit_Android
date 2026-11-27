package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.runtime.Composable
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerData
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerDialog
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

@Composable
fun ContactPickerDialog(
    isVisible: Boolean,
    title: String,
    contacts: List<ContactInfo>,
    maxSelection: Int = 100,
    preSelectedUserIds: List<String> = emptyList(),
    allowEmptyConfirm: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (List<ContactInfo>) -> Unit
) {
    UserPickerDialog(
        visible = isVisible,
        title = title,
        dataSource = contacts.map { contact ->
            UserPickerData(
                key = contact.userID,
                label = contact.displayName,
                avatarUrl = contact.avatarURL,
                extraData = contact
            )
        },
        maxCount = maxSelection,
        preSelectedKeys = preSelectedUserIds,
        allowEmptyConfirm = allowEmptyConfirm,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
