package io.trtc.tuikit.chat.uikit.components.messageinput.ui
internal object MessageInputQuoteEditingPolicy {
    fun shouldClearQuoteOnEmptyDelete(
        hasQuote: Boolean,
        inputText: String
    ): Boolean {
        return hasQuote && inputText.isEmpty()
    }
}
