package io.trtc.tuikit.chat.uikit.components.conversationlist.utils
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationMarkType

val ConversationInfo.isUnread: Boolean
    get() = unreadCount > 0 || conversationMarkList.contains(ConversationMarkType.unread)

val ConversationInfo.needShowBadge: Boolean
    get() = receiveOption == ReceiveMessageOption.RECEIVE

val ConversationInfo.needShowNotReceiveIcon: Boolean
    get() = receiveOption != ReceiveMessageOption.RECEIVE && groupType != GroupType.MEETING
