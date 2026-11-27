package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import android.content.Context
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.chat.uikit.compose.R

val MessageInfo.isShowReadReceipt: Boolean
    get() = isSentBySelf && needReadReceipt && status == MessageStatus.SEND_SUCCESS && messageType != MessageType.TIPS

fun MessageInfo.shouldShowReadReceiptIndicator(isContainerAllowed: Boolean = true): Boolean {
    return isContainerAllowed && isShowReadReceipt
}

val MessageInfo.senderDisplayName: String
    get() = from.nameCard?.takeIf { it.isNotBlank() }
        ?: from.friendRemark?.takeIf { it.isNotBlank() }
        ?: from.nickname?.takeIf { it.isNotBlank() }
        ?: from.userID

val MessageInfo.isAllRead: Boolean
    get() = when {
        readReceiptInfo == null -> false
        conversationType != ConversationType.GROUP -> readReceiptInfo?.isPeerRead == true
        else -> readReceiptInfo?.unreadCount == 0
    }

val MessageInfo.isUnread: Boolean
    get() = when {
        readReceiptInfo == null -> true
        conversationType != ConversationType.GROUP -> readReceiptInfo?.isPeerRead == false
        else -> readReceiptInfo?.readCount == 0
    }

fun getCreateGroupDisplayString(context: Context, message: MessageInfo): String {
    val customInfo = jsonData2Dictionary((message.messagePayload as? CustomMessagePayload)?.customData)
    val groupType = customInfo?.get("groupType")?.toString().orEmpty()
    // Senders only carry "cmd", so "groupType" stays empty for locally created groups.
    val isCommunity = groupType.equals("Community", ignoreCase = true) ||
        (customInfo?.get("cmd") as? Number)?.toDouble() == 1.0
    val creatorName = message.senderDisplayName.takeIf { it.isNotBlank() }
    if (message.isSentBySelf || creatorName == null) {
        return if (isCommunity) {
            context.getString(R.string.message_list_community_create_tips_message)
        } else {
            context.getString(R.string.message_list_group_create_tips_message)
        }
    }
    return if (isCommunity) {
        context.getString(R.string.message_list_community_create_tips_message_format, creatorName)
    } else {
        context.getString(R.string.message_list_group_create_tips_message_format, creatorName)
    }
}
