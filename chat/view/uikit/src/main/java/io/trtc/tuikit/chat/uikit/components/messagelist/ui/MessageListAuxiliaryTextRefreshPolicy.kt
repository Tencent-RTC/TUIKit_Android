package io.trtc.tuikit.chat.uikit.components.messagelist.ui
internal object MessageListAuxiliaryTextRefreshPolicy {
    fun changedMessageIds(previous: Set<String>, current: Set<String>): List<String> {
        return ((previous - current) + (current - previous)).toList()
    }
}
