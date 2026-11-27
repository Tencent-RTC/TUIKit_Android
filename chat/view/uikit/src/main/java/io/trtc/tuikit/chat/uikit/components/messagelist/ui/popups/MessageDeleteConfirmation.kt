package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageActionIDs
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction

internal fun List<MessageCustomAction>.withDeleteConfirmation(
    onDeleteRequested: (MessageInfo, onConfirm: () -> Unit) -> Unit
): List<MessageCustomAction> {
    return map { action ->
        if (action.ID == MessageActionIDs.DELETE) {
            action.copy(
                action = { message ->
                    onDeleteRequested(message) {
                        action.action(message)
                    }
                }
            )
        } else {
            action
        }
    }
}
