package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

internal object KeyboardInputController {
    fun show(activity: Activity, editText: View) {
        editText.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide(activity: Activity, rootView: View?): View? {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = activity.currentFocus ?: rootView ?: return null
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        return view
    }

    fun hideKeepFocus(activity: Activity, editText: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }
}
