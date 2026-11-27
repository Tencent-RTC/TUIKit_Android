package io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions

import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.message.MessageReaction

object MessageReactionPanelPolicy {
    const val COLUMNS = 6
    const val ROWS = 2
    private const val NAME_SEPARATOR = ", "

    fun displayable(reactions: List<MessageReaction>): List<MessageReaction> {
        return reactions.filter { it.reactionID.isNotBlank() && it.totalUserCount > 0 }
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
