package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class MessageListDialogStateStore {
    private val _longPressActionMessage = MutableStateFlow<MessageInfo?>(null)
    val longPressActionMessage: StateFlow<MessageInfo?> = _longPressActionMessage.asStateFlow()

    private val _onSingleMessageForward = MutableStateFlow<MessageInfo?>(null)
    val onSingleMessageForward: StateFlow<MessageInfo?> = _onSingleMessageForward.asStateFlow()

    private val _readReceiptMessage = MutableStateFlow<MessageInfo?>(null)
    val readReceiptMessage: StateFlow<MessageInfo?> = _readReceiptMessage.asStateFlow()

    private val _showEmojiPickerForMessage = MutableStateFlow<MessageInfo?>(null)
    val showEmojiPickerForMessage: StateFlow<MessageInfo?> = _showEmojiPickerForMessage.asStateFlow()

    fun showLongPressAction(message: MessageInfo) {
        _longPressActionMessage.value = message
    }

    fun clearLongPressAction() {
        _longPressActionMessage.value = null
    }

    fun showSingleMessageForward(message: MessageInfo) {
        _onSingleMessageForward.value = message
    }

    fun clearSingleMessageForward() {
        _onSingleMessageForward.value = null
    }

    fun showReadReceipt(message: MessageInfo) {
        _readReceiptMessage.value = message
    }

    fun clearReadReceipt() {
        _readReceiptMessage.value = null
    }

    fun showEmojiPicker(message: MessageInfo) {
        _showEmojiPickerForMessage.value = message
    }

    fun clearEmojiPicker() {
        _showEmojiPickerForMessage.value = null
    }
}
