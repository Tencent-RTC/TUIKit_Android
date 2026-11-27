package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageListType
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadDirection
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadOption
import io.trtc.tuikit.atomicxcore.api.message.MessageType

internal typealias MessageMediaPreviewRefresher = (onCompleted: () -> Unit) -> Unit

internal interface MessageMediaPreviewMessageSource {
    fun loadInitialMediaMessages(
        anchorMessage: MessageInfo,
        completion: (List<MessageInfo>) -> Unit
    )

    fun loadMoreMediaMessages(
        direction: MessageLoadDirection,
        completion: (List<MessageInfo>) -> Unit
    )

    fun currentMediaMessages(): List<MessageInfo>

    // Re-reads the underlying messages so paths that only exist after a download become visible.
    // Store-backed sources already return fresh messages, so refreshing is a no-op for them.
    fun refreshMediaMessages(onCompleted: () -> Unit) {
        onCompleted()
    }
}

internal class StaticMessageMediaPreviewMessageSource(
    private val messagesProvider: () -> List<MessageInfo>,
    private val reverseMessageOrderForPreview: Boolean = true,
    private val messagesRefresher: MessageMediaPreviewRefresher? = null
) : MessageMediaPreviewMessageSource {
    constructor(
        messages: List<MessageInfo>,
        reverseMessageOrderForPreview: Boolean = true
    ) : this({ messages }, reverseMessageOrderForPreview)

    override fun refreshMediaMessages(onCompleted: () -> Unit) {
        val refresher = messagesRefresher
        if (refresher == null) {
            onCompleted()
            return
        }
        refresher(onCompleted)
    }

    override fun loadInitialMediaMessages(
        anchorMessage: MessageInfo,
        completion: (List<MessageInfo>) -> Unit
    ) {
        completion(currentMediaMessages())
    }

    override fun loadMoreMediaMessages(
        direction: MessageLoadDirection,
        completion: (List<MessageInfo>) -> Unit
    ) {
        completion(currentMediaMessages())
    }

    override fun currentMediaMessages(): List<MessageInfo> {
        val messages = messagesProvider()
        val orderedMessages = if (reverseMessageOrderForPreview) {
            messages.asReversed()
        } else {
            messages
        }
        return orderedMessages.filter(::isMediaMessage)
    }
}

