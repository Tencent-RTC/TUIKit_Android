package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.app.Activity
import android.view.View

internal abstract class BaseKeyboardBridgeImpl(
    protected val activity: Activity
) : KeyboardBridge {
    protected val rootView: View? = activity.window?.decorView?.findViewById(android.R.id.content)
    private val keyboardHeightStore: KeyboardHeightStore = MmkvKeyboardHeightStore(activity)

    protected var keyboardMaxHeight: Int = keyboardHeightStore.getHeight()
    protected var lastDispatchedHeight = 0
    protected var lastDispatchedVisible = false

    override var listener: KeyboardBridge.Listener? = null

    override fun showKeyboard(editText: View) {
        KeyboardInputController.show(activity, editText)
        onShowKeyboardRequested(editText)
    }

    override fun hideKeyboard() {
        val view = KeyboardInputController.hide(activity, rootView) ?: return
        onHideKeyboardRequested(view)
    }

    override fun hideKeyboardKeepFocus(editText: View) {
        KeyboardInputController.hideKeepFocus(activity, editText)
        onHideKeyboardRequested(editText)
    }

    override fun getKeyboardHeight(): Int {
        return if (keyboardMaxHeight > 0) keyboardMaxHeight else defaultKeyboardHeight
    }

    override fun hasRecordedKeyboardHeight(): Boolean = keyboardMaxHeight > 0

    protected fun saveKeyboardHeight(height: Int) {
        keyboardHeightStore.saveHeight(height)
    }

    protected fun dispatchChanged(height: Int, isVisible: Boolean) {
        lastDispatchedHeight = height
        lastDispatchedVisible = isVisible
        listener?.onKeyboardHeightChanged(height, isVisible)
    }

    protected fun getNavigationBarBottom(view: View): Int {
        return KeyboardInsetsUtil.getNavigationBarBottom(activity, view)
    }

    protected open fun onShowKeyboardRequested(editText: View) {}

    protected open fun onHideKeyboardRequested(anchorView: View) {}

    private val defaultKeyboardHeight: Int
        get() {
            val density = activity.resources.displayMetrics.density
            return (DEFAULT_KEYBOARD_HEIGHT_DP * density).toInt()
        }

    companion object {
        private const val DEFAULT_KEYBOARD_HEIGHT_DP = 350
    }
}
