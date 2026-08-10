package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputUiState
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputViewModel

internal class MessageInputTextController(
    private val context: Context,
    private val editText: AtomicEditText,
    private val configProvider: () -> MessageInputConfigProtocol,
    private val viewModelProvider: () -> MessageInputViewModel?,
    private val isCoordinatorInitialized: () -> Boolean,
    private val currentState: () -> InputUiState,
    private val updateSendButtonVisibility: (InputUiState) -> Unit,
    private val clearQuote: () -> Unit,
    private val onMentionTrigger: () -> Unit,
    private val onTypingContentChanged: (Boolean) -> Unit = {},
    initialInputText: String = ""
) {
    var inputText: String = initialInputText
        private set

    var hasUserEditedText: Boolean = false
        private set

    private var isProgrammaticInsert = false

    fun setupTextWatcher() {
        editText.onEmptyDeleteKey = {
            val shouldClearQuote = MessageInputQuoteEditingPolicy.shouldClearQuoteOnEmptyDelete(
                hasQuote = currentState().overlay.quoteMessage != null,
                inputText = inputText
            )
            if (shouldClearQuote) {
                clearQuote()
            }
            shouldClearQuote
        }
        editText.addTextChangedListener(object : TextWatcher {
            private var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                previousText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val newText = s?.toString() ?: ""
                if (shouldShowMentionDialog(previousText, newText)) {
                    onMentionTrigger()
                }
                if (!isProgrammaticInsert) {
                    hasUserEditedText = true
                    onTypingContentChanged(newText.isNotEmpty())
                }
                inputText = newText
                refreshSendButtonIfReady()
                if (!isProgrammaticInsert && EmojiManager.containsEmojiKey(newText)) {
                    EmojiSpanHelper.processEditTextEmoji(editText)
                }
            }
        })
    }

    fun sendCurrentText(onSubmitted: () -> Unit = {}) {
        val text = editText.text.toString()
        if (text.isNotEmpty()) {
            val mentionList = editText.getAtomicRanges<MentionInfo>().map { it.data }
            val quotedMessage = currentState().overlay.quoteMessage?.toMessageInfo()
            viewModelProvider()?.sendTextMessage(
                context = context,
                text = text,
                mentionList = mentionList,
                quotedMessage = quotedMessage,
                onSuccess = clearQuote
            )
            onSubmitted()
            editText.setText("")
            editText.clearAtomicRanges()
            inputText = ""
            refreshSendButtonIfReady()
        }
    }

    fun sendFaceMessage(faceIndex: Int, faceData: String) {
        val quotedMessage = if (isCoordinatorInitialized()) {
            currentState().overlay.quoteMessage?.toMessageInfo()
        } else {
            null
        }
        viewModelProvider()?.sendFaceMessage(
            context = context,
            faceIndex = faceIndex,
            faceData = faceData,
            quotedMessage = quotedMessage,
            onSuccess = clearQuote
        )
    }

    fun resetInputText() {
        inputText = ""
        hasUserEditedText = false
        runProgrammaticTextEdit {
            editText.setText("")
            editText.clearAtomicRanges()
        }
        refreshSendButtonIfReady()
    }

    fun applyDraftIfEmpty(draft: String) {
        if (inputText.isNotEmpty() || draft.isEmpty()) return
        inputText = draft
        runProgrammaticTextEdit {
            editText.setText(draft)
            editText.clearAtomicRanges()
        }
        EmojiSpanHelper.processEditTextEmoji(editText)
        refreshSendButtonIfReady()
    }

    fun insertMention(mentionInfo: MentionInfo) {
        runProgrammaticTextEdit {
            editText.insertAtomicText(mentionInfo.mentionText, mentionInfo)
            EmojiSpanHelper.processEditTextEmoji(editText)
        }
    }

    fun insertMentionsReplacingTrigger(selectedMentions: List<MentionInfo>) {
        if (selectedMentions.isEmpty()) return
        runProgrammaticTextEdit {
            val currentText = editText.text?.toString().orEmpty()
            val cursorPosition = editText.selectionStart.coerceAtLeast(0)
            if (cursorPosition > 0) {
                val previousChar = currentText.getOrNull(cursorPosition - 1)
                if (MentionTriggerDetector.isTriggerChar(previousChar)) {
                    editText.deleteCharBeforeCursor()
                }
            }
            selectedMentions.forEach { mention ->
                editText.insertAtomicText(mention.mentionText, mention)
            }
            EmojiSpanHelper.processEditTextEmoji(editText)
        }
    }

    private fun shouldShowMentionDialog(previousText: String, newText: String): Boolean {
        if (isProgrammaticInsert || !configProvider().enableMention) return false
        val isGroupChat = viewModelProvider()?.conversationID?.startsWith("group_") == true
        return isGroupChat && MentionTriggerDetector.shouldTrigger(previousText, newText)
    }

    private fun refreshSendButtonIfReady() {
        if (shouldRefreshSendButtonOnTextChanged(isCoordinatorInitialized())) {
            updateSendButtonVisibility(currentState())
        }
    }

    private inline fun runProgrammaticTextEdit(action: () -> Unit) {
        isProgrammaticInsert = true
        try {
            action()
        } finally {
            isProgrammaticInsert = false
        }
    }
}

internal object MentionTriggerDetector {
    fun shouldTrigger(previousText: String, newText: String): Boolean {
        if (newText.length <= previousText.length) return false
        val insertedLength = newText.length - previousText.length
        val insertPosition = newText.indices.firstOrNull { index ->
            index >= previousText.length || newText[index] != previousText[index]
        } ?: previousText.length
        val endPosition = (insertPosition + insertedLength).coerceAtMost(newText.length)
        val insertedText = newText.substring(insertPosition, endPosition)
        return insertedText.any(::isTriggerChar)
    }

    fun isTriggerChar(char: Char?): Boolean {
        return char == '@' || char == '\uFF20'
    }
}
