package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageReadReceiptDisplayState
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageReadReceiptDisplayPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageMetadataTextStyle
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.isShowReadReceipt
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

@Composable
fun MessageReadReceiptIndicator(
    message: MessageInfo,
    modifier: Modifier = Modifier,
    receiptSignature: String = "",
    onClick: (() -> Unit)? = null
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    if (!message.isShowReadReceipt) {
        return
    }
    val clickable = onClick != null && message.conversationType == ConversationType.GROUP
    val displayText = MessageReadReceiptDisplayPolicy.displayText(context, message)
    Text(
        modifier = if (clickable) {
            modifier.clickable { onClick?.invoke() }
        } else {
            modifier
        },
        text = displayText,
        style = messageMetadataTextStyle(),
        color = colors.textColorSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
fun MessageInfo.readReceiptDisplayState(): MessageReadReceiptDisplayState {
    val receipt = readReceiptInfo
    if (conversationType != ConversationType.GROUP) {
        return if (receipt?.isPeerRead == true) {
            MessageReadReceiptDisplayState.READ
        } else {
            MessageReadReceiptDisplayState.UNREAD
        }
    }
    val readCount = receipt?.readCount ?: 0
    val unreadCount = receipt?.unreadCount ?: 0
    return when {
        receipt != null && unreadCount == 0 -> MessageReadReceiptDisplayState.ALL_READ
        readCount > 0 -> MessageReadReceiptDisplayState.READ
        else -> MessageReadReceiptDisplayState.UNREAD
    }
}
