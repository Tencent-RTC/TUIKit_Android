package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ChatContactListConfig
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ContactListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactCustomItem
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactListItemIDs
import io.trtc.tuikit.chat.uikit.components.contactlist.model.filterContactListDefaults
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ContactListViewModel(
    val contactStore: ContactStore,
    val groupStore: GroupStore,
) : ViewModel() {

    private val contactState = contactStore.state
    private val groupStoreState = groupStore.state

    val groupApplicationCount: StateFlow<Int> = groupStoreState.unreadApplicationCount
    val friendApplicationCount: StateFlow<Int> = contactState.friendApplicationUnreadCount

    val friendList: StateFlow<List<ContactInfo>> = contactState.friendList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val _initialLoadFinished = MutableStateFlow(false)
    val initialLoadFinished: StateFlow<Boolean> = _initialLoadFinished

    init {
        contactStore.loadFriends(object : CompletionHandler {
            override fun onSuccess() {
                _initialLoadFinished.value = true
            }
            override fun onFailure(code: Int, desc: String) {
                _initialLoadFinished.value = true
            }
        })
        contactStore.loadFriendApplications(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    fun getDefaultItems(
        config: ContactListConfigProtocol = ChatContactListConfig(),
        onNavigateToFriendApplications: () -> Unit = {},
        onNavigateToGroupApplications: () -> Unit = {},
        onNavigateToMyGroup: () -> Unit = {},
        onNavigateToBlacklist: () -> Unit = {},
    ): List<ContactCustomItem> {
        val candidates = listOf(
            ContactCustomItem(
                ID = ContactListItemIDs.NEW_CONTACTS,
                titleResID = R.string.contact_list_new_contacts,
                iconResID = R.drawable.contact_list_ic_new_contacts,
                badgeCount = friendApplicationCount,
                onClick = onNavigateToFriendApplications,
            ),
            ContactCustomItem(
                ID = ContactListItemIDs.GROUP_APPLICATIONS,
                titleResID = R.string.contact_list_new_group_applications,
                iconResID = R.drawable.contact_list_ic_group_notification,
                badgeCount = groupApplicationCount,
                onClick = onNavigateToGroupApplications,
            ),
            ContactCustomItem(
                ID = ContactListItemIDs.MY_GROUPS,
                titleResID = R.string.contact_list_my_group,
                iconResID = R.drawable.contact_list_ic_my_group,
                badgeCount = MutableStateFlow(0),
                onClick = onNavigateToMyGroup,
            ),
            ContactCustomItem(
                ID = ContactListItemIDs.BLACKLIST,
                titleResID = R.string.contact_list_blacklist,
                iconResID = R.drawable.contact_list_ic_blacklist,
                badgeCount = MutableStateFlow(0),
                onClick = onNavigateToBlacklist,
            ),
        )
        return filterContactListDefaults(config, candidates)
    }
}

class ContactListViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared,
    private val groupStore: GroupStore = GroupStore.shared,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactListViewModel::class.java)) {
            return ContactListViewModel(contactStore, groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
