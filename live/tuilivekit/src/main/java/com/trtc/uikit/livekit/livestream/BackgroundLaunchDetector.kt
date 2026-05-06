package com.trtc.uikit.livekit.livestream

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.tencent.liteav.base.ContextUtils
import com.trtc.uikit.livekit.common.LiveKitLogger

class BackgroundLaunchDetector private constructor() : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var isBackground: Boolean? = null

    private val backgroundLaunchListeners = LinkedHashMap<String, BackgroundLaunchListener>()

    private val startedActivitySet = HashSet<Int>()
    private val pausedActivitySet = HashSet<Int>()


    init {
        val context = ContextUtils.getApplicationContext()
        if (context == null) {
            logger.info("BackgroundLaunchDetector init failed. Context is null")
        } else {
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
            isBackground = checkBackground(context)
        }
    }

    @Synchronized
    fun addBackgroundLaunchListener(key: String, listener: BackgroundLaunchListener) {
        backgroundLaunchListeners[key] = listener
    }

    @Synchronized
    fun removeBackgroundLaunchListener(key: String) {
        backgroundLaunchListeners.remove(key)
    }

    @Synchronized
    private fun setBackground(isBackground: Boolean) {
        if (this.isBackground == null || this.isBackground != isBackground) {
            this.isBackground = isBackground
        }
    }

    @Synchronized
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    @Synchronized
    override fun onActivityStarted(activity: Activity) {
        logger.info("onActivityStarted : ${activity.componentName},isBackground=$isBackground")
        startActivityWhenBackgroundLaunch(activity)
        handleActivityStarted(activity)
    }

    @Synchronized
    override fun onActivityResumed(activity: Activity) {
        handleActivityStarted(activity)
    }

    @Synchronized
    override fun onActivityPaused(activity: Activity) {
        logger.info("onActivityPaused, activity=$activity")
        pausedActivitySet.add(activity.hashCode())
    }

    @Synchronized
    override fun onActivityStopped(activity: Activity) {
        logger.info("onActivityStopped, activity=$activity")
        val hashCode = activity.hashCode()
        if (startedActivitySet.contains(hashCode)) {
            startedActivitySet.remove(hashCode)
            setBackground(startedActivitySet.isEmpty())
        } else if (startedActivitySet.isEmpty()) {
            if (pausedActivitySet.contains(hashCode)) {
                setBackground(true)
            }
        } else {
            setBackground(false)
        }
        pausedActivitySet.remove(hashCode)
    }

    @Synchronized
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    @Synchronized
    override fun onActivityDestroyed(activity: Activity) {
    }

    private fun handleActivityStarted(activity: Activity) {
        startedActivitySet.add(activity.hashCode())
        setBackground(false)
    }

    private fun startActivityWhenBackgroundLaunch(activity: Activity) {
        if (isBackground == true) {
            logger.info("startActivityWhenBackgroundLaunch, activity=$activity, listeners=${backgroundLaunchListeners.keys}")
            backgroundLaunchListeners.values.toList().forEach { listener ->
                listener.onBackgroundLaunch(activity)
            }
        }
    }

    companion object {
        private val logger = LiveKitLogger.getLiveStreamLogger("BackgroundLaunchDetector")

        @Volatile
        private var instance: BackgroundLaunchDetector? = null

        @JvmStatic
        fun getInstance(): BackgroundLaunchDetector {
            return instance ?: synchronized(this) {
                instance ?: BackgroundLaunchDetector().also { instance = it }
            }
        }

        @JvmStatic
        fun registerActivityLifecycleCallbacks(application: Application, key: String, listener: BackgroundLaunchListener) {
            getInstance().addBackgroundLaunchListener(key, listener)
        }

        @JvmStatic
        fun unregisterActivityLifecycleCallbacks(application: Application, key: String) {
            getInstance().removeBackgroundLaunchListener(key)
        }

        @JvmStatic
        fun isAbnormalModelForBringTaskToFront(): Boolean {
            val isXiaomi13 = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
                    && (Build.MODEL.equals("2211133C", ignoreCase = true)
                    || Build.MODEL.equals("2211133G", ignoreCase = true)
                    || Build.MODEL.contains("Xiaomi 13", ignoreCase = true))
            val isAndroid15 = Build.VERSION.SDK_INT == 35
            return isXiaomi13 && isAndroid15
        }

        private fun checkBackground(context: Context): Boolean {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (activityManager == null) {
                    logger.info("activityManager is null.")
                    return false
                }
                val processInfoList = activityManager.runningAppProcesses
                if (processInfoList == null) {
                    logger.info("processInfoList is null.")
                    return false
                }
                for (runningAppProcessInfo in processInfoList) {
                    if (runningAppProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && context.packageName == runningAppProcessInfo.processName
                    ) {
                        return false
                    }
                }
                true
            } catch (e: Exception) {
                logger.info("Get App background state failed. $e")
                false
            }
        }
    }
}

enum class ActivityType {
    ANCHOR,
    AUDIENCE,
    UNKNOWN
}

interface BackgroundLaunchListener {
    fun onBackgroundLaunch(stackTopActivity: Activity)
}