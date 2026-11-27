package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermission
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupType

internal data class GroupChatSettingUiState(
    val groupID: String,
    val groupName: String,
    val avatarURL: String,
    val notification: String,
    val groupType: GroupType,
    val selfRole: GroupMemberRole,
    val joinOption: GroupJoinOption,
    val inviteOption: GroupInviteOption,
    val nameCard: String?,
    val memberCount: Int,
    val groupMembers: List<GroupMember>,
    val isNotDisturb: Boolean,
    val isPinned: Boolean,
    val chatBackgroundImageUri: String?,
    val permissions: GroupChatSettingPermissionSnapshot
) {
    val headerDisplayName: String
        get() = groupName.ifEmpty { groupID }

    val hasCustomChatBackground: Boolean
        get() = !chatBackgroundImageUri.isNullOrBlank()
}

internal data class GroupChatSettingPermissionSnapshot(
    val canEditGroupName: Boolean,
    val canEditGroupAvatar: Boolean,
    val canEditGroupNotice: Boolean,
    val canOpenGroupManagement: Boolean,
    val canEditJoinOption: Boolean,
    val canEditInviteOption: Boolean,
    val canEditSelfNameCard: Boolean,
    val canToggleDoNotDisturb: Boolean,
    val canTogglePinned: Boolean,
    val canAddMember: Boolean,
    val canRemoveMember: Boolean
)

internal fun createGroupChatSettingUiState(
    groupID: String,
    groupName: String,
    avatarURL: String,
    notification: String,
    groupType: GroupType,
    selfRole: GroupMemberRole,
    joinOption: GroupJoinOption,
    inviteOption: GroupInviteOption,
    nameCard: String?,
    memberCount: Int,
    groupMembers: List<GroupMember>,
    isNotDisturb: Boolean,
    isPinned: Boolean,
    chatBackgroundImageUri: String?,
    canPerformAction: (GroupType, GroupMemberRole, GroupPermission) -> Boolean
): GroupChatSettingUiState {
    return GroupChatSettingUiState(
        groupID = groupID,
        groupName = groupName,
        avatarURL = avatarURL,
        notification = notification,
        groupType = groupType,
        selfRole = selfRole,
        joinOption = joinOption,
        inviteOption = inviteOption,
        nameCard = nameCard,
        memberCount = memberCount,
        groupMembers = groupMembers,
        isNotDisturb = isNotDisturb,
        isPinned = isPinned,
        chatBackgroundImageUri = chatBackgroundImageUri,
        permissions = createPermissionSnapshot(
            groupType = groupType,
            selfRole = selfRole,
            inviteOption = inviteOption,
            canPerformAction = canPerformAction
        )
    )
}

private fun createPermissionSnapshot(
    groupType: GroupType,
    selfRole: GroupMemberRole,
    inviteOption: GroupInviteOption,
    canPerformAction: (GroupType, GroupMemberRole, GroupPermission) -> Boolean
): GroupChatSettingPermissionSnapshot {
    val canAddMember = canPerformAction(groupType, selfRole, GroupPermission.ADD_GROUP_MEMBER) &&
        inviteOption != GroupInviteOption.FORBID

    return GroupChatSettingPermissionSnapshot(
        canEditGroupName = canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_NAME),
        canEditGroupAvatar = canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_AVATAR),
        canEditGroupNotice = canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_NOTICE),
        canOpenGroupManagement = canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_MANAGEMENT),
        canEditJoinOption = canPerformAction(groupType, selfRole, GroupPermission.SET_JOIN_GROUP_APPROVAL_TYPE),
        canEditInviteOption = canPerformAction(
            groupType,
            selfRole,
            GroupPermission.SET_INVITE_TO_GROUP_APPROVAL_TYPE
        ),
        canEditSelfNameCard = canPerformAction(groupType, selfRole, GroupPermission.SET_GROUP_REMARK),
        canToggleDoNotDisturb = canPerformAction(groupType, selfRole, GroupPermission.SET_DO_NOT_DISTURB),
        canTogglePinned = canPerformAction(groupType, selfRole, GroupPermission.PIN_GROUP),
        canAddMember = canAddMember,
        canRemoveMember = canPerformAction(groupType, selfRole, GroupPermission.REMOVE_GROUP_MEMBER)
    )
}