internal class MessageMediaPreviewDataManager(
    private val loadMessages: (MessageLoadOption, CompletionHandler) -> Unit,
    private val loadOlder: (CompletionHandler) -> Unit,
    private val loadNewer: (CompletionHandler) -> Unit,
    private val currentMessages: () -> List<MessageInfo>,
    private val hasMoreOlderMessages: () -> Boolean,
    private val hasMoreNewerMessages: () -> Boolean,
    private val orderMessagesForPreview: (List<MessageInfo>) -> List<MessageInfo> = { it }
) : MessageMediaPreviewMessageSource {
    constructor(
        messageListStore: MessageListStore,
        orderMessagesForPreview: (List<MessageInfo>) -> List<MessageInfo> = { it }
    ) : this(
        loadMessages = { option, completion ->
            messageListStore.loadMessages(option, completion)
        },
        loadOlder = { completion -> messageListStore.loadOlderMessages(completion) },
        loadNewer = { completion -> messageListStore.loadNewerMessages(completion) },
        currentMessages = { messageListStore.state.messageList.value },
        hasMoreOlderMessages = { messageListStore.state.hasOlderMessages.value },
        hasMoreNewerMessages = { messageListStore.state.hasNewerMessages.value },
        orderMessagesForPreview = orderMessagesForPreview
    )

    private var mediaMessages: List<MessageInfo> = emptyList()
    private var isLoadingOlder = false
    private var isLoadingNewer = false

    override fun loadInitialMediaMessages(
        anchorMessage: MessageInfo,
        completion: (List<MessageInfo>) -> Unit
    ) {
        val option = MessageLoadOption(
            messageListType = MessageListType.HISTORY,
            cursor = anchorMessage,
            direction = MessageLoadDirection.BOTH,
            messageTypeList = MEDIA_MESSAGE_TYPES
        )
        loadMessages(option, object : CompletionHandler {
            override fun onSuccess() {
                mediaMessages = orderedMediaMessages(currentMessages())
                completion(mediaMessages)
            }

            override fun onFailure(code: Int, desc: String) {
                mediaMessages = emptyList()
                completion(emptyList())
            }
        })
    }

    override fun loadMoreMediaMessages(
        direction: MessageLoadDirection,
        completion: (List<MessageInfo>) -> Unit
    ) {
        val isOlder = direction == MessageLoadDirection.OLDER
        if (!hasMoreMessages(isOlder) || isLoading(isOlder) || mediaMessages.isEmpty()) {
            completion(refreshCurrentMediaMessages())
            return
        }
        setLoading(isOlder, true)
        val handler = object : CompletionHandler {
            override fun onSuccess() {
                val fetchedMediaMessages = orderedMediaMessages(currentMessages())
                mediaMessages = mergeMediaMessages(fetchedMediaMessages, isOlder)
                setLoading(isOlder, false)
                completion(mediaMessages)
            }

            override fun onFailure(code: Int, desc: String) {
                setLoading(isOlder, false)
                completion(mediaMessages)
            }
        }
        if (isOlder) {
            loadOlder(handler)
        } else {
            loadNewer(handler)
        }
    }

    override fun currentMediaMessages(): List<MessageInfo> = refreshCurrentMediaMessages()

    private fun orderedMediaMessages(messages: List<MessageInfo>): List<MessageInfo> {
        return orderMessagesForPreview(messages).filter(::isMediaMessage)
    }

    private fun refreshCurrentMediaMessages(): List<MessageInfo> {
        mediaMessages = refreshExistingMediaMessages(
            existingMessages = mediaMessages,
            fetchedMessages = orderedMediaMessages(currentMessages())
        )
        return mediaMessages
    }

    private fun mergeMediaMessages(
        fetchedMediaMessages: List<MessageInfo>,
        isOlder: Boolean
    ): List<MessageInfo> {
        val refreshedMediaMessages = refreshExistingMediaMessages(mediaMessages, fetchedMediaMessages)
        val existingMessageIds = refreshedMediaMessages.mapNotNull { it.msgID }.toSet()
        val uniqueMessages = fetchedMediaMessages.filter { message ->
            !existingMessageIds.contains(message.msgID)
        }
        return if (isOlder) {
            uniqueMessages + refreshedMediaMessages
        } else {
            refreshedMediaMessages + uniqueMessages
        }
    }

    private fun refreshExistingMediaMessages(
        existingMessages: List<MessageInfo>,
        fetchedMessages: List<MessageInfo>
    ): List<MessageInfo> {
        if (existingMessages.isEmpty() || fetchedMessages.isEmpty()) {
            return existingMessages
        }
        val fetchedMessageMap = fetchedMessages.mapNotNull { message ->
            message.msgID?.let { messageId -> messageId to message }
        }.toMap()
        if (fetchedMessageMap.isEmpty()) {
            return existingMessages
        }
        return existingMessages.map { message ->
            message.msgID?.let { messageId -> fetchedMessageMap[messageId] } ?: message
        }
    }

    private fun hasMoreMessages(isOlder: Boolean): Boolean {
        return if (isOlder) hasMoreOlderMessages() else hasMoreNewerMessages()
    }

    private fun isLoading(isOlder: Boolean): Boolean {
        return if (isOlder) isLoadingOlder else isLoadingNewer
    }

    private fun setLoading(isOlder: Boolean, isLoading: Boolean) {
        if (isOlder) {
            isLoadingOlder = isLoading
        } else {
            isLoadingNewer = isLoading
        }
    }
}

private fun isMediaMessage(message: MessageInfo): Boolean {
    return message.messageType == MessageType.IMAGE || message.messageType == MessageType.VIDEO
}

private val MEDIA_MESSAGE_TYPES: List<MessageType> = listOf(MessageType.IMAGE, MessageType.VIDEO)
