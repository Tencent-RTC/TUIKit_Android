package com.trtc.uikit.livekit.features.audiencecontainer.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.TUIThemeManager
import com.trtc.tuikit.common.imageloader.ImageLoader
import com.trtc.tuikit.common.imageloader.ImageOptions
import com.trtc.tuikit.common.util.ScreenUtil
import com.trtc.tuikit.common.util.ScreenUtil.dip2px
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.COMPONENT_LIVE_STREAM
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_PARAMS_IS_LINKING
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_LINK_STATUS_CHANGE
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.setComponent
import com.trtc.uikit.livekit.component.audiencelist.AudienceListView
import com.trtc.uikit.livekit.component.barrage.BarrageInputView
import com.trtc.uikit.livekit.component.barrage.BarrageStreamView
import com.trtc.uikit.livekit.component.beauty.BeautyUtils.resetBeauty
import com.trtc.uikit.livekit.component.beauty.tebeauty.store.TEBeautyStore
import com.trtc.uikit.livekit.component.gift.GiftPlayView
import com.trtc.uikit.livekit.component.gift.LikeButton
import com.trtc.uikit.livekit.component.giftaccess.GiftButton
import com.trtc.uikit.livekit.component.giftaccess.service.GiftCacheService
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_COUNT
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_ICON_URL
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_NAME
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_RECEIVER_USERNAME
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_VIEW_TYPE
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_VIEW_TYPE_1
import com.trtc.uikit.livekit.component.giftaccess.store.GiftStore
import com.trtc.uikit.livekit.component.giftaccess.view.BarrageViewTypeDelegate
import com.trtc.uikit.livekit.component.giftaccess.view.GiftBarrageAdapter
import com.trtc.uikit.livekit.component.networkInfo.NetworkInfoView
import com.trtc.uikit.livekit.component.roominfo.LiveInfoView
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceContainerConfig
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceStore
import com.trtc.uikit.livekit.features.audiencecontainer.store.observer.AudienceContainerViewListenerList
import com.trtc.uikit.livekit.features.audiencecontainer.view.battle.widgets.BattleInfoView
import com.trtc.uikit.livekit.features.audiencecontainer.view.battle.widgets.BattleMemberInfoView
import com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.panel.AnchorManagerDialog
import com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.panel.CancelRequestDialog
import com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.panel.CoGuestRequestFloatView
import com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.panel.StopCoGuestDialog
import com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.panel.TypeSelectDialog
import com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.widgets.AudienceEmptySeatView
import com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.widgets.CoGuestBackgroundWidgetsView
import com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.widgets.CoGuestForegroundWidgetsView
import com.trtc.uikit.livekit.features.audiencecontainer.view.cohost.widgets.CoHostBackgroundWidgetsView
import com.trtc.uikit.livekit.features.audiencecontainer.view.cohost.widgets.CoHostForegroundWidgetsView
import com.trtc.uikit.livekit.features.audiencecontainer.view.settings.AudienceSettingsPanelDialog
import com.trtc.uikit.livekit.features.audiencecontainer.view.userinfo.UserInfoDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.items
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import io.trtc.tuikit.atomicxcore.api.gift.Gift
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
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView
import io.trtc.tuikit.atomicxcore.api.view.VideoViewAdapter
import io.trtc.tuikit.atomicxcore.api.view.ViewLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale

@SuppressLint("ViewConstructor")
class AudienceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BasicView(context, attrs, defStyleAttr), AudienceStore.AudienceViewListener {

