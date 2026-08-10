package io.trtc.tuikit.chat.uikit.components.messagelist.typing

import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageEvent
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadOption
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

object TypingMessageProtocol {
    const val BUSINESS_ID = "user_typing_status"

    private const val USER_ACTION_TYPING = 14
    private const val ACTION_PARAM_TYPING_START = "EIMAMSG_InputStatus_Ing"
    private const val ACTION_PARAM_TYPING_END = "EIMAMSG_InputStatus_End"

    fun buildTypingMessageData(isTyping: Boolean): String {
        val json = JSONObject()
        json.put("businessID", BUSINESS_ID)
        json.put("typingStatus", if (isTyping) 1 else 0)
        json.put("version", 0)
        json.put("userAction", if (isTyping) USER_ACTION_TYPING else 0)
        json.put("actionParam", if (isTyping) ACTION_PARAM_TYPING_START else ACTION_PARAM_TYPING_END)
        return json.toString()
    }

    fun isTypingMessage(message: MessageInfo): Boolean {
        return parseTypingStatus(message) != null
    }

    fun parseTypingStatus(message: MessageInfo): Boolean? {
        if (message.messageType != MessageType.CUSTOM) return null
        val data = (message.messagePayload as? CustomMessagePayload)?.customData ?: return null
        return parseTypingStatus(data)
    }

    fun parseTypingStatus(data: String): Boolean? {
        return try {
            val json = JSONObject(data)
            if (json.optString("businessID") != BUSINESS_ID) return null
            if (json.has("typingStatus")) {
                json.optInt("typingStatus", 0) == 1
            } else {
                when (json.optString("actionParam")) {
                    ACTION_PARAM_TYPING_START -> true
                    ACTION_PARAM_TYPING_END -> false
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

internal class TypingIndicatorController private constructor(
    private val conversationID: String
) {
    companion object {
        private const val TYPING_SEND_INTERVAL_MS = 4_000L
        private const val TYPING_DISPLAY_TIMEOUT_MS = 5_000L
        private const val PEER_ACTIVE_WINDOW_MS = 30_000L
        private const val RECENT_MESSAGE_LOAD_COUNT = 20

        private val controllers = HashMap<String, TypingIndicatorController>()

        @Synchronized
        fun obtain(conversationID: String): TypingIndicatorController {
            val controller = controllers.getOrPut(conversationID) {
                TypingIndicatorController(conversationID)
            }
            controller.retainCount++
            return controller
        }

        @Synchronized
        fun release(controller: TypingIndicatorController) {
            controller.retainCount--
            if (controller.retainCount <= 0) {
                controllers.remove(controller.conversationID)
                controller.destroy()
            }
        }
    }

    private val peerUserID: String? = ConversationIDUtil.userIdOrNull(conversationID)

    private val _typingState = MutableStateFlow(false)
    val typingState: StateFlow<Boolean> = _typingState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var typingTimeoutJob: Job? = null

    @Volatile
    private var retainCount = 0

    @Volatile
    private var lastTypingSendTimeMs = 0L

    @Volatile
    private var hasSentTypingStart = false

    @Volatile
    private var lastPeerMessageTimeMs = 0L

    private val messageInputStore = MessageInputStore.create(conversationID)
    private val listenStore = if (peerUserID != null) MessageListStore.create(conversationID) else null

    init {
        listenStore?.let { store ->
            store.loadMessages(MessageLoadOption(pageCount = RECENT_MESSAGE_LOAD_COUNT))
            scope.launch {
                store.messageEventFlow.collect { event ->
                    when (event) {
                        is MessageEvent.OnReceiveNewMessage -> handleNewMessage(event.message)
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

    private fun handleNewMessage(message: MessageInfo) {
        if (message.isSentBySelf) return
        lastPeerMessageTimeMs = System.currentTimeMillis()
        TypingMessageProtocol.parseTypingStatus(message)?.let { updatePeerTyping(it) }
    }

    private fun updatePeerTyping(isTyping: Boolean) {
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        if (isTyping) {
            _typingState.value = true
            typingTimeoutJob = scope.launch {
                delay(TYPING_DISPLAY_TIMEOUT_MS)
                _typingState.value = false
            }
        } else {
            _typingState.value = false
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

    private fun destroy() {
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        if (hasSentTypingStart) {
            hasSentTypingStart = false
            dispatchTypingMessage(false)
        }
        scope.cancel()
    }
}
