package com.tencent.uikit.app.call.common

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.tencent.uikit.app.R
import java.lang.ref.WeakReference

internal object CallAccessToast {

    private const val DEFAULT_DURATION_MS = 2000L
    private const val ANIMATION_DURATION_MS = 220L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentToast: WeakReference<TextView>? = null
    private var pendingDismiss: Runnable? = null

    fun show(activity: Activity, text: CharSequence, durationMs: Long = DEFAULT_DURATION_MS) {
        if (text.isEmpty()) {
            return
        }
        mainHandler.post {
            val host = activity.window?.decorView as? ViewGroup ?: return@post
            removeCurrentToast()
            val density = activity.resources.displayMetrics.density
            val toast = TextView(activity)
            toast.text = text
            toast.setTextColor(Color.WHITE)
            toast.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            toast.setPadding(
                (16 * density).toInt(), (10 * density).toInt(),
                (16 * density).toInt(), (10 * density).toInt()
            )
            toast.setBackgroundResource(R.drawable.callaccess_bg_toast)
            toast.alpha = 0f
            toast.translationY = 8 * density
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            host.addView(toast, params)
            toast.maxWidth = host.width - (64 * density).toInt()
            currentToast = WeakReference(toast)
            toast.animate().alpha(1f).translationY(0f).setDuration(ANIMATION_DURATION_MS).start()
            val dismissTask = Runnable { dismissToast(toast) }
            pendingDismiss = dismissTask
            mainHandler.postDelayed(dismissTask, durationMs)
        }
    }

    private fun dismissToast(toast: TextView) {
        if (currentToast?.get() !== toast) {
            return
        }
        toast.animate().cancel()
        toast.animate().alpha(0f).translationY(8 * toast.resources.displayMetrics.density)
            .setDuration(ANIMATION_DURATION_MS)
            .withEndAction {
                (toast.parent as? ViewGroup)?.removeView(toast)
            }.start()
        currentToast = null
    }

    private fun removeCurrentToast() {
        pendingDismiss?.let(mainHandler::removeCallbacks)
        pendingDismiss = null
        val oldToast = currentToast?.get()
        oldToast?.animate()?.cancel()
        (oldToast?.parent as? ViewGroup)?.removeView(oldToast)
        currentToast = null
    }
}
