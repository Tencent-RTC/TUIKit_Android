package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager

data class AtomicTextRange<T>(
    val from: Int,
    val to: Int,
    val data: T
) {
    fun contains(position: Int): Boolean {
        return position > from && position <= to
    }

    fun containsRange(start: Int, end: Int): Boolean {
        return from <= start && to >= end
    }

    fun isWrappedBy(start: Int, end: Int): Boolean {
        return (start > from && start < to) || (end > from && end < to)
    }

    fun getAnchorPosition(value: Int): Int {
        return if ((value - from) - (to - value) >= 0) to else from
    }
}

class AtomicEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var onEmptyDeleteKey: (() -> Boolean)? = null

    private var atomicHelper: AtomicTextRangeHelper? = null

    init {
        if (atomicHelper == null) {
            atomicHelper = AtomicTextRangeHelper()
        }
        gravity = Gravity.TOP or Gravity.START
    }

    fun <T> insertAtomicText(text: String, data: T) {
        replaceSelection(text, atomicData = data)
    }

    fun insertText(text: String) {
        replaceSelection(text, atomicData = null)
    }

    fun deleteAtCursor() {
        val helper = getAtomicHelper()
        val currentText = text?.toString().orEmpty()
        val cursorPosition = selectionStart
        if (cursorPosition <= 0 || currentText.isEmpty()) {
            return
        }

        var deletedLength = 1
        for (emojiKey in EmojiManager.sortedLittleEmojiKeyList) {
            if (cursorPosition >= emojiKey.length &&
                currentText.substring(cursorPosition - emojiKey.length, cursorPosition) == emojiKey
            ) {
                deletedLength = emojiKey.length
                break
            }
        }

        val start = cursorPosition - deletedLength
        val newText = currentText.substring(0, start) + currentText.substring(cursorPosition)
        helper.isPaused = true
        setText(newText)
        setSelection(start)
        helper.isPaused = false
        helper.updateRangesOffset(start, -deletedLength)
    }

    fun deleteCharBeforeCursor() {
        deleteAtCursor()
    }

    fun clearAtomicRanges() {
        getAtomicHelper().clearAtomicRanges()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getAtomicRanges(): List<AtomicTextRange<T>> {
        return getAtomicHelper().getAtomicRanges() as? List<AtomicTextRange<T>> ?: emptyList()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        atomicHelper?.onSelectionChanged(selStart, selEnd, this)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        return getAtomicHelper().createAtomicInputConnection(
            super.onCreateInputConnection(outAttrs),
            this
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && text.isNullOrEmpty() && onEmptyDeleteKey?.invoke() == true) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        atomicHelper?.onTextChanged(start, lengthBefore, lengthAfter)
    }

    private fun <T> replaceSelection(text: String, atomicData: T?) {
        val helper = getAtomicHelper()
        val selectionStart = selectionStart.coerceAtLeast(0)
        val selectionEnd = selectionEnd.coerceAtLeast(selectionStart)
        val currentText = this.text?.toString().orEmpty()
        val newText = currentText.substring(0, selectionStart) +
            text +
            currentText.substring(selectionEnd)
        val insertEnd = selectionStart + text.length

        helper.isPaused = true
        setText(newText)
        setSelection(insertEnd)
        helper.isPaused = false

        val delta = text.length - (selectionEnd - selectionStart)
        helper.updateRangesOffset(selectionStart, delta)
        if (atomicData != null) {
            helper.addAtomicRange(selectionStart, insertEnd, atomicData)
        }
    }

    private fun getAtomicHelper(): AtomicTextRangeHelper {
        val helper = atomicHelper
        if (helper != null) {
            return helper
        }
        return AtomicTextRangeHelper().also {
            atomicHelper = it
        }
    }
}

private class AtomicTextRangeHelper {
    private val atomicRanges = mutableListOf<AtomicTextRange<Any>>()
    private var isAtomicSelected = false
    private var lastSelectedRange: AtomicTextRange<Any>? = null

    var isPaused = false

    fun <T> addAtomicRange(from: Int, to: Int, data: T) {
        atomicRanges.add(AtomicTextRange(from, to, data as Any))
        atomicRanges.sortBy { it.from }
    }

    fun getAtomicRanges(): List<AtomicTextRange<Any>> {
        return atomicRanges.toList()
    }

    fun clearAtomicRanges() {
        atomicRanges.clear()
        isAtomicSelected = false
        lastSelectedRange = null
    }

