package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerData
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerDialog

@Composable
internal fun ForwardContactPickerDialog(
    visible: Boolean,
    contacts: List<ContactInfo>,
    preSelectedUserIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<ContactInfo>) -> Unit,
) {
    UserPickerDialog(
        visible = visible,
        title = stringResource(R.string.message_list_select_from_contacts),
        dataSource = contacts.map { contact ->
            UserPickerData(
                key = contact.userID,
                label = contact.displayName,
                avatarUrl = contact.avatarURL,
                extraData = contact
            )
        },
        maxCount = 100,
        preSelectedKeys = preSelectedUserIds,
        allowEmptyConfirm = preSelectedUserIds.isNotEmpty(),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
