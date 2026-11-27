package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.StreamMessagePayload

/**
 * 判定消息列表是否被登录同步的重放消息污染。
 *
 * 全新安装登录时，IMSDK 会把一批历史消息经由新消息通道重放。这批消息会被追加到列表
 * 最新端（顺序错乱），而且流式消息的正文补丁（onRecvMessageModified）可能在消息真正
 * 入列之前就已经派发完毕并被丢弃，导致气泡永远停在加载态。
 *
 * 命中判定后由 ViewModel 重新拉取首屏，用历史接口的干净数据覆盖掉脏列表。
 */
internal class MessageListSyncRepairPolicy(
    private val nowSecondsProvider: () -> Long = { System.currentTimeMillis() / 1000 }
) {

    fun isCorrupted(messages: List<MessageInfo>): Boolean {
        if (messages.size < 2) return false
        return hasStaleUnfilledStream(messages) || hasTimestampInversion(messages)
    }

    /**
     * 流式消息既没有正文、也没有结束标记，且已经过了 [STREAM_SETTLE_SECONDS]。
     *
     * 正在生成中的流式消息同样是空正文 + 未结束，靠消息时间把两者区分开：真正在生成的
     * 消息一定是刚刚发生的，而重放进来的都是很久以前的。
     */
    private fun hasStaleUnfilledStream(messages: List<MessageInfo>): Boolean {
        val threshold = nowSecondsProvider() - STREAM_SETTLE_SECONDS
        return messages.any { message ->
            if (message.messageType != MessageType.STREAM) return@any false
            val timestamp = message.timestamp ?: return@any false
            if (timestamp <= 0L || timestamp > threshold) return@any false
            val payload = message.messagePayload as? StreamMessagePayload
            payload != null && payload.markdown.isEmpty() && !payload.isStreamEnded
        }
    }

    /** 列表整体走向由首尾决定，出现反向的相邻对即为乱序。 */
    private fun hasTimestampInversion(messages: List<MessageInfo>): Boolean {
        val stamps = messages.mapNotNull { it.timestamp?.takeIf { value -> value > 0L } }
        if (stamps.size < 2) return false
        val descending = stamps.first() > stamps.last()
        return stamps.zipWithNext().any { (prev, next) ->
            if (descending) next > prev else next < prev
        }
    }

    companion object {
        private const val STREAM_SETTLE_SECONDS = 60L
    }
}
