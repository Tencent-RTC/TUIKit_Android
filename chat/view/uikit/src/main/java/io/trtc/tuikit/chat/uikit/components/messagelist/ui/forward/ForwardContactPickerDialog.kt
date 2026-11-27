package io.trtc.tuikit.chat.uikit.components.messagelist.ui.forward
import android.content.Context
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.chat.uikit.components.userpicker.ui.UserPickerDialog
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

internal class ForwardContactPickerDialog(
    context: Context,
    title: String,
    contacts: List<ContactInfo>,
    maxSelection: Int = 100,
    preSelectedUserIds: List<String> = emptyList(),
    allowEmptyConfirm: Boolean = false,
    onConfirm: (List<ContactInfo>) -> Unit
) {

    private val delegate = UserPickerDialog(
        context = context,
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
        onConfirm = onConfirm
    )

    fun show() {
        delegate.show()
    }
}
