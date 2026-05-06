package com.trtc.uikit.livekit.livestream

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUINotification
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.features.audienceview.AudienceView
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceViewListener
import com.trtc.uikit.livekit.features.endstatistics.AudienceEndStatisticsView
import com.trtc.uikit.livekit.features.endstatistics.EndStatisticsDefine
import com.trtc.uikit.livekit.livestream.impl.LiveInfoUtils
import com.trtc.uikit.livekit.livestream.impl.VideoLiveKitImpl
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import io.trtc.tuikit.atomicx.common.foregroundservice.VideoForegroundService
import com.trtc.uikit.livekit.component.pippanel.PictureInPictureStore
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.launch

class VideoLiveAudienceActivity : FullScreenActivity(),
    ITUINotification,
    VideoLiveKitImpl.CallingAPIListener,
    AudienceViewListener {

    companion object {
        const val KEY_EXTENSION_NAME = "TEBeautyExtension"
        const val NOTIFY_START_ACTIVITY = "onStartActivityNotifyEvent"
        const val METHOD_ACTIVITY_RESULT = "onActivityResult"
        private val logger = LiveKitLogger.getLiveStreamLogger("VideoLiveAudienceActivity")
    }

    private lateinit var layoutContainer: FrameLayout
    private var audienceView: AudienceView? = null
    private var audienceEndStatisticsView: AudienceEndStatisticsView? = null
    private var cachedTaskId: Int = -1
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(context: Context?) {
        super.attachBaseContext(context)
        context?.let {
            val configuration = it.resources.configuration
            configuration.fontScale = 1f
            applyOverrideConfiguration(configuration)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setContentView(R.layout.livekit_activity_video_live_audience)

        val liveBundle = intent.extras
        if (liveBundle == null) {
            Log.e("VideoLiveAudience", "liveBundle is null")
            return
        }

        layoutContainer = findViewById(R.id.fl_container)
        val liveInfo = LiveInfoUtils.convertBundleToLiveInfo(liveBundle)
        
        audienceView = AudienceView(this).apply {
            init(this@VideoLiveAudienceActivity, liveInfo.liveID)
            addListener(this@VideoLiveAudienceActivity)
        }
        
        layoutContainer.addView(audienceView)
        VideoLiveKitImpl.createInstance(applicationContext).addCallingAPIListener(this)
        TUICore.registerEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, this)
        lifecycleScope.launchWhenStarted {
            launch {
                PermissionRequest.requestCompleteEvent.collect {
                    bringTaskToFront()
                }
            }
            launch {
                LoginStore.shared.loginState.loginStatus.collect {
                    if (it == LoginStatus.UNLOGIN) {
                        destroyAudienceView()
                    }
                }
            }
        }
        startForegroundService()

        BackgroundLaunchDetector.registerActivityLifecycleCallbacks(application, "VideoLiveAudienceActivity", object : BackgroundLaunchListener {
            override fun onBackgroundLaunch(stackTopActivity: Activity) {
                logger.info("onBackgroundLaunch: ${stackTopActivity::class.java.name}")
                if (stackTopActivity is VideoLiveAudienceActivity) {
                    return
                }
                bringTaskToFront()
            }
        })
    }

    override fun onNotifyEvent(key: String?, subKey: String?, param: Map<String, Any>?) {
        when {
            TextUtils.equals(key, KEY_EXTENSION_NAME) && TextUtils.equals(subKey, NOTIFY_START_ACTIVITY) -> {
                val intent = param?.get("intent") as? Intent
                val requestCode = param?.get("requestCode") as? Int
                
                if (requestCode != null && intent != null) {
                    startActivityForResult(intent, requestCode)
                } else if (intent != null) {
                    startActivity(intent)
                }
            }
            TextUtils.equals(key, EVENT_KEY_LIVE_KIT) && EVENT_SUB_KEY_DESTROY_LIVE_VIEW == subKey -> {
                destroyAudienceView()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        logger.info("onUserLeaveHint: $isFinishing")
        if (PIPPanelStore.sharedInstance().state.audienceIsPictureInPictureMode) {
            return
        }
        if (LiveListStore.shared().liveState.currentLive.value.liveID.isBlank()) {
            return
        }
        onClickFloatWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        BackgroundLaunchDetector.unregisterActivityLifecycleCallbacks(application, "VideoLiveAudienceActivity")
        PIPPanelStore.sharedInstance().reset()
        VideoLiveKitImpl.createInstance(applicationContext).removeCallingAPIListener(this)
        stopForegroundService()
        audienceView?.removeListener(this)
        TUICore.unRegisterEvent(this)
        PIPPanelStore.sharedInstance().setPictureInPictureModeRoomId("")
        PictureInPictureStore.shared.updateIsPictureInPictureMode(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val param = mapOf(
            "requestCode" to requestCode,
            "resultCode" to resultCode,
            "data" to data
        )
        TUICore.callService(KEY_EXTENSION_NAME, METHOD_ACTIVITY_RESULT, param)
    }

    override fun onBackPressed() {
        // Do nothing
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            val roomId = audienceView?.getRoomId()
            PIPPanelStore.sharedInstance().setPictureInPictureModeRoomId(roomId ?: "")
        }
    }

    override fun onLeaveLive() {
        finish()
    }

    override fun onStopLive() {
        finish()
    }

    override fun onResume() {
        super.onResume()
        cachedTaskId = taskId
        VideoLiveKitImpl.createInstance(applicationContext).startPushLocalVideoOnResume()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        logger.info("onPictureInPictureModeChanged: $isInPictureInPictureMode")
        PIPPanelStore.sharedInstance().state.audienceIsPictureInPictureMode = isInPictureInPictureMode
        PictureInPictureStore.shared.updateIsPictureInPictureMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            AtomicPopover.dismissAll()
            if (LiveListStore.shared().liveState.currentLive.value.liveID.isBlank()) {
                destroyAudienceView()
                return
            }
        }
        audienceView?.enablePictureInPictureMode(isInPictureInPictureMode)
        audienceEndStatisticsView?.enablePipMode(isInPictureInPictureMode)
        
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED
            && PictureInPictureStore.shared.hasPipPermission(this)) {
            destroyAudienceView()
        }
    }

    private fun startForegroundService() {
        ContextProvider.getApplicationContext()?.apply {
            VideoForegroundService.start(
                this,
                this.getString(this.applicationInfo.labelRes),
                this.getString(R.string.common_app_running),
                0
            )
        }
    }

    private fun stopForegroundService() {
        ContextProvider.getApplicationContext()?.apply {
            VideoForegroundService.stop(this)
        }
    }

    override fun onLiveEnded(roomId: String, ownerName: String, ownerAvatarUrl: String) {
        if (PIPPanelStore.sharedInstance().state.audienceIsPictureInPictureMode) {
            finish()
            return
        }
        AtomicPopover.dismissAll()
        audienceEndStatisticsView = AudienceEndStatisticsView(this).apply {
            init(roomId, ownerName, ownerAvatarUrl)
            setListener(object : EndStatisticsDefine.AudienceEndStatisticsViewListener {
                override fun onCloseButtonClick() {
                    finish()
                }
            })
        }
        
        layoutContainer.removeAllViews()
        layoutContainer.addView(audienceEndStatisticsView)
    }

    override fun onClickFloatWindow() {
        val success = VideoLiveKitImpl.createInstance(applicationContext).enterPictureInPictureMode(this)
        if (success) {
            val roomId = audienceView?.getRoomId()
            PIPPanelStore.sharedInstance().setPictureInPictureModeRoomId(roomId ?: "")
        }
    }

    private fun destroyAudienceView() {
        if (isFinishing || isDestroyed) {
            return
        }
        audienceView?.destroy()
        finishAndRemoveTask()
    }

    fun bringTaskToFront() {
        logger.info("bringTaskToFront, cachedTaskId=$cachedTaskId")
        mainHandler.post {
            try {
                if (BackgroundLaunchDetector.isAbnormalModelForBringTaskToFront() && cachedTaskId != -1) {
                    val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    activityManager?.moveTaskToFront(cachedTaskId, ActivityManager.MOVE_TASK_WITH_HOME)
                    logger.info("bringTaskToFront by moveTaskToFront, cachedTaskId=$cachedTaskId")
                } else {
                    bringTaskToFrontByIntent()
                }
            } catch (e: Exception) {
                logger.info("bringTaskToFront failed: $e, fallback to intent")
                bringTaskToFrontByIntent()
            }
        }
    }

    private fun bringTaskToFrontByIntent() {
        try {
            val intent = Intent(this, VideoLiveAudienceActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            logger.info("bringTaskToFront by startActivity")
        } catch (e: Exception) {
            logger.info("bringTaskToFrontByIntent failed: $e")
        }
    }
}