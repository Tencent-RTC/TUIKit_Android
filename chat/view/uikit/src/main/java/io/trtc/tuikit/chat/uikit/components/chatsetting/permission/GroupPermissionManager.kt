package io.trtc.tuikit.chat.uikit.components.chatsetting.permission
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupType

enum class GroupPermission {
    SET_GROUP_NAME,
    SET_GROUP_AVATAR,
    SEND_MESSAGE,
    SET_DO_NOT_DISTURB,
    PIN_GROUP,
    SET_GROUP_NOTICE,
    SET_GROUP_MANAGEMENT,
    GET_GROUP_TYPE,
    SET_JOIN_GROUP_APPROVAL_TYPE,
    SET_INVITE_TO_GROUP_APPROVAL_TYPE,
    SET_GROUP_REMARK,
    SET_BACKGROUND,
    GET_GROUP_MEMBER_LIST,
    SET_GROUP_MEMBER_ROLE,
    GET_GROUP_MEMBER_INFO,
    REMOVE_GROUP_MEMBER,
    ADD_GROUP_MEMBER,
    CLEAR_HISTORY_MESSAGES,
    DELETE_AND_QUIT,
    TRANSFER_OWNER,
    DISMISS_GROUP,
    REPORT_GROUP
}

object GroupPermissionManager {

    private val permissionMatrix: Map<GroupType, Map<GroupMemberRole, Map<GroupPermission, Boolean>>> =
        buildPermissionMatrix()

