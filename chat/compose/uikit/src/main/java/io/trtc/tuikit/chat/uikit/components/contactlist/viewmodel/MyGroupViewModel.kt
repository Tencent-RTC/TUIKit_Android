package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyGroupViewModel(
    private val groupStore: GroupStore,
) : ViewModel() {
    private val groupState = groupStore.state

    val groups = groupState.joinedGroupList
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

    override fun onCleared() {
        super.onCleared()
    }

    fun fetchGroups() {
        viewModelScope.launch {
            groupStore.loadJoinedGroups(object : CompletionHandler {
                override fun onSuccess() {
                    // Success handled by StateFlow
                }

                override fun onFailure(code: Int, desc: String) {
                    // Handle error if needed
                }
            })
        }
    }

}

class MyGroupViewModelFactory(
    private val groupStore: GroupStore = GroupStore.shared,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyGroupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyGroupViewModel(groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
