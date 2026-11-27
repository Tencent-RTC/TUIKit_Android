package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputMode
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputSurfaceState
import io.trtc.tuikit.chat.uikit.components.messageinput.state.QuoteInfo
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal data class MessageInputActionVisibility(
    val showMore: Boolean,
    val showSend: Boolean,
    val showStop: Boolean
)

internal object MessageInputActionVisibilityPolicy {
    fun resolve(
        hasText: Boolean,
        isTextMode: Boolean,
        isShowMore: Boolean,
        isChatbotActive: Boolean
    ): MessageInputActionVisibility {
        if (isChatbotActive) {
            return MessageInputActionVisibility(
                showMore = false,
                showSend = false,
                showStop = true
            )
        }
        val showSend = hasText && isTextMode
        return MessageInputActionVisibility(
            showMore = !showSend && isShowMore,
            showSend = showSend,
            showStop = false
        )
    }
}

internal object MessageInputTextAffordancePolicy {
    fun shouldHideInputHint(
        surface: InputSurfaceState,
        keyboardHeight: Int
    ): Boolean {
        return surface != InputSurfaceState.NONE || keyboardHeight > 0
    }
}

internal object MessageInputQuoteEditingPolicy {
    fun shouldClearQuoteOnEmptyDelete(
        hasQuote: Boolean,
        inputText: String
    ): Boolean {
        return hasQuote && inputText.isEmpty()
    }
}

internal object MessageInputQuoteEventPolicy {
    fun resolveQuoteInfo(
        event: Map<*, *>,
        currentConversationID: String?
    ): QuoteInfo? {
        val eventConversationID = event[KEY_CONVERSATION_ID] as? String
        if (!eventConversationID.isNullOrBlank() && eventConversationID != currentConversationID) {
            return null
        }
        val message = event[KEY_MESSAGE] as? MessageInfo ?: return null
        val messageId = message.msgID?.takeIf { it.isNotBlank() } ?: return null
        val summary = (event[KEY_SUMMARY] as? String)?.takeIf { it.isNotBlank() }.orEmpty()
        return QuoteInfo(
            messageId = messageId,
            senderName = message.senderDisplayName,
            summary = summary,
            messageInfo = message
        )
    }

    private const val KEY_CONVERSATION_ID = "conversationID"
    private const val KEY_MESSAGE = "message"
    private const val KEY_SUMMARY = "summary"
}

internal class MessageInputDraftPolicy {
    private var isDraftClearPending = false

    fun reset() {
        isDraftClearPending = false
    }

    fun markDraftClearPending() {
        isDraftClearPending = true
    }

    fun shouldApplyIncomingDraft(currentInput: String, draft: String?): Boolean {
        if (draft.isNullOrEmpty()) {
            isDraftClearPending = false
            return false
        }
        if (isDraftClearPending) {
            return false
        }
        return currentInput.isEmpty()
    }

    fun shouldSaveDraft(currentInput: String, hasUserEditedText: Boolean): Boolean {
        return currentInput.isNotEmpty() || hasUserEditedText
    }
}

internal object MessageInputLongPressRecordingPolicy {
    fun shouldArmRecording(
        inputMode: InputMode,
        surface: InputSurfaceState,
        isLongPressToTalkEnabled: Boolean,
        hasInputText: Boolean,
    ): Boolean {
        return isLongPressToTalkEnabled &&
            !hasInputText &&
            inputMode == InputMode.TEXT &&
            surface == InputSurfaceState.NONE
    }
}

internal object MessageInputEvents {
    const val SOURCE = "MessageInput"
    const val EVENT_INPUT_INTERACT = "onInputInteract"
    const val MESSAGE_LIST_SOURCE = "MessageList"
    const val BLANK_AREA_CLICK_EVENT = "onBlankAreaClick"
    const val QUOTE_MESSAGE_EVENT = "onQuoteMessage"
    const val USER_LONG_PRESS_EVENT = "onUserLongPress"

    fun postInputInteract() {
        EventBus.post(
            mapOf(
                "source" to SOURCE,
                "event" to EVENT_INPUT_INTERACT
            )
        )
    }
}

internal object MentionTriggerDetector {
    fun shouldTrigger(previousText: String, newText: String): Boolean {
        if (newText.length != previousText.length + 1) return false
        val insertPosition = newText.indices.firstOrNull { index ->
            index >= previousText.length || newText[index] != previousText[index]
        } ?: previousText.length
        return isTriggerChar(newText.getOrNull(insertPosition))
    }

    fun isTriggerChar(char: Char?): Boolean {
        return char == '@' || char == '＠'
    }
}