    private fun buildPermissionMatrix(): Map<GroupType, Map<GroupMemberRole, Map<GroupPermission, Boolean>>> {
        return mapOf(
            GroupType.WORK to mapOf(
                GroupMemberRole.OWNER to createPermissionMap(
                    setGroupName = true, setGroupAvatar = true, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = true,
                    setGroupManagement = true, getGroupType = true,
                    setJoinGroupApprovalType = true, setInviteToGroupApprovalType = true,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = true, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = true, dismissGroup = false, reportGroup = true
                ),
                GroupMemberRole.ADMIN to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = false,
                    setGroupManagement = false, getGroupType = true,
                    setJoinGroupApprovalType = true, setInviteToGroupApprovalType = false,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = false, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                ),
                GroupMemberRole.MEMBER to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = false,
                    setGroupManagement = false, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = false,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = false, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                )
            ),
            GroupType.PUBLIC_GROUP to mapOf(
                GroupMemberRole.OWNER to createPermissionMap(
                    setGroupName = true, setGroupAvatar = true, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = true,
                    setGroupManagement = true, getGroupType = true,
                    setJoinGroupApprovalType = true, setInviteToGroupApprovalType = true,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = true, getGroupMemberInfo = true,
                    removeGroupMember = true, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = false,
                    transferOwner = true, dismissGroup = true, reportGroup = true
                ),
                GroupMemberRole.ADMIN to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = true,
                    setGroupManagement = true, getGroupType = true,
                    setJoinGroupApprovalType = true, setInviteToGroupApprovalType = true,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = true, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                ),
                GroupMemberRole.MEMBER to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = false,
                    setGroupManagement = false, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = false,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = false, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                )
            ),
            GroupType.MEETING to mapOf(
                GroupMemberRole.OWNER to createPermissionMap(
                    setGroupName = true, setGroupAvatar = true, sendMessage = true,
                    setDoNotDisturb = false, pinGroup = true, setGroupNotice = true,
                    setGroupManagement = true, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = true,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = true, getGroupMemberInfo = true,
                    removeGroupMember = true, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = false,
                    transferOwner = true, dismissGroup = true, reportGroup = true
                ),
                GroupMemberRole.ADMIN to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = false, pinGroup = true, setGroupNotice = true,
                    setGroupManagement = true, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = true,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = true, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                ),
                GroupMemberRole.MEMBER to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = false, pinGroup = true, setGroupNotice = false,
                    setGroupManagement = false, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = false,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = false, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                )
            ),
            GroupType.COMMUNITY to mapOf(
                GroupMemberRole.OWNER to createPermissionMap(
                    setGroupName = true, setGroupAvatar = true, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = true,
                    setGroupManagement = true, getGroupType = true,
                    setJoinGroupApprovalType = true, setInviteToGroupApprovalType = true,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = true, getGroupMemberInfo = true,
                    removeGroupMember = true, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = false,
                    transferOwner = true, dismissGroup = true, reportGroup = true
                ),
                GroupMemberRole.ADMIN to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = true,
                    setGroupManagement = true, getGroupType = true,
                    setJoinGroupApprovalType = true, setInviteToGroupApprovalType = true,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = true, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                ),
                GroupMemberRole.MEMBER to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = false,
                    setGroupManagement = false, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = false,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = true,
                    setMemberRole = false, getGroupMemberInfo = true,
                    removeGroupMember = false, addMember = true,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                )
            ),
            GroupType.AV_CHAT_ROOM to mapOf(
                GroupMemberRole.OWNER to createPermissionMap(
                    setGroupName = true, setGroupAvatar = true, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = true,
                    setGroupManagement = true, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = false,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = false,
                    setMemberRole = false, getGroupMemberInfo = false,
                    removeGroupMember = false, addMember = false,
                    clearHistoryMessages = true, deleteAndQuit = false,
                    transferOwner = false, dismissGroup = true, reportGroup = true
                ),
                GroupMemberRole.ADMIN to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = false,
                    setGroupManagement = false, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = false,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = false,
                    setMemberRole = false, getGroupMemberInfo = false,
                    removeGroupMember = false, addMember = false,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                ),
                GroupMemberRole.MEMBER to createPermissionMap(
                    setGroupName = false, setGroupAvatar = false, sendMessage = true,
                    setDoNotDisturb = true, pinGroup = true, setGroupNotice = false,
                    setGroupManagement = false, getGroupType = true,
                    setJoinGroupApprovalType = false, setInviteToGroupApprovalType = false,
                    setGroupRemark = true, setBackground = true, getGroupMemberList = false,
                    setMemberRole = false, getGroupMemberInfo = false,
                    removeGroupMember = false, addMember = false,
                    clearHistoryMessages = true, deleteAndQuit = true,
                    transferOwner = false, dismissGroup = false, reportGroup = true
                )
            )
        )
    }

    private fun createPermissionMap(
        setGroupName: Boolean, setGroupAvatar: Boolean, sendMessage: Boolean,
        setDoNotDisturb: Boolean, pinGroup: Boolean, setGroupNotice: Boolean,
        setGroupManagement: Boolean, getGroupType: Boolean,
        setJoinGroupApprovalType: Boolean, setInviteToGroupApprovalType: Boolean,
        setGroupRemark: Boolean, setBackground: Boolean, getGroupMemberList: Boolean,
        setMemberRole: Boolean, getGroupMemberInfo: Boolean,
        removeGroupMember: Boolean, addMember: Boolean,
        clearHistoryMessages: Boolean, deleteAndQuit: Boolean,
        transferOwner: Boolean, dismissGroup: Boolean, reportGroup: Boolean
    ): Map<GroupPermission, Boolean> {
        return mapOf(
            GroupPermission.SET_GROUP_NAME to setGroupName,
            GroupPermission.SET_GROUP_AVATAR to setGroupAvatar,
            GroupPermission.SEND_MESSAGE to sendMessage,
            GroupPermission.SET_DO_NOT_DISTURB to setDoNotDisturb,
            GroupPermission.PIN_GROUP to pinGroup,
            GroupPermission.SET_GROUP_NOTICE to setGroupNotice,
            GroupPermission.SET_GROUP_MANAGEMENT to setGroupManagement,
            GroupPermission.GET_GROUP_TYPE to getGroupType,
            GroupPermission.SET_JOIN_GROUP_APPROVAL_TYPE to setJoinGroupApprovalType,
            GroupPermission.SET_INVITE_TO_GROUP_APPROVAL_TYPE to setInviteToGroupApprovalType,
            GroupPermission.SET_GROUP_REMARK to setGroupRemark,
            GroupPermission.SET_BACKGROUND to setBackground,
            GroupPermission.GET_GROUP_MEMBER_LIST to getGroupMemberList,
            GroupPermission.SET_GROUP_MEMBER_ROLE to setMemberRole,
            GroupPermission.GET_GROUP_MEMBER_INFO to getGroupMemberInfo,
            GroupPermission.REMOVE_GROUP_MEMBER to removeGroupMember,
            GroupPermission.ADD_GROUP_MEMBER to addMember,
            GroupPermission.CLEAR_HISTORY_MESSAGES to clearHistoryMessages,
            GroupPermission.DELETE_AND_QUIT to deleteAndQuit,
            GroupPermission.TRANSFER_OWNER to transferOwner,
            GroupPermission.DISMISS_GROUP to dismissGroup,
            GroupPermission.REPORT_GROUP to reportGroup
        )
    }

    fun canPerformAction(
        groupType: GroupType,
        memberRole: GroupMemberRole,
        action: GroupPermission
    ): Boolean {
        return permissionMatrix[groupType]?.get(memberRole)?.get(action) == true
    }
}
