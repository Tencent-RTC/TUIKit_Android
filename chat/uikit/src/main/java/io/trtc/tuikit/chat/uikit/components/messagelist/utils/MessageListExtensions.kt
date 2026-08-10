package io.trtc.tuikit.chat.uikit.components.messagelist.utils
import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.common.jsonData2Dictionary
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.GroupTipsInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType

val MessageInfo.groupReadCount: Int
    get() = readReceiptInfo?.readCount ?: 0

enum class MessageReadReceiptDisplayState {
    UNREAD,
    READ,
    ALL_READ,
}

val MessageInfo.isShowReadReceipt: Boolean
    get() {
        return isSentBySelf &&
            needReadReceipt &&
            status == MessageStatus.SEND_SUCCESS &&
            messageType != MessageType.TIPS
    }

fun MessageInfo.shouldShowReadReceiptIndicator(
    isContainerAllowed: Boolean = true
): Boolean {
    return isContainerAllowed && isShowReadReceipt
}

val MessageInfo.readReceiptDisplayState: MessageReadReceiptDisplayState
    get() {
        val receipt = readReceiptInfo
        if (conversationType != ConversationType.GROUP) {
            return if (receipt?.isPeerRead == true) {
                MessageReadReceiptDisplayState.READ
            } else {
                MessageReadReceiptDisplayState.UNREAD
            }
        }

        val readCount = receipt?.readCount ?: 0
        val unreadCount = receipt?.unreadCount ?: 0
        return when {
            receipt != null && unreadCount == 0 -> MessageReadReceiptDisplayState.ALL_READ
            readCount > 0 -> MessageReadReceiptDisplayState.READ
            else -> MessageReadReceiptDisplayState.UNREAD
        }
    }

val MessageInfo.isAllRead: Boolean
    get() {
        val receipt = readReceiptInfo
        return when {
            receipt == null -> false
            conversationType != ConversationType.GROUP -> receipt.isPeerRead
            else -> receipt.unreadCount == 0
        }
    }

val MessageInfo.isUnread: Boolean
    get() {
        val receipt = readReceiptInfo
        return when {
            receipt == null -> true
            conversationType != ConversationType.GROUP -> !receipt.isPeerRead
            else -> receipt.unreadCount > 0
        }
    }

val MessageInfo.senderDisplayName: String
    get() = from.nameCard?.takeIf { it.isNotBlank() }
        ?: from.friendRemark?.takeIf { it.isNotBlank() }
        ?: from.nickname?.takeIf { it.isNotBlank() }
        ?: from.userID

fun getCreateGroupDisplayString(context: Context, message: MessageInfo): String {
    val customData = (message.messagePayload as? CustomMessagePayload)?.customData
    val customInfo = jsonData2Dictionary(customData)
    val groupType = customInfo?.get("groupType").orEmpty()
    return if (groupType.equals("Community", ignoreCase = true)) {
        context.getString(R.string.message_list_community_create_tips_message)
    } else {
        context.getString(R.string.message_list_group_create_tips_message)
    }
}

