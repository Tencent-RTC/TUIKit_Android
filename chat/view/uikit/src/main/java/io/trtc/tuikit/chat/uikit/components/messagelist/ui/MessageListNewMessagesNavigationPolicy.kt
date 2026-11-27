package io.trtc.tuikit.chat.uikit.components.messagelist.ui

internal enum class MessageListNewMessagesNavigationAction {
    LocateLoadedMessage,
    ReloadAroundMessage
}

internal object MessageListNewMessagesNavigationPolicy {
    fun action(isFirstNewMessageLoaded: Boolean): MessageListNewMessagesNavigationAction {
        return if (isFirstNewMessageLoaded) {
            MessageListNewMessagesNavigationAction.LocateLoadedMessage
        } else {
            MessageListNewMessagesNavigationAction.ReloadAroundMessage
        }
    }
}
