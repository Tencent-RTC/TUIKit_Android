package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal class MessageListTimeGroupingPolicy(
    private val aggregationSeconds: Int = MESSAGE_AGGREGATION_TIME,
    private val nowProvider: () -> Date = { Date() },
    private val localeProvider: () -> Locale = { Locale.getDefault() },
    private val localizedYesterdayProvider: () -> String = {
        ContextProvider.getApplicationContext()
            ?.getString(R.string.message_list_time_yesterday)
            ?: "Yesterday"
    }
) {

    fun timeStringForMessageAt(index: Int, messages: List<MessageInfo>): String? {
        val message = messages.getOrNull(index)
        if (index == messages.lastIndex) {
            return formatTime(message?.timestamp?.times(1000))
        }
        val previousMessage = messages.getOrNull(index + 1)
        if (message != null && previousMessage != null) {
            val timeInterval = getIntervalSeconds(message.timestamp, previousMessage.timestamp)
            if (timeInterval > aggregationSeconds) {
                return formatTime(message.timestamp?.times(1000))
            }
        }
        return null
    }

    private fun getIntervalSeconds(ts1: Long?, ts2: Long?): Long {
        if (ts1 == null || ts2 == null) return 0L
        if (ts1 == 0L || ts2 == 0L) return 0L
        return abs(ts1 - ts2)
    }

    private fun formatTime(timestampMs: Long?): String? {
        return ChatDateTimeUtils.formatMessageListTime(
            timestampMs = timestampMs,
            now = nowProvider(),
            locale = localeProvider(),
            yesterdayLabel = localizedYesterdayProvider()
        )
    }
}
