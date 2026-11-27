package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import androidx.annotation.StringRes
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.message.MessageReaction
import io.trtc.tuikit.chat.uikit.compose.R

internal object MessageReactionActionPolicy {
    private const val ERR_SVR_MSG_REACTION_COUNT_LIMIT = 23005
    private const val ERR_SVR_MSG_REACTION_USER_COUNT_LIMIT = 23006
    private const val NAME_SEPARATOR = ", "

    @StringRes
    fun resolveAddFailureMessageResId(errorCode: Int): Int {
        return when (errorCode) {
            ERR_SVR_MSG_REACTION_COUNT_LIMIT -> R.string.message_list_reaction_count_limit
            ERR_SVR_MSG_REACTION_USER_COUNT_LIMIT -> R.string.message_list_reaction_user_count_limit
            else -> R.string.message_list_reaction_failed
        }
    }

    fun needsFullUserFetch(reaction: MessageReaction): Boolean {
        return reaction.reactionID.isNotBlank() &&
            reaction.totalUserCount > reaction.partialUserList.size
    }

    fun reactionSnapshotKey(msgID: String, reaction: MessageReaction): String {
        val partialIds = reaction.partialUserList.joinToString(",") { it.userID }
        return "$msgID|${reaction.reactionID}|${reaction.totalUserCount}|${reaction.reactedByMyself}|$partialIds"
    }

    fun reactionKeyPrefix(msgID: String, reactionID: String): String {
        return "$msgID|$reactionID|"
    }

    fun buildUsersLabel(
        reaction: MessageReaction,
        users: List<UserProfile> = reaction.partialUserList
    ): String {
        val totalUserCount = reaction.totalUserCount
        if (totalUserCount <= 0) {
            return ""
        }
        val sourceUsers = if (users.isNotEmpty()) users else reaction.partialUserList
        val seenUserIds = HashSet<String>()
        val names = ArrayList<String>(sourceUsers.size)
        sourceUsers.forEach { user ->
            val userId = user.userID.trim()
            if (userId.isEmpty() || seenUserIds.add(userId)) {
                val displayName = user.nickname?.trim()?.takeIf { it.isNotEmpty() }
                    ?: userId.takeIf { it.isNotEmpty() }
                if (!displayName.isNullOrEmpty()) {
                    names.add(displayName)
                }
            }
        }
        if (names.isEmpty()) {
            return totalUserCount.toString()
        }
        return names.joinToString(separator = NAME_SEPARATOR)
    }
}
