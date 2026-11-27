package io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore

class GroupMemberListViewModel(private val groupID: String) : ViewModel() {

    private val groupMemberStore = GroupMemberStore.create(groupID)
    private val state = groupMemberStore.state

    val members = state.memberList
    private var isLoadingMore = false

    init {
        groupMemberStore.loadMembers(roleList = listOf(GroupMemberFilterRole.ALL))
    }

    fun loadMoreMembers() {
        if (!GroupMemberPaginationPolicy.shouldLoadMore(isLoadingMore, state.hasMoreMembers.value)) {
            return
        }
        isLoadingMore = true
        groupMemberStore.loadMoreMembers(object : CompletionHandler {
            override fun onSuccess() {
                isLoadingMore = false
            }

            override fun onFailure(code: Int, desc: String) {
                isLoadingMore = false
            }
        })
    }
}

class GroupMemberListViewModelFactory(private val groupID: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupMemberListViewModel::class.java)) {
            return GroupMemberListViewModel(groupID) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
