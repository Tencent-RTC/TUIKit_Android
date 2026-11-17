package io.trtc.tuikit.chat.uikit.components.messagelist.ui.selection
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.FORWARD_MESSAGE_COUNT_LIMIT
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.message.MessageForwardType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

internal class MessageListSelectionController(
    private val context: Context,
    private val container: FrameLayout,
    private val selectedMessagesProvider: () -> Set<MessageInfo>,
    private val onDeleteSelectedMessages: () -> Unit,
    private val onExitMultiSelectMode: () -> Unit,
    private val onForwardSelectedMessages: (List<MessageInfo>, MessageForwardType) -> Unit
) {
    private var multiSelectBottomBar: MultiSelectBottomBar? = null

    fun show() {
        container.visibility = View.VISIBLE
        if (multiSelectBottomBar == null) {
            multiSelectBottomBar = MultiSelectBottomBar(context).also {
                container.addView(it)
            }
        }
        multiSelectBottomBar?.apply {
            updateSelectedCount(selectedMessagesProvider().size)
            onDelete = {
                if (selectedMessagesProvider().isNotEmpty()) {
                    showDeleteMessagesConfirmDialog {
                        onDeleteSelectedMessages()
                        onExitMultiSelectMode()
                    }
                }
            }
            onForwardSeparate = {
                handleForwardAction(MessageForwardType.SEPARATE)
            }
            onForwardMerge = {
                handleForwardAction(MessageForwardType.MERGED)
            }
        }
    }

    fun hide() {
        container.visibility = View.GONE
    }

    fun updateSelectedCount(count: Int) {
        multiSelectBottomBar?.updateSelectedCount(count)
    }

    private fun handleForwardAction(forwardType: MessageForwardType) {
        val selectedMessages = selectedMessagesProvider().toList()
        when (
            MessageListSelectionPolicy.validateForwardSelection(
                selectedMessages = selectedMessages,
                forwardType = forwardType
            )
        ) {
            MessageListSelectionPolicy.ForwardValidation.EMPTY_SELECTION -> return
            MessageListSelectionPolicy.ForwardValidation.CONTAINS_UNSENT_MESSAGE -> {
                AtomicToast.show(
                    context,
                    context.getString(R.string.message_list_forward_failed_tip),
                    style = AtomicToast.Style.WARNING
                )
            }
            MessageListSelectionPolicy.ForwardValidation.CONTAINS_CALL_MESSAGE -> {
                AtomicToast.show(
                    context,
                    context.getString(R.string.message_list_forward_call_message_tip),
                    style = AtomicToast.Style.WARNING
                )
            }
            MessageListSelectionPolicy.ForwardValidation.EXCEEDS_SEPARATE_FORWARD_LIMIT -> {
                AtomicToast.show(
                    context,
                    context.getString(R.string.message_list_forward_oneByOne_limit_number_tip),
                    style = AtomicToast.Style.WARNING
                )
            }
            MessageListSelectionPolicy.ForwardValidation.ALLOWED -> {
                onForwardSelectedMessages(selectedMessages, forwardType)
            }
            else -> Unit
        }
    }

    private fun showDeleteMessagesConfirmDialog(onConfirm: () -> Unit) {
        AtomicAlertDialog(context).apply {
            init {
                content = context.getString(R.string.message_list_delete_messages_tips)
                confirmButton(
                    context.getString(R.string.uikit_confirm),
                    type = AtomicAlertDialog.TextColorPreset.RED
                ) { _ ->
                    onConfirm()
                }
                cancelButton(context.getString(R.string.uikit_cancel))
            }
            show()
        }
    }
}

internal object MessageListSelectionPolicy {
    enum class ForwardValidation {
        ALLOWED,
        EMPTY_SELECTION,
        CONTAINS_UNSENT_MESSAGE,
        CONTAINS_CALL_MESSAGE,
        EXCEEDS_SEPARATE_FORWARD_LIMIT
    }

    fun validateForwardSelection(
        selectedMessages: List<MessageInfo>,
        forwardType: MessageForwardType
    ): ForwardValidation {
        if (selectedMessages.isEmpty()) {
            return ForwardValidation.EMPTY_SELECTION
        }
        val hasUnsentMessage = selectedMessages.any { message ->
            message.status != MessageStatus.SEND_SUCCESS
        }
        if (hasUnsentMessage) {
            return ForwardValidation.CONTAINS_UNSENT_MESSAGE
        }
        if (selectedMessages.any { CallMessageParser.isCallMessage(it) }) {
            return ForwardValidation.CONTAINS_CALL_MESSAGE
        }
        if (forwardType == MessageForwardType.SEPARATE &&
            selectedMessages.size > FORWARD_MESSAGE_COUNT_LIMIT
        ) {
            return ForwardValidation.EXCEEDS_SEPARATE_FORWARD_LIMIT
        }
        return ForwardValidation.ALLOWED
    }
}
