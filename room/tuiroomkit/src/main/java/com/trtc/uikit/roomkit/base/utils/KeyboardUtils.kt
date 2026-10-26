package com.trtc.uikit.roomkit.base.utils

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.view.inputmethod.InputMethodManager

/**
 * Keyboard-related utilities.
 *
 * Typical usage – "tap outside EditText to hide the soft keyboard":
 *
 * 1) In an Activity / Dialog:
 * ```
 * override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
 *     KeyboardUtils.hideKeyboardOnTouchOutside(ev, currentFocus, window)
 *     return super.dispatchTouchEvent(ev)
 * }
 * ```
 *
 * 2) In a custom ViewGroup:
 * ```
 * override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
 *     KeyboardUtils.hideKeyboardOnTouchOutside(ev, findFocus())
 *     return super.dispatchTouchEvent(ev)
 * }
 * ```
 */
object KeyboardUtils {

    /**
     * Hides the soft keyboard if the given [ev] is an ACTION_DOWN outside of [focusedView].
     *
     * Safe to call unconditionally – if [focusedView] is null, not an [EditText], or the
     * touch point falls inside it, this is a no-op.
     *
     * @param ev the touch event being dispatched
     * @param focusedView the currently focused view (usually an EditText). Pass
     *  `Activity.getCurrentFocus()` in an Activity/Dialog, or `findFocus()` inside a ViewGroup.
     * @param window optional window whose token is used to hide the IME. If null, the token
     *  is taken from [focusedView].
     * @param clearFocus whether to clear focus after hiding the keyboard. Defaults to true so
     *  the cursor stops blinking.
     */
    @JvmStatic
    @JvmOverloads
    fun hideKeyboardOnTouchOutside(
        ev: MotionEvent,
        focusedView: View?,
        window: Window? = null,
        clearFocus: Boolean = true
    ) {
        if (ev.action != MotionEvent.ACTION_DOWN) return
        val target = focusedView as? EditText ?: return
        if (isTouchInsideView(ev, target)) return
        hideKeyboard(target, window)
        if (clearFocus) {
            // Move focus to a non-EditText ancestor if possible so the IME doesn't pop back
            // when the outer container is focusableInTouchMode.
            val parent = target.rootView as? ViewGroup
            parent?.isFocusableInTouchMode = true
            parent?.requestFocus()
            target.clearFocus()
        }
    }

    /**
     * Hides the soft keyboard using the token of [anchor] (or [window] if provided).
     */
    @JvmStatic
    @JvmOverloads
    fun hideKeyboard(anchor: View, window: Window? = null) {
        val imm = anchor.context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? InputMethodManager ?: return
        val token = window?.decorView?.windowToken ?: anchor.windowToken ?: return
        imm.hideSoftInputFromWindow(token, 0)
    }

    /**
     * True if the raw screen coordinates of [ev] fall within the physical bounds of [view].
     *
     * Uses [View.getLocationOnScreen] rather than [View.getGlobalVisibleRect] so that
     * partial clipping (e.g. by a BottomSheet or by the soft keyboard) does not cause
     * false negatives.
     */
    @JvmStatic
    fun isTouchInsideView(ev: MotionEvent, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + view.width
        val bottom = top + view.height
        val x = ev.rawX.toInt()
        val y = ev.rawY.toInt()
        return x in left..right && y in top..bottom
    }
}
