package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.replaceEmojiKeysWithNames
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.model.CallStreamMediaType
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.getCallMessageDisplayString
import android.content.Context
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.GroupTipsInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.TipsMessagePayload
import java.util.Locale

const val FORWARD_MSG_ABSTRACT_LENGTH = 98

object MessageUtils {

    fun getSystemInfoDisplayString(groupTips: List<GroupTipsInfo>?): String {
        if (groupTips.isNullOrEmpty()) return ""
        val displayText = groupTips
            .map { formatGroupTip(it) }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "")
        return displayText.ifEmpty { ContextProvider.appContext.getString(R.string.message_list_unknown) }
    }

    private fun formatGroupTip(tips: GroupTipsInfo): String {
        val context = ContextProvider.appContext
        return when (tips) {
            is GroupTipsInfo.JoinGroup -> context.getString(
                R.string.message_list_message_tips_join_group_format,
                tips.joinMember.displayName()
            )

            is GroupTipsInfo.InviteToGroup -> context.getString(
                R.string.message_list_message_tips_invite_join_group_format,
                tips.inviter.displayName(),
                tips.invitees.joinDisplayNames()
            )

            is GroupTipsInfo.QuitGroup -> context.getString(
                R.string.message_list_message_tips_leave_group_format,
                tips.quitMember.displayName()
            )

            is GroupTipsInfo.KickedFromGroup -> context.getString(
                R.string.message_list_message_tips_kickoff_group_format,
                tips.opUser.displayName(),
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

            is GroupTipsInfo.MuteGroupMember -> {
                val memberShowName = tips.mutedGroupMembers.joinDisplayNames()
                val actualShowName =
                    if (tips.isSelfMuted) context.getString(R.string.message_list_you) else memberShowName
                if (tips.muteTime == 0L) {
                    "$actualShowName ${context.getString(R.string.message_list_message_tips_unmute)}"
                } else {
                    val duration = formatMuteTime(tips.muteTime)
                    "$actualShowName ${context.getString(R.string.message_list_message_tips_mute)} $duration"
                }
            }

            is GroupTipsInfo.PinGroupMessage -> context.getString(
                R.string.message_list_message_tips_group_pin_message,
                tips.opUser.displayName()
            )

            is GroupTipsInfo.UnpinGroupMessage -> context.getString(
                R.string.message_list_message_tips_group_unpin_message,
                tips.opUser.displayName()
            )

            is GroupTipsInfo.ChangeGroupName -> context.getString(
                R.string.message_list_message_tips_edit_group_name_format,
                tips.opUser.displayName(),
                tips.groupName
            )

            is GroupTipsInfo.ChangeGroupIntroduction -> context.getString(
                R.string.message_list_message_tips_edit_group_intro_format,
                tips.opUser.displayName(),
                tips.groupIntroduction
            )

            is GroupTipsInfo.ChangeGroupNotification -> {
                val text = tips.groupNotification
                if (text.isBlank()) {
                    context.getString(
                        R.string.message_list_message_tips_delete_group_announce_format,
                        tips.opUser.displayName()
                    )
                } else {
                    context.getString(
                        R.string.message_list_message_tips_edit_group_announce_format,
                        tips.opUser.displayName(),
                        text
                    )
                }
            }

            is GroupTipsInfo.ChangeGroupAvatar -> context.getString(
                R.string.message_list_message_tips_edit_group_avatar_format,
                tips.opUser.displayName()
            )

            is GroupTipsInfo.ChangeGroupOwner -> context.getString(
                R.string.message_list_message_tips_edit_group_owner_format,
                tips.opUser.displayName(),
                tips.groupOwner
            )

            is GroupTipsInfo.ChangeGroupMuteAll -> context.getString(
                if (tips.isMuteAll) R.string.message_list_set_mute_all_format else R.string.message_list_unmute_all_format,
                tips.opUser.displayName()
            )

            is GroupTipsInfo.ChangeJoinGroupApproval -> context.getString(
                R.string.message_list_message_tips_edit_group_add_opt_format,
                tips.opUser.displayName(),
                tips.groupJoinOption.displayText(context)
            )

            is GroupTipsInfo.ChangeInviteToGroupApproval -> context.getString(
                R.string.message_list_message_tips_edit_group_invite_opt_format,
                tips.opUser.displayName(),
                tips.groupInviteOption.displayText(context)
            )

            GroupTipsInfo.Unknown -> ""
        }
    }

    @JvmOverloads
    fun getCallStreamMediaType(
        messageInfo: MessageInfo?,
        conversationID: String? = null,
        config: MessageListConfigProtocol? = null
    ): CallStreamMediaType? {
        if (messageInfo == null) return null
        if (messageInfo.messageType != MessageType.CUSTOM) return null
        if (messageInfo.status == MessageStatus.REVOKED || messageInfo.status == MessageStatus.VIOLATION) return null
        val context = ContextProvider.appContext
        if (MessageListMessageSummaryRegistry.resolveSummary(messageInfo, conversationID, context, config) != null) {
            return null
        }
        val callModel = CallMessageParser.parse(messageInfo) ?: return null
        if (getCallMessageDisplayString(context, messageInfo, callModel).isEmpty()) return null
        return callModel.streamMediaType.takeIf { it != CallStreamMediaType.UNKNOWN }
    }

    @JvmOverloads
    fun getMessageAbstract(
        messageInfo: MessageInfo?,
        conversationID: String? = null,
        config: MessageListConfigProtocol? = null
    ): String {
        val context = ContextProvider.appContext
        if (messageInfo == null) return ""
        if (messageInfo.status == MessageStatus.REVOKED) {
            return RecalledMessageDisplayPolicy.format(context, messageInfo).orEmpty()
        }
        if (messageInfo.status == MessageStatus.VIOLATION) {
            return context.getString(R.string.message_list_violation_message)
        }
        MessageListMessageSummaryRegistry.resolveSummary(messageInfo, conversationID, context, config)?.let { return it }
        return when (messageInfo.messageType) {
            MessageType.TEXT -> (messageInfo.messagePayload as? TextMessagePayload)?.text ?: ""
            MessageType.IMAGE -> context.getString(R.string.message_list_message_type_image)
            MessageType.AUDIO -> {
                val voiceLabel = context.getString(R.string.message_list_message_type_voice)
                val duration = (messageInfo.messagePayload as? AudioMessagePayload)?.audioDuration ?: 0
                if (duration > 0) "$voiceLabel $duration\"" else voiceLabel
            }
            MessageType.FILE -> {
                val filePrefix = context.getString(R.string.message_list_message_type_file)
                val fileName = (messageInfo.messagePayload as? FileMessagePayload)?.fileName.orEmpty()
                if (fileName.isEmpty()) filePrefix else "$filePrefix $fileName"
            }
            MessageType.VIDEO -> context.getString(R.string.message_list_message_type_video)
            MessageType.FACE -> context.getString(R.string.message_list_message_type_animate_emoji)
            MessageType.CUSTOM -> {
                val callModel = CallMessageParser.parse(messageInfo)
                if (callModel != null) {
                    getCallMessageDisplayString(
                        context = context,
                        message = messageInfo,
                        callModel = callModel
                    )
                } else {
                    formatGroupCreateOrUnsupported(context, messageInfo)
                }
            }

            MessageType.TIPS -> getSystemInfoDisplayString((messageInfo.messagePayload as? TipsMessagePayload)?.groupTips)
            MessageType.MERGED -> context.getString(R.string.message_list_message_type_merged)
            else -> ""
        }
    }

    private fun formatGroupCreateOrUnsupported(context: Context, messageInfo: MessageInfo): String {
        val customInfo = jsonData2Dictionary((messageInfo.messagePayload as? CustomMessagePayload)?.customData)
        if (customInfo == null || customInfo["businessID"] != "group_create") {
            return context.getString(R.string.message_list_message_tips_unsupport_custom_message)
        }
        return getCreateGroupDisplayString(context, messageInfo)
    }

    fun formatMuteTime(seconds: Long): String {
        val context = ContextProvider.appContext
        if (seconds <= 0) return ""
        var timeStr =
            context.resources.getQuantityString(R.plurals.message_list_second, seconds.toInt(), seconds.toInt())
        if (seconds > 60) {
            val second = seconds % 60
            var min = seconds / 60
            timeStr = context.getString(R.string.message_list_min_second, min, second)
            if (min > 60) {
                min = (seconds / 60) % 60
                val hour = (seconds / 60) / 60
                timeStr = context.getString(R.string.message_list_hour_min_second, hour, min, second)
                if (hour % 24 == 0L) {
                    val day = ((seconds / 60) / 60) / 24
                    timeStr = context.resources.getQuantityString(R.plurals.message_list_day, day.toInt(), day.toInt())
                } else if (hour > 24) {
                    val newHour = ((seconds / 60) / 60) % 24
                    val day = ((seconds / 60) / 60) / 24
                    timeStr = context.getString(R.string.message_list_day_hour_min_second, day, newHour, min, second)
                }
            }
        }
        return timeStr
    }

}

