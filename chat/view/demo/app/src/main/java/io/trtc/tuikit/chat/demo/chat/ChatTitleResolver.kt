package io.trtc.tuikit.chat.demo.chat

internal object ChatTitleResolver {
    private const val GROUP_CONVERSATION_ID_PREFIX = "group_"

    fun isGroupConversation(conversationID: String): Boolean {
        return conversationID.startsWith(GROUP_CONVERSATION_ID_PREFIX)
    }

    fun extractGroupID(conversationID: String): String? {
        return if (isGroupConversation(conversationID)) {
            conversationID.removePrefix(GROUP_CONVERSATION_ID_PREFIX)
        } else {
            null
        }
    }

    fun resolve(
        conversationTitle: String? = null,
        conversationID: String,
        groupName: String? = null
    ): String {
        val groupID = extractGroupID(conversationID)
        if (groupID != null) {
            val resolvedGroupName = groupName?.takeIf { it.isNotBlank() }
            if (resolvedGroupName != null) {
                return resolvedGroupName
            }
            return conversationTitle
                ?.takeIf { it.isNotBlank() && it != groupID && it != conversationID }
                .orEmpty()
        }
        val catalogName = io.trtc.tuikit.chat.demo.ai.SupportAgentCatalog
            .displayNameForConversation(conversationID)
        val fallback = catalogName ?: conversationID
        return conversationTitle.takeUnless { it.isNullOrBlank() } ?: fallback
    }
}
