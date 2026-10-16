package com.trtc.uikit.livekit.common.theme

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.Selection
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatEditText
import com.trtc.uikit.livekit.common.theme.toast.LiveToast
import java.text.BreakIterator

/** Multiline input with a character limit applied only after IME composition commits. */
internal open class LiveTextInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {
    enum class LimitUnit { CHARACTERS, UTF8_BYTES }

    private var inputLimit = Int.MAX_VALUE
    private var limitUnit = LimitUnit.CHARACTERS
    private var limitMessage = 0
    private var isApplyingLimit = false
    private var committedText = ""
    private var editBefore = ""
    private var editStart = 0
    private var editRemoved = 0
    private var editAdded = 0
    private val placeholder = hint

    init {
        setHorizontallyScrolling(false)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isApplyingLimit) return
                editBefore = s?.toString().orEmpty()
                editStart = start
                editRemoved = count
                editAdded = after
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                enforceLimit()
            }
        })
    }

    fun setInputLimit(limit: Int, @StringRes message: Int, unit: LimitUnit = LimitUnit.CHARACTERS) {
        inputLimit = limit
        limitUnit = unit
        limitMessage = message
        enforceLimit()
    }

    val isComposing: Boolean
        get() = text?.let { BaseInputConnection.getComposingSpanStart(it) >= 0 } == true

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!placeholder.isNullOrEmpty() && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val width = (MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0)
            val fitted = TextUtils.ellipsize(placeholder, paint, width.toFloat(), TextUtils.TruncateAt.END)
            if (!TextUtils.equals(hint, fitted)) hint = fitted
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        // Multiline text still uses the owner's Done/Send action.
        outAttrs.imeOptions = outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()
        return object : InputConnectionWrapper(connection, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val result = super.commitText(text, newCursorPosition)
                enforceLimit()
                return result
            }

            override fun finishComposingText(): Boolean {
                val result = super.finishComposingText()
                enforceLimit()
                return result
            }
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) enforceLimit()
    }

    override fun focusSearch(direction: Int): View? = null

    private fun enforceLimit() {
        val editable = text ?: return
        if (isApplyingLimit || isComposing) return
        val value = editable.toString()
        val valueCharacters = characters(value)
        if (inputLength(value) <= inputLimit) {
            committedText = value
            return
        }
        val before = characters(committedText)
        val prefixCount = before.zip(valueCharacters).takeWhile { it.first == it.second }.size
        val suffixCount = before.drop(prefixCount).asReversed()
            .zip(valueCharacters.drop(prefixCount).asReversed()).takeWhile { it.first == it.second }.size
        var prefix = valueCharacters.take(prefixCount).joinToString("")
        var suffix = valueCharacters.takeLast(suffixCount).joinToString("")
        var insertion = valueCharacters.drop(prefixCount).dropLast(suffixCount).joinToString("")
        if (editStart + editRemoved <= editBefore.length && editStart + editAdded <= value.length &&
            inputLength(editBefore) <= inputLimit
        ) {
            val originalPrefix = editBefore.substring(0, editStart)
            val originalSuffix = editBefore.substring(editStart + editRemoved)
            if (value == originalPrefix + value.substring(editStart, editStart + editAdded) + originalSuffix) {
                prefix = originalPrefix
                suffix = originalSuffix
                insertion = value.substring(editStart, editStart + editAdded)
            }
        }
        val available = (inputLimit - inputLength(prefix) - inputLength(suffix)).coerceAtLeast(0)
        val accepted = limitedPrefix(insertion, available)
        isApplyingLimit = true
        editable.replace(0, editable.length, prefix + accepted + suffix)
        Selection.setSelection(editable, prefix.length + accepted.length)
        isApplyingLimit = false
        committedText = editable.toString()
        if (limitMessage != 0 && isAttachedToWindow) {
            LiveToast.show(context, context.getString(limitMessage, inputLimit))
        }
    }

    private fun inputLength(value: String): Int = when (limitUnit) {
        LimitUnit.CHARACTERS -> characters(value).size
        LimitUnit.UTF8_BYTES -> value.toByteArray(Charsets.UTF_8).size
    }

    private fun limitedPrefix(value: String, budget: Int): String {
        var remaining = budget.coerceAtLeast(0)
        return characters(value).takeWhile { character ->
            val length = inputLength(character)
            if (length <= remaining) {
                remaining -= length
                true
            } else {
                false
            }
        }.joinToString("")
    }

    private fun characters(value: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(value) }
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result.add(value.substring(start, end))
            start = end
            end = iterator.next()
        }
        return result
    }
}
