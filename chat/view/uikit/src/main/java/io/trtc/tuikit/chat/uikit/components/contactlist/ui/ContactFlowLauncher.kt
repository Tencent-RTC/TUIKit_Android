package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.content.Context
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ChatType
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore

object ContactFlowLauncher {

    @JvmStatic
    fun showStartSingleChatPage(
        context: Context,
        contactStore: ContactStore = ContactStore.shared,
        groupStore: GroupStore = GroupStore.shared,
        onCreateChat: ((String) -> Unit)? = null
    ) {
        AddNewChatDialog(
            context = context,
            chatType = ChatType.SINGLE,
            contactStore = contactStore,
            groupStore = groupStore,
            onCreateChat = onCreateChat
        ).show()
    }

    @JvmStatic
    fun showCreateGroupChatPage(
        context: Context,
        contactStore: ContactStore = ContactStore.shared,
        groupStore: GroupStore = GroupStore.shared,
        onCreateChat: ((String) -> Unit)? = null
    ) {
        AddNewChatDialog(
            context = context,
            chatType = ChatType.GROUP,
            contactStore = contactStore,
            groupStore = groupStore,
            onCreateChat = onCreateChat
        ).show()
    }

    @JvmStatic
    fun showAddFriendPage(
        context: Context,
        contactStore: ContactStore = ContactStore.shared,
        groupStore: GroupStore = GroupStore.shared
    ) {
        AddContactAndGroupDialog(
            context = context,
            addType = AddType.CONTACT,
            contactStore = contactStore,
            groupStore = groupStore
        ).show()
    }

    @JvmStatic
    fun showAddGroupPage(
        context: Context,
        contactStore: ContactStore = ContactStore.shared,
        groupStore: GroupStore = GroupStore.shared
    ) {
        AddContactAndGroupDialog(
            context = context,
            addType = AddType.GROUP,
            contactStore = contactStore,
            groupStore = groupStore
        ).show()
    }

    @JvmStatic
    fun showAddFriendForContact(
        context: Context,
        contactInfo: ContactInfo,
        contactStore: ContactStore = ContactStore.shared,
        groupStore: GroupStore = GroupStore.shared
    ) {
        AddContactAndGroupDialog(
            context = context,
            addType = AddType.CONTACT,
            contactStore = contactStore,
            groupStore = groupStore,
            initialContactInfo = contactInfo
        ).show()
    }
}
