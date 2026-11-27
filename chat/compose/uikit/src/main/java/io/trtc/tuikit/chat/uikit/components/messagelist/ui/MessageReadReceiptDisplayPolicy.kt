package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageReadReceiptDisplayState
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets.readReceiptDisplayState
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.shouldShowReadReceiptIndicator
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType

internal enum class ReadReceiptCandidate {
    DELIVERED,
    ALL_READ,
    PEER_READ,
    GROUP_COUNT
}

internal object MessageReadReceiptDisplayPolicy {
    fun shouldReserveWidth(
        message: MessageInfo,
        showMessageReadReceipt: Boolean
    ): Boolean {
        return showMessageReadReceipt &&
            message.isSentBySelf &&
            message.needReadReceipt &&
            message.messageType != MessageType.TIPS
    }

    fun resolvePotentialCandidates(isGroup: Boolean): List<ReadReceiptCandidate> {
        val candidates = mutableListOf(
            ReadReceiptCandidate.DELIVERED,
            ReadReceiptCandidate.ALL_READ
        )
        if (isGroup) {
            candidates.add(ReadReceiptCandidate.GROUP_COUNT)
        } else {
            candidates.add(ReadReceiptCandidate.PEER_READ)
        }
        return candidates
    }

    fun potentialTexts(context: Context, message: MessageInfo): List<String> {
        val isGroup = message.conversationType == ConversationType.GROUP
        return resolvePotentialCandidates(isGroup).map { candidate ->
            resolveCandidateText(
                context = context,
                candidate = candidate,
                groupReadCount = MessageItemDisplayPolicy.resolveStableGroupReadReceiptCount(
                    message.groupReadCount
                )
            )
        }
    }

    fun displayText(context: Context, message: MessageInfo): String {
        return when (message.readReceiptDisplayState()) {
            MessageReadReceiptDisplayState.UNREAD -> {
                context.getString(R.string.message_list_read_receipt_delivered_to)
            }
            MessageReadReceiptDisplayState.READ -> {
                if (message.conversationType != ConversationType.GROUP) {
                    context.getString(R.string.message_list_read_receipt_read_by)
                } else {
                    context.getString(
                        R.string.message_list_read_receipt_read_by_count,
                        message.groupReadCount
                    )
                }
            }
            MessageReadReceiptDisplayState.ALL_READ -> {
                context.getString(R.string.message_list_read_receipt_all_read)
            }
        }
    }

    private fun resolveCandidateText(
        context: Context,
        candidate: ReadReceiptCandidate,
        groupReadCount: Int
    ): String {
        return when (candidate) {
            ReadReceiptCandidate.DELIVERED -> {
                context.getString(R.string.message_list_read_receipt_delivered_to)
            }
            ReadReceiptCandidate.ALL_READ -> {
                context.getString(R.string.message_list_read_receipt_all_read)
            }
            ReadReceiptCandidate.PEER_READ -> {
                context.getString(R.string.message_list_read_receipt_read_by)
            }
            ReadReceiptCandidate.GROUP_COUNT -> {
                context.getString(
                    R.string.message_list_read_receipt_read_by_count,
                    groupReadCount
                )
            }
        }
    }
}

internal val MessageInfo.groupReadCount: Int
    get() = readReceiptInfo?.readCount ?: 0

@Composable
internal fun messageMetadataTextStyle(): TextStyle {
    return LocalTextStyle.current.merge(
        TextStyle(
            fontSize = MessageItemDisplayPolicy.READ_RECEIPT_TEXT_SIZE_SP.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    )
}

@Composable
internal fun rememberStatusReserveWidth(
    message: MessageInfo,
    showCallUnreadDot: Boolean,
    showMessageReadReceipt: Boolean,
    layout: MessageStatusLayout
): Dp {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localeTags = ConfigurationCompat.getLocales(configuration).toLanguageTags()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val receiptStyle = messageMetadataTextStyle()
    return remember(
        message.msgID,
        message.status,
        message.isSentBySelf,
        message.needReadReceipt,
        message.messageType,
        message.conversationType,
        message.readReceiptInfo?.readCount,
        message.readReceiptInfo?.unreadCount,
        message.readReceiptInfo?.isPeerRead,
        showCallUnreadDot,
        showMessageReadReceipt,
        layout,
        localeTags,
        context,
        density,
        receiptStyle,
        textMeasurer
    ) {
        fun measureText(text: String): Dp {
            val widthPx = textMeasurer.measure(
                text = text,
                style = receiptStyle,
                maxLines = 1
            ).size.width
            return with(density) { widthPx.toDp() }
        }
        val currentStatusContentWidth = when {
            showCallUnreadDot -> MessageItemDisplayPolicy.CALL_UNREAD_DOT_SIZE_DP.dp
            message.isSentBySelf &&
                (message.status == MessageStatus.SEND_FAIL ||
                    message.status == MessageStatus.VIOLATION) -> {
                MessageItemDisplayPolicy.FAIL_ICON_SIZE_DP.dp
            }
            message.status == MessageStatus.SENDING -> {
                MessageItemDisplayPolicy.SENDING_INDICATOR_SIZE_DP.dp
            }
            message.shouldShowReadReceiptIndicator(showMessageReadReceipt) -> {
                measureText(MessageReadReceiptDisplayPolicy.displayText(context, message))
            }
            else -> 0.dp
        }
        val potentialReadReceiptContentWidth =
            if (MessageReadReceiptDisplayPolicy.shouldReserveWidth(message, showMessageReadReceipt)) {
                MessageReadReceiptDisplayPolicy.potentialTexts(context, message)
                    .maxOf { measureText(it) }
            } else {
                0.dp
            }
        MessageItemDisplayPolicy.resolveStableStatusReserveWidth(
            currentStatusContentWidth = currentStatusContentWidth,
            potentialReadReceiptContentWidth = potentialReadReceiptContentWidth,
            layout = layout
        )
    }
}
