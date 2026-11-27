package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.atomicxcore.api.message.MediaQuality
import io.trtc.tuikit.atomicxcore.api.message.MergedMessageListCompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MergedMessageDetailViewModel(
    val messageListStore: MessageListStore,
    val messageInfo: MessageInfo
) : ViewModel() {

    private val _messageList = MutableStateFlow<List<MessageInfo>>(emptyList())
    val messageList: StateFlow<List<MessageInfo>> = _messageList.asStateFlow()

    init {
        MessageActionStore.create(messageInfo).downloadMergedMessageList(
            object : MergedMessageListCompletionHandler {
                override fun onSuccess(messageList: List<MessageInfo>) {
                    _messageList.value = normalizeMessageList(messageList)
                }

                override fun onFailure(code: Int, desc: String) {
                    _messageList.value = emptyList()
                }
            }
        )
    }

    // The media preview holds a detached snapshot of these sub-messages, so a completed download
    // never reaches it. Re-downloading rebuilds them with the now-present local media paths.
    fun refreshMessageList(onCompleted: (List<MessageInfo>) -> Unit) {
        MessageActionStore.create(messageInfo).downloadMergedMessageList(
            object : MergedMessageListCompletionHandler {
                override fun onSuccess(messageList: List<MessageInfo>) {
                    val refreshedMessages = normalizeMessageList(messageList)
                    _messageList.value = refreshedMessages
                    onCompleted(refreshedMessages)
                }

                override fun onFailure(code: Int, desc: String) {
                    onCompleted(_messageList.value)
                }
            }
        )
    }

    private fun normalizeMessageList(messageList: List<MessageInfo>): List<MessageInfo> {
        return messageList
            .filter { item -> item.msgID.isNotEmpty() }
            .distinctBy { item -> item.msgID }
    }


    fun downloadThumbImage(messageInfo: MessageInfo) {
        MessageActionStore.create(messageInfo).downloadMedia(MediaQuality.THUMBNAIL)
    }

    fun downloadSound(messageInfo: MessageInfo) {
        MessageActionStore.create(messageInfo).downloadMedia()
    }

    fun downloadVideoSnapShot(messageInfo: MessageInfo) {
        MessageActionStore.create(messageInfo).downloadMedia(MediaQuality.THUMBNAIL)
    }
}


class MergedMessageDetailViewModelFactory(
    private val messageListStore: MessageListStore,
    private val mergeMessage: MessageInfo
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MergedMessageDetailViewModel(messageListStore, mergeMessage) as T
    }
}
