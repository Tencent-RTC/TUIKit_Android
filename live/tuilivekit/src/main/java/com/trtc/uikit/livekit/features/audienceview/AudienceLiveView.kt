package com.trtc.uikit.livekit.features.audienceview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Point
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.TUIThemeManager
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.COMPONENT_LIVE_STREAM
import com.trtc.uikit.livekit.common.DEFAULT_COVER_URL
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_PARAMS_IS_LINKING
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_LINK_STATUS_CHANGE
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LIVE_INTEGRATION_SUCCESSFUL
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.reportAtomicMetrics
import com.trtc.uikit.livekit.common.setComponent
import com.trtc.uikit.livekit.component.barrage.BarrageStreamView
import com.trtc.uikit.livekit.component.beauty.BeautyIntegration.resetBeauty
import com.trtc.uikit.livekit.component.networkInfo.NetworkInfoView
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore.Companion.DEFAULT_VIDEO_HEIGHT
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore.Companion.DEFAULT_VIDEO_WIDTH
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceAction
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceBottomItem
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceNode
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceTopRightItem
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.scaffold.AudiencePlayingRootView
import com.trtc.uikit.livekit.features.audienceview.view.BasicView
import com.trtc.uikit.livekit.features.audienceview.view.scaffold.AudienceVideoViewAdapter
import com.trtc.uikit.livekit.features.audienceview.view.scaffold.LiveCoreViewMaskBackgroundView
import com.trtc.uikit.livekit.features.audienceview.view.game.AudienceGameView
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.common.imageloader.ImageOptions
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.items
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import io.trtc.tuikit.atomicxcore.api.live.BattleListener
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.GuestListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveKickedOutReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatListener
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.NoResponseReason
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

@SuppressLint("ViewConstructor")
class AudienceLiveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BasicView(context, attrs, defStyleAttr), AudienceStore.AudienceStoreListener {

    internal lateinit var liveInfo: LiveInfo
    private lateinit var liveCoreView: LiveCoreView
    private lateinit var liveCoreViewMaskBackgroundView: LiveCoreViewMaskBackgroundView
    private lateinit var layoutPlaying: FrameLayout
    private lateinit var layoutLiveCoreView: FrameLayout
    private lateinit var layoutLiveCoreViewMask: FrameLayout
    private lateinit var ivVideoViewBackground: ImageView
    private lateinit var layoutSwitchOrientationButton: FrameLayout
    private lateinit var imageSwitchOrientationIcon: ImageView
    private lateinit var audiencePlayingRootView: AudiencePlayingRootView
    private lateinit var networkInfoView: NetworkInfoView
    private lateinit var imageCompactExit: ImageView
    private var audienceGameView: AudienceGameView? = null
    lateinit var overlayView: AudienceOverlayView
        private set

    private var endLiveDialog: AtomicAlertDialog? = null
    private var viewObserver: ViewObserver? = null
    private var isLoading: Boolean = false
    private var videoViewAdapterImpl: AudienceVideoViewAdapter? = null

    private var touchX: Float = 0f
    private var touchY: Float = 0f
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var isSwiping: Boolean = false
    private var isLiveStreaming: Boolean = false
    private var playbackQuality: VideoQuality? = null

    private lateinit var ivHostAbsent: ImageView
    private var hostAbsentJob: Job? = null

    val coreView: LiveCoreView get() = liveCoreView
    val barrageStreamView: BarrageStreamView get() = overlayView.barrageStreamView

    var bottomItems: List<AudienceBottomItem>
        get() = overlayView.bottomItems
        set(value) {
            overlayView.bottomItems = value
        }

    var topRightItems: List<AudienceTopRightItem>
        get() = overlayView.topRightItems
        set(value) {
            overlayView.topRightItems = value
        }

    fun replace(node: AudienceNode, view: View?) {
        overlayView.replace(node, view)
    }

    fun perform(action: AudienceAction) {
        overlayView.perform(action)
    }