    private lateinit var liveInfo: LiveInfo
    private lateinit var liveCoreView: LiveCoreView
    private lateinit var liveCoreViewMaskBackgroundView: LiveCoreViewMaskBackgroundView
    private lateinit var layoutPlaying: FrameLayout
    private lateinit var layoutLiveCoreView: FrameLayout
    private lateinit var layoutLiveCoreViewMask: FrameLayout
    private lateinit var ivVideoViewBackground: ImageView
    private lateinit var imageMore: ImageView
    private lateinit var buttonGift: GiftButton
    private lateinit var buttonLike: LikeButton
    private lateinit var imageCoGuest: ImageView
    private lateinit var barrageInputView: BarrageInputView
    private lateinit var roomInfoView: LiveInfoView
    private lateinit var giftPlayView: GiftPlayView
    private lateinit var audienceListView: AudienceListView
    private lateinit var barrageStreamView: BarrageStreamView
    private lateinit var networkInfoView: NetworkInfoView
    private lateinit var imageFloatWindow: ImageView
    private lateinit var imageStandardExit: ImageView
    private lateinit var imageCompactExit: ImageView
    private lateinit var layoutSwitchOrientationButton: FrameLayout
    private lateinit var imageSwitchOrientationIcon: ImageView
    private lateinit var waitingCoGuestPassView: CoGuestRequestFloatView
    private lateinit var audiencePlayingRootView: AudiencePlayingRootView
    private var endLiveDialog: AtomicAlertDialog? = null
    private var userInfoDialog: UserInfoDialog? = null
    private var anchorManagerDialog: AnchorManagerDialog? = null
    private var viewObserver: ViewObserver? = null
    private var isLoading: Boolean = false
    private var videoViewAdapterImpl: VideoViewAdapterImpl? = null

    private var touchX: Float = 0f
    private var touchY: Float = 0f
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var isSwiping: Boolean = false
    private var isLiveStreaming: Boolean = false
    private var isClickEmptySeat: Boolean = false
    private var playbackQuality: VideoQuality? = null

    fun init(liveInfo: LiveInfo) {
        LOGGER.info("AudienceView init:$this")
        this.liveInfo = liveInfo
        liveCoreView = LiveCoreView(context)
        liveCoreView.setLiveID(liveInfo.liveID)
    }

    fun initStore() {
        audienceStore = AudienceStore(liveInfo.liveID)
        init(audienceStore)
        this@AudienceView.audienceStore.getMediaStore().setCustomVideoProcess()
        if (liveCoreView.parent != null && liveCoreView.parent == layoutLiveCoreView) {
            layoutLiveCoreView.removeView(liveCoreView)
        }
        layoutLiveCoreView.addView(liveCoreView)
        liveCoreViewMaskBackgroundView = LiveCoreViewMaskBackgroundView(context)
        liveCoreViewMaskBackgroundView.init(audienceStore)
        layoutLiveCoreViewMask.addView(liveCoreViewMaskBackgroundView)
        createVideoMuteBitmap()
        setComponent(COMPONENT_LIVE_STREAM)
        setLayoutBackground(liveInfo.coverURL, liveInfo.seatLayoutTemplateID)
    }

    fun getRoomId(): String {
        return liveInfo.liveID
    }

    fun setAudienceContainerViewListenerList(viewListenerList: AudienceContainerViewListenerList) {
        audienceStore.setAudienceContainerViewListenerList(viewListenerList)
    }

