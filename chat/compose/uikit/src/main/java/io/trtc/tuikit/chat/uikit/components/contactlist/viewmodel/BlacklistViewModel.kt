package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlacklistViewModel(
    private val contactStore: ContactStore,
) : ViewModel() {
    private val contactState = contactStore.state

    val blacklistUsers = contactState.blackList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    override fun onCleared() {
        super.onCleared()
    }

    fun fetchBlacklistUsers() {
        viewModelScope.launch {
            contactStore.loadBlackList(object : CompletionHandler {
                override fun onSuccess() {
                    // Success handled by StateFlow
                }

                override fun onFailure(code: Int, desc: String) {
                    // Handle error if needed
                }
            })
        }
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


class BlacklistViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BlacklistViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BlacklistViewModel(contactStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
