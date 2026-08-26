package io.trtc.tuikit.chat.uikit.components.chatbot

import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ChatbotConversationController(
    val conversationID: String,
    private val interruptAction: ChatbotInterruptAction = ChatbotInterruptSender(conversationID),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000L }
) {
    private val mutableState = MutableStateFlow<ChatbotConversationState>(
        ChatbotConversationState.Idle
    )
    private var knownVisualMessageIDs: Set<String> = emptySet()
    private var waitingBaselineMessageIDs: Set<String> = emptySet()

    val state: StateFlow<ChatbotConversationState> = mutableState.asStateFlow()

    val isActive: Boolean
        get() = mutableState.value != ChatbotConversationState.Idle

    val shouldShowPlaceholder: Boolean
        get() = mutableState.value is ChatbotConversationState.Waiting

    fun shouldShowPlaceholder(messages: List<MessageInfo>): Boolean {
        if (mutableState.value !is ChatbotConversationState.Waiting) {
            return false
        }
        return messages.asReversed().firstParsedMessage { message, data ->
            message.msgID !in waitingBaselineMessageIDs && data.isVisualResponse()
        } == null
    }

    fun onTextMessageSent() {
        waitingBaselineMessageIDs = knownVisualMessageIDs
        dispatch(ChatbotConversationEvent.TextMessageSent(nowSeconds()))
    }

    fun onMessagesChanged(messages: List<MessageInfo>) {
        when (val current = mutableState.value) {
            ChatbotConversationState.Idle -> restoreFromHistory(messages)
            is ChatbotConversationState.Waiting -> {
                val response = messages.asReversed().firstParsedMessage { message, data ->
                    message.msgID !in waitingBaselineMessageIDs &&
                        data.isVisualResponse()
                }
                if (response != null) {
                    dispatch(
                        ChatbotConversationEvent.BotMessageReceived(
                            response.first.msgID,
                            response.second
                        )
                    )
                }
            }
            is ChatbotConversationState.Streaming -> {
                val response = messages.asReversed().firstParsedMessage { message, data ->
                    message.msgID == current.messageID && data.isVisualResponse()
                }
                if (response != null) {
                    dispatch(
                        ChatbotConversationEvent.BotMessageReceived(
                            response.first.msgID,
                            response.second
                        )
                    )
                }
            }
        }
        knownVisualMessageIDs = messages.mapNotNull { message ->
            val data = ChatbotMessageProtocol.parse(message)
            message.msgID.takeIf { data?.isVisualResponse() == true }
        }.toSet()
    }

    fun sendInterrupt(completion: CompletionHandler? = null): Boolean {
        val streaming = mutableState.value as? ChatbotConversationState.Streaming
            ?: return false
        return interruptAction.send(
            messageID = streaming.messageID,
            fallbackMsgKey = streaming.msgKey,
            completion = completion
        )
    }

    fun clear() {
        knownVisualMessageIDs = emptySet()
        waitingBaselineMessageIDs = emptySet()
        dispatch(ChatbotConversationEvent.Cleared)
    }

    private fun restoreFromHistory(messages: List<MessageInfo>) {
        val response = messages.asReversed().firstParsedMessage { _, data ->
            data.isVisualResponse()
        }
        dispatch(
            ChatbotConversationEvent.HistoryRestored(
                messageID = response?.first?.msgID,
                data = response?.second
            )
        )
    }

    private fun dispatch(event: ChatbotConversationEvent) {
        mutableState.value = ChatbotConversationReducer.reduce(mutableState.value, event)
    }

    private fun ChatbotMessageData.isVisualResponse(): Boolean {
        return source == ChatbotMessageSource.FLOW || source == ChatbotMessageSource.ERROR
    }

    private inline fun List<MessageInfo>.firstParsedMessage(
        predicate: (MessageInfo, ChatbotMessageData) -> Boolean
    ): Pair<MessageInfo, ChatbotMessageData>? {
        for (message in this) {
            val data = ChatbotMessageProtocol.parse(message) ?: continue
            if (predicate(message, data)) {
                return message to data
            }
        }
        return null
    }
}
