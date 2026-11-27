package io.trtc.tuikit.chat.uikit.components.common

object ConversationIDUtil {
    const val C2C_PREFIX = "c2c_"
    const val GROUP_PREFIX = "group_"

    fun isC2C(conversationID: String): Boolean = conversationID.startsWith(C2C_PREFIX)

    fun isGroup(conversationID: String): Boolean = conversationID.startsWith(GROUP_PREFIX)

    fun fromUser(userID: String): String = "$C2C_PREFIX$userID"

    fun fromGroup(groupID: String): String = "$GROUP_PREFIX$groupID"

    fun userIdOrNull(conversationID: String): String? =
        conversationID.takeIf { isC2C(it) }
            ?.removePrefix(C2C_PREFIX)
            ?.takeIf { it.isNotEmpty() }

    fun groupIdOrNull(conversationID: String): String? =
        conversationID.takeIf { isGroup(it) }
            ?.removePrefix(GROUP_PREFIX)
            ?.takeIf { it.isNotEmpty() }

    fun userId(conversationID: String): String =
        if (isC2C(conversationID)) conversationID.removePrefix(C2C_PREFIX) else ""

    fun groupId(conversationID: String): String =
        if (isGroup(conversationID)) conversationID.removePrefix(GROUP_PREFIX) else ""
}
