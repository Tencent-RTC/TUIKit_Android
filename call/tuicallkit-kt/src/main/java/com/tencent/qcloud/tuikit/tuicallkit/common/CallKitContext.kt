package com.tencent.qcloud.tuikit.tuicallkit.common

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

internal object CallKitContext {

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var topActivity: WeakReference<Activity>? = null

    @Volatile
    private var startedActivityCount = 0

    @Volatile
    private var resumedActivityCount = 0

    @Volatile
    private var lifecycleRegistered = false

    private val activityObservers = mutableListOf<(Activity?) -> Unit>()

    fun install(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        if (!lifecycleRegistered && appContext is Application) {
            lifecycleRegistered = true
            appContext.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumedActivityCount += 1
                    topActivity = WeakReference(activity)
                    notifyTopActivityChanged(activity)
                }

                override fun onActivityStarted(activity: Activity) {
                    startedActivityCount += 1
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (topActivity?.get() === activity) {
                        topActivity = null
                        notifyTopActivityChanged(null)
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityPaused(activity: Activity) {
                    resumedActivityCount = (resumedActivityCount - 1).coerceAtLeast(0)
                }
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            })
        }
    }

    fun requireContext(): Context {
        return checkNotNull(applicationContext) { "TUICallKit.createInstance(context) must be called before using the kit" }
    }

    fun topActivity(): Activity? = topActivity?.get()

    val isForeground: Boolean
        get() = resumedActivityCount > 0

    fun addTopActivityObserver(observer: (Activity?) -> Unit) {
        synchronized(activityObservers) { activityObservers.add(observer) }
    }

    private fun notifyTopActivityChanged(activity: Activity?) {
        val snapshot = synchronized(activityObservers) { activityObservers.toList() }
        for (observer in snapshot) {
            observer(activity)
        }
    }
}
