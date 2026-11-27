package io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.adapter.MessageStatusLayoutPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.groupReadCount
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageReadReceiptDisplayState
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.readReceiptDisplayState
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class MessageReadReceiptIndicatorView(
    context: Context
) : AppCompatTextView(context) {

    init {
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        textDirection = View.TEXT_DIRECTION_LOCALE
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun bind(message: MessageInfo, colors: ColorTokens) {
        text = displayText(message)
        setTextColor(colors.textColorSecondary)
    }

    fun resolvePotentialWidth(message: MessageInfo): Int {
        val candidateTexts = mutableListOf(
            context.getString(R.string.message_list_read_receipt_delivered_to),
            context.getString(R.string.message_list_read_receipt_all_read)
        )
        if (message.conversationType != ConversationType.GROUP) {
            candidateTexts.add(context.getString(R.string.message_list_read_receipt_read_by))
        } else {
            candidateTexts.add(
                context.getString(
                    R.string.message_list_read_receipt_read_by_count,
                    MessageStatusLayoutPolicy.resolveStableGroupReadReceiptCount(message.groupReadCount)
                )
            )
        }
        return candidateTexts.maxOf { candidateText ->
            kotlin.math.ceil(paint.measureText(candidateText)).toInt() +
                compoundPaddingLeft +
                compoundPaddingRight
        }
    }

    private fun displayText(message: MessageInfo): String {
        return when (message.readReceiptDisplayState) {
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
            else -> {
                context.getString(R.string.message_list_read_receipt_delivered_to)
            }
        }
    }
}
