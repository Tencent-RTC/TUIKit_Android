package io.trtc.tuikit.chat.uikit.components.chatsetting.permission
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole

internal object GroupMemberActionPolicy {

    fun hasActionPermission(
        currentUserRole: GroupMemberRole,
        targetMemberRole: GroupMemberRole
    ): Boolean {
        return when (currentUserRole) {
            GroupMemberRole.OWNER -> targetMemberRole != GroupMemberRole.OWNER
            GroupMemberRole.ADMIN -> targetMemberRole == GroupMemberRole.MEMBER
            else -> false
        }
    }

    fun canSetAdmin(
        currentUserRole: GroupMemberRole,
        targetMemberRole: GroupMemberRole
    ): Boolean {
        return currentUserRole == GroupMemberRole.OWNER && targetMemberRole == GroupMemberRole.MEMBER
    }

    fun canRemoveAdmin(
        currentUserRole: GroupMemberRole,
        targetMemberRole: GroupMemberRole
    ): Boolean {
        return currentUserRole == GroupMemberRole.OWNER && targetMemberRole == GroupMemberRole.ADMIN
    }

    fun canRemoveMember(
        currentUserRole: GroupMemberRole,
        targetMemberRole: GroupMemberRole
    ): Boolean {
        return when (currentUserRole) {
            GroupMemberRole.OWNER -> targetMemberRole != GroupMemberRole.OWNER
            GroupMemberRole.ADMIN -> targetMemberRole == GroupMemberRole.MEMBER
            else -> false
        }
    }

    fun <T> filterRemovableMembers(
        currentUserRole: GroupMemberRole,
        members: List<T>,
        roleSelector: (T) -> GroupMemberRole
    ): List<T> {
        return members.filter { member ->
            canRemoveMember(currentUserRole, roleSelector(member))
        }
    }
}
