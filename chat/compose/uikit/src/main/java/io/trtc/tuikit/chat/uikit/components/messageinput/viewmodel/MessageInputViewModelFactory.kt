package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore

class MessageInputViewModelFactory(
    val messageInputStore: MessageInputStore,
    private val conversationID: String,
    private val messageInputConfig: MessageInputConfigProtocol = ChatMessageInputConfig()
) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MessageInputViewModel(messageInputStore, conversationID, messageInputConfig) as T
    }
}