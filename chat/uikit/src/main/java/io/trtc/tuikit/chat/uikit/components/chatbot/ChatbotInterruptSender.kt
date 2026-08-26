package io.trtc.tuikit.chat.uikit.components.chatbot

import android.os.SystemClock
import com.tencent.imsdk.BaseConstants
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMSendCallback
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import java.nio.charset.StandardCharsets

internal fun interface ChatbotInterruptAction {
    fun send(
        messageID: String,
        fallbackMsgKey: String,
        completion: CompletionHandler?
    ): Boolean
}

internal interface ChatbotControlMessageTransport {
    fun send(
        conversationID: String,
        customData: String,
        completion: CompletionHandler?
    )
}

internal class ChatbotInterruptSender(
    private val conversationID: String,
    private val transport: ChatbotControlMessageTransport = ImsdkChatbotControlMessageTransport,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) : ChatbotInterruptAction {
    private var lastSentAtMs: Long? = null

    override fun send(
        messageID: String,
        fallbackMsgKey: String,
        completion: CompletionHandler?
    ): Boolean {
        if (messageID.isBlank() && fallbackMsgKey.isBlank()) {
            return false
        }
        val now = elapsedRealtime()
        val lastSentAt = lastSentAtMs
        if (lastSentAt != null && now - lastSentAt < SEND_INTERVAL_MS) {
            return false
        }
        lastSentAtMs = now
        if (messageID.isBlank()) {
            sendWithMessageKey(fallbackMsgKey, completion)
        } else {
            resolveMessageKeyAndSend(messageID, fallbackMsgKey, completion)
        }
        return true
    }

    private fun resolveMessageKeyAndSend(
        messageID: String,
        fallbackMsgKey: String,
        completion: CompletionHandler?
    ) {
        V2TIMManager.getMessageManager().findMessages(
            listOf(messageID),
            object : V2TIMValueCallback<List<V2TIMMessage>> {
                override fun onSuccess(messages: List<V2TIMMessage>?) {
                    val messageKey = messages.orEmpty().firstOrNull()
                        ?.let(::generateMessageKey)
                        .orEmpty()
                        .ifBlank { fallbackMsgKey }
                    sendWithMessageKey(messageKey, completion)
                }

                override fun onError(code: Int, desc: String?) {
                    if (fallbackMsgKey.isNotBlank()) {
                        sendWithMessageKey(fallbackMsgKey, completion)
                    } else {
                        completion?.onFailure(code, desc.orEmpty())
                    }
                }
            }
        )
    }

    private fun sendWithMessageKey(
        msgKey: String,
        completion: CompletionHandler?
    ) {
        if (msgKey.isBlank()) {
            completion?.onFailure(
                BaseConstants.ERR_INVALID_PARAMETERS,
                "Chatbot message key is unavailable"
            )
            return
        }
        transport.send(
            conversationID = conversationID,
            customData = ChatbotMessageProtocol.createInterruptPayload(msgKey),
            completion = completion
        )
    }

    private fun generateMessageKey(message: V2TIMMessage): String {
        return "${message.seq}_${message.random}_${message.timestamp}"
    }

    private companion object {
        const val SEND_INTERVAL_MS = 1_000L
    }
}

private object ImsdkChatbotControlMessageTransport : ChatbotControlMessageTransport {
    override fun send(
        conversationID: String,
        customData: String,
        completion: CompletionHandler?
    ) {
        val messageManager = V2TIMManager.getMessageManager()
        val message = messageManager.createCustomMessage(
            customData.toByteArray(StandardCharsets.UTF_8)
        )
        message.setExcludedFromLastMessage(true)
        message.setExcludedFromUnreadCount(true)
        messageManager.sendMessage(
            message,
            ConversationIDUtil.userIdOrNull(conversationID),
            ConversationIDUtil.groupIdOrNull(conversationID),
            V2TIMMessage.V2TIM_PRIORITY_DEFAULT,
            true,
            null,
            object : V2TIMSendCallback<V2TIMMessage> {
                override fun onProgress(progress: Int) = Unit

                override fun onSuccess(message: V2TIMMessage?) {
                    completion?.onSuccess()
                }

                override fun onError(code: Int, desc: String?) {
                    completion?.onFailure(code, desc.orEmpty())
                }
            }
        )
    }
}
