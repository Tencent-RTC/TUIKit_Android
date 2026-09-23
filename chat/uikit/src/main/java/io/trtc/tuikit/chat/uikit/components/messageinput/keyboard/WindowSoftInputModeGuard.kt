package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager

internal class WindowSoftInputModeGuard {

    private var guardedActivity: Activity? = null
    private var previousSoftInputMode: Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED

    fun apply(context: Context) {
        if (guardedActivity != null) return
        val activity = context.findHostActivity() ?: return
        val window = activity.window ?: return
        val current = window.attributes.softInputMode
        if (current.adjustMode == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) {
            return
        }
        previousSoftInputMode = current
        guardedActivity = activity
        val adjusted = (current and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        window.setSoftInputMode(adjusted)
        dispatchSoftInputMode(activity, window)
    }

    fun restore() {
        val activity = guardedActivity ?: return
        guardedActivity = null
        val window = activity.window ?: return
        window.setSoftInputMode(previousSoftInputMode)
        dispatchSoftInputMode(activity, window)
    }

    /**
     * Window.setSoftInputMode only mutates the local attributes when the window is
     * already attached; it does not reliably reach the WindowManagerService. Push the
     * attributes explicitly so the IME actually honors the new adjust mode.
     */
    private fun dispatchSoftInputMode(activity: Activity, window: Window) {
        val decor = window.decorView
        if (!decor.isAttachedToWindow) return
        runCatching { activity.windowManager.updateViewLayout(decor, window.attributes) }
    }

    private val Int.adjustMode: Int
        get() = this and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
}

private tailrec fun Context.findHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findHostActivity()
        else -> null
    }
}