    override fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livekit_livestream_audience_view, this, true)
        layoutPlaying = findViewById(R.id.fl_playing)
        imageCoGuest = findViewById(R.id.iv_co_guest)
        imageMore = findViewById(R.id.iv_more)
        buttonGift = findViewById(R.id.btn_gift)
        buttonLike = findViewById(R.id.btn_like)
        barrageInputView = findViewById(R.id.barrage_input_view)
        roomInfoView = findViewById(R.id.room_info_view)
        audienceListView = findViewById(R.id.audience_list_view)
        barrageStreamView = findViewById(R.id.barrage_stream_view)
        networkInfoView = findViewById(R.id.network_info_view)
        waitingCoGuestPassView = findViewById(R.id.btn_waiting_pass)
        giftPlayView = findViewById(R.id.gift_play_view)
        imageFloatWindow = findViewById(R.id.iv_float_window)
        imageStandardExit = findViewById(R.id.iv_standard_exit_room)
        imageCompactExit = findViewById(R.id.iv_compact_exit_room)
        ivVideoViewBackground = findViewById(R.id.video_view_background)
        layoutLiveCoreView = findViewById(R.id.live_core_view)
        layoutLiveCoreViewMask = findViewById(R.id.live_core_view_mask)
        audiencePlayingRootView = findViewById(R.id.fl_playing_root)
        layoutSwitchOrientationButton = findViewById(R.id.fl_switch_orientation_button)
        imageSwitchOrientationIcon = findViewById(R.id.img_switch_orientation_button_icon)
        initFloatWindowView()
    }

    override fun addObserver() {
    }

    override fun removeObserver() {
    }

    private fun initFloatWindowView() {
        imageFloatWindow.setOnClickListener {
            if ((context as Activity).requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                setScreenOrientation(true)
            }
            audienceStore.notifyPictureInPictureClick()
        }
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
    }

    fun startPreviewLiveStream() {
        liveCoreView.startPreviewLiveStream(liveInfo.liveID, true, null)
    }

    fun stopPreviewLiveStream() {
        liveCoreView.stopPreviewLiveStream(liveInfo.liveID)
    }

    fun joinRoom() {
        subscribeObserver()
        isLiveStreaming = true
        audienceStore.addObserver()
        layoutPlaying.visibility = GONE
        onViewLoading()
        this@AudienceView.audienceStore.getMediaStore().setCustomVideoProcess()
        liveCoreView.setLocalVideoMuteImage(
            mediaState.bigMuteBitmap.value,
            mediaState.smallMuteBitmap.value
        )
        setVideoViewAdapter()
        liveListStore.joinLive(liveInfo.liveID, object : LiveInfoCompletionHandler {
            override fun onSuccess(liveInfo: LiveInfo) {
                this@AudienceView.liveInfo = liveInfo
                setCoreViewLayoutParamsWhenLandscape(liveInfo.seatLayoutTemplateID, true)
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
                liveCoreView.setBackgroundColor(resources.getColor(android.R.color.black))
                mediaStore.getMultiPlaybackQuality(liveInfo.liveID)
                audienceStore.updateLiveInfo(liveInfo)
                initComponentView(liveInfo)
                initCoGuestVisibility()
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

    fun leaveRoom() {
        unsubscribeObserver()
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
        roomInfoView.unInit()
        resetBeauty()
        TEBeautyStore.unInit()
        playbackQuality = null
    }

    fun setViewObserver(observer: ViewObserver?) {
        viewObserver = observer
    }

    fun isLiveStreaming(): Boolean {
        return isLiveStreaming
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
        setCoreViewLayoutParamsWhenLandscape(liveInfo.seatLayoutTemplateID, isPortrait)
    }

    private fun updateViewByOrientation(isPortrait: Boolean) {
        layoutSwitchOrientationButton.layoutParams = getSwitchScreenButtonPosition(isPortrait)
        imageSwitchOrientationIcon.setImageResource(
            if (isPortrait) R.drawable.livekit_ic_switch_landscape_button
            else R.drawable.livekit_ic_switch_portrait_button
        )
        barrageInputView.visibility = if (isPortrait) VISIBLE else GONE
        buttonGift.visibility = if (isPortrait) VISIBLE else GONE
        imageMore.visibility = if (isPortrait) VISIBLE else GONE
        buttonLike.visibility = if (isPortrait) VISIBLE else GONE

        val audienceListParams = audienceListView.layoutParams as FrameLayout.LayoutParams
        audienceListParams.topMargin = if (isPortrait) dip2px(60f) else dip2px(30f)
        audienceListView.setScreenOrientation(isPortrait)
        audienceListView.layoutParams = audienceListParams

        val compactExitParams = imageCompactExit.layoutParams as FrameLayout.LayoutParams
        compactExitParams.topMargin = if (isPortrait) dip2px(60f) else dip2px(30f)
        imageCompactExit.layoutParams = compactExitParams

        val standardExitParams = imageStandardExit.layoutParams as FrameLayout.LayoutParams
        standardExitParams.topMargin = if (isPortrait) dip2px(60f) else dip2px(30f)
        imageStandardExit.layoutParams = standardExitParams

        val floatWindowParams = imageFloatWindow.layoutParams as FrameLayout.LayoutParams
        floatWindowParams.topMargin = if (isPortrait) dip2px(60f) else dip2px(30f)
        imageFloatWindow.layoutParams = floatWindowParams

        val roomInfoParams = roomInfoView.layoutParams as FrameLayout.LayoutParams
        roomInfoParams.topMargin = if (isPortrait) dip2px(52f) else dip2px(30f)
        roomInfoView.layoutParams = roomInfoParams
        roomInfoView.setScreenOrientation(isPortrait)

        networkInfoView.setScreenOrientation(isPortrait)
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

    private fun setLayoutBackground(imageUrl: String?, seatLayoutTemplateId: Int) {
        LOGGER.info("setLayoutBackground->imageUrl: $imageUrl, seatLayoutTemplateId:$seatLayoutTemplateId")
        if (seatLayoutTemplateId != 200) {
            val builder = ImageOptions.Builder()
            builder.setBlurEffect(80f)
            if (TextUtils.isEmpty(imageUrl)) {
                ImageLoader.load(context, ivVideoViewBackground, DEFAULT_COVER_URL, builder.build())
            } else {
                ImageLoader.load(context, ivVideoViewBackground, imageUrl, builder.build())
            }
        } else {
            ImageLoader.clear(context, ivVideoViewBackground)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                ivVideoViewBackground.setRenderEffect(null)
            }
            ivVideoViewBackground.setImageDrawable(null)
            ivVideoViewBackground.setBackgroundColor(android.graphics.Color.BLACK)
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

    private fun showCoGuestManageDialog(userInfo: LiveUserInfo?) {
        if (userInfo == null) {
            return
        }
        if (TextUtils.isEmpty(userInfo.userID)) {
            return
        }
        if (coGuestState.connected.value.size <= 1) {
            return
        }
        if (userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
            showAnchorManagerDialog(userInfo)
        } else {
            showUserInfoDialog(userInfo)
        }
    }

    private fun showAnchorManagerDialog(userInfo: LiveUserInfo) {
        if (anchorManagerDialog == null) {
            anchorManagerDialog = AnchorManagerDialog(context, audienceStore)
        }
        anchorManagerDialog?.init(userInfo)
        anchorManagerDialog?.show()
    }

    private fun showUserInfoDialog(userInfo: LiveUserInfo) {
        if (userInfoDialog == null) {
            userInfoDialog = UserInfoDialog(context, audienceStore)
        }
        userInfoDialog?.init(userInfo)
        userInfoDialog?.show()
    }

    private fun setVideoViewAdapter() {
        if (videoViewAdapterImpl == null) {
            videoViewAdapterImpl = VideoViewAdapterImpl(context)
        }
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

    private fun initComponentView(liveInfo: LiveInfo) {
        layoutPlaying.visibility = VISIBLE
        initCoGuestIcon()
        initRoomInfoView(liveInfo)
        initAudienceListView(liveInfo)
        initNetworkView(liveInfo)
        initExitRoomView()
        initBarrageStreamView(liveInfo)
        initBarrageInputView(liveInfo)
        initGiftView(liveInfo)
        initLikeView(liveInfo)
        initGiftPlayView(liveInfo)
        initWaitingCoGuestPassView()
        initMoreIcon()
    }

    private fun initMoreIcon() {
        imageMore.setOnClickListener {
            val audienceSettingsPanelDialog = AudienceSettingsPanelDialog(context, audienceStore)
            audienceSettingsPanelDialog.show()
        }
    }

    private fun initCoGuestVisibility() {
        val canvas = audienceStore.getLiveSeatState().canvas.value
        if (liveListState.currentLive.value.liveID != liveInfo.liveID) return
        if (canvas.w == 0 || canvas.h == 0) return
        val isLandscape = canvas.w >= canvas.h
        onVideoOrientationChanged(isLandscape)
        imageCoGuest.visibility = if (isLandscape) GONE else VISIBLE
    }

    private fun initAudienceListView(liveInfo: LiveInfo) {
        audienceListView.init(liveInfo)
    }

    private fun initNetworkView(liveInfo: LiveInfo) {
        networkInfoView.init(liveInfo)
    }

    private fun initExitRoomView() {
        imageStandardExit.setOnClickListener { onExitButtonClick() }
        imageCompactExit.setOnClickListener { onExitButtonClick() }
    }

    private fun initRoomInfoView(liveInfo: LiveInfo) {
        roomInfoView.init(liveInfo)
    }

    private fun initBarrageStreamView(liveInfo: LiveInfo) {
        barrageStreamView.init(liveInfo.liveID, liveInfo.liveOwner.userID)
        barrageStreamView.setItemTypeDelegate(BarrageViewTypeDelegate())
        barrageStreamView.setItemAdapter(GIFT_VIEW_TYPE_1, GiftBarrageAdapter(context))
        barrageStreamView.setOnMessageClickListener(object :
            BarrageStreamView.OnMessageClickListener {
            override fun onMessageClick(userInfo: LiveUserInfo) {
                if (TextUtils.isEmpty(userInfo.userID)) {
                    return@onMessageClick
                }
                if (userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                    return@onMessageClick
                }

                if (userInfoDialog == null) {
                    userInfoDialog = UserInfoDialog(context, audienceStore)
                }
                userInfoDialog?.init(userInfo)
                userInfoDialog?.show()
            }
        })
    }

    private fun initBarrageInputView(liveInfo: LiveInfo) {
        barrageInputView.init(liveInfo.liveID)
    }

    private fun initGiftView(liveInfo: LiveInfo) {
        buttonGift.init(
            liveInfo.liveID,
            liveInfo.liveOwner.userID,
            liveInfo.liveOwner.userName,
            liveInfo.liveOwner.avatarURL
        )
    }

    private fun initLikeView(liveInfo: LiveInfo) {
        buttonLike.init(liveInfo.liveID)
    }

    private fun initGiftPlayView(liveInfo: LiveInfo) {
        giftPlayView.init(liveInfo.liveID)
        val giftCacheService = GiftStore.getInstance().giftCacheService
        giftPlayView.setListener(object : GiftPlayView.TUIGiftPlayViewListener {
            override fun onReceiveGift(
                view: GiftPlayView?,
                gift: Gift,
                giftCount: Int,
                sender: LiveUserInfo,
            ) {
                val barrage = Barrage()
                barrage.textContent = "gift"
                barrage.sender.userID = sender.userID
                barrage.sender.userName =
                    if (TextUtils.isEmpty(sender.userName)) sender.userID else sender.userName
                barrage.sender.avatarURL = sender.avatarURL
                val extInfo = HashMap<String, String>()
                extInfo[GIFT_VIEW_TYPE] = GIFT_VIEW_TYPE_1.toString()
                extInfo[GIFT_NAME] = gift.name
                extInfo[GIFT_COUNT] = giftCount.toString()
                extInfo[GIFT_ICON_URL] = gift.iconURL
                extInfo[GIFT_RECEIVER_USERNAME] =
                    if (TextUtils.isEmpty(liveListState.currentLive.value.liveOwner.userName))
                        liveListState.currentLive.value.liveOwner.userID else liveListState.currentLive.value.liveOwner.userName
                barrage.extensionInfo = extInfo
                barrageStreamView.insertBarrages(barrage)
            }

            override fun onPlayGiftAnimation(view: GiftPlayView?, gift: Gift) {
                giftCacheService.request(
                    gift.resourceURL,
                    object : GiftCacheService.Callback<String> {
                        override fun onResult(error: Int, result: String?) {
                            if (error == 0) {
                                view?.playGiftAnimation(result ?: "")
                            }
                        }
                    })
            }
        })
    }

    private fun initCoGuestIcon() {
        imageCoGuest.setImageResource(R.drawable.livekit_function_link_default)
        imageCoGuest.setOnClickListener {
            if (viewState.isApplyingToTakeSeat.value
                || !coGuestState.connected.value.none { it.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID }
                || isClickEmptySeat
            ) {
                return@setOnClickListener
            }
            isClickEmptySeat = true
            val typeSelectDialog = TypeSelectDialog(context, audienceStore, -1)
            typeSelectDialog.setOnDismissListener { isClickEmptySeat = false }
            typeSelectDialog.show()
        }
    }

    private fun initWaitingCoGuestPassView() {
        waitingCoGuestPassView.setOnClickListener { showCancelCoGuestRequestDialog() }
    }

    private fun cancelCoGuestRequest() {
        imageCoGuest.setImageResource(R.drawable.livekit_function_link_request)
        imageCoGuest.setOnClickListener { showCancelCoGuestRequestDialog() }
    }

    private fun stopCoGuest() {
        imageCoGuest.setImageResource(R.drawable.livekit_function_linked)
        imageCoGuest.setOnClickListener {
            showStopCoGuestDialog()
        }
    }

    private fun showStopCoGuestDialog() {
        val stopCoGuestDialog = StopCoGuestDialog(context, audienceStore)
        stopCoGuestDialog.show()
    }

    private fun showCancelCoGuestRequestDialog() {
        val linkMicDialog = CancelRequestDialog(context, audienceStore)
        linkMicDialog.show()
    }

    private fun setCoreViewLayoutParamsWhenLandscape(templateId: Int, isPortrait: Boolean) {
        LOGGER.info("setCoreViewLayoutParamsWhenLandscape:templateId:$templateId,isPortrait:$isPortrait")
        val layoutParams: FrameLayout.LayoutParams = layoutLiveCoreView.layoutParams as FrameLayout.LayoutParams
        if (templateId == 200 && isPortrait) {
            layoutParams.topMargin = dip2px(150f)
            layoutParams.height = ScreenUtil.getScreenWidth(context) * 720 / 1280

        } else {
            layoutParams.topMargin = 0
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
        }
        layoutLiveCoreView.layoutParams = layoutParams
        setLayoutBackground(liveInfo.backgroundURL, templateId)
    }

    fun subscribeObserver() {
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
                coHostState.connected.collect {
                    onCoHostConnectedUsersChanged(it)
                }
            }
            launch {
                mediaState.playbackQuality.collect {
                    onPlaybackQualityChanged(it)
                }
            }
            launch {
                AudienceContainerConfig.disableHeaderFloatWin.collect {
                    onHeaderFloatWinDisable(it)
                }
            }
            launch {
                AudienceContainerConfig.disableHeaderLiveData.collect {
                    onHeaderLiveDataDisable(it)
                }
            }
            launch {
                AudienceContainerConfig.disableHeaderVisitorCnt.collect {
                    onHeaderVisitorCntDisable(it)
                }
            }
            launch {
                AudienceContainerConfig.disableFooterCoGuest.collect {
                    onFooterCoGuestDisable(it)
                }
            }
        }
        liveListStore.addLiveListListener(liveListListener)
        liveSeatStore.addLiveSeatEventListener(seatListener)
        coGuestStore.addGuestListener(conGuestListener)
        battleStore.addBattleListener(battleListener)
        liveAudienceStore.addLiveAudienceListener(liveAudienceListener)
    }

    fun unsubscribeObserver() {
        audienceStore.removeAudienceViewListener(this)
        subscribeStateJob?.cancel()
        liveListStore.removeLiveListListener(liveListListener)
        liveSeatStore.removeLiveSeatEventListener(seatListener)
        coGuestStore.removeGuestListener(conGuestListener)
        battleStore.removeBattleListener(battleListener)
        liveAudienceStore.removeLiveAudienceListener(liveAudienceListener)
    }

    fun onExitButtonClick() {
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
            waitingCoGuestPassView.visibility = GONE
            stopCoGuest()
            params[EVENT_PARAMS_IS_LINKING] = true
        } else if (viewState.isApplyingToTakeSeat.value) {
            waitingCoGuestPassView.visibility = VISIBLE
            cancelCoGuestRequest()
            params[EVENT_PARAMS_IS_LINKING] = true
        } else {
            waitingCoGuestPassView.visibility = GONE
            initCoGuestIcon()
            params[EVENT_PARAMS_IS_LINKING] = false
        }
        TUICore.notifyEvent("EVENT_KEY_LIVE_KIT", EVENT_SUB_KEY_LINK_STATUS_CHANGE, params)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onCoHostConnectedUsersChanged(connectedList: List<SeatUserInfo>) {
        post { enableView(imageCoGuest, connectedList.isEmpty()) }
    }

    private fun onHeaderFloatWinDisable(disable: Boolean) {
        imageFloatWindow.visibility = if (disable) GONE else VISIBLE
    }

    private fun onHeaderLiveDataDisable(disable: Boolean) {
        roomInfoView.visibility = if (disable) GONE else VISIBLE
        if (disable) {
            audienceListView.visibility = GONE
        } else {
            audienceListView.visibility =
                if (AudienceContainerConfig.disableHeaderVisitorCnt.value == true) GONE else VISIBLE
        }
    }

    private fun onHeaderVisitorCntDisable(disable: Boolean) {
        if (AudienceContainerConfig.disableHeaderLiveData.value == true) {
            audienceListView.visibility = GONE
        } else {
            audienceListView.visibility = if (disable) GONE else VISIBLE
        }
    }

    private fun onFooterCoGuestDisable(disable: Boolean) {
        imageCoGuest.visibility = if (disable) GONE else VISIBLE
    }

    private fun onVideoOrientationChanged(videoStreamIsLandscape: Boolean) {
        layoutSwitchOrientationButton.visibility = if (videoStreamIsLandscape) VISIBLE else GONE
        imageCoGuest.visibility = if (videoStreamIsLandscape) GONE else VISIBLE
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

    private fun enableView(view: View, enable: Boolean) {
        view.isEnabled = enable
        view.alpha = if (enable) 1.0f else 0.5f
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

                    1 -> { // common_exit_live
                        TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
                    }

                    2 -> {
                    }
                }
            }
        }
        endLiveDialog = atomicEndLiveDialog
        endLiveDialog?.show()
    }

    inner class VideoViewAdapterImpl(context: Context) : VideoViewAdapter {

        private val weakContext = WeakReference(context)

        override fun createCoGuestView(
            seatInfo: SeatInfo?,
            viewLayer: ViewLayer?,
        ): View? {
            val context = weakContext.get()
            if (context == null) {
                LOGGER.error("createCoGuestView: context is null")
                return null
            }
            if (TextUtils.isEmpty(seatInfo?.userInfo?.userID)) {
                return if (viewLayer == ViewLayer.BACKGROUND) {
                    val emptySeatView = AudienceEmptySeatView(getContext())
                    emptySeatView.init(audienceStore)
                    emptySeatView.tag = seatInfo
                    emptySeatView.setOnClickListener { v ->
                        if (audienceStore.getViewState().isApplyingToTakeSeat.value
                            || !coGuestState.connected.value.none { it.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID }
                            || isClickEmptySeat
                        ) {
                            return@setOnClickListener
                        }
                        isClickEmptySeat = true
                        val seat = v.tag as SeatInfo
                        val typeSelectDialog = TypeSelectDialog(
                            this@AudienceView.context,
                            audienceStore,
                            seat.index
                        )
                        typeSelectDialog.setOnDismissListener { isClickEmptySeat = false }
                        typeSelectDialog.show()
                    }
                    emptySeatView
                } else {
                    null
                }
            }
            return if (ViewLayer.BACKGROUND == viewLayer) {
                val backgroundWidgetsView = CoGuestBackgroundWidgetsView(context)
                backgroundWidgetsView.init(audienceStore, seatInfo?.userInfo ?: SeatUserInfo())
                backgroundWidgetsView
            } else {
                val foregroundWidgetsView = CoGuestForegroundWidgetsView(context)
                foregroundWidgetsView.init(audienceStore, seatInfo?.userInfo ?: SeatUserInfo())
                foregroundWidgetsView.setOnClickListener {
                    showCoGuestManageDialog(
                        LiveUserInfo(
                            userID = seatInfo?.userInfo?.userID ?: "",
                            userName = seatInfo?.userInfo?.userName ?: "",
                            avatarURL = seatInfo?.userInfo?.avatarURL ?: "",
                        )
                    )
                }
                foregroundWidgetsView
            }
        }

        override fun createCoHostView(
            seatInfo: SeatInfo?,
            viewLayer: ViewLayer?,
        ): View? {
            val context = weakContext.get()
            if (context == null) {
                LOGGER.error("createCoHostView: context is null")
                return null
            }
            return if (ViewLayer.BACKGROUND == viewLayer) {
                val backgroundWidgetsView = CoHostBackgroundWidgetsView(context)
                backgroundWidgetsView.init(audienceStore, seatInfo?.userInfo ?: SeatUserInfo())
                backgroundWidgetsView
            } else {
                val foregroundWidgetsView = CoHostForegroundWidgetsView(context)
                foregroundWidgetsView.init(audienceStore, seatInfo?.userInfo ?: SeatUserInfo())
                foregroundWidgetsView
            }
        }

        override fun createBattleView(seatInfo: SeatInfo?): View? {
            val context = weakContext.get()
            if (context == null) {
                LOGGER.error("createBattleView: context is null")
                return null
            }
            val battleMemberInfoView = BattleMemberInfoView(context)
            battleMemberInfoView.init(audienceStore, seatInfo?.userInfo?.userID ?: "")
            return battleMemberInfoView
        }

        override fun createBattleContainerView(): View? {
            val context = weakContext.get()
            if (context == null) {
                LOGGER.error("createBattleContainerView: context is null")
                return null
            }
            val battleInfoView = BattleInfoView(context)
            battleInfoView.init(audienceStore)
            return battleInfoView
        }
    }

    override fun onRoomDismissed(roomId: String) {
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
        private const val DEFAULT_COVER_URL =
            "https://liteav-test-1252463788.cos.ap-guangzhou.myqcloud.com/voice_room/voice_room_cover1.png"

        fun showBattleToast(tips: String) {
            val context = ContextProvider.getApplicationContext()
            AtomicToast.show(
                context,
                tips,
                customIcon = R.drawable.livekit_connection_toast_icon,
                style = AtomicToast.Style.INFO
            )
        }
    }
}
