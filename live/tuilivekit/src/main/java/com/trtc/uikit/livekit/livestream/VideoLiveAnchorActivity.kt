package com.trtc.uikit.livekit.livestream

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUINotification
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.component.pippanel.PictureInPictureStore
import com.trtc.uikit.livekit.features.anchorprepare.AnchorPrepareView
import com.trtc.uikit.livekit.features.anchorprepare.AnchorPrepareViewListener
import com.trtc.uikit.livekit.features.anchorprepare.LiveStreamPrivacyStatus
import com.trtc.uikit.livekit.features.anchorprepare.VideoStreamSource
import com.trtc.uikit.livekit.features.anchorview.AnchorState
import com.trtc.uikit.livekit.features.anchorview.AnchorView
import com.trtc.uikit.livekit.features.anchorview.AnchorViewListener
import com.trtc.uikit.livekit.features.anchorview.RoomBehavior
import com.trtc.uikit.livekit.features.endstatistics.AnchorEndStatisticsView
import com.trtc.uikit.livekit.features.endstatistics.EndStatisticsDefine
import com.trtc.uikit.livekit.features.endstatistics.EndStatisticsDefine.AnchorEndStatisticsInfo
import com.trtc.uikit.livekit.livestream.impl.LiveInfoUtils
import com.trtc.uikit.livekit.livestream.impl.VideoLiveKitImpl
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class VideoLiveAnchorActivity : FullScreenActivity(),
    VideoLiveKitImpl.CallingAPIListener,
    AnchorPrepareViewListener,
    AnchorViewListener,
    ITUINotification {

    companion object {
        const val INTENT_KEY_ROOM_ID = "intent_key_room_id"
        const val INTENT_KEY_NEED_CREATE = "intent_key_need_create"
        const val KEY_EXTENSION_NAME = "TEBeautyExtension"
        const val NOTIFY_START_ACTIVITY = "onStartActivityNotifyEvent"
        const val METHOD_ACTIVITY_RESULT = "onActivityResult"
        const val PICK_CONTENT_ALL = "image/*|video/*"
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val logger = LiveKitLogger.getLiveStreamLogger("VideoLiveAnchorActivity")
    }

    private var startActivityRequestCode = 0
    private lateinit var layoutContainer: FrameLayout
    private var anchorPrepareView: AnchorPrepareView? = null
    private var anchorView: AnchorView? = null
    private var anchorEndStatisticsView: AnchorEndStatisticsView? = null
    private var needCreateRoom = true
    private var roomId = ""
    private var liveInfo = LiveInfo(seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats)
    private var videoStreamSource = VideoStreamSource.CAMERA
    private val anchorEndStatisticsInfo = AnchorEndStatisticsInfo()
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
        setContentView(R.layout.livekit_activity_video_live_anchor)
        PIPPanelStore.sharedInstance().state.isAnchorStreaming = true
        roomId = intent.getStringExtra(INTENT_KEY_ROOM_ID) ?: ""
        liveInfo.liveID = roomId
        needCreateRoom = intent.getBooleanExtra(INTENT_KEY_NEED_CREATE, true)

        val liveBundle = intent.extras
        if (liveBundle != null && !liveBundle.containsKey(INTENT_KEY_ROOM_ID)) {
            liveInfo = LiveInfoUtils.convertBundleToLiveInfo(liveBundle)
        }

        layoutContainer = findViewById(R.id.fl_container)

        if (needCreateRoom) {
            addPrepareView()
        } else {
            addAnchorView()
        }
        lifecycleScope.launchWhenStarted {
            LoginStore.shared.loginState.loginStatus.collect {
                if (it == LoginStatus.UNLOGIN) {
                    finishAndRemoveTask()
                    anchorView?.unInit()
                }
            }
        }
        TUICore.registerEvent(KEY_EXTENSION_NAME, NOTIFY_START_ACTIVITY, this)
        TUICore.registerEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, this)
        VideoLiveKitImpl.createInstance(applicationContext).addCallingAPIListener(this)

        BackgroundLaunchDetector.registerActivityLifecycleCallbacks(application, "VideoLiveAnchorActivity", object : BackgroundLaunchListener {
            override fun onBackgroundLaunch(stackTopActivity: Activity) {
                logger.info("onBackgroundLaunch: ${stackTopActivity::class.java.name}")
                if (stackTopActivity is VideoLiveAnchorActivity) {
                    return
                }
                bringTaskToFront()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        cachedTaskId = taskId
        if (!isScreenShareLive()) {
            VideoLiveKitImpl.createInstance(applicationContext).startPushLocalVideoOnResume()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isScreenShareLive()) {
            VideoLiveKitImpl.createInstance(applicationContext).stopPushLocalVideoOnStop()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        logger.info("onUserLeaveHint: $isFinishing")
        if (videoStreamSource == VideoStreamSource.SCREEN_SHARE ||
            (liveInfo.keepOwnerOnSeat && liveInfo.seatLayoutTemplateID == 200)) {
            return
        }
        if (PIPPanelStore.sharedInstance().state.anchorIsPictureInPictureMode) {
            return
        }
        if (LiveListStore.shared().liveState.currentLive.value.liveID.isBlank()) {
            return
        }
        onClickFloatWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.info("onDestroy: bringTaskToFront-> $isFinishing")
        BackgroundLaunchDetector.unregisterActivityLifecycleCallbacks(application, "VideoLiveAnchorActivity")
        PIPPanelStore.sharedInstance().reset()
        TUICore.unRegisterEvent(this)

        anchorPrepareView?.removeAnchorPrepareViewListener(this)
        anchorView?.removeAnchorViewListener(this)

        PIPPanelStore.sharedInstance().setPictureInPictureModeRoomId("")
        PictureInPictureStore.shared.updateIsPictureInPictureMode(false)
    }

    override fun onBackPressed() {}

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        logger.info("onPictureInPictureModeChanged: $isInPictureInPictureMode")
        PictureInPictureStore.shared.updateIsPictureInPictureMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            AtomicPopover.dismissAll()
        }
        PIPPanelStore.sharedInstance().state.anchorIsPictureInPictureMode = isInPictureInPictureMode
        anchorPrepareView?.enablePipMode(isInPictureInPictureMode)
        anchorView?.enablePipMode(isInPictureInPictureMode)
        anchorEndStatisticsView?.enablePipMode(isInPictureInPictureMode)

        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED
            && PictureInPictureStore.shared.hasPipPermission(this)) {
            finishAndRemoveTask()
            anchorView?.unInit()
        }
    }

    private fun addPrepareView() {
        anchorPrepareView = AnchorPrepareView(this).apply {
            init(liveInfo.liveID, null)
            addAnchorPrepareViewListener(this@VideoLiveAnchorActivity)
        }

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        layoutContainer.addView(anchorPrepareView, layoutParams)
    }

    private fun addAnchorView() {
        anchorView = AnchorView(this).apply {
            val params = mutableMapOf<String, Any>()
            params["videoStreamSource"] = videoStreamSource
            anchorPrepareView?.let { prepareView ->
                params["coHostTemplateId"] = prepareView.getState()?.coHostTemplateId?.value ?: ""
                init(
                    liveInfo,
                    prepareView.getCoreView(),
                    if (needCreateRoom) RoomBehavior.CREATE_ROOM else RoomBehavior.ENTER_ROOM,
                    params
                )
            } ?: run {
                init(
                    liveInfo,
                    null,
                    if (needCreateRoom) RoomBehavior.CREATE_ROOM else RoomBehavior.ENTER_ROOM,
                    params
                )
            }
            addAnchorViewListener(this@VideoLiveAnchorActivity)
        }

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        layoutContainer.addView(anchorView, layoutParams)
    }

    private fun removeAnchorPrepareView() {
        layoutContainer.removeAllViews()
    }

    private fun initLiveInfo() {
        anchorPrepareView?.getState()?.let {
            liveInfo.liveName = it.roomName.value
            liveInfo.isPublicVisible = it.liveMode.value == LiveStreamPrivacyStatus.PUBLIC
            liveInfo.coverURL = it.coverURL.value
            liveInfo.backgroundURL = it.coverURL.value
            liveInfo.seatTemplate = LiveInfoUtils.getSeatLayoutTemplateByID(it.coGuestTemplateId.value, 0)
            videoStreamSource = it.videoStreamSource.value
        }
    }

    private fun initEndStatisticsInfo(state: AnchorState?) {
        state?.let {
            anchorEndStatisticsInfo.apply {
                roomId = liveInfo.liveID
                liveDurationMS = it.totalDuration
                maxViewersCount = it.totalViewers
                messageCount = it.totalMessageSent
                giftIncome = it.totalGiftCoins
                giftSenderCount = it.totalGiftUniqueSenders
                likeCount = it.totalLikesReceived
                liveEndedReason = it.liveEndedReason
            }
        }
    }

    private fun removeAnchorView() {
        layoutContainer.removeAllViews()
    }

    private fun isScreenShareLive() : Boolean {
        return (videoStreamSource == VideoStreamSource.SCREEN_SHARE ||
                (liveInfo.seatLayoutTemplateID == 200 && liveInfo.keepOwnerOnSeat))
    }

    private fun addAnchorEndStatisticsView() {
        anchorEndStatisticsView = AnchorEndStatisticsView(this).apply {
            init(anchorEndStatisticsInfo)
            setListener(object : EndStatisticsDefine.AnchorEndStatisticsViewListener {
                override fun onCloseButtonClick() {
                    finishAndRemoveTask()
                }

            })
        }

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        layoutContainer.addView(anchorEndStatisticsView, layoutParams)
    }

    override fun onClickStartButton() {
        anchorPrepareView?.removeAnchorPrepareViewListener(this)
        initLiveInfo()
        removeAnchorPrepareView()
        addAnchorView()
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
            val intent = Intent(this, VideoLiveAnchorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            logger.info("bringTaskToFront by startActivity")
        } catch (e: Exception) {
            logger.info("bringTaskToFrontByIntent failed: $e")
        }
    }

    override fun onClickBackButton() {
        finish()
    }

    override fun onEndLiving(state: AnchorState) {
        anchorView?.removeAnchorViewListener(this)
        AtomicPopover.dismissAll()
        initEndStatisticsInfo(state)
        removeAnchorView()
        addAnchorEndStatisticsView()
    }

    override fun onClickFloatWindow() {
        if (isScreenShareLive()) {
            return
        }
        val success = VideoLiveKitImpl.createInstance(applicationContext).enterPictureInPictureMode(this)
        if (success) {
            PIPPanelStore.sharedInstance().setPictureInPictureModeRoomId(liveInfo.liveID)
        }
    }

    override fun onLeaveLive() {
        finish()
    }

    override fun onStopLive() {
        finish()
    }

    override fun onNotifyEvent(key: String?, subKey: String?, param: Map<String, Any>?) {
        when {
            TextUtils.equals(key, KEY_EXTENSION_NAME) && TextUtils.equals(subKey, NOTIFY_START_ACTIVITY) -> {
                param?.get("requestCode")?.let { requestCode ->
                    startActivityRequestCode = requestCode as Int
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        val intentToPickPic =
                            Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
                                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, PICK_CONTENT_ALL)
                            }
                        startActivityForResult(intentToPickPic, startActivityRequestCode)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                REQUEST_CODE_PERMISSIONS
                            )
                        }
                    }
                }
            }

            TextUtils.equals(key, EVENT_KEY_LIVE_KIT) && TextUtils.equals(subKey, EVENT_SUB_KEY_DESTROY_LIVE_VIEW) -> {
                destroyAnchorView()
            }
        }
    }

    override fun finish() {
        launchAPP()
        super.finish()
    }

    override fun finishAndRemoveTask() {
        launchAPP()
        super.finishAndRemoveTask()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val intentToPickPic = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, PICK_CONTENT_ALL)
            }
            startActivityForResult(intentToPickPic, startActivityRequestCode)
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun destroyAnchorView() {
        if (isFinishing || isDestroyed) {
            return
        }
        finishAndRemoveTask()
    }

    private fun launchAPP() {
        val packageName = packageName
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(it)
        }
    }
}