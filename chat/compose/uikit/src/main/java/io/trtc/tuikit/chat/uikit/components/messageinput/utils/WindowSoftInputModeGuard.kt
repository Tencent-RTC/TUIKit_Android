package io.trtc.tuikit.chat.uikit.components.messageinput.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Forces SOFT_INPUT_ADJUST_NOTHING while the input area is active so the
 * window does not resize and the in-place panel can occupy the keyboard area,
 * mirroring the View version's WindowSoftInputModeGuard.
 *
 * A dynamically applied adjust mode is not reliably honored by the system when
 * the manifest declares no baseline softInputMode: the first IME show may still
 * resize/pan before the pushed attributes reach the WindowManagerService, and
 * the mode can be dropped after the activity returns from the background. To
 * make ADJUST_NOTHING stick, the guard re-enforces it on every ON_RESUME and
 * window focus gain, and pushes the attributes synchronously once the decor
 * view is attached.
 */
internal class WindowSoftInputModeGuard {

    private var guardedActivity: Activity? = null
    private var previousSoftInputMode: Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
    private var lifecycleObserver: LifecycleEventObserver? = null
    private var windowFocusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null

    fun apply(context: Context) {
        if (guardedActivity != null) return
        val activity = context.findHostActivity() ?: return
        val window = activity.window ?: return
        previousSoftInputMode = window.attributes.softInputMode
        guardedActivity = activity
        enforceNow()
        registerReEnforce(activity, window)
    }

    fun restore() {
        val activity = guardedActivity ?: return
        unregisterReEnforce(activity)
        guardedActivity = null
        val window = activity.window ?: return
        window.setSoftInputMode(previousSoftInputMode)
        pushSoftInputMode(activity, window)
    }

    /**
     * Re-apply ADJUST_NOTHING locally and push the attributes to the
     * WindowManagerService. The push is unconditional: the app-side attributes
     * may still hold ADJUST_NOTHING while the service side has dropped it, so
     * skipping the push when the local value looks right would leave the
     * service side stale.
     */
    fun enforceNow() {
        val activity = guardedActivity ?: return
        val window = activity.window ?: return
        val current = window.attributes.softInputMode
        val adjusted = (current and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        if (current != adjusted) {
            window.setSoftInputMode(adjusted)
        }
        pushSoftInputMode(activity, window)
    }

    private fun registerReEnforce(activity: Activity, window: Window) {
        (activity as? LifecycleOwner)?.let { owner ->
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    enforceNow()
                }
            }
            lifecycleObserver = observer
            owner.lifecycle.addObserver(observer)
        }
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                enforceNow()
            }
        }
        windowFocusListener = listener
        window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
    }

    private fun unregisterReEnforce(activity: Activity) {
        lifecycleObserver?.let { (activity as? LifecycleOwner)?.lifecycle?.removeObserver(it) }
        lifecycleObserver = null
        val viewTreeObserver = activity.window?.decorView?.viewTreeObserver
        if (viewTreeObserver != null && viewTreeObserver.isAlive) {
            windowFocusListener?.let { viewTreeObserver.removeOnWindowFocusChangeListener(it) }
        }
        windowFocusListener = null
    }

    /**
     * Window.setSoftInputMode only mutates the local attributes; it does not
     * reliably reach the WindowManagerService on its own. Push the attributes
     * explicitly so the IME honors the adjust mode. doOnAttach runs the push
     * synchronously when the decor view is already attached, or right after it
     * attaches; posting it would let a pending IME show slip through with the
     * stale mode.
     */
    private fun pushSoftInputMode(activity: Activity, window: Window) {
        val decor = window.decorView
        decor.doOnAttach {
            runCatching { activity.windowManager.updateViewLayout(it, window.attributes) }
                .onFailure { Log.w(TAG, "push failed", it) }
        }
    }

    companion object {
        private const val TAG = "KBCheck"
    }
}

private tailrec fun Context.findHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findHostActivity()
        else -> null
    }
}
