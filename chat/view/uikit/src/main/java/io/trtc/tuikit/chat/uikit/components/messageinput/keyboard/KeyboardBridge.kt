package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View

interface KeyboardBridge {
    var listener: Listener?
        get() = null
        set(value) {}

    fun attach(view: View) {}
    fun detach(view: View) {}
    fun showKeyboard(editText: View)
    fun hideKeyboard()
    fun hideKeyboardKeepFocus(editText: View)
    fun getKeyboardHeight(): Int
    fun hasRecordedKeyboardHeight(): Boolean
    fun isAnimationSupported(): Boolean

    interface Listener {
        fun onKeyboardHeightChanged(height: Int, isVisible: Boolean)
        fun onKeyboardHeightChanging(currentHeight: Int)
    }

    companion object {
        fun create(context: Context): KeyboardBridge {
            val activity = context as? Activity ?: return createNoop()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ModernKeyboardBridgeImpl(activity)
            } else {
                LegacyKeyboardBridgeImpl(activity)
            }
        }

        private fun createNoop(): KeyboardBridge {
            return object : KeyboardBridge {
                override var listener: Listener? = null

                override fun showKeyboard(editText: View) {}
                override fun hideKeyboard() {}
                override fun hideKeyboardKeepFocus(editText: View) {}
                override fun getKeyboardHeight(): Int = 0
                override fun hasRecordedKeyboardHeight(): Boolean = false
                override fun isAnimationSupported(): Boolean = false
            }
        }
    }
}
