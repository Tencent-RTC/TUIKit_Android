package com.trtc.uikit.livekit.common.barrage

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.trtc.uikit.livekit.common.theme.LiveTextInputView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel

class BarrageInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val INPUT_BAR_KEYBOARD_GAP_DP = 4
        private const val MESSAGE_LIST_GAP_DP = 6
        private const val INSETS_DURATION_MS = 220L
    }

    private val inputBar: View
    private val editText: CommentEditText
    private val sendView: AtomicLabel

    private var liveID = ""
    private var inputBarBottomMargin = 0

    private var originalSoftInputMode: Int? = null

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
        LayoutInflater.from(context).inflate(
            R.layout.livekit_pusher_live_comment_composer_view,
            this,
            true,
        )
        inputBar = findViewById(R.id.livekit_pusher_comment_input_bar)
        editText = findViewById(R.id.livekit_pusher_comment_input)
        sendView = findViewById(R.id.livekit_pusher_comment_send)

        visibility = GONE
        isClickable = true
        isFocusable = true
        setOnClickListener { dismiss() }
        inputBar.setOnClickListener { /* Keep input-bar taps inside the composer. */ }
        findViewById<View>(R.id.livekit_pusher_comment_cancel).setOnClickListener { dismiss() }
        sendView.setOnClickListener { send() }
        editText.setInputLimit(200, R.string.livekit_comment_character_limit)
        inputBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (visibility == VISIBLE && bottom - top != oldBottom - oldTop) {
                notifyPresentationChanged(isPresented = true, animated = false)
            }
        }
        editText.dismissRequest = { dismiss() }
        editText.setOnEditorActionListener { _, actionID, event ->
            val isSendAction = actionID == EditorInfo.IME_ACTION_SEND
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_UP
            if (isSendAction || isEnter) {
                send()
                true
            } else {
                false
            }
        }
        editText.addTextChangedListener(SimpleTextWatcher { updateSendState() })
        updateSendState()

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
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
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        insetsAnimator?.cancel()
                        isImeAnimating = true
                    }
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    applyBottomInset(bottomInsetOf(insets), animated = false)
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        isImeAnimating = false
                        ViewCompat.getRootWindowInsets(this@BarrageInputView)?.let {
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
        if (visibility == VISIBLE || liveID.isEmpty()) return
        applyResizeSoftInputMode()
        // Settle the input bar at its keyboard-less position before the first frame, so the IME
        // animation only has to move it upwards.
        ViewCompat.getRootWindowInsets(this)?.let { applyBottomInset(bottomInsetOf(it), animated = false) }
        visibility = VISIBLE
        notifyPresentationChanged(isPresented = true, animated = true)
        // The composer is opened from a settings panel that is still handing input focus back to the
        // host window, so the IME request has to wait for onWindowFocusChanged in that case.
        isKeyboardRequestPending = true
        isAwaitingKeyboard = true
        if (hasWindowFocus()) showKeyboard()
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

    private fun showKeyboard() {
        isKeyboardRequestPending = false
        if (visibility != VISIBLE) return
        editText.requestFocus()
        insetsController()?.show(WindowInsetsCompat.Type.ime())
    }

    private fun hideKeyboard() {
        isKeyboardRequestPending = false
        insetsController()?.hide(WindowInsetsCompat.Type.ime())
    }

    private fun handleKeyboardVisibility(insets: WindowInsetsCompat) {
        if (visibility != VISIBLE) return
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

    private fun send() {
        if (editText.isComposing) return
        val content = editText.text?.toString()?.trim().orEmpty()
        if (content.isEmpty()) return
        onSend?.invoke(content)
        dismiss()
        editText.clearFocus()
        editText.text = null
    }

    private fun dismiss(notify: Boolean) {
        if (visibility != VISIBLE) {
            restoreSoftInputMode()
            return
        }
        insetsAnimator?.cancel()
        insetsAnimator = null
        isAwaitingKeyboard = false
        hideKeyboard()
        editText.clearFocus()
        visibility = GONE
        restoreSoftInputMode()
        if (notify) notifyPresentationChanged(isPresented = false, animated = true)
    }

    private fun applyResizeSoftInputMode() {
        val window = hostActivity()?.window ?: return
        if (originalSoftInputMode == null) {
            originalSoftInputMode = window.attributes.softInputMode
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun restoreSoftInputMode() {
        val original = originalSoftInputMode ?: return
        originalSoftInputMode = null
        hostActivity()?.window?.setSoftInputMode(original)
    }

    private fun updateSendState() {
        val hasContent = !editText.text?.toString()?.trim().isNullOrEmpty()
        sendView.isEnabled = hasContent
        sendView.alpha = if (hasContent) 1f else 0.45f
    }

    private fun bottomInsetOf(insets: WindowInsetsCompat): Int {
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        return maxOf(imeBottom, navigationBottom) + dp(INPUT_BAR_KEYBOARD_GAP_DP)
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
        if (visibility == VISIBLE) {
            notifyPresentationChanged(isPresented = true, animated = false)
        }
    }

    private fun notifyPresentationChanged(isPresented: Boolean, animated: Boolean) {
        val messageBottomMargin = inputBarBottomMargin + maxOf(inputBar.height, inputBar.minimumHeight) +
            dp(MESSAGE_LIST_GAP_DP)
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

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) &&
            event.action == KeyEvent.ACTION_UP
        ) {
            dismissRequest?.invoke()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }
}

private class SimpleTextWatcher(
    private val afterChanged: () -> Unit,
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: android.text.Editable?) = afterChanged()
}
