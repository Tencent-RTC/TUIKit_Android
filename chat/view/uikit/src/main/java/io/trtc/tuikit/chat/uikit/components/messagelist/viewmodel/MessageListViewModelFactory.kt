package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore

class MessageListViewModelFactory(
    private val messageListStore: MessageListStore,
    private val conversationID: String,
    private val locateMessage: MessageInfo? = null,
    private val messageListConfig: MessageListConfigProtocol = ChatMessageListConfig(),
    private val enableMediaPreviewBoundaryLoading: Boolean = true,
    private val reverseMediaPreviewMessageOrder: Boolean = true
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageListViewModel::class.java)) {
            return MessageListViewModel(
                messageListStore = messageListStore,
                conversationID = conversationID,
                locateMessage = locateMessage,
                messageListConfig = messageListConfig,
                enableMediaPreviewBoundaryLoading = enableMediaPreviewBoundaryLoading,
                reverseMediaPreviewMessageOrder = reverseMediaPreviewMessageOrder
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
