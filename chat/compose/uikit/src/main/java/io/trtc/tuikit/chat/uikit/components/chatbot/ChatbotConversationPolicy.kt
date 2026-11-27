package io.trtc.tuikit.chat.uikit.components.chatbot

object ChatbotConversationPolicy {
    private const val CHATBOT_ID_PREFIX = "@RBT#"
    private const val C2C_CONVERSATION_PREFIX = "c2c_"
    private const val GROUP_CONVERSATION_PREFIX = "group_"

    @JvmStatic
    fun isChatbotID(targetID: String?): Boolean {
        return targetID?.startsWith(CHATBOT_ID_PREFIX) == true
    }

    @JvmStatic
    fun isChatbotConversation(conversationID: String?): Boolean {
        return isChatbotID(targetID(conversationID))
    }

    @JvmStatic
    fun targetID(conversationID: String?): String? {
        if (conversationID.isNullOrEmpty()) {
            return null
        }
        return when {
            conversationID.startsWith(C2C_CONVERSATION_PREFIX) ->
                conversationID.removePrefix(C2C_CONVERSATION_PREFIX)
            conversationID.startsWith(GROUP_CONVERSATION_PREFIX) ->
                conversationID.removePrefix(GROUP_CONVERSATION_PREFIX)
            else -> null
        }
    }

    internal fun isC2CConversation(conversationID: String?): Boolean {
        return conversationID?.startsWith(C2C_CONVERSATION_PREFIX) == true
    }

    internal fun isGroupConversation(conversationID: String?): Boolean {
        return conversationID?.startsWith(GROUP_CONVERSATION_PREFIX) == true
    }

    internal fun userIdOrNull(conversationID: String?): String? {
        return conversationID?.takeIf { isC2CConversation(it) }
            ?.removePrefix(C2C_CONVERSATION_PREFIX)
    }

    internal fun groupIdOrNull(conversationID: String?): String? {
        return conversationID?.takeIf { isGroupConversation(it) }
            ?.removePrefix(GROUP_CONVERSATION_PREFIX)
    }
}
