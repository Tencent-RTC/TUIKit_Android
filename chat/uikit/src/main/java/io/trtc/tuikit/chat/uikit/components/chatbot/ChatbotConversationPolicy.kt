package io.trtc.tuikit.chat.uikit.components.chatbot

internal object ChatbotConversationPolicy {
    private const val CHATBOT_ID_PREFIX = "@RBT#"
    private const val C2C_CONVERSATION_PREFIX = "c2c_"
    private const val GROUP_CONVERSATION_PREFIX = "group_"

    fun isChatbotID(targetID: String?): Boolean {
        return targetID?.startsWith(CHATBOT_ID_PREFIX) == true
    }

    fun isChatbotConversation(conversationID: String?): Boolean {
        return isChatbotID(targetID(conversationID))
    }

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
}
