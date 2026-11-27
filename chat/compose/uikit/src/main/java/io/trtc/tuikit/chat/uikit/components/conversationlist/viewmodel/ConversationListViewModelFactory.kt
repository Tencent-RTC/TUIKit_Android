package io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ChatConversationActionConfig
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ConversationActionConfigProtocol
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore

class ConversationListViewModelFactory(
    private val conversationListStore: ConversationListStore = ConversationListStore.create(),
    private val conversationActionConfig: ConversationActionConfigProtocol = ChatConversationActionConfig()
) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConversationListViewModel::class.java)) {
            return ConversationListViewModel(conversationListStore, conversationActionConfig) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}