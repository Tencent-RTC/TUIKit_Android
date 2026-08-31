package com.trtc.uikit.roomkit.base.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.base.utils.KeyboardUtils

/**
 * Marker interface for containers that auto-hide the soft keyboard when the
 * user taps outside of the currently focused EditText.
 *
 * Prefer using one of the concrete implementations below as the root of your
 * XML layout:
 *  - [KeyboardDismissFrameLayout]      (replaces FrameLayout)
 *  - [KeyboardDismissLinearLayout]     (replaces LinearLayout)
 *  - [KeyboardDismissConstraintLayout] (replaces ConstraintLayout)
 *
 * Example:
 * ```xml
 * <com.trtc.uikit.roomkit.base.ui.KeyboardDismissConstraintLayout
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent">
 *     <!-- content with EditText -->
 * </com.trtc.uikit.roomkit.base.ui.KeyboardDismissConstraintLayout>
 * ```
 *
 * No Kotlin/Java code required on the hosting side. Works for Activity,
 * Fragment, Dialog, BottomSheet, or plain View trees, because the behavior
 * is scoped to this container's touch dispatch, not to the window.
 */
interface KeyboardDismissContainer {
    /** Set to false to temporarily disable auto-hide at runtime. */
    var isKeyboardDismissEnabled: Boolean
}

/**
 * Shared implementation used by all three concrete layouts below.
 * Must be called from each subclass's `dispatchTouchEvent` and `init`.
 */
private object KeyboardDismissDelegate {

    fun setup(view: ViewGroup) {
        // Allow the container itself to take focus so the EditText's focus can
        // be moved here after the keyboard is hidden. This prevents the IME
        // from popping back due to the EditText re-gaining focus on the next
        // frame in some hosting contexts.
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    fun onDispatchTouchEvent(view: ViewGroup, ev: MotionEvent, enabled: Boolean) {
        if (!enabled) return
        KeyboardUtils.hideKeyboardOnTouchOutside(ev, view.findFocus())
    }
}

open class KeyboardDismissFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), KeyboardDismissContainer {

    override var isKeyboardDismissEnabled: Boolean = true

    init {
        KeyboardDismissDelegate.setup(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        KeyboardDismissDelegate.onDispatchTouchEvent(this, ev, isKeyboardDismissEnabled)
        return super.dispatchTouchEvent(ev)
    }
}

open class KeyboardDismissLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), KeyboardDismissContainer {

    override var isKeyboardDismissEnabled: Boolean = true

    init {
        KeyboardDismissDelegate.setup(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        KeyboardDismissDelegate.onDispatchTouchEvent(this, ev, isKeyboardDismissEnabled)
        return super.dispatchTouchEvent(ev)
    }
}

open class KeyboardDismissConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), KeyboardDismissContainer {

    override var isKeyboardDismissEnabled: Boolean = true

    init {
        KeyboardDismissDelegate.setup(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        KeyboardDismissDelegate.onDispatchTouchEvent(this, ev, isKeyboardDismissEnabled)
        return super.dispatchTouchEvent(ev)
    }
}
