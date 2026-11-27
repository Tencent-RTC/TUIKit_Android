package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.trtc.tuikit.chat.uikit.components.messagelist.typing.TypingIndicatorController
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val LocalMessageListController = staticCompositionLocalOf<MessageListController?> { null }

class MessageListController {
    internal var viewModel: MessageListViewModel? = null

    internal var typingController: TypingIndicatorController? by mutableStateOf(null)

    fun exitMultiSelectMode() {
        viewModel?.exitMultiSelectMode()
    }

    fun isInMultiSelectMode(): Boolean {
        return viewModel?.isMultiSelectMode?.value == true
    }

    fun sendTypingStatus(isTyping: Boolean) {
        typingController?.sendTypingStatus(isTyping)
    }

    val typingState: StateFlow<Boolean>
        get() = typingController?.typingState ?: inactiveTypingState

    private companion object {
        val inactiveTypingState = MutableStateFlow(false)
    }
}

@Composable
fun rememberMessageListController(): MessageListController {
    return remember { MessageListController() }
}
