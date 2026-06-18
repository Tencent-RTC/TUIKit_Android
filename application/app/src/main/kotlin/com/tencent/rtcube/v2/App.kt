package com.tencent.rtcube.v2

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.Choreographer
import com.google.android.play.core.splitcompat.SplitCompat
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.v2.debug.GenerateTestUserSig
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.ServerEnvironment
import com.tencent.rtcube.v2.privacy.PrivacyEntry
import com.tencent.rtcube.v2.privacy.PrivacyPageType
import com.tencent.rtcube.v2.push.PushManager
import com.tencent.trtc.TRTCCloud
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Application entry point
 */
class App : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        // Scene-Application MiniProgram is in a separate process, avoid initializing app-wide components to save memory.
        if (AppConfig.isMiniProgramProcess(this)) {
            return
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivityRef?.get() === activity) currentActivityRef = null
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() === activity) currentActivityRef = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })

        if (BuildConfig.DEBUG) {
            enableStrictMode()
            PerformanceMonitor.start()
        }

        setupModules()
    }

    private fun setupModules() {
        LoginEntry.userSigGenerator = { identifier, sdkAppId, secretKey ->
            GenerateTestUserSig.genTestUserSig(identifier, sdkAppId, secretKey)
        }

        LoginEntry.privacyLinkHandler = { linkType, context ->
            val pageType: PrivacyPageType? = when (linkType) {
                "privacy" -> PrivacyPageType.Privacy
                "privacySummary" -> PrivacyPageType.PrivacySummary
                "agreement" -> PrivacyPageType.Agreement
                "termsOfService" -> PrivacyPageType.TermsOfService
                else -> null
            }
            pageType?.let { PrivacyEntry.pushPrivacyPage(it, context) }
        }
        LoginEntry.privacyAlertDialogHandler = {
            if (AppTargetResolver.getAppTarget() == AppTarget.DOMESTIC) {
                PrivacyEntry.showFirstLaunchPrivacyDialog(onAgree = { LoginEntry.markPrivacyAgreementAccepted() })
            }
        }

        LoginEntry.initialAutoLoginEnabled = AppTargetResolver.getAppTarget() != AppTarget.LAB
        LoginEntry.initialize(
            application = this,
            baseUrl = GenerateTestUserSig.BASE_URL,
            testBaseUrl = GenerateTestUserSig.BASE_URL_TEST,
            apaasAppId = GenerateTestUserSig.APAAS_APP_ID,
            sdkAppId = GenerateTestUserSig.SDKAPPID,
            secretKey = GenerateTestUserSig.SECRETKEY,
            debugSdkAppId = GenerateTestUserSig.DEBUG_SDKAPPID,
            debugSecretKey = GenerateTestUserSig.DEBUG_SECRETKEY,
        )

        LoginEntry.onEnvironmentChanged = { env ->
            switchEnvironment(env == ServerEnvironment.TEST)
        }

        if (AppTargetResolver.getAppTarget() != AppTarget.LAB) {
            PushManager.init(
                context = this,
                sdkAppId = GenerateTestUserSig.SDKAPPID,
                secretKey = GenerateTestUserSig.SECRETKEY,
            )
        }
    }

    private fun switchEnvironment(isTestEnv: Boolean) {
        Log.i(TAG, "switchEnvironment: $isTestEnv")
        switchIMEnvironment(isTestEnv)
        setNetEnv(isTestEnv)
    }

    private fun switchIMEnvironment(isTestEnv: Boolean) {
        try {
            val roomJson = JSONObject().apply {
                put("api", "setTestEnvironment")
                put("params", JSONObject().apply {
                    put("enableRoomTestEnv", isTestEnv)
                })
            }
            TUIRoomEngine.sharedInstance().callExperimentalAPI(roomJson.toString()) {}
        } catch (e: Exception) {
            Log.e(TAG, "switchRoomEnvironment failed: ${e.message}")
        }

        try {
            V2TIMManager.getInstance().callExperimentalAPI("setTestEnvironment", isTestEnv, null)
        } catch (e: Exception) {
            Log.e(TAG, "switchIMEnvironment failed: ${e.message}")
        }
    }

    private fun setNetEnv(isTestEnv: Boolean) {
        try {
            val rtcJson = JSONObject().apply {
                put("api", "setNetEnv")
                put("params", JSONObject().apply {
                    put("env", if (isTestEnv) 1 else 0)
                })
            }
            TRTCCloud.sharedInstance(applicationContext).callExperimentalAPI(rtcJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "setNetEnv failed: ${e.message}")
        }
    }

    /// Enable StrictMode in Debug mode to detect main thread violations
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    companion object {
        const val TAG = "RTCubeApp"
        lateinit var instance: App
            private set

        private var currentActivityRef: WeakReference<Activity>? = null

        val currentActivity: Activity?
            get() = currentActivityRef?.get()
    }
}

object PerformanceMonitor {

    private const val TAG = "PerformanceMonitor"
    private const val FRAME_INTERVAL_NS = 16_666_666L
    private const val JANK_THRESHOLD_MULTIPLIER = 2

    private var isMonitoring = false
    private var lastFrameTimeNs = 0L

    fun start() {
        if (isMonitoring) return
        isMonitoring = true
        Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
            onFrameCallback(frameTimeNanos)
        }
    }

    fun stop() {
        isMonitoring = false
    }

    private fun onFrameCallback(frameTimeNanos: Long) {
        if (!isMonitoring) return

        if (lastFrameTimeNs > 0) {
            val costMs = (frameTimeNanos - lastFrameTimeNs) / 1_000_000
            val droppedFrames = ((frameTimeNanos - lastFrameTimeNs) / FRAME_INTERVAL_NS) - 1
            if (droppedFrames >= JANK_THRESHOLD_MULTIPLIER) {
                Log.w(TAG, "Jank detected: ${costMs}ms, dropped $droppedFrames frames")
            }
        }
        lastFrameTimeNs = frameTimeNanos
        if (isMonitoring) {
            Choreographer.getInstance().postFrameCallback { onFrameCallback(it) }
        }
    }
}
