package io.trtc.tuikit.chat.uikit.components.chatbot

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.StreamMessagePayload

/**
 * 机器人回复所属的通道。
 *
 * 现在有两套结构完全不同、互不兼容的流式方案，解析入口必须分开，不能拿一套的字段去读另一套：
 *
 * - [PLUGIN]：chatbotPlugin 自定义消息，见 [ChatbotMessageProtocol]。正文放在 chunks 数组里，
 *   靠 msgKey 定位、isFinished 收尾，服务端支持按 msgKey 打断。
 * - [STREAM]：TIMStreamElem 流式消息（[MessageType.STREAM]）。正文是 markdown，
 *   靠 isStreamEnded 收尾，没有 msgKey，服务端也没有打断入口。
 *
 * 另外 [PLAIN] 指普通文本、卡片这类一次性回复，[CONTROL] 指不该展示给用户的信令。
 */
internal enum class ChatbotReplyChannel {
    PLUGIN,
    STREAM,
    PLAIN,
    CONTROL;

    /** 控制信令不算机器人的回复。 */
    val isReply: Boolean
        get() = this != CONTROL
}

internal object ChatbotReplyClassifier {

    /**
     * 是否是一条已经有内容可看的回复。
     *
     * TIMStreamElem 的消息体先到、正文随后由 onRecvMessageModified 补上，刚到的那一刻
     * markdown 还是空的，列表里也被 MessageListVisibilityPolicy 挡着不显示。此时若判定
     * 「回复到了」就把等待态收掉，屏幕上会空出一段什么都没有的时间，所以要等到第一段正文。
     */
    fun isVisibleReply(message: MessageInfo): Boolean {
        val channel = classify(message)
        if (!channel.isReply) {
            return false
        }
        if (channel != ChatbotReplyChannel.STREAM) {
            return true
        }
        val payload = message.messagePayload as? StreamMessagePayload ?: return true
        return payload.markdown.isNotEmpty()
    }

    fun classify(message: MessageInfo): ChatbotReplyChannel {
        if (isStreamElement(message)) {
            return ChatbotReplyChannel.STREAM
        }
        val plugin = ChatbotMessageProtocol.parse(message)
            ?: return ChatbotReplyChannel.PLAIN
        return when (plugin.source) {
            ChatbotMessageSource.FLOW,
            ChatbotMessageSource.ERROR -> ChatbotReplyChannel.PLUGIN
            ChatbotMessageSource.INTERRUPT,
            ChatbotMessageSource.UNKNOWN -> ChatbotReplyChannel.CONTROL
        }
    }

    private fun isStreamElement(message: MessageInfo): Boolean {
        return message.messageType == MessageType.STREAM &&
            message.messagePayload is StreamMessagePayload
    }
}
