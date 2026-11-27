package io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel
internal object GroupMemberPaginationPolicy {

    fun shouldLoadMore(
        isLoadingMore: Boolean,
        hasMoreMembers: Boolean
    ): Boolean {
        return !isLoadingMore && hasMoreMembers
    }
}
