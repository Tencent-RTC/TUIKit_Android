package com.trtc.uikit.livekit.features.anchorview

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.gson.Gson
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.cloud.tuikit.engine.extension.TUILiveListManager
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.TUIThemeManager
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.COMPONENT_LIVE_STREAM
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LIVE_INTEGRATION_SUCCESSFUL
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.common.reportAtomicMetrics
import com.trtc.uikit.livekit.common.setComponent
import com.trtc.uikit.livekit.common.ui.RoundFrameLayout
import com.trtc.uikit.livekit.component.barrage.BarrageStreamView
import com.trtc.uikit.livekit.component.beauty.BeautyIntegration
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore.Companion.DEFAULT_VIDEO_HEIGHT
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore.Companion.DEFAULT_VIDEO_WIDTH
import com.trtc.uikit.livekit.features.anchorprepare.VideoStreamSource
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.store.BattleUser
import com.trtc.uikit.livekit.features.anchorview.view.AnchorVideoViewAdapter
import com.trtc.uikit.livekit.features.anchorview.view.BasicView
import com.trtc.uikit.livekit.features.anchorview.view.LiveStreamEndDialog
import com.trtc.uikit.livekit.features.anchorview.view.battle.panel.BattleCountdownDialog
import com.trtc.uikit.livekit.features.anchorview.view.coguest.panel.AnchorManagerDialog
import com.trtc.uikit.livekit.features.anchorview.view.game.AnchorGameView
import com.trtc.uikit.livekit.livestream.VideoLiveAnchorActivity
import io.trtc.tuikit.atomicx.common.foregroundservice.VideoForegroundService
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostListener
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.HostListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveKickedOutReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.NoResponseReason
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.live.StopLiveCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.TakeSeatMode
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.CoreViewType
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Objects

@SuppressLint("ViewConstructor")
class AnchorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : BasicView(context, attrs, defStyleAttr) {

    private val logger = LiveKitLogger.getFeaturesLogger("AnchorView")
    private val eventKeyTimeLimit: String = "RTCRoomTimeLimitService"
    private val eventSubKeyCountdownStart: String = "CountdownStart"
    private val eventSubKeyCountdownEnd: String = "CountdownEnd"
    private val isScreenShareLive: Boolean get() = ((videoStreamSource == VideoStreamSource.SCREEN_SHARE) ||
            (liveInfo?.keepOwnerOnSeat == true && liveInfo?.seatLayoutTemplateID == 200))
    private var behavior: RoomBehavior = RoomBehavior.CREATE_ROOM
    private var screenShareOverlay: FrameLayout? = null
    private var videoMaskView: View? = null
    private var anchorGameView: AnchorGameView? = null
    private var processConnectionDialog: AtomicAlertDialog? = null
    private var processBattleDialog: AtomicAlertDialog? = null
    private var battleCountdownDialog: BattleCountdownDialog? = null
    private var videoStreamSource: VideoStreamSource = VideoStreamSource.CAMERA
    private var isDestroy = false
    private var subscribeStateJob: Job? = null
    private var subscribeStateScreenShareJob: Job? = null
    private var isScreenShareSuccess = false
    private lateinit var _liveCoreView: LiveCoreView
    private lateinit var layoutCoreViewContainer: RoundFrameLayout
    private lateinit var liveInfo: LiveInfo

    lateinit var overlayView: AnchorOverlayView
        private set
    val coreView: LiveCoreView get() = _liveCoreView
    val barrageStreamView: BarrageStreamView get() = overlayView.barrageStreamView
    var bottomItems: List<AnchorBottomItem>
        get() = overlayView.bottomItems
        set(value) {
            overlayView.bottomItems = value
            refreshBottomItems()
        }
    var topRightItems: List<AnchorTopRightItem>
        get() = overlayView.topRightItems
        set(value) {
            overlayView.topRightItems = value
            refreshTopRightItems()
        }

    private var disabledBottomItems: Set<AnchorBottomItem> = emptySet()
        set(value) {
            field = value
            refreshBottomItems()
        }

