package io.trtc.tuikit.chat.uikit.components.chatbot

internal data class ChatbotForwardTargetPartition(
    val allowedTargets: List<String>,
    val rejectedChatbotTargets: List<String>
)

internal object ChatbotForwardTargetPolicy {
    fun partition(conversationIDs: List<String>): ChatbotForwardTargetPartition {
        val rejected = conversationIDs.filter(ChatbotConversationPolicy::isChatbotConversation)
        return ChatbotForwardTargetPartition(
            allowedTargets = conversationIDs.filterNot(ChatbotConversationPolicy::isChatbotConversation),
            rejectedChatbotTargets = rejected
        )
    }
}
