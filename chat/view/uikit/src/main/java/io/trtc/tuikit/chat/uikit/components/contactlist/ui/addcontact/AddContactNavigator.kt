package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType

internal object AddContactNavigator {

    enum class BackAction {
        DISMISS,
        SHOW_SEARCH,
        SHOW_CONTACT_DETAIL
    }

    fun titleRes(step: AddContactFlowStep, addType: AddType): Int {
        return when (step) {
            AddContactFlowStep.SEARCH -> {
                if (addType == AddType.CONTACT) R.string.contact_list_add_contact else R.string.contact_list_join_group
            }
            AddContactFlowStep.CONTACT_DETAIL -> {
                if (addType == AddType.CONTACT) R.string.contact_list_add_contact else R.string.contact_list_contact_info
            }
            AddContactFlowStep.ADD_FRIEND_FORM -> R.string.contact_list_add_contact
            AddContactFlowStep.GROUP_JOIN_FORM -> R.string.contact_list_group_info
            else -> {
                if (addType == AddType.CONTACT) R.string.contact_list_add_contact else R.string.contact_list_join_group
            }
        }
    }

    fun backAction(step: AddContactFlowStep, hasInitialContactInfo: Boolean): BackAction {
        return when (step) {
            AddContactFlowStep.SEARCH -> BackAction.DISMISS
            AddContactFlowStep.CONTACT_DETAIL -> {
                if (hasInitialContactInfo) BackAction.DISMISS else BackAction.SHOW_SEARCH
            }
            AddContactFlowStep.ADD_FRIEND_FORM -> BackAction.SHOW_CONTACT_DETAIL
            AddContactFlowStep.GROUP_JOIN_FORM -> {
                if (hasInitialContactInfo) BackAction.DISMISS else BackAction.SHOW_SEARCH
            }
            else -> BackAction.DISMISS
        }
    }
}
