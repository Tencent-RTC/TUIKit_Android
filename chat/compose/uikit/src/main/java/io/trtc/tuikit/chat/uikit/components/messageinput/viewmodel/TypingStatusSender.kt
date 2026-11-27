package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.TypingMessageProtocol
import io.trtc.tuikit.atomicxcore.api.message.MessageEvent
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class TypingStatusSender(
    private val conversationID: String,
    private val messageInputStore: MessageInputStore
) {
    companion object {
        private const val TYPING_SEND_INTERVAL_MS = 4_000L
        private const val PEER_ACTIVE_WINDOW_MS = 30_000L
        private const val RECENT_MESSAGE_LOAD_COUNT = 20
        private const val C2C_CONVERSATION_PREFIX = "c2c_"
    }

    private val peerUserID: String? = conversationID
        .takeIf { it.startsWith(C2C_CONVERSATION_PREFIX) }
        ?.removePrefix(C2C_CONVERSATION_PREFIX)
        ?.takeIf { it.isNotEmpty() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var lastTypingSendTimeMs = 0L

    @Volatile
    private var hasSentTypingStart = false

    @Volatile
    private var lastPeerMessageTimeMs = 0L

    private val listenStore = if (peerUserID != null) MessageListStore.create(conversationID) else null

    init {
        listenStore?.let { store ->
            store.loadMessages(MessageLoadOption(pageCount = RECENT_MESSAGE_LOAD_COUNT))
            scope.launch {
                store.messageEventFlow.collect { event ->
                    if (event is MessageEvent.OnReceiveNewMessage && !event.message.isSentBySelf) {
                        lastPeerMessageTimeMs = System.currentTimeMillis()
                    }
                }
            }
            scope.launch {
                store.state.messageList.collect { list ->
                    val lastPeerMessageTimeSec = list.lastOrNull { !it.isSentBySelf }?.timestamp ?: return@collect
                    val timeMs = lastPeerMessageTimeSec * 1000L
                    if (timeMs > lastPeerMessageTimeMs) {
                        lastPeerMessageTimeMs = timeMs
                    }
                }
            }
        }
    }

    fun sendTypingStatus(isTyping: Boolean) {
        if (peerUserID == null) return
        if (!isPeerActive()) return
        if (isTyping) {
            val now = System.currentTimeMillis()
            if (now - lastTypingSendTimeMs < TYPING_SEND_INTERVAL_MS) return
            lastTypingSendTimeMs = now
        } else if (!hasSentTypingStart) {
            return
        }
        hasSentTypingStart = isTyping
        dispatchTypingMessage(isTyping)
    }

    fun release() {
        if (hasSentTypingStart) {
            hasSentTypingStart = false
            dispatchTypingMessage(false)
        }
        scope.cancel()
    }

    private fun dispatchTypingMessage(isTyping: Boolean) {
        messageInputStore.sendMessage(
            payload = SendMessagePayload.CustomSendMessagePayload(
                customData = TypingMessageProtocol.buildTypingMessageData(isTyping),
                description = ""
            ),
            option = SendMessageOption(onlineUserOnly = true)
        )
    }

    private fun isPeerActive(): Boolean {
        if (lastPeerMessageTimeMs == 0L) return false
        return System.currentTimeMillis() - lastPeerMessageTimeMs < PEER_ACTIVE_WINDOW_MS
    }
}
