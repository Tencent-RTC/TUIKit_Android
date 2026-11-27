package io.trtc.tuikit.chat.uikit.components.chatbot

import android.os.SystemClock
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatbotConversationController(
    val conversationID: String,
    private val interruptAction: ChatbotInterruptAction = ChatbotInterruptSender(conversationID),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) {
    private val mutableState = MutableStateFlow<ChatbotConversationState>(
        ChatbotConversationState.Idle
    )
    private var knownMessageIDs: Set<String> = emptySet()
    private var waitingBaselineMessageIDs: Set<String> = emptySet()
    private var watchdogJob: Job? = null
    private var lastProgressMillis: Long = 0L
    private var abandonedMessageIDs: Set<String> = emptySet()

    val state: StateFlow<ChatbotConversationState> = mutableState.asStateFlow()

    val isActive: Boolean
        get() = mutableState.value != ChatbotConversationState.Idle

    fun onTextMessageSent() {
        waitingBaselineMessageIDs = knownMessageIDs
        dispatch(ChatbotConversationEvent.TextMessageSent(nowSeconds()))
    }

    fun onMessagesChanged(messages: List<MessageInfo>) {
        when (val current = mutableState.value) {
            ChatbotConversationState.Idle -> restoreFromHistory(messages)
            is ChatbotConversationState.Waiting -> {
                val response = messages.asReversed().firstChatbotPluginResponse { message, data ->
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
                } else if (hasInboundReply(messages, waitingBaselineMessageIDs)) {
                    // TIMStreamElem / 文本 / 卡片：没有 msgKey 也没有打断能力，收到就结束等待
                    dispatch(ChatbotConversationEvent.Cleared)
                }
            }
            is ChatbotConversationState.Streaming -> {
                val response = messages.asReversed().firstChatbotPluginResponse { message, data ->
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
        knownMessageIDs = messages.mapNotNull { message ->
            message.msgID.takeIf { it.isNotBlank() }
        }.toSet()
    }

    /**
     * 等待回复期间要补在列表最新端的「正在输入」气泡的时间戳，不需要补时返回 null。
     *
     * 只有确实还没有任何东西可显示时才补：chatbotPlugin 协议收到首个分片后由真实气泡接管，
     * TIMStreamElem 则要等正文落地，判定见 [ChatbotReplyClassifier.isVisibleReply]。
     */
    fun placeholderTimestampSeconds(messages: List<MessageInfo>): Long? {
        val waiting = mutableState.value as? ChatbotConversationState.Waiting ?: return null
        val pluginResponse = messages.asReversed().firstChatbotPluginResponse { message, data ->
            message.msgID !in waitingBaselineMessageIDs && data.isVisualResponse()
        }
        if (pluginResponse != null || hasInboundReply(messages, waitingBaselineMessageIDs)) {
            return null
        }
        return waiting.requestTimestampSeconds
    }

    /**
     * 当前状态是否支持打断。
     *
     * 只有 chatbotPlugin 协议的流式输出能按 msgKey 打断。TIMStreamElem 那条路径既没有 msgKey，
     * 服务端也没有打断入口，状态机根本进不了 Streaming，所以那边不出停止按钮，
     * 「正在回复」交给列表里的占位气泡表达。详见 [ChatbotReplyChannel]。
     */
    val canInterrupt: Boolean
        get() = mutableState.value is ChatbotConversationState.Streaming

    fun sendInterrupt(completion: CompletionHandler? = null): Boolean {
        val streaming = mutableState.value as? ChatbotConversationState.Streaming
            ?: return false
        return interruptAction.send(
            messageID = streaming.messageID,
            fallbackMsgKey = streaming.msgKey,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    completion?.onSuccess()
                }

                // 打断都发不出去（例如后台不可用），本地强制解锁，别把用户困住
                override fun onFailure(code: Int, desc: String) {
                    abandon(streaming.messageID)
                    completion?.onFailure(code, desc)
                }
            }
        )
    }

    fun clear() {
        knownMessageIDs = emptySet()
        waitingBaselineMessageIDs = emptySet()
        abandonedMessageIDs = emptySet()
        dispatch(ChatbotConversationEvent.Cleared)
    }

    /**
     * 是否收到了机器人的新回复。
     *
     * chatbotPlugin 协议的回复由 [onMessagesChanged] 按 msgKey 精确跟踪，走不到这里；
     * TIMStreamElem 流式消息和普通文本、卡片都没有 msgKey，只能按"来了一条新的对端消息"判断。
     * 机器人的控制信令（打断回执之类）不算回复，正文还没补上的流式消息也不算。
     */
    private fun hasInboundReply(
        messages: List<MessageInfo>,
        baseline: Set<String>
    ): Boolean {
        return messages.any { message ->
            !message.isSentBySelf &&
                message.msgID.isNotBlank() &&
                message.msgID !in baseline &&
                ChatbotReplyClassifier.isVisibleReply(message)
        }
    }

    /**
     * 历史里最后一条未结束的流式消息，可能只是很久以前留下的残骸：后台挂掉、
     * 或登录同步时正文补丁丢失。把它恢复成 Streaming 会让输入框一进会话就锁死，
     * 所以只认刚刚发生的、且没有被超时放弃过的消息。
     */
    private fun restoreFromHistory(messages: List<MessageInfo>) {
        val response = messages.asReversed().firstChatbotPluginResponse { _, data ->
            data.isVisualResponse()
        }
        val resumable = response != null &&
            response.first.msgID !in abandonedMessageIDs &&
            response.first.isRecentEnoughToResume()
        dispatch(
            ChatbotConversationEvent.HistoryRestored(
                messageID = response?.first?.msgID?.takeIf { resumable },
                data = response?.second?.takeIf { resumable }
            )
        )
    }

    private fun MessageInfo.isRecentEnoughToResume(): Boolean {
        val timestamp = timestamp ?: return false
        if (timestamp <= 0L) return false
        return nowSeconds() - timestamp <= STREAM_RESUME_WINDOW_SECONDS
    }

    /** 超时或打断失败后强制回到空闲，并记下这条消息，避免历史恢复又把它锁回去。 */
    private fun abandon(messageID: String?) {
        if (!messageID.isNullOrBlank()) {
            abandonedMessageIDs = abandonedMessageIDs + messageID
        }
        dispatch(ChatbotConversationEvent.Cleared)
    }

    private fun dispatch(event: ChatbotConversationEvent) {
        val next = ChatbotConversationReducer.reduce(mutableState.value, event)
        mutableState.value = next
        syncWatchdog(next)
    }

    /**
     * 机器人没有任何回应时把状态兜回空闲。
     *
     * 后台挂掉或回复丢失的情况下，Waiting / Streaming 不会有任何事件把状态推回 Idle，
     * 「正在输入」气泡就会一直转下去、停止按钮也收不回来。这里在超时后强制收场。
     *
     * 每次有进展（发出消息、收到分片）都会续期，所以正常长回复不会被误伤。
     */
    private fun syncWatchdog(state: ChatbotConversationState) {
        if (state == ChatbotConversationState.Idle) {
            watchdogJob?.cancel()
            watchdogJob = null
            return
        }
        lastProgressMillis = elapsedMillis()
        if (watchdogJob != null) {
            return
        }
        watchdogJob = scope.launch {
            while (true) {
                delay(WATCHDOG_TICK_MS)
                val current = mutableState.value
                if (current == ChatbotConversationState.Idle) {
                    return@launch
                }
                val timeoutMillis = when (current) {
                    is ChatbotConversationState.Waiting -> WAITING_TIMEOUT_MS
                    else -> STREAMING_STALL_TIMEOUT_MS
                }
                if (elapsedMillis() - lastProgressMillis < timeoutMillis) {
                    continue
                }
                // 先摘掉句柄，避免下面的 dispatch 反过来取消正在执行的自己
                watchdogJob = null
                abandon((current as? ChatbotConversationState.Streaming)?.messageID)
                return@launch
            }
        }
    }

    private fun ChatbotMessageData.isVisualResponse(): Boolean {
        return source == ChatbotMessageSource.FLOW || source == ChatbotMessageSource.ERROR
    }

    /**
     * 只在 chatbotPlugin 自定义消息里找，TIMStreamElem 流式消息不走这条路径
     * （[ChatbotMessageProtocol.parse] 对非 CUSTOM 消息一律返回 null）。
     */
    private inline fun List<MessageInfo>.firstChatbotPluginResponse(
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

    private companion object {
        const val WATCHDOG_TICK_MS = 500L

        /**
         * 发出消息后多久没有任何回复就撤掉「正在输入」气泡。
         *
         * 后台正常出字要几秒，阈值必须盖住这段，否则气泡会在回复到达前就消失。
         */
        const val WAITING_TIMEOUT_MS = 15_000L

        /**
         * chatbotPlugin 流式输出停滞多久判定为中断。
         *
         * 这条路径上正文是一个分片一个分片补进来的，每个分片都会续期，
         * 所以阈值要比 [WAITING_TIMEOUT_MS] 宽得多，避免网络抖动误杀正在生成的回复。
         */
        const val STREAMING_STALL_TIMEOUT_MS = 60_000L

        const val STREAM_RESUME_WINDOW_SECONDS = 60L
    }
}
