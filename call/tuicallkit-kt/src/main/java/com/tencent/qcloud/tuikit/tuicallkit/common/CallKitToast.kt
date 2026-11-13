package com.tencent.qcloud.tuikit.tuicallkit.common

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.tencent.qcloud.tuikit.tuicallkit.R
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal object CallKitToast {

    private const val DISMISS_DELAY_MS = 1800L
    private const val FADE_DURATION_MS = 200L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var topActivity: WeakReference<Activity>? = null
    private var currentToast: WeakReference<TextView>? = null
    private var pendingDismiss: Runnable? = null

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                topActivity = WeakReference(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (topActivity?.get() === activity) {
                    topActivity = null
                }
                if (currentToast?.get()?.context === activity) {
                    removeCurrentToast()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    fun show(text: CharSequence) {
        if (text.isEmpty()) {
            return
        }
        val activity = topActivity?.get() ?: return
        val host = activity.window?.decorView as? ViewGroup ?: return
        removeCurrentToast()
        val density = activity.resources.displayMetrics.density
        val toast = TextView(activity)
        toast.text = text
        toast.setTextColor(Color.WHITE)
        toast.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        val horizontalPadding = (16 * density).roundToInt()
        val verticalPadding = (10 * density).roundToInt()
        toast.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        toast.setBackgroundResource(R.drawable.tuicallkit_bg_toast)
        toast.alpha = 0f
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        host.addView(toast, params)
        currentToast = WeakReference(toast)
        toast.animate().alpha(1f).setDuration(FADE_DURATION_MS).start()
        val dismissTask = Runnable { fadeOutAndRemove(toast) }
        pendingDismiss = dismissTask
        mainHandler.postDelayed(dismissTask, DISMISS_DELAY_MS)
    }

    private fun fadeOutAndRemove(toast: TextView) {
        if (currentToast?.get() !== toast) {
            return
        }
        toast.animate().cancel()
        toast.animate().alpha(0f).setDuration(FADE_DURATION_MS).withEndAction {
            (toast.parent as? ViewGroup)?.removeView(toast)
        }.start()
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
