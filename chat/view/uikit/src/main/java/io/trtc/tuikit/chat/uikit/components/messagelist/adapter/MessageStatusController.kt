package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts.MessageReadReceiptIndicatorView
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.shouldShowReadReceiptIndicator
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import kotlin.math.ceil

internal class MessageStatusController(
    private val context: Context,
    private val density: Float,
    private val sendingIndicator: ProgressBar,
    private val sendFailIcon: TextView,
    private val readReceiptIndicatorView: MessageReadReceiptIndicatorView,
    private val statusContainer: FrameLayout
) {
    private var sendingRotator: ObjectAnimator? = null

    fun update(
        message: MessageInfo,
        colors: ColorTokens,
        viewModel: MessageListViewModel,
        enableMessageInteraction: Boolean,
        showMessageReadReceipt: Boolean,
        statusLayout: MessageStatusLayout
    ) {
        sendingIndicator.visibility = View.GONE
        stopSendingRotator()
        sendFailIcon.visibility = View.GONE
        readReceiptIndicatorView.visibility = View.GONE
        statusContainer.visibility = View.GONE
        statusContainer.setOnClickListener(null)
        statusContainer.isClickable = false
        readReceiptIndicatorView.setOnClickListener(null)
        readReceiptIndicatorView.isClickable = false
        sendFailIcon.setOnClickListener(null)

        val statusLayoutParams = statusContainer.layoutParams as LinearLayout.LayoutParams
        statusLayoutParams.marginStart = (statusLayout.marginStartDp * density).toInt()
        statusLayoutParams.marginEnd = (statusLayout.marginEndDp * density).toInt()
        statusContainer.layoutParams = statusLayoutParams

        when (message.status) {
            MessageStatus.SENDING -> {
                statusContainer.visibility = View.VISIBLE
                sendingIndicator.visibility = View.VISIBLE
                startSendingRotator()
            }

            MessageStatus.SEND_FAIL, MessageStatus.VIOLATION -> {
                if (message.isSentBySelf) {
                    statusContainer.visibility = View.VISIBLE
                    sendFailIcon.visibility = View.VISIBLE
                    sendFailIcon.setTextColor(colors.textColorButton)
                    sendFailIcon.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(colors.textColorError)
                    }
                    if (enableMessageInteraction && message.status == MessageStatus.SEND_FAIL) {
                        sendFailIcon.setOnClickListener {
                            AtomicAlertDialog(context).apply {
                                init {
                                    content = context.getString(R.string.message_list_resend_tips)
                                    confirmButton(context.getString(R.string.uikit_confirm)) { _ ->
                                        viewModel.retrySendMessage(context, message)
                                    }
                                    cancelButton(context.getString(R.string.uikit_cancel))
                                }
                                show()
                            }
                        }
                    }
                }
            }

            else -> {
                val showReadReceiptIndicator = message.shouldShowReadReceiptIndicator(showMessageReadReceipt)
                if (showReadReceiptIndicator) {
                    statusContainer.visibility = View.VISIBLE
                    readReceiptIndicatorView.visibility = View.VISIBLE
                    readReceiptIndicatorView.bind(message, colors)
                    if (
                        enableMessageInteraction &&
                        message.conversationType == ConversationType.GROUP
                    ) {
                        val openReadReceiptDetailListener = View.OnClickListener {
                            viewModel.showReadReceiptDialog(message)
                        }
                        statusContainer.setOnClickListener(openReadReceiptDetailListener)
                        readReceiptIndicatorView.setOnClickListener(openReadReceiptDetailListener)
                    }
                }
            }
        }
    }

    fun resolveStableStatusReserveWidth(
        message: MessageInfo,
        showMessageReadReceipt: Boolean
    ): Int {
        val layoutParams = statusContainer.layoutParams as? LinearLayout.LayoutParams
        return MessageStatusLayoutPolicy.resolveStableStatusReserveWidth(
            currentStatusContentWidth = resolveVisibleStatusContentWidth(),
            potentialReadReceiptContentWidth = resolvePotentialReadReceiptContentWidth(
                message = message,
                showMessageReadReceipt = showMessageReadReceipt
            ),
            marginStartPx = layoutParams?.marginStart ?: 0,
            marginEndPx = layoutParams?.marginEnd ?: 0
        )
    }

    fun stopSendingRotator() {
        sendingRotator?.cancel()
        sendingRotator = null
        sendingIndicator.rotation = 0f
    }

    private fun resolveVisibleStatusContentWidth(): Int {
        if (statusContainer.visibility != View.VISIBLE) {
            return 0
        }
        return when {
            readReceiptIndicatorView.visibility == View.VISIBLE -> {
                ceil(readReceiptIndicatorView.paint.measureText(readReceiptIndicatorView.text.toString())).toInt() +
                    readReceiptIndicatorView.compoundPaddingLeft +
                    readReceiptIndicatorView.compoundPaddingRight
            }
            sendingIndicator.visibility == View.VISIBLE -> sendingIndicator.resolveDesiredWidth()
            sendFailIcon.visibility == View.VISIBLE -> sendFailIcon.resolveDesiredWidth()
            else -> statusContainer.measuredWidth
        }.coerceAtLeast(0)
    }

    private fun resolvePotentialReadReceiptContentWidth(
        message: MessageInfo,
        showMessageReadReceipt: Boolean
    ): Int {
        if (!showMessageReadReceipt ||
            !message.isSentBySelf ||
            !message.needReadReceipt ||
            message.messageType == MessageType.TIPS
        ) {
            return 0
        }
        return readReceiptIndicatorView.resolvePotentialWidth(message)
    }

    private fun View.resolveDesiredWidth(): Int {
        val widthFromLayoutParams = layoutParams?.width?.takeIf { it > 0 } ?: 0
        return maxOf(measuredWidth, widthFromLayoutParams, minimumWidth)
    }

    private fun startSendingRotator() {
        if (sendingRotator?.isRunning == true) {
            return
        }
        sendingRotator = ObjectAnimator.ofFloat(sendingIndicator, View.ROTATION, 0f, 360f).apply {
            duration = SENDING_ROTATOR_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
    }
}