    fun updateRangesOffset(position: Int, delta: Int) {
        if (atomicRanges.isEmpty() || delta == 0) {
            return
        }

        val toRemove = mutableListOf<AtomicTextRange<Any>>()
        val toUpdate = mutableListOf<Pair<Int, AtomicTextRange<Any>>>()

        atomicRanges.forEachIndexed { index, range ->
            when {
                position >= range.to -> Unit
                position <= range.from -> {
                    val newFrom = range.from + delta
                    val newTo = range.to + delta
                    if (newFrom >= 0) {
                        toUpdate.add(index to range.copy(from = newFrom, to = newTo))
                    } else {
                        toRemove.add(range)
                    }
                }

                else -> {
                    toRemove.add(range)
                }
            }
        }

        toUpdate.forEach { (index, newRange) ->
            if (index < atomicRanges.size) {
                atomicRanges[index] = newRange
            }
        }
        atomicRanges.removeAll(toRemove)
    }

    fun createAtomicInputConnection(
        target: InputConnection?,
        editText: AtomicEditText
    ): InputConnection {
        return AtomicInputConnection(target, editText)
    }

    fun onSelectionChanged(selStart: Int, selEnd: Int, editText: AtomicEditText) {
        if (selStart < 0 || selEnd < 0) {
            return
        }
        val textLength = editText.text?.length ?: return
        if (selStart > textLength || selEnd > textLength) {
            return
        }

        val selectedRange = lastSelectedRange
        if (selectedRange != null &&
            ((selectedRange.from == selStart && selectedRange.to == selEnd) ||
                (selectedRange.from == selEnd && selectedRange.to == selStart))
        ) {
            return
        }

        val closestRange = atomicRanges.find { it.containsRange(selStart, selEnd) }
        if (closestRange?.to == selEnd) {
            isAtomicSelected = false
        }

        val nearbyRange = atomicRanges.find { it.isWrappedBy(selStart, selEnd) } ?: return
        try {
            if (selStart == selEnd) {
                val anchorPosition = nearbyRange.getAnchorPosition(selStart)
                if (anchorPosition in 0..textLength) {
                    editText.setSelection(anchorPosition)
                }
            } else {
                if (selEnd < nearbyRange.to && nearbyRange.to <= textLength) {
                    editText.setSelection(selStart, nearbyRange.to)
                }
                if (selStart > nearbyRange.from && nearbyRange.from >= 0) {
                    editText.setSelection(nearbyRange.from, selEnd)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun onTextChanged(start: Int, before: Int, count: Int) {
        if (isPaused || atomicRanges.isEmpty()) {
            return
        }

        val delta = count - before
        val deleteEnd = start + before
        val toRemove = mutableListOf<AtomicTextRange<Any>>()
        val toUpdate = mutableListOf<Pair<Int, AtomicTextRange<Any>>>()

        atomicRanges.forEachIndexed { index, range ->
            when {
                start >= range.to -> Unit
                deleteEnd <= range.from -> {
                    if (delta != 0) {
                        val newFrom = range.from + delta
                        val newTo = range.to + delta
                        if (newFrom >= 0) {
                            toUpdate.add(index to range.copy(from = newFrom, to = newTo))
                        } else {
                            toRemove.add(range)
                        }
                    }
                }

                else -> {
                    toRemove.add(range)
                }
            }
        }

        toUpdate.forEach { (index, newRange) ->
            if (index < atomicRanges.size) {
                atomicRanges[index] = newRange
            }
        }
        atomicRanges.removeAll(toRemove)
    }

    private inner class AtomicInputConnection(
        target: InputConnection?,
        private val editText: AtomicEditText
    ) : InputConnectionWrapper(target, true) {

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                val selectionStart = editText.selectionStart
                val selectionEnd = editText.selectionEnd
                val closestRange = atomicRanges.find {
                    it.containsRange(selectionStart, selectionEnd)
                }

                if (closestRange == null) {
                    isAtomicSelected = false
                    return super.sendKeyEvent(event)
                }

                if (isAtomicSelected || selectionStart == closestRange.from) {
                    isAtomicSelected = false
                    return super.sendKeyEvent(event)
                }

                isAtomicSelected = true
                lastSelectedRange = closestRange
                editText.setSelection(closestRange.to, closestRange.from)
                return sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }
            return super.sendKeyEvent(event)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0) {
                return sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)) &&
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }
    }
}
