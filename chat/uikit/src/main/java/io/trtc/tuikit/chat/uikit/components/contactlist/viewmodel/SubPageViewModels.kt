package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BlacklistSubViewModel(
    private val contactStore: ContactStore
) : ViewModel() {

    val blacklistUsers: StateFlow<List<ContactInfo>> = contactStore.state.blackList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        contactStore.loadBlackList(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    fun removeFromBlacklist(userID: String, onFailure: (String) -> Unit = {}) {
        contactStore.removeFromBlacklist(userID, object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }
}

class FriendApplicationSubViewModel(
    private val contactStore: ContactStore
) : ViewModel() {

    val friendApplications: StateFlow<List<FriendApplicationInfo>>
        get() = contactStore.state.friendApplicationList

    init {
        contactStore.loadFriendApplications(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })

        contactStore.clearFriendApplicationUnreadCount(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    fun acceptFriendApplication(
        application: FriendApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        contactStore.acceptFriendApplication(application, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }

    fun refuseFriendApplication(
        application: FriendApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        contactStore.refuseFriendApplication(application, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }
}

class GroupApplicationSubViewModel(
    private val groupStore: GroupStore
) : ViewModel() {

    val groupApplications: StateFlow<List<GroupApplicationInfo>>
        get() = groupStore.state.applicationList

    init {
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })

        clearGroupApplicationUnreadCount()
    }

    fun clearGroupApplicationUnreadCount() {
        groupStore.clearApplicationUnreadCount(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    fun acceptApplication(
        application: GroupApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        groupStore.acceptApplication(application, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }

    fun refuseApplication(
        application: GroupApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        groupStore.refuseApplication(application, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }
}

class MyGroupSubViewModel(
    private val groupStore: GroupStore
) : ViewModel() {

    val groups: StateFlow<List<ContactInfo>> = groupStore.state.joinedGroupList
        .map { list ->
            list.map { info ->
                ContactInfo(
                    userID = info.groupID,
                    avatarURL = info.avatarURL,
                    nickname = info.groupName
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        groupStore.loadJoinedGroups(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }
}

class BlacklistSubViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BlacklistSubViewModel::class.java)) {
            return BlacklistSubViewModel(contactStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class FriendApplicationSubViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FriendApplicationSubViewModel::class.java)) {
            return FriendApplicationSubViewModel(contactStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class GroupApplicationSubViewModelFactory(
    private val groupStore: GroupStore = GroupStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupApplicationSubViewModel::class.java)) {
            return GroupApplicationSubViewModel(groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class MyGroupSubViewModelFactory(
    private val groupStore: GroupStore = GroupStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyGroupSubViewModel::class.java)) {
            return MyGroupSubViewModel(groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
