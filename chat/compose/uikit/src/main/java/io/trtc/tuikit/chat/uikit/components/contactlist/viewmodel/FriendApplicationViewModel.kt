package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo
import kotlinx.coroutines.launch

class FriendApplicationViewModel(
    private val contactStore: ContactStore,
) : ViewModel() {
    private val contactState = contactStore.state

    val friendApplications = contactState.friendApplicationList

    init {
        loadFriendApplications()
    }

    private fun loadFriendApplications() {
        contactStore.loadFriendApplications(object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
    }

    fun acceptFriendApplication(
        application: FriendApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            contactStore.acceptFriendApplication(
                application,
                object : CompletionHandler {
                    override fun onSuccess() {
                        onSuccess()
                    }

                    override fun onFailure(code: Int, desc: String) {
                        onFailure(desc)
                    }
                }
            )
        }
    }

    fun refuseFriendApplication(
        application: FriendApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            contactStore.refuseFriendApplication(
                application,
                object : CompletionHandler {
                    override fun onSuccess() {
                        onSuccess()
                    }

                    override fun onFailure(code: Int, desc: String) {
                        onFailure(desc)
                    }
                }
            )
        }
    }

    fun clearFriendApplicationUnreadCount() {
        viewModelScope.launch {
            contactStore.clearFriendApplicationUnreadCount(
                object : CompletionHandler {
                    override fun onSuccess() {
                    }

                    override fun onFailure(code: Int, desc: String) {
                    }
                }
            )
        }
    }

}

class FriendApplicationViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FriendApplicationViewModel::class.java)) {
            return FriendApplicationViewModel(contactStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