fun jsonData2Dictionary(jsonData: String?): Map<String, Any>? {
    if (jsonData.isNullOrEmpty()) return null

    return try {
        val type = TypeToken.getParameterized(Map::class.java, String::class.java, Any::class.java).type
        Gson().fromJson(jsonData, type)
    } catch (e: Exception) {
        Log.e("Util", "Gson conversion failed", e)
        null
    }
}

private fun GroupMember.displayName(): String {
    return nameCard?.takeIf { it.isNotBlank() }
        ?: friendRemark?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: userID
}

private fun List<GroupMember>.joinDisplayNames(): String {
    return joinToString(separator = ", ") { it.displayName() }
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

fun isGroupConversation(conversationID: String): Boolean {
    return conversationID.startsWith("group_")
}

/**
 * Align emoji string to prevent cutting emoji in the middle
 * @param userName User name for calculating prefix length
 * @param text Text to align
 * @return Aligned text that won't cut emoji in the middle
 */
fun alignEmojiString(userName: String, text: String?): String {
    if (text.isNullOrEmpty()) return ""

    val emojiKeyList = EmojiManager.littleEmojiKeyList
    val separator = "\u202C:"
    val prefixLength = userName.length + separator.length
    val maxTextLength = FORWARD_MSG_ABSTRACT_LENGTH - prefixLength

    if (maxTextLength <= 0) return ""
    if (text.length <= maxTextLength) return text

    var safeCutPosition = maxTextLength

    for (emojiKey in emojiKeyList) {
        val emojiLength = emojiKey.length
        if (emojiLength <= 1) continue

        val searchStart = maxOf(0, maxTextLength - emojiLength + 1)
        val searchEnd = minOf(text.length, maxTextLength + emojiLength - 1)

        for (i in searchStart until searchEnd) {
            if (i + emojiLength <= text.length) {
                val substring = text.substring(i, i + emojiLength)
                if (substring == emojiKey) {
                    val emojiEnd = i + emojiLength
                    if (emojiEnd > maxTextLength && i < maxTextLength) {
                        safeCutPosition = minOf(safeCutPosition, i)
                    }
                }
            }
        }
    }

    return text.substring(0, safeCutPosition)
}

/**
 * Get message abstract text based on message type
 * @param message MessageInfo to get abstract from
 * @return Abstract text for the message
 */
fun getMessageTypeAbstract(message: MessageInfo): String {
    val context = ContextProvider.appContext
    return when (message.messageType) {
        MessageType.TEXT -> replaceEmojiKeysWithNames((message.messagePayload as? TextMessagePayload)?.text.orEmpty())
        MessageType.FACE -> context.getString(R.string.message_list_message_type_animate_emoji)
        MessageType.AUDIO -> context.getString(R.string.message_list_message_type_voice)
        MessageType.IMAGE -> context.getString(R.string.message_list_message_type_image)
        MessageType.VIDEO -> context.getString(R.string.message_list_message_type_video)
        MessageType.FILE -> context.getString(R.string.message_list_message_type_file)
        MessageType.MERGED -> context.getString(R.string.message_list_merge_message)
        else -> ""
    }
}

/**
 * Build forward message abstract item
 * @param userName User display name
 * @param messageAbstract Message abstract text
 * @return Formatted abstract string
 */
fun buildForwardAbstractItem(userName: String, messageAbstract: String): String {
    val alignedAbstract = alignEmojiString(userName, messageAbstract)
    return String.format(Locale.US, "%1\$s\u202C:%2\$s", userName, alignedAbstract)
}

fun isGroupChat(conversationID: String): Boolean {
    return conversationID.startsWith("group_")
}