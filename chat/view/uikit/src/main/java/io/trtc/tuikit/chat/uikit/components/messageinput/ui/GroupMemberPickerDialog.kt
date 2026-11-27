package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.content.Context
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.chat.uikit.components.userpicker.ui.UserPickerDialog
import io.trtc.tuikit.atomicxcore.api.group.GroupMember

internal class GroupMemberPickerDialog(
    context: Context,
    title: String,
    candidates: List<GroupMember>,
    preSelectedMemberIDs: List<String> = emptyList(),
    maxSelection: Int = Int.MAX_VALUE,
    onConfirm: (List<GroupMember>) -> Unit
) {

    private val delegate = UserPickerDialog(
        context = context,
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
        onConfirm = onConfirm
    )

    fun show() {
        delegate.show()
    }

    fun dismiss() {
        delegate.dismiss()
    }
}
