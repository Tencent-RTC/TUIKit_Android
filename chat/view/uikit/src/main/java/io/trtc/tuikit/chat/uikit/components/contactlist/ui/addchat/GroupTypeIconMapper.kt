package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupTypeOption

internal object GroupTypeIconMapper {

    fun iconResId(option: GroupTypeOption): Int {
        return iconResId(option.type.value)
    }

    fun iconResId(groupTypeValue: String): Int {
        return when (groupTypeValue) {
            "Work" -> R.drawable.contact_list_ic_group_type_work
            "Public" -> R.drawable.contact_list_ic_group_type_public
            "Meeting" -> R.drawable.contact_list_ic_group_type_meeting
            "Community" -> R.drawable.contact_list_ic_group_type_community
            else -> R.drawable.contact_list_ic_group_type_work
        }
    }
}
