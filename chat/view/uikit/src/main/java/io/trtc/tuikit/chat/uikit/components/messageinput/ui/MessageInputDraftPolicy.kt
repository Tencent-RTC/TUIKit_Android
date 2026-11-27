package io.trtc.tuikit.chat.uikit.components.messageinput.ui

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