    private var disabledTopRightItems: Set<AnchorTopRightItem> = emptySet()
        set(value) {
            field = value
            refreshTopRightItems()
        }

    private val coHostListener = object : CoHostListener() {
        override fun onCoHostRequestReceived(inviter: SeatUserInfo, extensionInfo: String) {
            logger.info("${hashCode()} onCoHostRequestReceived:[inviter:${Gson().toJson(inviter)}]")
            handleCoHostRequestReceived(inviter)
        }

        override fun onCoHostRequestRejected(invitee: SeatUserInfo) {
            logger.info("${hashCode()} onConnectionRequestReject:[invitee:$invitee]")
            anchorStore?.getAnchorCoHostStore()?.onConnectionRequestReject(invitee)
        }

        override fun onCoHostRequestTimeout(inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("${hashCode()} onCrossRoomConnectionTimeout:[inviter:$inviter,invitee:$invitee")
            processConnectionDialog?.dismiss()
            anchorStore?.getAnchorCoHostStore()?.onConnectionRequestTimeout(inviter, invitee)
        }
    }

    private val coGuestListener = object : HostListener() {
        override fun onGuestApplicationReceived(guestUser: LiveUserInfo) {
            logger.info("${hashCode()} onGuestApplicationReceived:[inviterUser:${Gson().toJson(guestUser)}]")
            handleGuestApplicationReceived(guestUser)
        }

        override fun onHostInvitationNoResponse(guestUser: LiveUserInfo, reason: NoResponseReason) {
            if (reason == NoResponseReason.TIMEOUT) {
                logger.info("${hashCode()} onUserConnectionAccepted:[guestUser:$guestUser]")
                handleHostInvitationTimeout()
            }
        }
    }

    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            if (liveID == liveInfo.liveID && reason != LiveEndedReason.ENDED_BY_HOST) {
                endLive(reason, false)
            }
        }

        override fun onKickedOutOfLive(liveID: String, reason: LiveKickedOutReason, message: String) {
            if (liveID == liveInfo.liveID) {
                AtomicToast.show(
                    baseContext,
                    baseContext.getString(R.string.common_kicked_out_of_room_by_owner),
                    AtomicToast.Style.INFO
                )
                endLive()
            }
        }
    }

    private val liveAudienceListener = object : LiveAudienceListener() {
        override fun onAudienceMessageDisabled(audience: LiveUserInfo, isDisable: Boolean) {
            logger.info("${hashCode()} onAudienceMessageDisabled:[userID:${audience.userID},isDisable:$isDisable]")
            if (audience.userID != LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                return
            }
            ContextProvider.getApplicationContext()?.apply {
                if (isDisable) {
                    AtomicToast.show(
                        this,
                        this.resources.getString(R.string.common_send_message_disabled),
                        AtomicToast.Style.INFO
                    )
                } else {
                    AtomicToast.show(
                        this,
                        this.resources.getString(R.string.common_send_message_enable),
                        AtomicToast.Style.INFO
                    )
                }
            }
        }
    }

    override fun initView() {
        LayoutInflater.from(baseContext).inflate(R.layout.livekit_livestream_anchor_view, this, true)

        layoutCoreViewContainer = findViewById(R.id.fl_video_view_container)
        overlayView = findViewById(R.id.anchor_overlay_view)
        screenShareOverlay = findViewById(R.id.fl_screen_share_overlay)
        videoMaskView = findViewById(R.id.fl_video_mask)

        layoutCoreViewContainer.setRadius(ScreenUtil.dip2px(16f))

        overlayView.onEndLiveClick = { showLiveStreamEndDialog() }
    }

    fun init(liveInfo: LiveInfo, liveCoreView: LiveCoreView?, behavior: RoomBehavior, params: Map<String, Any>?) {
        this.behavior = behavior
        this.liveInfo = liveInfo
        anchorStore = AnchorStore(liveInfo)
        screenShareOverlay?.let { overlay ->
            anchorGameView = AnchorGameView.wrap(baseContext, anchorStore!!, overlay)
        }
        initLiveCoreView(liveCoreView)
        overlayView.init(anchorStore!!)
        super.init(anchorStore!!)
        parseParams(params)
        createVideoMuteBitmap()
        createOrEnterRoom()
        startForegroundService()
    }

    fun unInit() {
        stopScreenShareIfNeeded()
        if (anchorStore?.getState()?.liveInfo?.keepOwnerOnSeat == true) {
            LiveListStore.shared().endLive(null)
        } else {
            LiveListStore.shared().leaveLive(null)
        }
        destroy()
        notifyRoomStopped()
    }

    fun addAnchorViewListener(listener: AnchorViewListener) {
        anchorStore?.addAnchorViewListener(listener)
    }

    fun removeAnchorViewListener(listener: AnchorViewListener) {
        anchorStore?.removeAnchorViewListener(listener)
    }

    fun replace(node: AnchorNode, view: View?) {
        overlayView.replace(node, view)
    }

    fun perform(action: AnchorAction) {
        overlayView.perform(action)
    }


    /**
     * This API call is called in the Activity.onPictureInPictureModeChanged(boolean)
     * The code example is as follows:
     * override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
     *     super.onPictureInPictureModeChanged(isInPictureInPictureMode)
     *     mAnchorView?.enablePipMode(isInPictureInPictureMode)
     * }
     *
     * @param enable true:Turn on picture-in-picture mode; false:Turn off picture-in-picture mode
     */
    fun enablePipMode(enable: Boolean) {
        anchorStore?.enablePipMode(enable)

        setCoreViewLayoutParamsWhenLandscape(liveInfo.seatTemplate)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    override fun refreshView() {
        // Empty implementation
    }

    private fun refreshBottomItems() {
        val effectiveItems = bottomItems.filter { it !in disabledBottomItems }
        overlayView.updateEffectiveBottomItems(effectiveItems)
    }

    private fun refreshTopRightItems() {
        val effectiveItems = topRightItems.filter { it !in disabledTopRightItems }
        overlayView.updateEffectiveTopRightItems(effectiveItems)
    }

    private fun handleCoHostRequestReceived(inviter: SeatUserInfo) {
        val coGuestStore = CoGuestStore.create(liveInfo.liveID)
        val coHostStore = CoHostStore.create(liveInfo.liveID)

        val hasOtherCoGuest = coGuestStore.coGuestState.connected.value.any { userInfo ->
            userInfo.userID != LoginStore.shared.loginState.loginUserInfo.value?.userID && userInfo.liveID == liveInfo.liveID
        }

        if (hasOtherCoGuest || coGuestStore.coGuestState.applicants.value.isNotEmpty() || coGuestStore.coGuestState.invitees.value.isNotEmpty()) {
            coHostStore.rejectHostConnection(inviter.liveID, null)
            return
        }

        if (mediaState?.isPipModeEnabled?.value == true) {
            return
        }

        val content = context.getString(R.string.common_connect_inviting_append, inviter.userName)
        showConnectionRequestDialog(content, inviter.avatarURL, inviter.liveID)
    }

    private fun handleGuestApplicationReceived(guestUser: LiveUserInfo) {
        val coGuestStore = CoGuestStore.create(liveInfo.liveID)
        val coHostStore = CoHostStore.create(liveInfo.liveID)

        if (coHostStore.coHostState.invitees.value.isNotEmpty() || coHostStore.coHostState.connected.value.isNotEmpty() || coHostStore.coHostState.applicant.value != null) {
            coGuestStore.rejectApplication(guestUser.userID, null)
        }
    }

    private fun handleHostInvitationTimeout() {
        ContextProvider.getApplicationContext()?.apply {
            AtomicToast.show(
                context,
                context.resources.getString(R.string.common_voiceroom_take_seat_timeout),
                AtomicToast.Style.INFO
            )
        }
    }

    private fun showCoGuestManageDialog(userInfo: SeatInfo?) {
        if (userInfo == null || TextUtils.isEmpty(userInfo.userInfo.userID)) {
            return
        }
        anchorStore?.let {
            val anchorManagerDialog = AnchorManagerDialog(baseContext, it)
            anchorManagerDialog.init(userInfo)
            anchorManagerDialog.show()
        }
    }

    private fun initLiveCoreView(liveCoreView: LiveCoreView?) {
        _liveCoreView = if (liveCoreView != null) {
            if (liveCoreView.parent != null) {
                (liveCoreView.parent as ViewGroup).removeView(liveCoreView)
            }
            liveCoreView
        } else {
            LiveCoreView(context, null, 0, CoreViewType.PUSH_VIEW)
        }
        _liveCoreView.setLiveID(liveInfo.liveID)
        layoutCoreViewContainer.addView(_liveCoreView)
    }

    private fun createVideoMuteBitmap() {
        val bigMuteImageResId = if (Locale.ENGLISH.language == TUIThemeManager.getInstance().currentLanguage) {
                R.drawable.livekit_local_mute_image_en
            } else {
                R.drawable.livekit_local_mute_image_zh
            }
        val smallMuteImageResId = R.drawable.livekit_local_mute_image_multi
        mediaStore?.createVideoMuteBitmap(context, bigMuteImageResId, smallMuteImageResId)
    }

    private fun createOrEnterRoom() {
        setComponent(COMPONENT_LIVE_STREAM)
        anchorStore?.let { store ->
            _liveCoreView.setVideoViewAdapter(
                AnchorVideoViewAdapter(context, store) { seatInfo ->
                    showCoGuestManageDialog(seatInfo)
                }
            )
        }

        if (behavior == RoomBehavior.ENTER_ROOM) {
            enterRoom()
        } else {
            createRoom()
        }
    }

    private fun enterRoom() {
        anchorState?.let {
            _liveCoreView.setLocalVideoMuteImage(mediaState?.bigMuteBitmap, mediaState?.smallMuteBitmap)

            reportAtomicMetrics(LIVE_INTEGRATION_SUCCESSFUL)
            val liveListStore = LiveListStore.shared()
            liveListStore.joinLive(it.roomId, object : LiveInfoCompletionHandler {
                override fun onSuccess(liveInfo: LiveInfo) {
                    this@AnchorView.liveInfo = liveInfo
                    setCoreViewLayoutParamsWhenLandscape(liveInfo.seatTemplate)
                    startLocalPreview(liveInfo)
                    val activity = baseContext as Activity
                    if (activity.isFinishing || activity.isDestroyed) {
                        logger.warn("activity is exit")
                        _liveCoreView.setVideoViewAdapter(null)
                        if (liveInfo.keepOwnerOnSeat) {
                            liveListStore.endLive(null)
                        } else {
                            liveListStore.leaveLive(null)
                        }
                        cleanupLiveResources()
                        return
                    }
                    if (liveInfo.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats) {
                        PIPPanelStore.sharedInstance().state.width = DEFAULT_VIDEO_HEIGHT
                        PIPPanelStore.sharedInstance().state.height = DEFAULT_VIDEO_WIDTH
                    } else {
                        PIPPanelStore.sharedInstance().state.width = DEFAULT_VIDEO_WIDTH
                        PIPPanelStore.sharedInstance().state.height = DEFAULT_VIDEO_HEIGHT
                    }
                    PIPPanelStore.sharedInstance().state.isAnchorStreaming = true
                    anchorStore?.updateRoomState(liveInfo)
                    overlayView.initComponentView()
                    startScreenShareIfNeeded()
                }

                override fun onFailure(code: Int, desc: String) {
                    PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
                    ErrorLocalized.onError(code)
                    finishActivity()
                }
            })
        }
    }

    private fun createRoom() {
        liveInfo.keepOwnerOnSeat = true
        liveInfo.isSeatEnabled = true
        liveInfo.seatMode = TakeSeatMode.APPLY
        _liveCoreView.setLocalVideoMuteImage(mediaState?.bigMuteBitmap, mediaState?.smallMuteBitmap)
        val liveListStore = LiveListStore.shared()
        reportAtomicMetrics(LIVE_INTEGRATION_SUCCESSFUL)
        liveListStore.startLive(liveInfo, object : LiveInfoCompletionHandler {
            override fun onSuccess(liveInfo: LiveInfo) {
                val activity = baseContext as Activity
                if (activity.isFinishing || activity.isDestroyed) {
                    logger.warn("activity is exit, stopLiveStream")
                    LiveListStore.shared().endLive(null)
                    cleanupLiveResources()
                    return
                }
                if (liveInfo.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats) {
                    PIPPanelStore.sharedInstance().state.width = DEFAULT_VIDEO_HEIGHT
                    PIPPanelStore.sharedInstance().state.height = DEFAULT_VIDEO_WIDTH
                } else {
                    PIPPanelStore.sharedInstance().state.width = DEFAULT_VIDEO_WIDTH
                    PIPPanelStore.sharedInstance().state.height = DEFAULT_VIDEO_HEIGHT
                }
                PIPPanelStore.sharedInstance().state.isAnchorStreaming = true
                anchorStore?.updateRoomState(liveInfo)
                overlayView.initComponentView()
                startScreenShareIfNeeded()
                showAlertUserLiveTips()
                TUICore.notifyEvent(eventKeyTimeLimit, eventSubKeyCountdownStart, null)
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("startLiveStream failed:error:$code,desc:$desc")
                PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
                ErrorLocalized.onError(code)
                finishActivity()
            }
        })
    }


    private fun startScreenShareIfNeeded() {
        if (!isScreenShareLive) return
        logger.info("startScreenShareIfNeeded: starting screen share after room created/entered")
        disabledBottomItems = setOf(AnchorBottomItem.CoHost, AnchorBottomItem.Battle, AnchorBottomItem.More)
        disabledTopRightItems = setOf(AnchorTopRightItem.FloatWindow)

        // 隐藏视频容器和 mask
        layoutCoreViewContainer.visibility = View.GONE
        videoMaskView?.visibility = View.GONE

        anchorGameView?.init(liveInfo.liveID)
        TUIRoomEngine.sharedInstance().enableSystemAudioSharing(true)
        DeviceStore.shared().startScreenShare()
    }

    private fun stopScreenShareIfNeeded() {
        if (!isScreenShareLive) return
        logger.info("stopScreenShareIfNeeded: stopping screen share")
        TUIRoomEngine.sharedInstance().enableSystemAudioSharing(false)
        DeviceStore.shared().stopScreenShare()
    }

    private fun startLocalPreview(liveInfo: LiveInfo) {
        if (liveInfo.keepOwnerOnSeat && liveInfo.seatTemplate != SeatLayoutTemplate.VideoLandscape4Seats) {
            if (isScreenShareLive) {
                logger.info("startLocalPreview: screen share mode, skip camera, only open microphone")
                ContextProvider.getApplicationContext()?.apply {
                    PermissionRequest.requestMicrophonePermissions(
                        this, object : PermissionCallback() {
                            override fun onGranted() {
                                logger.info("requestMicrophonePermissions success (screen share mode)")
                                DeviceStore.shared().openLocalMicrophone(null)
                            }

                            override fun onDenied() {
                                logger.error("requestMicrophonePermissions:[onDenied] (screen share mode)")
                            }
                        })
                }
                return
            }
            ContextProvider.getApplicationContext()?.apply {
                PermissionRequest.requestCameraPermissions(
                    this, object : PermissionCallback() {
                        override fun onGranted() {
                            logger.info("requestCameraPermissions:[onGranted]")
                            DeviceStore.shared().openLocalCamera(true, object : CompletionHandler {
                                override fun onSuccess() {
                                    logger.info("startCamera success, requestMicrophonePermissions")
                                    PermissionRequest.requestMicrophonePermissions(
                                        this@apply, object : PermissionCallback() {
                                            override fun onGranted() {
                                                logger.info("requestMicrophonePermissions success")
                                                DeviceStore.shared().openLocalMicrophone(null)
                                                (context as? VideoLiveAnchorActivity)?.bringTaskToFront()
                                            }

                                            override fun onDenied() {
                                                logger.error("requestMicrophonePermissions:[onDenied]")
                                                endLive()
                                            }
                                        })
                                }

                                override fun onFailure(code: Int, desc: String) {
                                    logger.error("startCamera failed:code:$code,desc:$desc")
                                    endLive()
                                }

                            })
                        }

                        override fun onDenied() {
                            logger.error("requestCameraPermissions:[onDenied]")
                            endLive()
                        }
                    })
            }
        }
    }

    private fun setCoreViewLayoutParamsWhenLandscape(template: SeatLayoutTemplate) {
        logger.info("setCoreViewLayoutParamsWhenLandscape:template:$template,")
        anchorStore?.let {
            val layoutParams: FrameLayout.LayoutParams =
                layoutCoreViewContainer.layoutParams as FrameLayout.LayoutParams
            if (it.getMediaStore().mediaState.isPipModeEnabled.value) {
                layoutCoreViewContainer.setRadius(0)
                layoutParams.setMargins(0, 0, 0, 0)
                layoutParams.height = LayoutParams.MATCH_PARENT
                overlayView.visibility = GONE
            } else if (template == SeatLayoutTemplate.VideoLandscape4Seats) {
                layoutCoreViewContainer.setRadius(0)
                layoutParams.topMargin = ScreenUtil.dip2px(150f)
                layoutParams.height = ScreenUtil.getScreenWidth(context) * 720 / 1280
                overlayView.visibility = VISIBLE
            } else {
                layoutCoreViewContainer.setRadius(ScreenUtil.dip2px(16f))
                layoutParams.setMargins(0, ScreenUtil.dip2px(44f), 0, ScreenUtil.dip2px(96f))
                overlayView.visibility = VISIBLE
            }
            layoutCoreViewContainer.layoutParams = layoutParams
        }
    }

    private fun showAlertUserLiveTips() {
        try {
            val map = hashMapOf<String, Any>(
                TUIConstants.Privacy.PARAM_DIALOG_CONTEXT to Objects.requireNonNull(context)
            )
            TUICore.notifyEvent(TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED, TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_START, map)
        } catch (e: Exception) {
            logger.error("showAlertUserLiveTips exception:${e.message}")
        }
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                onPipModeObserver()
            }
            launch {
                battleState?.receivedBattleRequest?.collect { user ->
                    onReceivedBattleRequestChange(user)
                }
            }
            launch {
                onInWaitingChange()
            }
            subscribeStateScreenShareJob = launch {
                onScreenShareStatusChange()
            }
        }
        CoHostStore.create(liveInfo.liveID).addCoHostListener(coHostListener)
        CoGuestStore.create(liveInfo.liveID).addHostListener(coGuestListener)
        LiveListStore.shared().addLiveListListener(liveListListener)
        LiveAudienceStore.create(liveInfo.liveID).addLiveAudienceListener(liveAudienceListener)
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
        subscribeStateScreenShareJob?.cancel()
        CoHostStore.create(liveInfo.liveID).removeCoHostListener(coHostListener)
        CoGuestStore.create(liveInfo.liveID).removeHostListener(coGuestListener)
        LiveListStore.shared().removeLiveListListener(liveListListener)
        LiveAudienceStore.create(liveInfo.liveID).removeLiveAudienceListener(liveAudienceListener)
    }

    private fun showLiveStreamEndDialog() {
        val store = anchorStore ?: return

        LiveStreamEndDialog(
            context = baseContext,
            anchorStore = store,
            onEndLive = { exitLive(store) },
        ).show()
    }

    private fun exitLive(store: AnchorStore) {
        subscribeStateScreenShareJob?.cancel()
        val keepOwnerOnSeat = store.getState().liveInfo.keepOwnerOnSeat
        if (keepOwnerOnSeat) {
            LiveListStore.shared().endLive(object : StopLiveCompletionHandler {
                override fun onSuccess(statisticsData: TUILiveListManager.LiveStatisticsData) {
                    store.setLiveStatisticsData(statisticsData)
                    store.notifyLiveExit()
                }

                override fun onFailure(code: Int, desc: String) {
                    store.notifyLiveExit()
                }
            })
        } else {
            leaveLive()
        }

        notifyRoomStopped()
        _liveCoreView.setLocalVideoMuteImage(null, null)
    }

    private fun onReceivedCoHostRequest(receivedConnectionRequest: SeatUserInfo?) {
        if (mediaState?.isPipModeEnabled?.value == true) {
            return
        }

        if (receivedConnectionRequest == null) {
            processConnectionDialog?.dismiss()
            return
        }

        val content = context.getString(
            R.string.common_connect_inviting_append, receivedConnectionRequest.userName
        )
        showConnectionRequestDialog(content, receivedConnectionRequest.avatarURL, receivedConnectionRequest.liveID)
    }

    private fun showConnectionRequestDialog(content: String, avatarUrl: String, roomId: String) {
        processConnectionDialog = AtomicAlertDialog(context).apply {
            init {
                title = content
                countdownDuration = 0
                confirmButton(
                    text = context.getString(R.string.common_receive),
                    type = AtomicAlertDialog.TextColorPreset.BLUE
                ) { dialog ->
                    CoHostStore.create(liveInfo.liveID).acceptHostConnection(roomId, null)
                    dialog.dismiss()
                }

                cancelButton(
                    text = context.getString(R.string.common_reject),
                    type = AtomicAlertDialog.TextColorPreset.GREY
                ) { dialog ->
                    CoHostStore.create(liveInfo.liveID).rejectHostConnection(roomId, null)
                    dialog.dismiss()
                }
            }
        }
        processConnectionDialog?.show()
    }

    private fun onReceivedBattleRequestChange(user: BattleUser?) {
        if (mediaState?.isPipModeEnabled?.value == true) return

        processBattleDialog?.dismiss()
        processBattleDialog = null

        user?.let {
            processBattleDialog = createBattleRequestDialog(user)
            processBattleDialog?.show()
        }
    }

    private fun createBattleRequestDialog(user: BattleUser): AtomicAlertDialog {
        return AtomicAlertDialog(context).apply {
            init {
                title = context.getString(R.string.common_battle_inviting, user.userName)
                countdownDuration = 0

                confirmButton(
                    text = context.getString(R.string.common_receive),
                    type = AtomicAlertDialog.TextColorPreset.BLUE
                ) { dialog ->
                    dialog.dismiss()
                    processBattleDialog = null
                    respondToBattle(isAccept = true)
                }

                cancelButton(
                    text = context.getString(R.string.common_reject),
                    type = AtomicAlertDialog.TextColorPreset.GREY
                ) { dialog ->
                    dialog.dismiss()
                    processBattleDialog = null
                    respondToBattle(isAccept = false)
                }
            }
        }
    }

    private fun respondToBattle(isAccept: Boolean) {
        battleState?.let { state ->
            val battleStore = BattleStore.create(liveInfo.liveID)
            val handler = object : CompletionHandler {
                override fun onSuccess() {
                    anchorStore?.getAnchorBattleStore()?.onResponseBattle()
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("respondToBattle failed:code:$code,desc:$desc")
                    ErrorLocalized.onError(code)
                }
            }
            if (isAccept) {
                battleStore.acceptBattle(state.battleId, handler)
            } else {
                battleStore.rejectBattle(state.battleId, handler)
            }
        }
    }

    private fun showBattleCountdownDialog() {
        anchorStore?.let {
            if (battleCountdownDialog == null) {
                battleCountdownDialog = BattleCountdownDialog(baseContext, it)
            }
            battleCountdownDialog?.show()
        }
    }

    private fun dismissBattleCountdownDialog() {
        battleCountdownDialog?.dismiss()
        battleCountdownDialog = null
    }

    private fun finishActivity() {
        if (baseContext is Activity) {
            val intent = Intent()
            baseContext.setResult(RESULT_OK, intent)
            baseContext.finishAndRemoveTask()
        }
    }

    private suspend fun onInWaitingChange() {
        battleState?.isInWaiting?.collect { isInWaiting ->
            when (isInWaiting) {
                true -> showBattleCountdownDialog()
                else -> dismissBattleCountdownDialog()
            }
        }
    }

    private suspend fun onScreenShareStatusChange() {
        if (!isScreenShareLive) return
        anchorStore?.getState()?.screenStatus?.collect { status ->
            logger.info("onScreenShareStatusChange: status=$status")
            if (status == DeviceStatus.OFF) {
                logger.info("onScreenShareStatusChange: screen share stopped, ending live")
                endLive(isFinish = (!isScreenShareSuccess))
                subscribeStateScreenShareJob?.cancel()
                isScreenShareSuccess = false
            } else {
                logger.info("onScreenShareStatusChange: screen share started, resuming live")
                isScreenShareSuccess = true
                (context as? VideoLiveAnchorActivity)?.bringTaskToFront()
            }
            true
        }
    }

    private fun parseParams(params: Map<String, Any>?) {
        if (params == null) return

        params["coHostTemplateId"]?.let { coHostTemplateId ->
            if (coHostTemplateId is Int) {
                anchorStore?.getAnchorCoHostStore()?.setCoHostTemplateId(coHostTemplateId)
            }
        }

        params["videoStreamSource"]?.let { source ->
            if (source is VideoStreamSource) {
                videoStreamSource = source
                logger.info("parseParams videoStreamSource: $videoStreamSource")
            }
        }
    }

    private fun destroy() {
        if (isDestroy) return
        isDestroy = true

        stopScreenShareIfNeeded()
        if (!isScreenShareLive) {
            DeviceStore.shared().closeLocalCamera()
        }
        DeviceStore.shared().closeLocalMicrophone()
        BeautyIntegration.resetBeauty()

        anchorStore?.destroy()
        stopForegroundService()
    }

    private fun startForegroundService() {
        ContextProvider.getApplicationContext()?.apply {
            VideoForegroundService.start(
                context,
                context.getString(context.applicationInfo.labelRes),
                context.getString(R.string.common_app_running),
                0
            )
        }
    }

    private fun stopForegroundService() {
        ContextProvider.getApplicationContext()?.apply { VideoForegroundService.stop(context) }
    }

    private suspend fun onPipModeObserver() {
        mediaState?.isPipModeEnabled?.collect { enable ->
            if (!enable && liveInfo.liveID.isNotEmpty()) {
                postDelayed({
                    onReceivedCoHostRequest(CoHostStore.create(liveInfo.liveID).coHostState.applicant.value)
                    onReceivedBattleRequestChange(battleState?.receivedBattleRequest?.value)
                }, if (isPipModeAbnormalPhoneModel()) 500 else 0)
            }
        }
    }

    private fun isPipModeAbnormalPhoneModel(): Boolean {
        return Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) || Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    private fun endLive(reason: LiveEndedReason = LiveEndedReason.ENDED_BY_HOST, isFinish: Boolean = true) {
        LiveListStore.shared().endLive(object : StopLiveCompletionHandler {
            override fun onSuccess(statisticsData: TUILiveListManager.LiveStatisticsData) {
                anchorStore?.setLiveStatisticsData(statisticsData)
                cleanupLiveResources()
                anchorStore?.notifyLiveExit(reason)
                if (isFinish) {
                    finishActivity()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                cleanupLiveResources()
                anchorStore?.notifyLiveExit(reason)
                if (isFinish) {
                    finishActivity()
                }
            }
        })
    }

    private fun leaveLive(isFinish: Boolean = true) {
        LiveListStore.shared().leaveLive(null)
        cleanupLiveResources()
        if (isFinish) {
            finishActivity()
        }
    }

    private fun cleanupLiveResources() {
        PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
        _liveCoreView.setLocalVideoMuteImage(null, null)
        notifyRoomStopped()
    }

    private fun notifyRoomStopped() {
        TUICore.notifyEvent(
            TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
            TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP,
            null
        )
        TUICore.notifyEvent(eventKeyTimeLimit, eventSubKeyCountdownEnd, null)
    }
}