    fun init(liveInfo: LiveInfo) {
        LOGGER.info("AudienceView init:$this")
        this.liveInfo = liveInfo
        liveCoreView = LiveCoreView(context)
        liveCoreView.setLiveID(liveInfo.liveID)
        if (liveCoreView.parent != layoutLiveCoreView) {
            (liveCoreView.parent as? ViewGroup)?.removeView(liveCoreView)
            layoutLiveCoreView.addView(liveCoreView)
        }
    }

    internal fun initStore() {
        audienceStore = AudienceStore(liveInfo.liveID)
        init(audienceStore)
        overlayView.init(audienceStore)
        this@AudienceLiveView.audienceStore.getMediaStore().setCustomVideoProcess()
        liveCoreViewMaskBackgroundView = LiveCoreViewMaskBackgroundView(context)
        liveCoreViewMaskBackgroundView.init(audienceStore)
        layoutLiveCoreViewMask.addView(liveCoreViewMaskBackgroundView)
        createVideoMuteBitmap()
        setComponent(COMPONENT_LIVE_STREAM)
        setLayoutBackground(
            if (TextUtils.isEmpty(liveInfo.backgroundURL)) liveInfo.coverURL else liveInfo.backgroundURL,
            liveInfo.seatTemplate
        )
    }

    fun getRoomId(): String {
        return liveInfo.liveID
    }

    fun addListener(listener: AudienceStore.AudienceStoreListener) {
        audienceStore.addAudienceViewListener(listener)
    }

    fun removeListener(listener: AudienceStore.AudienceStoreListener) {
        audienceStore.removeAudienceViewListener(listener)
    }

