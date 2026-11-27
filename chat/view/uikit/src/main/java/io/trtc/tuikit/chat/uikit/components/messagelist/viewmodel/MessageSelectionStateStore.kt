package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class MessageSelectionStateStore {
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedMessages = MutableStateFlow<Set<MessageInfo>>(emptySet())
    val selectedMessages: StateFlow<Set<MessageInfo>> = _selectedMessages.asStateFlow()

    fun enter(initialMessage: MessageInfo? = null) {
        _isMultiSelectMode.value = true
        _selectedMessages.value = if (initialMessage != null) setOf(initialMessage) else emptySet()
    }

    fun exit() {
        _isMultiSelectMode.value = false
        _selectedMessages.value = emptySet()
    }

    fun toggle(message: MessageInfo) {
        val currentSet = _selectedMessages.value
        _selectedMessages.value = if (currentSet.contains(message)) currentSet - message else currentSet + message
    }
}
