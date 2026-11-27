package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.launch

class GroupApplicationViewModel(
    private val groupStore: GroupStore,
) : ViewModel() {
    private val groupState = groupStore.state

    val groupApplications = groupState.applicationList

    fun fetchGroupApplicationList() {
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
    }

    fun acceptGroupApplication(
        application: GroupApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            groupStore.acceptApplication(
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

    fun refuseGroupApplication(
        application: GroupApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            groupStore.refuseApplication(
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

    fun clearGroupApplicationUnreadCount() {
        viewModelScope.launch {
            groupStore.clearApplicationUnreadCount(
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

class GroupApplicationViewModelFactory(
    private val groupStore: GroupStore = GroupStore.shared,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupApplicationViewModel::class.java)) {
            return GroupApplicationViewModel(groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
