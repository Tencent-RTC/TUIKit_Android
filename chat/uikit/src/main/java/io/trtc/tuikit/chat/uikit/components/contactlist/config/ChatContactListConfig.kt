package io.trtc.tuikit.chat.uikit.components.contactlist.config

interface ContactListConfigProtocol {
    val showNewContacts: Boolean
    val showGroupApplications: Boolean
    val showMyGroups: Boolean
    val showBlacklist: Boolean
    val showSearchBar: Boolean
    val itemCustomizer: ContactListItemCustomizer?
        get() = null
}

class ChatContactListConfig(
    override var showNewContacts: Boolean = true,
    override var showGroupApplications: Boolean = true,
    override var showMyGroups: Boolean = true,
    override var showBlacklist: Boolean = true,
    override var showSearchBar: Boolean = true,
) : ContactListConfigProtocol {

    override var itemCustomizer: ContactListItemCustomizer? = null
        private set

    fun customizeItems(block: ContactListItemEditor.() -> Unit): ChatContactListConfig = apply {
        itemCustomizer = ContactListItemCustomizer { editor ->
            editor.block()
        }
    }
}
