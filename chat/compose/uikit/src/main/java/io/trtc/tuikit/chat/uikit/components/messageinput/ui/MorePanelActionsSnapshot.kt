package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction

internal class MorePanelActionsSnapshot {
    private var hasLoaded: Boolean = false
    private var wasExpanded: Boolean = false
    private var lastStateKey: Any? = null
    private var actions: List<MessageInputMenuAction> = emptyList()

    fun resolve(
        expanded: Boolean,
        stateKey: Any? = null,
        load: () -> List<MessageInputMenuAction>,
    ): List<MessageInputMenuAction> {
        if (!hasLoaded || (expanded && !wasExpanded) || stateKey != lastStateKey) {
            actions = load()
            hasLoaded = true
            lastStateKey = stateKey
        }
        wasExpanded = expanded
        return actions
    }
}
