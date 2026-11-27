package io.trtc.tuikit.chat.uikit.components.messagelist.utils
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils

fun formatSmartTime(totalSeconds: Int?): String {
    if (totalSeconds == null) return "00:00"
    return ChatDateTimeUtils.formatDurationSeconds(totalSeconds.toLong())
}
