package io.trtc.tuikit.chat.uikit.components.messagelist.ui

internal enum class MessageListBackToLatestAction {
    ReloadLatestMessages,
    ScrollLoadedLatest
}

internal object MessageListBackToLatestPolicy {
    fun action(hasMoreNewerMessages: Boolean): MessageListBackToLatestAction {
        return if (hasMoreNewerMessages) {
            MessageListBackToLatestAction.ReloadLatestMessages
        } else {
            MessageListBackToLatestAction.ScrollLoadedLatest
        }
    }
}
