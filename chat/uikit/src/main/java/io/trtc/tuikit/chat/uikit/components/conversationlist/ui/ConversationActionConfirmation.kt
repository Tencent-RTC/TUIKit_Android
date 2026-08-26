package io.trtc.tuikit.chat.uikit.components.conversationlist.ui

import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationActionIDs
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationCustomAction

internal fun List<ConversationCustomAction>.withDestructiveActionConfirmation(
    onConfirmationRequested: (actionID: String, onConfirm: () -> Unit) -> Unit
): List<ConversationCustomAction> {
    return map { action ->
        if (action.ID == ConversationActionIDs.DELETE ||
            action.ID == ConversationActionIDs.CLEAR_HISTORY
        ) {
            action.copy(
                action = { conversation ->
                    onConfirmationRequested(action.ID) {
                        action.action(conversation)
                    }
                }
            )
        } else {
            action
        }
    }
}
