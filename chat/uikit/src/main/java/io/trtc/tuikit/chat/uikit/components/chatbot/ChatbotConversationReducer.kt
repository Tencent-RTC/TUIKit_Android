package io.trtc.tuikit.chat.uikit.components.chatbot

internal sealed interface ChatbotConversationState {
    data object Idle : ChatbotConversationState

    data class Waiting(
        val requestTimestampSeconds: Long
    ) : ChatbotConversationState

    data class Streaming(
        val messageID: String,
        val msgKey: String
    ) : ChatbotConversationState
}

internal sealed interface ChatbotConversationEvent {
    data class TextMessageSent(
        val timestampSeconds: Long
    ) : ChatbotConversationEvent

    data class BotMessageReceived(
        val messageID: String,
        val data: ChatbotMessageData
    ) : ChatbotConversationEvent

    data class HistoryRestored(
        val messageID: String?,
        val data: ChatbotMessageData?
    ) : ChatbotConversationEvent

    data object Cleared : ChatbotConversationEvent
}

internal object ChatbotConversationReducer {
    fun reduce(
        current: ChatbotConversationState,
        event: ChatbotConversationEvent
    ): ChatbotConversationState {
        return when (event) {
            is ChatbotConversationEvent.TextMessageSent -> {
                ChatbotConversationState.Waiting(event.timestampSeconds)
            }
            is ChatbotConversationEvent.BotMessageReceived -> {
                reduceMessage(current, event.messageID, event.data)
            }
            is ChatbotConversationEvent.HistoryRestored -> {
                val messageID = event.messageID
                val data = event.data
                if (
                    messageID != null &&
                    data?.source == ChatbotMessageSource.FLOW &&
                    !data.isFinished
                ) {
                    ChatbotConversationState.Streaming(messageID, data.msgKey)
                } else {
                    ChatbotConversationState.Idle
                }
            }
            ChatbotConversationEvent.Cleared -> ChatbotConversationState.Idle
        }
    }

    private fun reduceMessage(
        current: ChatbotConversationState,
        messageID: String,
        data: ChatbotMessageData
    ): ChatbotConversationState {
        return when (data.source) {
            ChatbotMessageSource.FLOW -> {
                if (data.isFinished) {
                    ChatbotConversationState.Idle
                } else {
                    ChatbotConversationState.Streaming(messageID, data.msgKey)
                }
            }
            ChatbotMessageSource.ERROR -> ChatbotConversationState.Idle
            ChatbotMessageSource.INTERRUPT,
            ChatbotMessageSource.UNKNOWN -> current
        }
    }
}
