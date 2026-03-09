package com.trtc.uikit.livekit.livestream

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
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
import com.trtc.uikit.livekit.features.audiencecontainer.AudienceContainerView
import com.trtc.uikit.livekit.features.audiencecontainer.AudienceContainerViewDefine
import com.trtc.uikit.livekit.features.endstatistics.AudienceEndStatisticsView
import com.trtc.uikit.livekit.features.endstatistics.EndStatisticsDefine
import com.trtc.uikit.livekit.livestream.impl.LiveInfoUtils
import com.trtc.uikit.livekit.livestream.impl.VideoLiveKitImpl
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import io.trtc.tuikit.atomicx.common.foregroundservice.VideoForegroundService
import io.trtc.tuikit.atomicx.pictureinpicture.PictureInPictureStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.launch

class VideoLiveAudienceActivity : FullScreenActivity(),
    ITUINotification,
    VideoLiveKitImpl.CallingAPIListener,
    AudienceContainerViewDefine.AudienceContainerViewListener {

    companion object {
        const val KEY_EXTENSION_NAME = "TEBeautyExtension"
        const val NOTIFY_START_ACTIVITY = "onStartActivityNotifyEvent"
        const val METHOD_ACTIVITY_RESULT = "onActivityResult"
        private val logger = LiveKitLogger.getLiveStreamLogger("VideoLiveAudienceActivity")
    }

    private lateinit var layoutContainer: FrameLayout
    private var audienceContainerView: AudienceContainerView? = null
    private var audienceEndStatisticsView: AudienceEndStatisticsView? = null

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

        audienceContainerView = AudienceContainerView(this).apply {
            init(this@VideoLiveAudienceActivity, liveInfo)
            addListener(this@VideoLiveAudienceActivity)
        }

        layoutContainer.addView(audienceContainerView)
        VideoLiveKitImpl.createInstance(applicationContext).addCallingAPIListener(this)
        TUICore.registerEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, this)
        lifecycleScope.launchWhenStarted {
            launch {
                PermissionRequest.requestCompleteEvent.collect {
                    reorderToFront()
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
        onPictureInPictureClick()
    }

    override fun onDestroy() {
        super.onDestroy()
        PIPPanelStore.sharedInstance().reset()
        VideoLiveKitImpl.createInstance(applicationContext).removeCallingAPIListener(this)
        stopForegroundService()
        audienceContainerView?.removeListener(this)
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
            VideoLiveKitImpl.createInstance(applicationContext).stopPushLocalVideoOnStop()
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
        VideoLiveKitImpl.createInstance(applicationContext).startPushLocalVideoOnResume()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        logger.info("onPictureInPictureModeChanged: $isInPictureInPictureMode")
        PIPPanelStore.sharedInstance().state.audienceIsPictureInPictureMode = isInPictureInPictureMode
        PictureInPictureStore.shared.updateIsPictureInPictureMode(isInPictureInPictureMode)
        audienceContainerView?.enablePictureInPictureMode(isInPictureInPictureMode)
        audienceEndStatisticsView?.enablePipMode(isInPictureInPictureMode)

        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            destroyAudienceView()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isPortrait = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        audienceContainerView?.setScreenOrientation(isPortrait)
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

    override fun onPictureInPictureClick() {
        val success = VideoLiveKitImpl.createInstance(applicationContext).enterPictureInPictureMode(this)
        if (success) {
            val roomId = audienceContainerView?.getRoomId()
            PIPPanelStore.sharedInstance().setPictureInPictureModeRoomId(roomId ?: "")
        }
    }

    private fun destroyAudienceView() {
        if (isFinishing || isDestroyed) {
            return
        }
        audienceContainerView?.destroy()
        finishAndRemoveTask()
    }

    private fun reorderToFront() {
        // 重新将当前 Activity 排到最前面, 当前 Activity 在 申请权限时，会被切换为画中画小窗，当权限申请完毕后，需要调用此 api 将当前 Activity 退出画中画回到大屏画面。
        val intent = Intent(this, VideoLiveAudienceActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent)
    }
}