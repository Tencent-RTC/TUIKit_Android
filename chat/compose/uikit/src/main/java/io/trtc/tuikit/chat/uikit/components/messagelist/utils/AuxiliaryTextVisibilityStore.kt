package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuxiliaryTextVisibilityStore {
    private val _hiddenMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenMessageIds: StateFlow<Set<String>> = _hiddenMessageIds.asStateFlow()

    fun hide(msgID: String) {
        _hiddenMessageIds.value = _hiddenMessageIds.value + msgID
    }

    fun unhide(msgID: String) {
        _hiddenMessageIds.value = _hiddenMessageIds.value - msgID
    }

    fun isHidden(msgID: String): Boolean {
        return _hiddenMessageIds.value.contains(msgID)
    }

    fun clear() {
        _hiddenMessageIds.value = emptySet()
    }
}
