package io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel

import androidx.lifecycle.ViewModel
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class FriendPickerViewModel : ViewModel() {

    private val contactStore = ContactStore.shared
    private val contactState = contactStore.state

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val friends: StateFlow<List<ContactInfo>> = contactState.friendList

    fun loadFriends() {
        _isLoading.value = true
        contactStore.loadFriends(object : CompletionHandler {
            override fun onSuccess() {
                _isLoading.value = false
            }

            override fun onFailure(code: Int, desc: String) {
                _isLoading.value = false
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
    }
}