fun getSystemInfoDisplayString(context: Context, groupTips: List<GroupTipsInfo>?): String {
    val tips = groupTips?.firstOrNull() ?: return ""
    return when (tips) {
        is GroupTipsInfo.JoinGroup -> context.getString(
            R.string.message_list_message_tips_join_group_format,
            tips.joinMember.displayName
        )

        is GroupTipsInfo.InviteToGroup -> context.getString(
            R.string.message_list_message_tips_invite_join_group_format,
            tips.inviter.displayName,
            tips.invitees.joinDisplayNames()
        )

        is GroupTipsInfo.QuitGroup -> context.getString(
            R.string.message_list_message_tips_leave_group_format,
            tips.quitMember.displayName
        )

        is GroupTipsInfo.KickedFromGroup -> context.getString(
            R.string.message_list_message_tips_kickoff_group_format,
            tips.opUser.displayName,
            tips.kickedMembers.joinDisplayNames()
        )

        is GroupTipsInfo.SetGroupAdmin -> context.getString(
            R.string.message_list_message_tips_set_admin_format,
            tips.setAdminMembers.joinDisplayNames()
        )

        is GroupTipsInfo.CancelGroupAdmin -> context.getString(
            R.string.message_list_message_tips_cancel_admin_format,
            tips.cancelAdminMembers.joinDisplayNames()
        )

        is GroupTipsInfo.ChangeGroupName -> context.getString(
            R.string.message_list_message_tips_edit_group_name_format,
            tips.opUser.displayName,
            tips.groupName
        )

        is GroupTipsInfo.ChangeGroupAvatar -> context.getString(
            R.string.message_list_message_tips_edit_group_avatar_format,
            tips.opUser.displayName
        )

        is GroupTipsInfo.ChangeGroupNotification -> {
            val text = tips.groupNotification
            if (text.isBlank()) {
                context.getString(
                    R.string.message_list_message_tips_delete_group_announce_format,
                    tips.opUser.displayName
                )
            } else {
                context.getString(
                    R.string.message_list_message_tips_edit_group_announce_format,
                    tips.opUser.displayName,
                    text
                )
            }
        }

        is GroupTipsInfo.ChangeGroupIntroduction -> context.getString(
            R.string.message_list_message_tips_edit_group_intro_format,
            tips.opUser.displayName,
            tips.groupIntroduction
        )

        is GroupTipsInfo.ChangeGroupOwner -> context.getString(
            R.string.message_list_message_tips_edit_group_owner_format,
            tips.opUser.displayName,
            tips.groupOwner
        )

        is GroupTipsInfo.ChangeGroupMuteAll -> {
            val resId = if (tips.isMuteAll) {
                R.string.message_list_set_mute_all_format
            } else {
                R.string.message_list_unmute_all_format
            }
            context.getString(resId, tips.opUser.displayName)
        }

        is GroupTipsInfo.ChangeJoinGroupApproval -> context.getString(
            R.string.message_list_message_tips_edit_group_add_opt_format,
            tips.opUser.displayName,
            tips.groupJoinOption.displayText(context)
        )

        is GroupTipsInfo.ChangeInviteToGroupApproval -> context.getString(
            R.string.message_list_message_tips_edit_group_invite_opt_format,
            tips.opUser.displayName,
            tips.groupInviteOption.displayText(context)
        )

        is GroupTipsInfo.MuteGroupMember -> {
            val actionText = if (tips.muteTime > 0) {
                context.getString(R.string.message_list_message_tips_mute)
            } else {
                context.getString(R.string.message_list_message_tips_unmute)
            }
            "${tips.mutedGroupMembers.joinDisplayNames()} $actionText"
        }

        is GroupTipsInfo.PinGroupMessage -> context.getString(
            R.string.message_list_message_tips_group_pin_message,
            tips.opUser.displayName
        )

        is GroupTipsInfo.UnpinGroupMessage -> context.getString(
            R.string.message_list_message_tips_group_unpin_message,
            tips.opUser.displayName
        )

        GroupTipsInfo.Unknown -> ""
    }
}

private fun List<GroupMember>.joinDisplayNames(): String {
    return joinToString(separator = ", ") { it.displayName }
}

private fun GroupJoinOption.displayText(context: Context): String {
    return when (this) {
        GroupJoinOption.ANY -> context.getString(R.string.message_list_group_profile_auto_approval)
        GroupJoinOption.FORBID -> context.getString(R.string.message_list_group_profile_join_disable)
        GroupJoinOption.AUTH -> context.getString(R.string.message_list_group_profile_admin_approve)
        else -> context.getString(R.string.message_list_group_profile_admin_approve)
    }
}

private fun GroupInviteOption.displayText(context: Context): String {
    return when (this) {
        GroupInviteOption.ANY -> context.getString(R.string.message_list_group_profile_auto_approval)
        GroupInviteOption.FORBID -> context.getString(R.string.message_list_group_profile_invite_disable)
        GroupInviteOption.AUTH -> context.getString(R.string.message_list_group_profile_admin_approve)
        else -> context.getString(R.string.message_list_group_profile_admin_approve)
    }
}
