package io.trtc.tuikit.chat.uikit.components.contactlist.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup.AddContactAndGroupBottomSheet
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addnewchat.AddNewChatBottomSheet
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddContactAndGroupViewModelFactory
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddNewChatViewModelFactory
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ChatType
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore

enum class ContactListFlow {
    START_SINGLE_CHAT,
    CREATE_GROUP_CHAT,
    ADD_CONTACT,
    JOIN_GROUP
}

class ContactListFlowController internal constructor() {

    var currentFlow by mutableStateOf<ContactListFlow?>(null)
        private set

    internal var initialContactInfo by mutableStateOf<ContactInfo?>(null)
        private set

    fun showStartSingleChatPage() {
        initialContactInfo = null
        currentFlow = ContactListFlow.START_SINGLE_CHAT
    }

    fun showCreateGroupChatPage() {
        initialContactInfo = null
        currentFlow = ContactListFlow.CREATE_GROUP_CHAT
    }

    fun showAddFriendPage() {
        initialContactInfo = null
        currentFlow = ContactListFlow.ADD_CONTACT
    }

    fun showAddFriendForContact(contactInfo: ContactInfo) {
        initialContactInfo = contactInfo
        currentFlow = ContactListFlow.ADD_CONTACT
    }

    fun showAddGroupPage() {
        initialContactInfo = null
        currentFlow = ContactListFlow.JOIN_GROUP
    }

    fun showAddContactDialog() {
        showAddFriendPage()
    }

    fun showJoinGroupDialog() {
        showAddGroupPage()
    }

    fun showCreateSingleChatDialog() {
        showStartSingleChatPage()
    }

    fun showCreateGroupDialog() {
        showCreateGroupChatPage()
    }

    fun dismiss() {
        currentFlow = null
        initialContactInfo = null
    }
}

@Composable
fun rememberContactListFlowController(): ContactListFlowController {
    return remember { ContactListFlowController() }
}

@Composable
fun ContactListFlowHost(
    controller: ContactListFlowController,
    onCreateChat: (String) -> Unit = {},
    contactStore: ContactStore = ContactStore.shared,
    groupStore: GroupStore = GroupStore.shared,
) {
    val addNewChatViewModelFactory = remember(contactStore, groupStore) {
        AddNewChatViewModelFactory(contactStore, groupStore)
    }
    val addContactAndGroupViewModelFactory = remember(contactStore, groupStore) {
        AddContactAndGroupViewModelFactory(contactStore, groupStore)
    }
    when (controller.currentFlow) {
        ContactListFlow.START_SINGLE_CHAT -> AddNewChatBottomSheet(
            chatType = ChatType.SINGLE,
            onDismiss = { controller.dismiss() },
            onCreateChat = onCreateChat,
            viewModelFactory = addNewChatViewModelFactory
        )

        ContactListFlow.CREATE_GROUP_CHAT -> AddNewChatBottomSheet(
            chatType = ChatType.GROUP,
            onDismiss = { controller.dismiss() },
            onCreateChat = onCreateChat,
            viewModelFactory = addNewChatViewModelFactory
        )

        ContactListFlow.ADD_CONTACT -> AddContactAndGroupBottomSheet(
            addType = AddType.CONTACT,
            onDismiss = { controller.dismiss() },
            initialContactInfo = controller.initialContactInfo,
            viewModelFactory = addContactAndGroupViewModelFactory
        )

        ContactListFlow.JOIN_GROUP -> AddContactAndGroupBottomSheet(
            addType = AddType.GROUP,
            onDismiss = { controller.dismiss() },
            viewModelFactory = addContactAndGroupViewModelFactory
        )

        null -> Unit
    }
}
