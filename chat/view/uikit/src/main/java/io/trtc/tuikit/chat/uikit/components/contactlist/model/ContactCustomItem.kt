package io.trtc.tuikit.chat.uikit.components.contactlist.model

import android.content.Context
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomItem
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.common.uicustom.EditorContext
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ContactListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ContactListItemCustomizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ContactListItemIDs {
    const val NEW_CONTACTS = "contactList.newContacts"
    const val GROUP_APPLICATIONS = "contactList.groupApplications"
    const val MY_GROUPS = "contactList.myGroups"
    const val BLACKLIST = "contactList.blacklist"
}

data class ContactCustomItem(
    override val ID: String,
    val title: String = "",
    val titleResID: Int = 0,
    val iconResID: Int = 0,
    val badgeCount: StateFlow<Int> = MutableStateFlow(0),
    val onClick: () -> Unit = {},
) : CustomItem

class ContactCustomItemContext(
    override val androidContext: Context,
): EditorContext

internal fun filterContactListDefaults(
    config: ContactListConfigProtocol,
    candidates: List<ContactCustomItem>,
): List<ContactCustomItem> {
    return candidates.filter { item ->
        when (item.ID) {
            ContactListItemIDs.NEW_CONTACTS -> config.showNewContacts
            ContactListItemIDs.GROUP_APPLICATIONS -> config.showGroupApplications
            ContactListItemIDs.MY_GROUPS -> config.showMyGroups
            ContactListItemIDs.BLACKLIST -> config.showBlacklist
            else -> true
        }
    }
}

internal fun buildContactListItems(
    itemContext: ContactCustomItemContext,
    defaults: List<ContactCustomItem>,
    customizer: ContactListItemCustomizer?,
): List<ContactCustomItem> {
    val editor = CustomEditor(itemContext, defaults)
    customizer?.customize(editor)
    return editor.build()
}