    override fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livekit_livestream_audience_view, this, true)
        layoutPlaying = findViewById(R.id.fl_playing)
        ivVideoViewBackground = findViewById(R.id.video_view_background)
        layoutLiveCoreView = findViewById(R.id.live_core_view)
        layoutLiveCoreViewMask = findViewById(R.id.live_core_view_mask)
        ivHostAbsent = findViewById(R.id.iv_host_absent)
        audiencePlayingRootView = findViewById(R.id.fl_playing_root)
        layoutSwitchOrientationButton = findViewById(R.id.fl_switch_orientation_button)
        imageSwitchOrientationIcon = findViewById(R.id.img_switch_orientation_button_icon)

        overlayView = findViewById(R.id.audience_overlay_view)
        networkInfoView = findViewById(R.id.network_info_view)
        imageCompactExit = findViewById(R.id.iv_compact_exit_room)
        imageCompactExit.setOnClickListener { onExitButtonClick() }
        overlayView.onExitClick = { onExitButtonClick() }
        overlayView.onFloatWindowClick = {
            if ((context as Activity).requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                setScreenOrientation(true)
            }
            audienceStore.notifyPictureInPictureClick()
        }
    }

    override fun addObserver() {
    }

    override fun removeObserver() {
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                isSwiping = false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - touchX
                val deltaY = event.y - touchY
                if (Math.abs(deltaX) > touchSlop && Math.abs(deltaX) > Math.abs(deltaY)) {
                    isSwiping = true
                }
            }
        }

        return if (isSwiping) {
            audiencePlayingRootView.dispatchTouchEvent(event)
        } else {
            super.dispatchTouchEvent(event)
        }
    }

    fun enablePictureInPictureMode(enable: Boolean) {
        if (enable) {
            audiencePlayingRootView.visibility = GONE
        } else {
            audiencePlayingRootView.visibility = VISIBLE
        }
        mediaStore.enablePictureInPictureMode(enable)

        val isPortrait =
            (context as Activity).requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setCoreViewLayoutParamsWhenLandscape(liveInfo.seatTemplate, isPortrait)
    }

    internal fun startPreviewLiveStream() {
        liveCoreView.startPreviewLiveStream(liveInfo.liveID, true, null)
    }

    internal fun stopPreviewLiveStream() {
        liveCoreView.stopPreviewLiveStream(liveInfo.liveID)
    }

    fun joinRoom() {
        subscribeObserver()
        isLiveStreaming = true
        audienceStore.addObserver()
        layoutPlaying.visibility = GONE
        onViewLoading()
        this@AudienceLiveView.audienceStore.getMediaStore().setCustomVideoProcess()
        liveCoreView.setLocalVideoMuteImage(
            mediaState.bigMuteBitmap.value,
            mediaState.smallMuteBitmap.value
        )
        setVideoViewAdapter()
        reportAtomicMetrics(LIVE_INTEGRATION_SUCCESSFUL)
        liveListStore.joinLive(liveInfo.liveID, object : LiveInfoCompletionHandler {
            override fun onSuccess(liveInfo: LiveInfo) {
                this@AudienceLiveView.liveInfo = liveInfo
                setCoreViewLayoutParamsWhenLandscape(liveInfo.seatTemplate, true)
                if (liveInfo.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats) {
                    PIPPanelStore.sharedInstance().state.width = DEFAULT_VIDEO_HEIGHT
                    PIPPanelStore.sharedInstance().state.height = DEFAULT_VIDEO_WIDTH
                } else {
                    PIPPanelStore.sharedInstance().state.width = DEFAULT_VIDEO_WIDTH
                    PIPPanelStore.sharedInstance().state.height = DEFAULT_VIDEO_HEIGHT
                }
                val activity = context as Activity
                if (activity.isFinishing || activity.isDestroyed) {
                    LOGGER.warn("activity is exit, leaveLiveStream")
                    audienceStore.getLiveListStore().leaveLive(null)
                    liveCoreView.setLocalVideoMuteImage(null, null)
                    isLiveStreaming = false
                    return
                }
                liveCoreViewMaskBackgroundView.setBackgroundUrl(
                    if (TextUtils.isEmpty(liveInfo.backgroundURL)) liveInfo.coverURL else liveInfo.backgroundURL
                )
                mediaStore.getMultiPlaybackQuality(liveInfo.liveID)
                audienceStore.updateLiveInfo(liveInfo)
                layoutPlaying.visibility = VISIBLE
                overlayView.initComponentView(liveInfo)
                overlayView.initCoGuestVisibility(liveInfo)
                networkInfoView.init(liveInfo)
                initGameView(liveInfo)
                subscribeHostAbsentState()
                onViewFinished()
            }

            override fun onFailure(code: Int, desc: String) {
                isLiveStreaming = false
                onViewFinished()
                ErrorLocalized.onError(code)
                TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
            }
        })
    }

    internal fun leaveRoom() {
        unsubscribeObserver()
        clearHostAbsentState()
        audiencePlayingRootView.resetClearScreenState()
        liveCoreView.setBackgroundColor(resources.getColor(android.R.color.transparent))
        if (!isLiveStreaming) {
            return
        }
        isLiveStreaming = false
        stopPreviewLiveStream()
        audienceStore.removeObserver()
        audienceStore.getLiveListStore().leaveLive(null)
        audienceStore.updateLiveInfo(LiveInfo(seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats))
        liveCoreView.setLocalVideoMuteImage(null, null)
        mediaStore.releaseVideoMuteBitmap()
        overlayView.unInitComponentView()
        resetBeauty()
        playbackQuality = null
    }

    fun setViewObserver(observer: ViewObserver?) {
        viewObserver = observer
    }

    private fun subscribeHostAbsentState() {
        hostAbsentJob?.cancel()
        hostAbsentJob = CoroutineScope(Dispatchers.Main).launch {
            liveSeatState.seatList
                .map { seatList -> seatList.any { !TextUtils.isEmpty(it.userInfo.userID) } }
                .distinctUntilChanged()
                .collectLatest { hasHost ->
                    if (hasHost) {
                        ivHostAbsent.visibility = GONE
                    } else {
                        delay(1000)
                        ivHostAbsent.setImageResource(getHostAbsentImageResId())
                        ivHostAbsent.visibility = VISIBLE
                    }
                }
        }
    }

    private fun getHostAbsentImageResId(): Int {
        val isLandscape = liveInfo.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats
        val isEn = Locale.ENGLISH.language == Locale.getDefault().language
        return if (isLandscape) {
            if (isEn) R.drawable.livekit_local_mute_image_en_land
            else R.drawable.livekit_local_mute_image_land
        } else {
            if (isEn) R.drawable.livekit_local_mute_image_en
            else R.drawable.livekit_local_mute_image_zh
        }
    }

    private fun clearHostAbsentState() {
        ivHostAbsent.visibility = GONE
        hostAbsentJob?.cancel()
        hostAbsentJob = null
    }

    private fun initSwitchOrientationButtonView() {
        updateViewByOrientation((context as Activity).requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        layoutSwitchOrientationButton.setOnClickListener {
            val isPortrait =
                (context as Activity).requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            setScreenOrientation(!isPortrait)
        }
    }

    private fun setScreenOrientation(isPortrait: Boolean) {
        (context as Activity).requestedOrientation =
            if (isPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        updateViewByOrientation(isPortrait)
        liveCoreViewMaskBackgroundView.setPortrait(isPortrait)
        setCoreViewLayoutParamsWhenLandscape(liveInfo.seatTemplate, isPortrait)
    }

    private fun updateViewByOrientation(isPortrait: Boolean) {
        layoutSwitchOrientationButton.layoutParams = getSwitchScreenButtonPosition(isPortrait)
        imageSwitchOrientationIcon.setImageResource(
            if (isPortrait) R.drawable.livekit_ic_switch_landscape_button
            else R.drawable.livekit_ic_switch_portrait_button
        )

        val compactExitParams = imageCompactExit.layoutParams as LayoutParams
        compactExitParams.topMargin = if (isPortrait) dip2px(60f) else dip2px(30f)
        imageCompactExit.layoutParams = compactExitParams

        networkInfoView.setScreenOrientation(isPortrait)

        overlayView.updateViewByOrientation(isPortrait)
    }

    private fun getScreenPoint(): Point {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val defaultDisplay = windowManager.defaultDisplay
        val point = Point()
        defaultDisplay.getSize(point)
        return point
    }

    private fun getSwitchScreenButtonPosition(isPortrait: Boolean): LayoutParams {
        val point = getScreenPoint()
        val screenWidth = point.x
        val screenHeight = point.y
        val videoWidth: Int
        val videoHeight: Int
        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )

        if (isPortrait) {
            videoWidth = screenWidth
            videoHeight = videoWidth * 9 / 16
            val videoTop = videoHeight + dip2px(100f)
            params.rightMargin = dip2px(12f)
            params.topMargin = videoTop
        } else {
            videoHeight = screenWidth
            videoWidth = videoHeight * 16 / 9
            val videoRightMargin = (screenHeight - videoWidth) / 2
            val videoBottomMargin = videoHeight / 2
            params.rightMargin = videoRightMargin + dip2px(24f)
            params.topMargin = videoBottomMargin
        }
        return params
    }

    private fun setLayoutBackground(imageUrl: String?, seatLayoutTemplate: SeatLayoutTemplate) {
        LOGGER.info("setLayoutBackground->imageUrl: $imageUrl, seatLayoutTemplate:$seatLayoutTemplate")
        if (seatLayoutTemplate != SeatLayoutTemplate.VideoLandscape4Seats) {
            val builder = ImageOptions.Builder()
            builder.setBlurEffect(80f)
            if (TextUtils.isEmpty(imageUrl)) {
                ImageLoader.load(context, ivVideoViewBackground, DEFAULT_COVER_URL, builder.build())
            } else {
                ImageLoader.load(context, ivVideoViewBackground, imageUrl, builder.build())
            }
        } else {
            ImageLoader.clear(context, ivVideoViewBackground)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivVideoViewBackground.setRenderEffect(null)
            }
            ivVideoViewBackground.setImageDrawable(null)
            ivVideoViewBackground.setBackgroundColor(Color.BLACK)
        }
    }

    private fun onViewLoading() {
        isLoading = true
        viewObserver?.onLoading()
    }

    private fun onViewFinished() {
        isLoading = false
        viewObserver?.onFinished()
    }

    private fun initGameView(liveInfo: LiveInfo) {
        if (liveInfo.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats && liveInfo.keepOwnerOnSeat) {
            liveCoreView.setVideoViewAdapter(null)
            if (audienceGameView == null) {
                audienceGameView = AudienceGameView.create(this, audienceStore)
            }
        }
        audienceGameView?.init(audienceStore)
    }

    private fun setVideoViewAdapter() {
        videoViewAdapterImpl = AudienceVideoViewAdapter(
            context, audienceStore,
            object : AudienceVideoViewAdapter.Callback {
                override fun showCoGuestManageDialog(userInfo: LiveUserInfo) = overlayView.showCoGuestManageDialog(userInfo)
            }
        )
        liveCoreView.setVideoViewAdapter(videoViewAdapterImpl)
    }

    private fun createVideoMuteBitmap() {
        val bigMuteImageResId =
            if (Locale.ENGLISH.language == TUIThemeManager.getInstance().currentLanguage)
                R.drawable.livekit_local_mute_image_en else R.drawable.livekit_local_mute_image_zh
        val smallMuteImageResId = R.drawable.livekit_local_mute_image_multi
        mediaStore.createVideoMuteBitmap(context, bigMuteImageResId, smallMuteImageResId)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        liveCoreView.setVideoViewAdapter(null)
    }

    private fun setCoreViewLayoutParamsWhenLandscape(
        template: SeatLayoutTemplate,
        isPortrait: Boolean
    ) {
        LOGGER.info("setCoreViewLayoutParamsWhenLandscape:template:$template,isPortrait:$isPortrait")
        val layoutParams: LayoutParams =
            layoutLiveCoreView.layoutParams as LayoutParams
        if (audienceStore.getMediaStore().mediaState.isPictureInPictureMode.value) {
            layoutParams.topMargin = 0
            layoutParams.height = LayoutParams.MATCH_PARENT
        } else if (template == SeatLayoutTemplate.VideoLandscape4Seats && isPortrait) {
            layoutParams.topMargin = dip2px(150f)
            layoutParams.height = ScreenUtil.getScreenWidth(context) * 720 / 1280
        } else {
            layoutParams.topMargin = 0
            layoutParams.height = LayoutParams.MATCH_PARENT
        }
        layoutLiveCoreView.layoutParams = layoutParams
        syncHostAbsentLayout(layoutParams)
        setLayoutBackground(
            if (TextUtils.isEmpty(liveInfo.backgroundURL)) liveInfo.coverURL else liveInfo.backgroundURL,
            template
        )
    }

    private fun syncHostAbsentLayout(coreViewParams: LayoutParams) {
        val hostAbsentParams = ivHostAbsent.layoutParams as LayoutParams
        hostAbsentParams.topMargin = coreViewParams.topMargin
        hostAbsentParams.height = coreViewParams.height
        ivHostAbsent.layoutParams = hostAbsentParams
    }

    private fun subscribeObserver() {
        audienceStore.addAudienceViewListener(this)
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                liveSeatState.canvas.collect {
                    if (liveListState.currentLive.value.liveID != liveInfo.liveID) return@collect
                    if (it.w == 0 || it.h == 0) return@collect
                    val isLandscape = it.w >= it.h
                    onVideoOrientationChanged(isLandscape)
                }
            }
            launch {
                coGuestState.connected.collect {
                    onLinkStatusChange()
                }
            }
            launch {
                viewState.isApplyingToTakeSeat.collect {
                    onLinkStatusChange()
                }
            }
            launch {
                mediaState.playbackQuality.collect {
                    onPlaybackQualityChanged(it)
                }
            }
        }
        liveListStore.addLiveListListener(liveListListener)
        liveSeatStore.addLiveSeatEventListener(seatListener)
        coGuestStore.addGuestListener(conGuestListener)
        battleStore.addBattleListener(battleListener)
        liveAudienceStore.addLiveAudienceListener(liveAudienceListener)
    }

    private fun unsubscribeObserver() {
        audienceStore.removeAudienceViewListener(this)
        subscribeStateJob?.cancel()
        liveListStore.removeLiveListListener(liveListListener)
        liveSeatStore.removeLiveSeatEventListener(seatListener)
        coGuestStore.removeGuestListener(conGuestListener)
        battleStore.removeBattleListener(battleListener)
        liveAudienceStore.removeLiveAudienceListener(liveAudienceListener)
    }

    private fun onExitButtonClick() {
        LOGGER.info("onExitButtonClick, isLoading:$isLoading")
        if (isLoading) {
            return
        }
        if (!coGuestState.connected.value.none { it.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID }) {
            showLiveStreamEndDialog()
        } else {
            TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
        }
    }

    private fun onLinkStatusChange() {
        val params = HashMap<String, Any>()
        if (!coGuestState.connected.value.none { it.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID }) {
            params[EVENT_PARAMS_IS_LINKING] = true
        } else if (viewState.isApplyingToTakeSeat.value) {
            params[EVENT_PARAMS_IS_LINKING] = true
        } else {
            params[EVENT_PARAMS_IS_LINKING] = false
        }
        TUICore.notifyEvent("EVENT_KEY_LIVE_KIT", EVENT_SUB_KEY_LINK_STATUS_CHANGE, params)
    }

    private fun onVideoOrientationChanged(videoStreamIsLandscape: Boolean) {
        layoutSwitchOrientationButton.visibility =
            if (videoStreamIsLandscape && !liveInfo.keepOwnerOnSeat) VISIBLE else GONE
        if (videoStreamIsLandscape) {
            initSwitchOrientationButtonView()
        } else {
            if ((context as Activity).requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                setScreenOrientation(true)
            }
        }
    }

    private fun onPlaybackQualityChanged(videoQuality: VideoQuality?) {
        if (playbackQuality != null && playbackQuality != videoQuality) {
            AtomicToast.show(
                context,
                context.getString(R.string.live_video_resolution_changed) + videoQualityToString(
                    videoQuality!!
                ),
                AtomicToast.Style.INFO
            )
        }

        playbackQuality = videoQuality
    }

    private fun showLiveStreamEndDialog() {
        val atomicEndLiveDialog = AtomicAlertDialog(context)

        atomicEndLiveDialog.init {
            title = resources.getString(R.string.common_audience_end_link_tips)
            items(
                listOf(
                    Pair(
                        resources.getString(R.string.common_end_link),
                        AtomicAlertDialog.TextColorPreset.RED
                    ),
                    Pair(
                        resources.getString(R.string.common_exit_live),
                        AtomicAlertDialog.TextColorPreset.PRIMARY
                    ),
                    Pair(
                        resources.getString(R.string.common_cancel),
                        AtomicAlertDialog.TextColorPreset.PRIMARY
                    )
                ),
                isBold = false
            ) { dialog, index, text ->
                when (index) {
                    0 -> {
                        coGuestStore.disconnect(null)
                        audienceStore.getViewStore().updateTakeSeatState(false)
                    }

                    1 -> {
                        TUICore.notifyEvent(
                            EVENT_KEY_LIVE_KIT,
                            EVENT_SUB_KEY_DESTROY_LIVE_VIEW,
                            null
                        )
                    }

                    2 -> {
                    }
                }
            }
        }
        endLiveDialog = atomicEndLiveDialog
        endLiveDialog?.show()
    }

    override fun onLiveEnded(roomId: String, ownerName: String, ownerAvatarUrl: String) {
        isLiveStreaming = false
        endLiveDialog?.dismiss()
    }

    private fun videoQualityToString(quality: VideoQuality): String {
        return when (quality) {
            VideoQuality.QUALITY_1080P -> "1080P"
            VideoQuality.QUALITY_720P -> "720P"
            VideoQuality.QUALITY_540P -> "540P"
            VideoQuality.QUALITY_360P -> "360P"
            else -> "original"
        }
    }

    private val liveListListener = object : LiveListListener() {
        override fun onKickedOutOfLive(
            liveID: String,
            reason: LiveKickedOutReason,
            message: String,
        ) {
            if (liveListState.currentLive.value.liveOwner.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                return
            }
            if (LiveKickedOutReason.BY_LOGGED_ON_OTHER_DEVICE != reason) {
                AtomicToast.show(
                    context,
                    context.resources.getString(R.string.common_kicked_out_of_room_by_owner),
                    AtomicToast.Style.INFO
                )
                TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
            }
        }
    }
    private val seatListener = object : LiveSeatListener() {
        override fun onLocalCameraOpenedByAdmin(policy: DeviceControlPolicy) {
            if (policy == DeviceControlPolicy.UNLOCK_ONLY) {
                AtomicToast.show(
                    context,
                    context.resources.getString(R.string.common_un_mute_video_by_master),
                    AtomicToast.Style.INFO
                )
            }
        }

        override fun onLocalCameraClosedByAdmin() {
            AtomicToast.show(
                context,
                context.resources.getString(R.string.common_mute_video_by_owner),
                AtomicToast.Style.INFO
            )
        }

        override fun onLocalMicrophoneOpenedByAdmin(policy: DeviceControlPolicy) {
            if (policy == DeviceControlPolicy.UNLOCK_ONLY) {
                AtomicToast.show(
                    context,
                    context.resources.getString(R.string.common_un_mute_audio_by_master),
                    AtomicToast.Style.INFO
                )
            }

        }

        override fun onLocalMicrophoneClosedByAdmin() {
            AtomicToast.show(
                context,
                context.resources.getString(R.string.common_mute_audio_by_master),
                AtomicToast.Style.INFO
            )
        }
    }
    private val conGuestListener = object : GuestListener() {
        override fun onGuestApplicationResponded(isAccept: Boolean, hostUser: LiveUserInfo) {
            audienceStore.getViewStore()
                .updateTakeSeatState(false)
            if (isAccept) {

                if (viewState.openCameraAfterTakeSeat.value) {
                    audienceStore.getDeviceStore().openLocalCamera(
                        audienceStore.getDeviceStore().deviceState.isFrontCamera.value,
                        null
                    )
                }
                audienceStore.getDeviceStore().openLocalMicrophone(null)
                return
            }
            AtomicToast.show(
                context,
                context.resources.getString(R.string.common_voiceroom_take_seat_rejected),
                AtomicToast.Style.INFO
            )
        }

        override fun onGuestApplicationNoResponse(reason: NoResponseReason) {
            if (reason == NoResponseReason.TIMEOUT) {
                AtomicToast.show(
                    context,
                    context.resources.getString(R.string.common_voiceroom_take_seat_timeout),
                    AtomicToast.Style.INFO
                )
            }
            audienceStore.getViewStore()
                .updateTakeSeatState(false)
        }

        override fun onKickedOffSeat(seatIndex: Int, hostUser: LiveUserInfo) {
            AtomicToast.show(
                context,
                context.resources.getString(R.string.common_voiceroom_kicked_out_of_seat),
                AtomicToast.Style.INFO
            )
        }
    }
    private val battleListener = object : BattleListener() {
        override fun onBattleRequestCancelled(
            battleID: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            val toast =
                inviter.userName + " " + context.getString(R.string.common_battle_inviter_cancel)
            showBattleToast(toast)
        }

        override fun onBattleRequestReject(
            battleID: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            val toast =
                invitee.userName + " " + context.getString(R.string.common_battle_invitee_reject)
            showBattleToast(toast)
        }

        override fun onBattleRequestTimeout(
            battleID: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            showBattleToast(context.getString(R.string.common_battle_invitation_timeout))
        }
    }

    private val liveAudienceListener = object : LiveAudienceListener() {
        override fun onAudienceMessageDisabled(audience: LiveUserInfo, isDisable: Boolean) {
            audienceStore.getIMStore().onAudienceMessageDisabled(audience.userID, isDisable)
        }
    }

    interface ViewObserver {
        fun onLoading()
        fun onFinished()
    }

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("AudienceView")

        fun showBattleToast(tips: String) {
            ContextProvider.getApplicationContext()?.apply {
                AtomicToast.show(
                    this,
                    tips,
                    style = AtomicToast.Style.INFO
                )
            }
        }
    }
}
