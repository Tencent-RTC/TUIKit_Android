package com.trtc.uikit.livekit.common.barrage

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.barrage.emoji.BarrageEmoji
import com.trtc.uikit.livekit.common.barrage.emoji.BarrageEmojiConverter
import com.trtc.uikit.livekit.common.barrage.emoji.BarrageEmojiPanelView
import com.trtc.uikit.livekit.common.theme.LiveTextInputView

class BarrageInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val MESSAGE_LIST_GAP_DP = 6
        private const val INSETS_DURATION_MS = 220L
        private const val MODE_DURATION_MS = 250L
        private const val CHARACTER_LIMIT = 200

        internal fun shouldUsePlatformImeAnimation(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.R
    }

    private enum class InputMode { KEYBOARD, EMOJI }

    private val inputBar: View
    private val editText: CommentEditText
    private val emojiButton: AppCompatImageView
    private val emojiPanel: BarrageEmojiPanelView
    private val keyboardPlaceholder: View

    private var liveID = ""
    private var inputBarBottomMargin = 0
    private var inputMode = InputMode.KEYBOARD
    private var navigationBottom = 0
    private var emojiPanelHeight = resources.getDimensionPixelSize(R.dimen.livekit_barrage_emoji_panel_initial_height)

    private var originalSoftInputMode: Int? = null
    private var originalSoftInputWindow: Window? = null

    /** True between [show] and the first moment the host window is able to raise the IME. */
    private var isKeyboardRequestPending = false
    private var isAwaitingKeyboard = false

    /**
     * True while the platform runs an IME animation. The framework dispatches the final insets once
     * before the animation starts, so honouring them here would snap the input bar to the end
     * position and let [WindowInsetsAnimationCompat.Callback.onProgress] drag it back.
     */
    private var isImeAnimating = false

    /** Smooths the single-shot inset update on platforms without IME animation callbacks. */
    private var insetsAnimator: ValueAnimator? = null

    /**
     * Raised whenever the composer is shown, dismissed or follows the keyboard, so the owner can
     * move the message list. [messageListBottom] is the bottom margin the message list needs while
     * the composer is presented.
     */
    var onPresentationChanged: ((isPresented: Boolean, animated: Boolean, messageListBottom: Int) -> Unit)? = null
    var onSend: ((String) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_live_comment_composer_view, this, true)
        inputBar = findViewById(R.id.livekit_pusher_comment_input_bar)
        editText = findViewById(R.id.livekit_pusher_comment_input)
        editText.isSaveEnabled = false
        emojiButton = findViewById(R.id.livekit_pusher_comment_emoji)
        emojiPanel = findViewById(R.id.livekit_pusher_comment_emoji_panel)
        keyboardPlaceholder = findViewById(R.id.livekit_pusher_comment_keyboard_placeholder)

        visibility = GONE
        isClickable = true
        isFocusable = true
        setOnClickListener { dismiss() }
        inputBar.setOnClickListener { }
        keyboardPlaceholder.setOnClickListener { }
        emojiPanel.setOnClickListener { }
        emojiButton.setOnClickListener {
            setInputMode(if (inputMode == InputMode.KEYBOARD) InputMode.EMOJI else InputMode.KEYBOARD)
        }
        editText.setInputLimit(CHARACTER_LIMIT, R.string.livekit_comment_character_limit)
        editText.characterLength = BarrageEmojiConverter::visibleLength
        editText.characterPrefix = BarrageEmojiConverter::visiblePrefix
        editText.deleteRequest = { deleteFromInput() }
        editText.dismissRequest = { dismiss() }
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && inputMode == InputMode.EMOJI) setInputMode(InputMode.KEYBOARD)
        }
        emojiPanel.onSelect = { insertEmoji(it) }
        emojiPanel.onDelete = { deleteFromInput() }
        emojiPanel.onSend = { send() }
        editText.setOnEditorActionListener { _, actionID, event ->
            val isSendAction = actionID == EditorInfo.IME_ACTION_SEND
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (isSendAction || isEnter) {
                send()
                true
            } else {
                false
            }
        }
        editText.addTextChangedListener(SimpleTextWatcher { updateTextState() })
        updateTextState()
        inputBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (visibility == VISIBLE && bottom - top != oldBottom - oldTop) {
                notifyPresentationChanged(isPresented = true, animated = false)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (inputMode == InputMode.KEYBOARD) captureKeyboardHeight(insets)
            updatePanelBounds()
            if (!isImeAnimating) {
                applyBottomInset(bottomInsetOf(insets), animated = true)
                handleKeyboardVisibility(insets)
            }
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            this,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    val isIme = animation.typeMask and WindowInsetsCompat.Type.ime() != 0
                    if (isIme && shouldUsePlatformImeAnimation(Build.VERSION.SDK_INT)) {
                        insetsAnimator?.cancel()
                        isImeAnimating = true
                    }
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    if (isImeAnimating) applyBottomInset(bottomInsetOf(insets), animated = false)
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    val isIme = animation.typeMask and WindowInsetsCompat.Type.ime() != 0
                    if (isIme && shouldUsePlatformImeAnimation(Build.VERSION.SDK_INT)) {
                        isImeAnimating = false
                        ViewCompat.getRootWindowInsets(this@BarrageInputView)?.let {
                            if (inputMode == InputMode.KEYBOARD) captureKeyboardHeight(it)
                            applyBottomInset(bottomInsetOf(it), animated = false)
                            handleKeyboardVisibility(it)
                        }
                    }
                }
            },
        )
    }

    fun bind(liveID: String) {
        if (this.liveID != liveID) {
            editText.text = null
            dismiss(notify = false)
        }
        this.liveID = liveID
    }

    fun show() {
        show(emojiDirectly = false)
    }

    fun show(emojiDirectly: Boolean) {
        if (visibility == VISIBLE || liveID.isEmpty()) return
        if (emojiDirectly) emojiPanel.resetScrollPosition()
        inputMode = if (emojiDirectly) InputMode.EMOJI else InputMode.KEYBOARD
        applyInsetsSoftInputMode()
        ViewCompat.getRootWindowInsets(this)?.let {
            navigationBottom = it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (inputMode == InputMode.KEYBOARD) captureKeyboardHeight(it)
            updatePanelBounds()
            applyBottomInset(bottomInsetOf(it), animated = false)
        }
        renderInputMode(animated = false)
        visibility = VISIBLE
        notifyPresentationChanged(isPresented = true, animated = true)
        if (emojiDirectly) {
            isKeyboardRequestPending = false
            isAwaitingKeyboard = false
            editText.clearFocus()
            hideKeyboard()
        } else {
            isKeyboardRequestPending = true
            isAwaitingKeyboard = true
            if (hasWindowFocus()) showKeyboard()
        }
    }

    fun dismiss(): Boolean {
        if (visibility != VISIBLE) return false
        dismiss(notify = true)
        return true
    }

    internal fun reset() {
        editText.text = null
        dismiss()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && isKeyboardRequestPending) showKeyboard()
    }

    override fun onDetachedFromWindow() {
        dismiss(notify = false)
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDismissKey = event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE
        if (visibility == VISIBLE && isDismissKey && event.action == KeyEvent.ACTION_UP) {
            dismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setInputMode(mode: InputMode) {
        val shouldRender = inputMode != mode
        inputMode = mode
        if (shouldRender) {
            insetsAnimator?.cancel()
            insetsAnimator = null
            renderInputMode(animated = true)
        }
        when (mode) {
            InputMode.KEYBOARD -> {
                isKeyboardRequestPending = true
                isAwaitingKeyboard = true
                if (hasWindowFocus()) showKeyboard()
            }

            InputMode.EMOJI -> {
                ViewCompat.getRootWindowInsets(this)?.let { captureKeyboardHeight(it) }
                isKeyboardRequestPending = false
                isAwaitingKeyboard = false
                updatePanelBounds()
                applyBottomInset(emojiPanelHeight + navigationBottom, animated = false)
                editText.clearFocus()
                hideKeyboard()
            }
        }
    }

    private fun renderInputMode(animated: Boolean) {
        val isEmoji = inputMode == InputMode.EMOJI
        emojiButton.setImageResource(
            if (isEmoji) R.drawable.live_barrage_softkeyboard else R.drawable.live_emoji_icon,
        )
        emojiButton.contentDescription = context.getString(
            if (isEmoji) R.string.livekit_barrage_emoji_keyboard else R.string.livekit_barrage_emoji_title,
        )
        keyboardPlaceholder.animate().cancel()
        keyboardPlaceholder.isClickable = !isEmoji
        keyboardPlaceholder.isFocusable = !isEmoji
        val alpha = if (isEmoji) 0f else 1f
        if (animated) {
            keyboardPlaceholder.animate().alpha(alpha).setDuration(MODE_DURATION_MS).start()
        } else {
            keyboardPlaceholder.alpha = alpha
        }
    }

    private fun showKeyboard() {
        isKeyboardRequestPending = false
        if (visibility != VISIBLE || inputMode != InputMode.KEYBOARD) return
        editText.requestFocus()
        insetsController()?.show(WindowInsetsCompat.Type.ime())
    }

    private fun hideKeyboard() {
        isKeyboardRequestPending = false
        insetsController()?.hide(WindowInsetsCompat.Type.ime())
    }

    private fun handleKeyboardVisibility(insets: WindowInsetsCompat) {
        if (visibility != VISIBLE || inputMode != InputMode.KEYBOARD) return
        if (isKeyboardVisible(insets)) {
            isAwaitingKeyboard = false
            return
        }
        if (isKeyboardRequestPending || isAwaitingKeyboard) return
        dismiss()
    }

    private fun isKeyboardVisible(insets: WindowInsetsCompat): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.isVisible(WindowInsetsCompat.Type.ime())
        } else {
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
        }

    private fun insetsController(): WindowInsetsControllerCompat? {
        val window = hostActivity()?.window ?: return null
        return WindowCompat.getInsetsController(window, editText)
    }

    private fun hostActivity(): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun insertEmoji(emoji: BarrageEmoji) {
        if (editText.isComposing) return
        val content = editText.text ?: return
        val selectionStart = editText.selectionStart
        val selectionEnd = editText.selectionEnd
        if (selectionStart !in 0..content.length || selectionEnd !in 0..content.length) return
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        val selectedText = BarrageEmojiConverter.serialize(SpannableStringBuilder(content, start, end))
        val contentLength = BarrageEmojiConverter.visibleLength(BarrageEmojiConverter.serialize(content))
        if (contentLength - BarrageEmojiConverter.visibleLength(selectedText) >= CHARACTER_LIMIT) {
            editText.showLimitExceeded()
            return
        }
        val attachment = BarrageEmojiConverter.attachment(context, emoji, editText.paint) ?: return
        content.replace(start, end, attachment)
        editText.setSelection(start + attachment.length)
    }

    private fun deleteFromInput(): Boolean {
        if (editText.isComposing) return false
        val content = editText.text ?: return false
        val selection = BarrageEmojiConverter.deleteBackward(content, editText.selectionStart, editText.selectionEnd)
            ?: return false
        editText.setSelection(selection)
        return true
    }

    private fun send() {
        if (editText.isComposing) return
        val content = editText.text?.let { BarrageEmojiConverter.serialize(it).trim() }.orEmpty()
        if (content.isEmpty()) {
            if (inputMode == InputMode.EMOJI) setInputMode(InputMode.KEYBOARD)
            return
        }
        onSend?.invoke(content)
        editText.text = null
        updateTextState()
        dismiss()
    }

    private fun dismiss(notify: Boolean) {
        isImeAnimating = false
        if (visibility != VISIBLE) {
            restoreSoftInputMode()
            return
        }
        insetsAnimator?.cancel()
        insetsAnimator = null
        keyboardPlaceholder.animate().cancel()
        inputMode = InputMode.KEYBOARD
        renderInputMode(animated = false)
        isAwaitingKeyboard = false
        hideKeyboard()
        editText.clearFocus()
        visibility = GONE
        restoreSoftInputMode()
        if (notify) notifyPresentationChanged(isPresented = false, animated = true)
    }

    private fun applyInsetsSoftInputMode() {
        val window = hostActivity()?.window ?: return
        if (originalSoftInputMode == null) {
            originalSoftInputMode = window.attributes.softInputMode
            originalSoftInputWindow = window
        }
        val mode = window.attributes.softInputMode
        val adjustMask = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        val adjust = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        window.setSoftInputMode((mode and adjustMask.inv()) or adjust)
    }

    private fun restoreSoftInputMode() {
        val original = originalSoftInputMode ?: return
        val window = originalSoftInputWindow
        originalSoftInputMode = null
        originalSoftInputWindow = null
        window?.setSoftInputMode(original)
    }

    private fun updateTextState() {
        val content = editText.text
        val hasContent = content?.let { BarrageEmojiConverter.serialize(it).trim().isNotEmpty() } == true
        emojiPanel.setSendEnabled(hasContent)
        emojiPanel.setDeleteEnabled(!content.isNullOrEmpty())
    }

    private fun captureKeyboardHeight(insets: WindowInsetsCompat) {
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        if (imeBottom > navigationBottom) emojiPanelHeight = imeBottom - navigationBottom
    }

    private fun updatePanelBounds() {
        val panelHeight = emojiPanelHeight + navigationBottom
        listOf(emojiPanel, keyboardPlaceholder).forEach { view ->
            if (view.layoutParams.height != panelHeight) {
                view.layoutParams = view.layoutParams.apply { height = panelHeight }
            }
        }
    }

    private fun bottomInsetOf(insets: WindowInsetsCompat): Int {
        if (inputMode == InputMode.EMOJI) return emojiPanelHeight + navigationBottom
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        return maxOf(imeBottom, navigationBottom)
    }

    private fun applyBottomInset(target: Int, animated: Boolean) {
        insetsAnimator?.cancel()
        insetsAnimator = null
        if (inputBarBottomMargin == target) return
        if (!animated || visibility != VISIBLE) {
            setInputBarBottomMargin(target)
            return
        }
        insetsAnimator = ValueAnimator.ofInt(inputBarBottomMargin, target).apply {
            duration = INSETS_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { setInputBarBottomMargin(it.animatedValue as Int) }
            start()
        }
    }

    private fun setInputBarBottomMargin(bottomMargin: Int) {
        if (inputBarBottomMargin == bottomMargin) return
        inputBarBottomMargin = bottomMargin
        (inputBar.layoutParams as MarginLayoutParams).apply {
            this.bottomMargin = bottomMargin
            inputBar.layoutParams = this
        }
        if (visibility == VISIBLE) notifyPresentationChanged(isPresented = true, animated = false)
    }

    private fun notifyPresentationChanged(isPresented: Boolean, animated: Boolean) {
        val messageBottomMargin =
            inputBarBottomMargin + maxOf(inputBar.height, inputBar.minimumHeight) + dp(MESSAGE_LIST_GAP_DP)
        onPresentationChanged?.invoke(isPresented, animated, messageBottomMargin)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

internal class CommentEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : LiveTextInputView(context, attrs, defStyleAttr) {

    var dismissRequest: (() -> Unit)? = null
    var deleteRequest: (() -> Boolean)? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        val isDismissKey = keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
        if (isDismissKey && event.action == KeyEvent.ACTION_UP) {
            dismissRequest?.invoke()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0 && deleteRequest?.invoke() == true) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }
}

private class SimpleTextWatcher(
    private val afterChanged: () -> Unit,
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: android.text.Editable?) = afterChanged()
}
