package io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel

import androidx.lifecycle.ViewModel
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore
import kotlinx.coroutines.flow.StateFlow

class GroupMemberListViewModel(private val groupID: String) : ViewModel() {

    private val groupMemberStore = GroupMemberStore.create(groupID)
    private val groupMemberState = groupMemberStore.state

    val members: StateFlow<List<GroupMember>>
        get() = groupMemberState.memberList

    private var isLoadingMore = false

    init {
        groupMemberStore.loadMembers(
            roleList = listOf(GroupMemberFilterRole.ALL),
            completion = object : CompletionHandler {
                override fun onSuccess() {
                }

                override fun onFailure(code: Int, desc: String) {
                }
            }
        )
    }

    fun loadMoreMembers() {
        if (!GroupMemberPaginationPolicy.shouldLoadMore(isLoadingMore, groupMemberState.hasMoreMembers.value)) {
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

    override fun onCleared() {
        super.onCleared()
    }
}
