package io.trtc.tuikit.chat.uikit.components.messagelist.utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuxiliaryTextVisibilityStore {
    private val hiddenMessageIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val hiddenMessageIds: StateFlow<Set<String>> = hiddenMessageIdsFlow.asStateFlow()

    fun hide(messageId: String) {
        hiddenMessageIdsFlow.value = hiddenMessageIdsFlow.value + messageId
    }

    fun unhide(messageId: String) {
        hiddenMessageIdsFlow.value = hiddenMessageIdsFlow.value - messageId
    }

    fun isHidden(messageId: String): Boolean {
        return hiddenMessageIdsFlow.value.contains(messageId)
    }

    fun clear() {
        hiddenMessageIdsFlow.value = emptySet()
    }
}
