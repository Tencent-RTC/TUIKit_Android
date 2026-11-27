package io.trtc.tuikit.chat.uikit.components.search.utils
import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.getCreateGroupDisplayString
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.getSystemInfoDisplayString
import io.trtc.tuikit.chat.uikit.components.common.jsonData2Dictionary
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.TipsMessagePayload
import io.trtc.tuikit.atomicxcore.api.search.FriendSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.GroupSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem

val FriendSearchInfo.userAvatarURL: String
    get() = userInfo?.avatarURL ?: ""

val GroupSearchInfo.displayName: String
    get() = groupName?.takeIf { it.isNotEmpty() } ?: groupID

val MessageSearchResultItem.displayName: String
    get() = conversationShowName.ifEmpty { conversationID }

val MessageInfo.messageSender: String
    get() = listOfNotNull(
        from.nameCard,
        from.nickname,
        from.friendRemark,
        from.userID
    ).firstOrNull { it.isNotEmpty() } ?: ""

val MessageInfo.messageSenderAvatarUrl: String
    get() = from.avatarURL ?: ""

fun MessageInfo.getMessageAbstract(context: Context): String {
    return when (messageType) {
        MessageType.TEXT -> {
            (messagePayload as? TextMessagePayload)?.text ?: ""
        }
        MessageType.IMAGE -> context.getString(R.string.message_list_message_type_image)
        MessageType.AUDIO -> context.getString(R.string.message_list_message_type_voice)
        MessageType.FILE -> context.getString(R.string.message_list_message_type_file)
        MessageType.VIDEO -> context.getString(R.string.message_list_message_type_video)
        MessageType.FACE -> context.getString(R.string.message_list_message_type_animate_emoji)
        MessageType.CUSTOM -> {
            val payload = messagePayload as? CustomMessagePayload
            val customData = payload?.customData
            val customInfo = jsonData2Dictionary(customData)
            if (customInfo?.get("businessID") == "group_create") {
                getCreateGroupDisplayString(context, this)
            } else {
                payload?.description?.takeIf { it.isNotEmpty() }
                    ?: customData?.takeIf { it.isNotEmpty() }
                    ?: context.getString(R.string.message_list_message_tips_unsupport_custom_message)
            }
        }
        MessageType.TIPS -> {
            getSystemInfoDisplayString(context, (messagePayload as? TipsMessagePayload)?.groupTips)
        }
        MessageType.MERGED -> context.getString(R.string.message_list_message_type_merged)
        else -> ""
    }
}
