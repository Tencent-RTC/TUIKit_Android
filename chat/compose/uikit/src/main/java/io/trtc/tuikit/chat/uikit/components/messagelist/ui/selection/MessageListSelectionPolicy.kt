package io.trtc.tuikit.chat.uikit.components.messagelist.ui.selection

import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.FORWARD_MESSAGE_COUNT_LIMIT
import io.trtc.tuikit.atomicxcore.api.message.MessageForwardType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

internal object MessageListSelectionPolicy {
    enum class ForwardValidation {
        ALLOWED,
        EMPTY_SELECTION,
        CONTAINS_UNSENT_MESSAGE,
        CONTAINS_CALL_MESSAGE,
        EXCEEDS_SEPARATE_FORWARD_LIMIT
    }

    fun validateForwardSelection(
        selectedMessages: List<MessageInfo>,
        forwardType: MessageForwardType
    ): ForwardValidation {
        if (selectedMessages.isEmpty()) {
            return ForwardValidation.EMPTY_SELECTION
        }
        val hasUnsentMessage = selectedMessages.any { message ->
            message.status != MessageStatus.SEND_SUCCESS
        }
        if (hasUnsentMessage) {
            return ForwardValidation.CONTAINS_UNSENT_MESSAGE
        }
        if (selectedMessages.any { CallMessageParser.isCallMessage(it) }) {
            return ForwardValidation.CONTAINS_CALL_MESSAGE
        }
        if (forwardType == MessageForwardType.SEPARATE &&
            selectedMessages.size > FORWARD_MESSAGE_COUNT_LIMIT
        ) {
            return ForwardValidation.EXCEEDS_SEPARATE_FORWARD_LIMIT
        }
        return ForwardValidation.ALLOWED
    }
}
