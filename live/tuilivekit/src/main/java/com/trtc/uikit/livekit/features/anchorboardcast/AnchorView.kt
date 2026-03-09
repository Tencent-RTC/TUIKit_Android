package com.trtc.uikit.livekit.features.anchorboardcast

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
import android.widget.ImageView
import com.google.gson.Gson
import com.tencent.cloud.tuikit.engine.extension.TUILiveListManager
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.TUIThemeManager
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.COMPONENT_LIVE_STREAM
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.common.setComponent
import com.trtc.uikit.livekit.common.ui.RoundFrameLayout
import com.trtc.uikit.livekit.component.audiencelist.AudienceListView
import com.trtc.uikit.livekit.component.barrage.BarrageInputView
import com.trtc.uikit.livekit.component.barrage.BarrageStreamView
import com.trtc.uikit.livekit.component.beauty.BeautyUtils
import com.trtc.uikit.livekit.component.beauty.tebeauty.store.TEBeautyStore
import com.trtc.uikit.livekit.component.gift.GiftPlayView
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
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.component.roominfo.LiveInfoView
import com.trtc.uikit.livekit.features.anchorboardcast.store.AnchorBattleStore.Companion.BATTLE_DURATION
import com.trtc.uikit.livekit.features.anchorboardcast.store.AnchorBattleStore.Companion.BATTLE_REQUEST_TIMEOUT
import com.trtc.uikit.livekit.features.anchorboardcast.store.AnchorConfig
import com.trtc.uikit.livekit.features.anchorboardcast.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorboardcast.store.BattleUser
import com.trtc.uikit.livekit.features.anchorboardcast.view.BasicView
import com.trtc.uikit.livekit.features.anchorboardcast.view.battle.panel.AnchorEndBattleDialog
import com.trtc.uikit.livekit.features.anchorboardcast.view.battle.panel.BattleCountdownDialog
import com.trtc.uikit.livekit.features.anchorboardcast.view.battle.widgets.BattleInfoView
import com.trtc.uikit.livekit.features.anchorboardcast.view.battle.widgets.BattleMemberInfoView
import com.trtc.uikit.livekit.features.anchorboardcast.view.coguest.panel.AnchorCoGuestManageDialog
import com.trtc.uikit.livekit.features.anchorboardcast.view.coguest.panel.AnchorManagerDialog
import com.trtc.uikit.livekit.features.anchorboardcast.view.coguest.panel.ApplyCoGuestFloatView
import com.trtc.uikit.livekit.features.anchorboardcast.view.coguest.panel.CoGuestIconView
import com.trtc.uikit.livekit.features.anchorboardcast.view.coguest.widgets.AnchorEmptySeatView
import com.trtc.uikit.livekit.features.anchorboardcast.view.coguest.widgets.CoGuestBackgroundWidgetsView
import com.trtc.uikit.livekit.features.anchorboardcast.view.coguest.widgets.CoGuestForegroundWidgetsView
import com.trtc.uikit.livekit.features.anchorboardcast.view.cohost.panel.AnchorCoHostManageDialog
import com.trtc.uikit.livekit.features.anchorboardcast.view.cohost.widgets.CoHostBackgroundWidgetsView
import com.trtc.uikit.livekit.features.anchorboardcast.view.cohost.widgets.CoHostForegroundWidgetsView
import com.trtc.uikit.livekit.features.anchorboardcast.view.settings.SettingsPanelDialog
import com.trtc.uikit.livekit.features.anchorboardcast.view.usermanage.UserManagerDialog
import io.trtc.tuikit.atomicx.common.foregroundservice.VideoForegroundService
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.addItem
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.init
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.device.DeviceError
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.live.BattleConfig
import io.trtc.tuikit.atomicxcore.api.live.BattleEndedReason
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleListener
import io.trtc.tuikit.atomicxcore.api.live.BattleRequestCallback
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
import io.trtc.tuikit.atomicxcore.api.view.VideoViewAdapter
import io.trtc.tuikit.atomicxcore.api.view.ViewLayer
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
    private var behavior: RoomBehavior = RoomBehavior.CREATE_ROOM
    private lateinit var layoutCoreViewContainer: RoundFrameLayout
    private lateinit var liveCoreView: LiveCoreView
    private lateinit var layoutComponentsContainer: FrameLayout
    private lateinit var layoutHeaderContainer: FrameLayout
    private lateinit var imageEndLive: ImageView
    private lateinit var imageFloatWindow: ImageView
    private lateinit var viewCoGuest: View
    private lateinit var viewCoHost: View
    private lateinit var viewBattle: View
    private lateinit var audienceListView: AudienceListView
    private lateinit var roomInfoView: LiveInfoView
    private lateinit var networkInfoView: NetworkInfoView
    private lateinit var barrageInputView: BarrageInputView
    private lateinit var barrageStreamView: BarrageStreamView
    private lateinit var giftPlayView: GiftPlayView
    private lateinit var applyCoGuestFloatView: ApplyCoGuestFloatView

    private var anchorCoHostManageDialog: AnchorCoHostManageDialog? = null
    private var processConnectionDialog: AtomicAlertDialog? = null
    private var processBattleDialog: AtomicAlertDialog? = null
    private var battleCountdownDialog: BattleCountdownDialog? = null
    private var anchorManagerDialog: AnchorManagerDialog? = null
    private var anchorEndBattleDialog: AnchorEndBattleDialog? = null
    private var realEndBattleDialog: AtomicAlertDialog? = null
    private lateinit var liveInfo: LiveInfo
    private var isDestroy = false
    private var subscribeStateJob: Job? = null
    private val EVENT_KEY_TIME_LIMIT: String = "RTCRoomTimeLimitService"
    private val EVENT_SUB_KEY_COUNTDOWN_START: String = "CountdownStart"
    private val EVENT_SUB_KEY_COUNTDOWN_END: String = "CountdownEnd"

    private val coHostListener = object : CoHostListener() {
        override fun onCoHostRequestReceived(
            inviter: SeatUserInfo, extensionInfo: String,
        ) {
            logger.info("${hashCode()} onCoHostRequestReceived:[inviter:${Gson().toJson(inviter)}]")
            val coGuestStore = CoGuestStore.create(liveInfo.liveID)
            val coHostStore = CoHostStore.create(liveInfo.liveID)
            val list = mutableListOf<SeatUserInfo>()

            for (userInfo in coGuestStore.coGuestState.connected.value) {
                if (userInfo.userID != LoginStore.shared.loginState.loginUserInfo.value?.userID && userInfo.liveID == liveInfo.liveID) {
                    list.add(userInfo)
                }
            }

            if (list.isNotEmpty() || coGuestStore.coGuestState.applicants.value.isNotEmpty() || coGuestStore.coGuestState.invitees.value.isNotEmpty()) {
                coHostStore.rejectHostConnection(inviter.liveID, null)
                return
            }

            if (mediaState?.isPipModeEnabled?.value == true) {
                return
            }

            val content = context.getString(R.string.common_connect_inviting_append, inviter.userName)
            showConnectionRequestDialog(content, inviter.avatarURL, inviter.liveID)
            anchorStore?.getAnchorCoHostStore()?.onConnectionRequestReceived(inviter)
        }

        override fun onCoHostRequestCancelled(inviter: SeatUserInfo, invitee: SeatUserInfo?) {
            logger.info("${hashCode()} onCrossRoomConnectionCancelled:[inviter:$inviter")
        }

        override fun onCoHostRequestAccepted(invitee: SeatUserInfo) {
            logger.info("${hashCode()} onCrossRoomConnectionAccepted:[invitee:$invitee]")
            anchorStore?.getAnchorCoHostStore()?.onConnectionRequestAccept(invitee)
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
            val coGuestStore = CoGuestStore.create(liveInfo.liveID)
            val coHostStore = CoHostStore.create(liveInfo.liveID)

            if (coHostStore.coHostState.invitees.value.isNotEmpty() || coHostStore.coHostState.connected.value.isNotEmpty() || coHostStore.coHostState.applicant.value != null) {
                coGuestStore.rejectApplication(guestUser.userID, null)
            }
        }

        override fun onGuestApplicationCancelled(guestUser: LiveUserInfo) {
            logger.info("${hashCode()} onUserConnectionCancelled:[inviterUser:$guestUser]")
        }

        override fun onGuestApplicationProcessedByOtherHost(guestUser: LiveUserInfo, hostUser: LiveUserInfo) {

        }

        override fun onHostInvitationResponded(isAccept: Boolean, guestUser: LiveUserInfo) {
        }

        override fun onHostInvitationNoResponse(guestUser: LiveUserInfo, reason: NoResponseReason) {
            if (reason == NoResponseReason.TIMEOUT) {
                logger.info("${hashCode()} onUserConnectionAccepted:[guestUser:$guestUser]")
                ContextProvider.getApplicationContext()?.apply {
                    AtomicToast.show(
                        context,
                        context.resources.getString(R.string.common_voiceroom_take_seat_timeout),
                        AtomicToast.Style.INFO
                    )
                }
            }
        }
    }

    private val battleListener = object : BattleListener() {
        override fun onBattleStarted(battleInfo: BattleInfo, inviter: SeatUserInfo, invitees: List<SeatUserInfo>) {
            logger.info("${hashCode()} onBattleStarted:[battleInfo:$battleInfo]")
            anchorStore?.getAnchorBattleStore()?.onBattleStarted(battleInfo, inviter, invitees)
        }

        override fun onBattleEnded(battleInfo: BattleInfo, reason: BattleEndedReason?) {
            logger.info("${hashCode()} onBattleEnded:[battleInfo:$battleInfo]")
            anchorStore?.getAnchorBattleStore()?.onBattleEnded(battleInfo)
        }

        override fun onUserJoinBattle(battleID: String, battleUser: SeatUserInfo) {
            logger.info("${hashCode()} onUserJoinBattle:[battleID:$battleID,battleUser:$battleUser]")
        }

        override fun onUserExitBattle(battleID: String, battleUser: SeatUserInfo) {
            logger.info("${hashCode()} onUserExitBattle:[battleID:$battleID,battleUser:$battleUser]")
            anchorStore?.getAnchorBattleStore()?.onUserExitBattle(battleUser)
        }

        override fun onBattleRequestReceived(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("${hashCode()} onBattleRequestReceived:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            anchorStore?.getAnchorBattleStore()?.onBattleRequestReceived(battleID, inviter)
        }

        override fun onBattleRequestCancelled(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("${hashCode()} onBattleRequestCancelled:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            anchorStore?.getAnchorBattleStore()?.onBattleRequestCancelled(inviter)
        }

        override fun onBattleRequestTimeout(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("${hashCode()} onBattleRequestTimeout:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            anchorStore?.getAnchorBattleStore()?.onBattleRequestTimeout(inviter, invitee)
        }

        override fun onBattleRequestAccept(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("${hashCode()} onBattleRequestAccept:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            anchorStore?.getAnchorBattleStore()?.onBattleRequestAccept(invitee)
        }

        override fun onBattleRequestReject(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("${hashCode()} onBattleRequestReject:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            anchorStore?.getAnchorBattleStore()?.onBattleRequestReject(invitee)
        }
    }

    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            if (liveID == liveInfo.liveID) {
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
            anchorStore?.getUserStore()?.onAudienceMessageDisabled(audience.userID, isDisable)
        }
    }


    override fun initView() {
        LayoutInflater.from(baseContext).inflate(R.layout.livekit_livestream_anchor_view, this, true)

        layoutCoreViewContainer = findViewById(R.id.fl_video_view_container)
        layoutComponentsContainer = findViewById(R.id.rl_component_container)
        layoutHeaderContainer = findViewById(R.id.fl_header_container)
        audienceListView = findViewById(R.id.audience_list_view)
        imageEndLive = findViewById(R.id.iv_end_live_stream)
        imageFloatWindow = findViewById(R.id.iv_float_window)
        viewCoGuest = findViewById(R.id.ll_co_guest)
        viewCoHost = findViewById(R.id.ll_co_host)
        viewBattle = findViewById(R.id.ll_battle)
        barrageInputView = findViewById(R.id.barrage_input_view)
        barrageStreamView = findViewById(R.id.barrage_stream_view)
        roomInfoView = findViewById(R.id.room_info_view)
        networkInfoView = findViewById(R.id.network_info_view)
        applyCoGuestFloatView = findViewById(R.id.rl_apply_link_audience)
        giftPlayView = findViewById(R.id.gift_play_view)

        layoutCoreViewContainer.setRadius(ScreenUtil.dip2px(16f))
    }

    fun init(
        liveInfo: LiveInfo, coreView: LiveCoreView?, behavior: RoomBehavior, params: Map<String, Any>?,
    ) {
        this.behavior = behavior
        this.liveInfo = liveInfo
        anchorStore = AnchorStore(liveInfo)
        initLiveCoreView(coreView)
        super.init(anchorStore!!)
        parseParams(params)
        createVideoMuteBitmap()
        createOrEnterRoom()
        startForegroundService()
    }

    fun unInit() {
        if (anchorStore?.getState()?.liveInfo?.keepOwnerOnSeat == true) {
            LiveListStore.shared().endLive(null)
        } else {
            LiveListStore.shared().leaveLive(null)
        }
        destroy()
        TUICore.notifyEvent(
            TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED, TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP, null
        )
        TUICore.notifyEvent(EVENT_KEY_TIME_LIMIT, EVENT_SUB_KEY_COUNTDOWN_END, null)
    }

    fun addAnchorViewListener(listener: AnchorViewListener) {
        anchorStore?.addAnchorViewListener(listener)
    }

    fun removeAnchorViewListener(listener: AnchorViewListener) {
        anchorStore?.removeAnchorViewListener(listener)
    }

    fun getState(): AnchorBoardcastState {
        return anchorStore?.getExternalState() ?: AnchorBoardcastState()
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

        val layoutParams = layoutCoreViewContainer.layoutParams as FrameLayout.LayoutParams
        if (enable) {
            layoutParams.setMargins(0, 0, 0, 0)
            layoutCoreViewContainer.setRadius(ScreenUtil.dip2px(0f))
            layoutComponentsContainer.visibility = GONE
        } else {
            layoutParams.setMargins(0, ScreenUtil.dip2px(44f), 0, ScreenUtil.dip2px(96f))
            layoutCoreViewContainer.setRadius(ScreenUtil.dip2px(16f))
            layoutComponentsContainer.visibility = VISIBLE
        }
        layoutCoreViewContainer.layoutParams = layoutParams
    }

    fun disableHeaderLiveData(disable: Boolean?) {
        logger.info("disableHeaderLiveData: disable = $disable")
        AnchorStore.disableHeaderLiveData(disable == true)
    }

    fun disableHeaderVisitorCnt(disable: Boolean?) {
        logger.info("disableHeaderVisitorCnt: disable = $disable")
        AnchorStore.disableHeaderVisitorCnt(disable == true)
    }

    fun disableFooterCoGuest(disable: Boolean?) {
        logger.info("disableFooterCoGuest: disable = $disable")
        AnchorStore.disableFooterCoGuest(disable == true)
    }

    fun disableFooterCoHost(disable: Boolean?) {
        logger.info("disableFooterCoHost: disable = $disable")
        AnchorStore.disableFooterCoHost(disable == true)
    }

    fun disableFooterBattle(disable: Boolean?) {
        logger.info("disableFooterBattle: disable = $disable")
        AnchorStore.disableFooterBattle(disable == true)
    }

    fun disableFooterSoundEffect(disable: Boolean?) {
        logger.info("disableFooterSoundEffect: disable = $disable")
        AnchorStore.disableFooterSoundEffect(disable == true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    override fun refreshView() {
        // Empty implementation
    }

    private fun showCoGuestManageDialog(userInfo: SeatInfo?) {
        if (userInfo == null || TextUtils.isEmpty(userInfo.userInfo.userID)) {
            return
        }
        anchorStore?.let {
            if (anchorManagerDialog == null) {
                anchorManagerDialog = AnchorManagerDialog(baseContext, it)
            }
            anchorManagerDialog?.init(userInfo)
            anchorManagerDialog?.show()
        }
    }

    private fun initLiveCoreView(coreView: LiveCoreView?) {
        liveCoreView = if (coreView != null) {
            if (coreView.parent != null) {
                (coreView.parent as ViewGroup).removeView(coreView)
            }
            coreView
        } else {
            LiveCoreView(context, null, 0, CoreViewType.PUSH_VIEW)
        }
        liveCoreView.setLiveID(liveInfo.liveID)
        layoutCoreViewContainer.addView(liveCoreView)
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
        liveCoreView.setVideoViewAdapter(object : VideoViewAdapter {
            override fun createCoGuestView(seatInfo: SeatInfo?, viewLayer: ViewLayer?): View? {
                if (TextUtils.isEmpty(seatInfo?.userInfo?.userID)) {
                    if (viewLayer == ViewLayer.BACKGROUND) {
                        val emptySeatView = AnchorEmptySeatView(context)
                        if (anchorStore != null && seatInfo != null) {
                            emptySeatView.init(anchorStore!!, seatInfo)
                        }
                        return emptySeatView
                    }
                    return null
                }

                if (viewLayer == ViewLayer.BACKGROUND) {
                    val backgroundView = CoGuestBackgroundWidgetsView(context)
                    if (anchorStore != null && seatInfo != null) {
                        backgroundView.init(anchorStore!!, seatInfo)
                    }
                    return backgroundView
                } else {
                    val foregroundView = CoGuestForegroundWidgetsView(context)
                    if (anchorStore != null && seatInfo != null) {
                        foregroundView.init(anchorStore!!, seatInfo)
                    }
                    foregroundView.setOnClickListener { showCoGuestManageDialog(seatInfo) }
                    return foregroundView
                }
            }

            override fun createCoHostView(seatInfo: SeatInfo?, viewLayer: ViewLayer?): View? {
                if (anchorStore == null || seatInfo == null) {
                    return null
                }
                return if (viewLayer == ViewLayer.BACKGROUND) {
                    CoHostBackgroundWidgetsView(baseContext).apply {
                        init(anchorStore!!, seatInfo)
                    }
                } else {
                    CoHostForegroundWidgetsView(baseContext).apply {
                        init(anchorStore!!, seatInfo)
                    }
                }
            }

            override fun createBattleView(seatInfo: SeatInfo?): View? {
                if (anchorStore == null || seatInfo == null) {
                    return null
                }
                return BattleMemberInfoView(baseContext).apply {
                    init(anchorStore!!, seatInfo.userInfo.userID)
                }
            }

            override fun createBattleContainerView(): View? {
                anchorStore?.let {
                    return BattleInfoView(baseContext).apply {
                        init(it)
                    }
                }
                return null
            }
        })

        if (behavior == RoomBehavior.ENTER_ROOM) {
            enterRoom()
        } else {
            createRoom()
        }
    }

    private fun enterRoom() {
        anchorState?.let {
            // TODO @xander 这个 api 被废弃，看下是否迁移到非 废弃方法中
            liveCoreView.setLocalVideoMuteImage(mediaState?.bigMuteBitmap, mediaState?.smallMuteBitmap)

            val liveListStore = LiveListStore.shared()
            liveListStore.joinLive(it.roomId, object : LiveInfoCompletionHandler {
                override fun onSuccess(liveInfo: LiveInfo) {
                    setCoreViewLayoutParamsWhenLandscape(liveInfo.seatTemplate)
                    startLocalPreview(liveInfo)
                    val activity = baseContext as Activity
                    if (activity.isFinishing || activity.isDestroyed) {
                        logger.warn("activity is exit")
                        liveCoreView.setVideoViewAdapter(null)
                        if (liveInfo.keepOwnerOnSeat) {
                            liveListStore.endLive(null)
                        } else {
                            liveListStore.leaveLive(null)
                        }
                        TUICore.notifyEvent(
                            TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
                            TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP,
                            null
                        )
                        TUICore.notifyEvent(EVENT_KEY_TIME_LIMIT, EVENT_SUB_KEY_COUNTDOWN_END, null)
                        liveCoreView.setLocalVideoMuteImage(null, null)
                        PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
                        return
                    }
                    anchorStore?.updateRoomState(liveInfo)
                    initComponentView()
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
        liveCoreView.setLocalVideoMuteImage(mediaState?.bigMuteBitmap, mediaState?.smallMuteBitmap)
        val liveListStore = LiveListStore.shared()
        liveListStore.createLive(liveInfo, object : LiveInfoCompletionHandler {
            override fun onSuccess(liveInfo: LiveInfo) {
                val activity = baseContext as Activity
                if (activity.isFinishing || activity.isDestroyed) {
                    logger.warn("activity is exit, stopLiveStream")
                    LiveListStore.shared().endLive(null)
                    TUICore.notifyEvent(
                        TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
                        TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP,
                        null
                    )
                    TUICore.notifyEvent(EVENT_KEY_TIME_LIMIT, EVENT_SUB_KEY_COUNTDOWN_END, null)
                    liveCoreView.setLocalVideoMuteImage(null, null)
                    return
                }
                anchorStore?.updateRoomState(liveInfo)
                initComponentView()
                showAlertUserLiveTips()
                TUICore.notifyEvent(EVENT_KEY_TIME_LIMIT, EVENT_SUB_KEY_COUNTDOWN_START, null)
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("startLiveStream failed:error:$code,desc:$desc")
                PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
                ErrorLocalized.onError(code)
                finishActivity()
            }
        })
    }

    private fun startLocalPreview(liveInfo: LiveInfo) {
        if (liveInfo.keepOwnerOnSeat) {
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
                                            }

                                            override fun onDenied() {
                                                logger.error("requestMicrophonePermissions:[onDenied]")
                                            }
                                        })
                                }

                                override fun onFailure(code: Int, desc: String) {
                                    logger.error("startCamera failed:code:$code,desc:$desc")
                                }

                            })
                        }

                        override fun onDenied() {
                            logger.error("requestCameraPermissions:[onDenied]")
                        }
                    })
            }
        }
    }

    private fun initComponentView() {
        initRoomInfoView()
        initAudienceListView()
        initNetworkView()
        initEndLiveStreamView()
        initFloatWindowView()
        initBarrageInputView()
        initBarrageStreamView()
        initCoGuestView()
        initCoHostView()
        initBattleView()
        initSettingsPanel()
        initApplyCoGuestFloatView()
        initGiftPlayView()
    }

    private fun initNetworkView() {
        anchorState?.let {
            networkInfoView.init(it.liveInfo)
        }
    }

    private fun initSettingsPanel() {
        findViewById<View>(R.id.ll_more).setOnClickListener { view ->
            if (!view.isEnabled) return@setOnClickListener
            view.isEnabled = false
            anchorStore?.let {
                val settingsPanelDialog = SettingsPanelDialog(baseContext, it, liveCoreView)
                settingsPanelDialog.setOnDismissListener { view.isEnabled = true }
                settingsPanelDialog.show()
            }
        }
    }

    private fun showAlertUserLiveTips() {
        try {
            val map = hashMapOf<String, Any>(
                TUIConstants.Privacy.PARAM_DIALOG_CONTEXT to Objects.requireNonNull(context)
            )
            TUICore.notifyEvent(
                TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED, TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_START, map
            )
        } catch (e: Exception) {
            logger.error("showAlertUserLiveTips exception:${e.message}")
        }
    }

    private fun initAudienceListView() {
        anchorState?.let {
            audienceListView.init(it.liveInfo)
            audienceListView.setOnUserItemClickListener(object : AudienceListView.OnUserItemClickListener {
                override fun onUserItemClick(userInfo: LiveUserInfo) {
                    anchorStore?.let {
                        val userManagerDialog = UserManagerDialog(baseContext, it, userInfo)
                        userManagerDialog.show()
                    }
                }
            })
        }
    }

    private fun initEndLiveStreamView() {
        imageEndLive.setOnClickListener { showLiveStreamEndDialog() }
    }

    private fun initFloatWindowView() {
        imageFloatWindow.setOnClickListener {
            anchorStore?.notifyPictureInPictureClick()
        }
    }

    private fun initRoomInfoView() {
        anchorState?.let {
            roomInfoView.init(it.liveInfo)
        }
    }

    private fun initBarrageInputView() {
        anchorState?.let {
            barrageInputView.init(it.roomId)
        }
    }

    private fun setCoreViewLayoutParamsWhenLandscape(template: SeatLayoutTemplate) {
        logger.info("setCoreViewLayoutParamsWhenLandscape:template:$template,")
        if (template == SeatLayoutTemplate.VideoLandscape4Seats) {
            layoutCoreViewContainer.setRadius(0)
            val layoutParams: FrameLayout.LayoutParams = layoutCoreViewContainer.layoutParams as FrameLayout.LayoutParams
            layoutParams.topMargin = dip2px(150f)
            layoutParams.height = ScreenUtil.getScreenWidth(context) * 720 / 1280
            layoutCoreViewContainer.layoutParams = layoutParams
        }
    }

    private fun initBarrageStreamView() {
        val ownerUserId = LiveListStore.shared().liveState.currentLive.value.liveOwner.userID
        val liveId = LiveListStore.shared().liveState.currentLive.value.liveID
        barrageStreamView.init(liveId, ownerUserId)
        barrageStreamView.setItemTypeDelegate(BarrageViewTypeDelegate())
        barrageStreamView.setItemAdapter(GIFT_VIEW_TYPE_1, GiftBarrageAdapter(baseContext))
        barrageStreamView.setOnMessageClickListener(object : BarrageStreamView.OnMessageClickListener {
            override fun onMessageClick(userInfo: LiveUserInfo) {
                if (TextUtils.isEmpty(userInfo.userID) || userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                    return
                }
                anchorStore?.let {
                    val userManagerDialog = UserManagerDialog(baseContext, it, userInfo)
                    userManagerDialog.show()
                }
            }
        })
    }

    private fun initCoGuestView() {
        viewCoGuest.setOnClickListener { view ->
            if (!view.isEnabled) return@setOnClickListener
            view.isEnabled = false
            val dialog = AnchorCoGuestManageDialog(baseContext, anchorStore, liveCoreView)
            dialog.setOnDismissListener { view.isEnabled = true }
            dialog.show()
        }
    }

    private fun initCoHostView() {
        viewCoHost.setOnClickListener { view ->
            if (!view.isEnabled) return@setOnClickListener
            view.isEnabled = false
            anchorStore?.let {
                anchorCoHostManageDialog = AnchorCoHostManageDialog(baseContext, it, liveCoreView)
                anchorCoHostManageDialog?.setOnDismissListener { view.isEnabled = true }
                anchorCoHostManageDialog?.show()
            }
        }
    }

    private fun initBattleView() {
        viewBattle.setOnClickListener { view ->
            if (anchorBattleStore == null || anchorStore == null || anchorCoHostStore == null) {
                return@setOnClickListener
            }
            if (battleState?.isBattleRunning?.value == true && anchorBattleStore!!.isSelfInBattle()) {
                if (anchorEndBattleDialog == null) {
                    anchorEndBattleDialog = AnchorEndBattleDialog(baseContext)
                    anchorEndBattleDialog?.setOnEndBattleListener(object : AnchorEndBattleDialog.OnEndBattleListener {
                        override fun onEndBattle() {
                            showEndBattleDialog()
                        }
                    })
                }
                anchorEndBattleDialog?.show()
            } else {
                if (battleState?.isOnDisplayResult?.value == true || !anchorCoHostStore!!.isSelfInCoHost()) {
                    logger.warn("can not requestBattle")
                    return@setOnClickListener
                }

                val list = mutableListOf<String>()
                val selfId = LoginStore.shared.loginState.loginUserInfo.value?.userID
                anchorState?.let {
                    for (user in CoHostStore.create(it.roomId).coHostState.connected.value) {
                        if (user.userID != selfId) {
                            list.add(user.userID)
                        }
                    }
                }

                val battleConfig = BattleConfig().apply {
                    duration = BATTLE_DURATION
                    needResponse = true
                    extensionInfo = ""
                }
                BattleStore.create(liveInfo.liveID).requestBattle(
                    battleConfig, list, BATTLE_REQUEST_TIMEOUT, object : BattleRequestCallback {
                        override fun onSuccess(
                            battleInfo: BattleInfo, resultMap: Map<String, Int>,
                        ) {
                            anchorStore?.getAnchorBattleStore()?.onRequestBattle(battleInfo.battleID, list)
                        }

                        override fun onError(code: Int, desc: String) {
                            logger.error("requestBattle failed:code:$code,desc:$desc")
                            ErrorLocalized.onError(code)
                        }

                    })
            }
        }
    }

    private fun initApplyCoGuestFloatView() {
        anchorStore?.let {
            applyCoGuestFloatView.init(it, liveCoreView)
        }
    }

    private fun initGiftPlayView() {
        val giftCacheService: GiftCacheService = GiftStore.getInstance().giftCacheService
        giftPlayView.setListener(object : GiftPlayView.TUIGiftPlayViewListener {
            override fun onReceiveGift(
                view: GiftPlayView?, gift: Gift, giftCount: Int, sender: LiveUserInfo,
            ) {
                val barrage = Barrage().apply {
                    textContent = "gift"
                    this.sender.userID = sender.userID
                    this.sender.userName = if (TextUtils.isEmpty(sender.userName)) {
                        sender.userID
                    } else {
                        sender.userName
                    }
                    this.sender.avatarURL = sender.avatarURL

                    val extInfo = hashMapOf<String, String>(
                        GIFT_VIEW_TYPE to GIFT_VIEW_TYPE_1.toString(),
                        GIFT_NAME to gift.name,
                        GIFT_COUNT to giftCount.toString(),
                        GIFT_ICON_URL to gift.iconURL,
                        GIFT_RECEIVER_USERNAME to context.getString(R.string.common_gift_me)
                    )
                    extensionInfo = extInfo
                }
                barrageStreamView.insertBarrages(barrage)
            }

            override fun onPlayGiftAnimation(
                view: GiftPlayView?, gift: Gift,
            ) {
                giftCacheService.request(gift.resourceURL, object : GiftCacheService.Callback<String> {
                    override fun onResult(error: Int, result: String?) {
                        if (error == 0) {
                            result?.let {
                                view?.playGiftAnimation(it)
                            }
                        }
                    }

                })
            }
        })
        anchorState?.let {
            giftPlayView.init(it.roomId)
        }
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                onDisableLiveDataChange()
            }
            launch {
                onDisableVisitorCntChange()
            }
            launch {
                ondDisableCoGuestChange()
            }
            launch {
                onDisableCoHostChange()
            }
            launch {
                onDisableBattleChange()
            }
            launch {
                onCoHostUserListChange()
            }
            launch {
                val coGuestStore = CoGuestStore.create(liveInfo.liveID)
                coGuestStore.coGuestState.applicants.collect { applicants ->
                    onCoGuestApplicantsChange(applicants)
                }
            }
            launch {
                onCoGuestUserListChange()
            }
            launch {
                onPipModeObserver()
            }
            launch {
                onBattleUserChange()
            }
            launch {
                battleState?.receivedBattleRequest?.collect { user ->
                    onReceivedBattleRequestChange(user)
                }
            }
            launch {
                onBattleStartChange()
            }
            launch {
                onInWaitingChange()
            }
            launch {
                onBattleResultDisplay()
            }
        }
        CoHostStore.create(liveInfo.liveID).addCoHostListener(coHostListener)
        CoGuestStore.create(liveInfo.liveID).addHostListener(coGuestListener)
        BattleStore.create(liveInfo.liveID).addBattleListener(battleListener)
        LiveListStore.shared().addLiveListListener(liveListListener)
        LiveAudienceStore.create(liveInfo.liveID).addLiveAudienceListener(liveAudienceListener)
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
        CoHostStore.create(liveInfo.liveID).removeCoHostListener(coHostListener)
        CoGuestStore.create(liveInfo.liveID).removeHostListener(coGuestListener)
        BattleStore.create(liveInfo.liveID).removeBattleListener(battleListener)
        LiveListStore.shared().removeLiveListListener(liveListListener)
        LiveAudienceStore.create(liveInfo.liveID).removeLiveAudienceListener(liveAudienceListener)
    }

    private fun showLiveStreamEndDialog() {
        val store = anchorStore ?: return
        val currentLiveId = LiveListStore.shared().liveState.currentLive.value.liveID

        val isInCoGuest = CoGuestStore.create(currentLiveId).coGuestState.connected
            .value.filterNot { it.liveID != currentLiveId }.size > 1
        val isInCoHost = CoHostStore.create(currentLiveId).coHostState.connected.value.isNotEmpty()
        val isInBattle = store.getBattleState().isBattleRunning.value == true

        AtomicAlertDialog(baseContext).let { dialog ->
            if (isInBattle) {
                val title = baseContext.getString(R.string.common_end_pk_tips)

                dialog.init {
                    init(title)

                    addItem(
                        text = baseContext.getString(R.string.common_end_pk),
                        type = AtomicAlertDialog.TextColorPreset.RED,
                    ) {
                        val battleId = store.getBattleState().battleId
                        BattleStore.create(currentLiveId).exitBattle(battleId, object : CompletionHandler {
                            override fun onSuccess() {
                                store.getAnchorBattleStore().onExitBattle()
                                it.dismiss()
                            }

                            override fun onFailure(code: Int, desc: String) {
                                it.dismiss()
                            }
                        })
                    }

                    addItem(
                        text = baseContext.getString(R.string.common_end_live),
                        type = AtomicAlertDialog.TextColorPreset.PRIMARY
                    ) {
                        exitLive(store)
                        it.dismiss()
                    }

                    addItem(
                        text = baseContext.getString(R.string.common_cancel),
                        type = AtomicAlertDialog.TextColorPreset.PRIMARY
                    ) {
                        it.dismiss()
                    }
                }
            } else if (isInCoHost) {
                val title = baseContext.getString(R.string.common_end_connection_tips)

                dialog.init {
                    init(title)

                    addItem(
                        text = baseContext.getString(R.string.common_end_connection),
                        type = AtomicAlertDialog.TextColorPreset.RED,
                    ) {
                        CoHostStore.create(currentLiveId).exitHostConnection(null)
                        it.dismiss()
                    }

                    addItem(
                        text = baseContext.getString(R.string.common_end_live),
                        type = AtomicAlertDialog.TextColorPreset.PRIMARY
                    ) {
                        exitLive(store)
                        it.dismiss()
                    }

                    addItem(
                        text = baseContext.getString(R.string.common_cancel),
                        type = AtomicAlertDialog.TextColorPreset.PRIMARY
                    ) {
                        it.dismiss()
                    }
                }
            } else if (isInCoGuest) {
                val title = baseContext.getString(R.string.common_anchor_end_link_tips)

                dialog.init {
                    init(title)

                    cancelButton(
                        text = baseContext.getString(R.string.common_cancel),
                        type = AtomicAlertDialog.TextColorPreset.GREY
                    ) {
                        it.dismiss()
                    }

                    confirmButton(
                        text = baseContext.getString(R.string.common_end_live),
                        type = AtomicAlertDialog.TextColorPreset.RED
                    ) {
                        exitLive(store)
                        it.dismiss()
                    }
                }
            } else {
                dialog.init {
                    init(baseContext.getString(R.string.live_end_live_tips))

                    cancelButton(
                        text = baseContext.getString(R.string.common_cancel),
                        type = AtomicAlertDialog.TextColorPreset.GREY
                    ) {
                        it.dismiss()
                    }

                    confirmButton(
                        text = baseContext.getString(R.string.common_end_live),
                        type = AtomicAlertDialog.TextColorPreset.RED
                    ) {
                        exitLive(store)
                        it.dismiss()
                    }
                }
            }

            dialog.show()
        }
    }

    private fun exitLive(store: AnchorStore) {
        val keepOwnerOnSeat = store.getState().liveInfo.keepOwnerOnSeat

        if (keepOwnerOnSeat) {
            LiveListStore.shared().endLive(object : StopLiveCompletionHandler {
                override fun onSuccess(statisticsData: TUILiveListManager.LiveStatisticsData) {
                    store.setLiveStatisticsData(statisticsData)
                    onRoomExitEndStatistics()
                    store.notifyRoomExit()
                }

                override fun onFailure(code: Int, desc: String) {
                    onRoomExitEndStatistics()
                    store.notifyRoomExit()
                }
            })
        } else {
            leaveLive()
        }

        TUICore.notifyEvent(
            TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
            TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP,
            null
        )
        TUICore.notifyEvent("RTCRoomTimeLimitService", "CountdownEnd", null)
        liveCoreView.setLocalVideoMuteImage(null, null)
    }

    private fun onRoomExitEndStatistics() {
        anchorStore?.setExternalState(barrageStreamView.getBarrageCount())
        PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
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

    @SuppressLint("NotifyDataSetChanged")
    private suspend fun onCoHostUserListChange() {
        val coHostStore = CoHostStore.create(liveInfo.liveID)
        coHostStore.coHostState.connected.collect { userList ->
            updateBattleView()
            enableView(viewCoGuest, userList.isEmpty())
        }
    }

    private suspend fun onBattleUserChange() {
        battleState?.battledUsers?.collect {
            post { updateBattleView() }
        }
    }

    private suspend fun onCoGuestUserListChange() {
        val coGuestStore = CoGuestStore.create(liveInfo.liveID)
        coGuestStore.coGuestState.connected.collect { seatList ->
            enableView(viewCoHost, seatList.size <= 1)
            val coGuestIconView = findViewById<CoGuestIconView>(R.id.co_guest_icon)
            if (seatList.size > 1) {
                coGuestIconView.startAnimation()
            } else {
                coGuestIconView.stopAnimation()
            }
        }
    }

    private fun onReceivedBattleRequestChange(user: BattleUser?) {
        if (mediaState?.isPipModeEnabled?.value != true) {
            processBattleDialog?.dismiss()
            processBattleDialog = null

            user?.let {
                val content = context.getString(
                    R.string.common_battle_inviting, user.userName
                )
                processBattleDialog = AtomicAlertDialog(context).apply {
                    init {
                        title = content
                        countdownDuration = 0
                        confirmButton(
                            text = context.getString(R.string.common_receive),
                            type = AtomicAlertDialog.TextColorPreset.BLUE
                        ) { dialog ->
                            dialog.dismiss()
                            processBattleDialog = null
                            battleState?.let { battleState ->
                                BattleStore.create(liveInfo.liveID)
                                    .acceptBattle(battleState.battleId, object : CompletionHandler {
                                        override fun onSuccess() {
                                            anchorStore?.getAnchorBattleStore()?.onResponseBattle()
                                        }

                                        override fun onFailure(code: Int, desc: String) {
                                            logger.error("respondToBattle failed:code:$code,desc:$desc")
                                            ErrorLocalized.onError(code)
                                        }
                                    })
                            }
                        }

                        cancelButton(
                            text = context.getString(R.string.common_reject),
                            type = AtomicAlertDialog.TextColorPreset.GREY
                        ) { dialog ->
                            dialog.dismiss()
                            processBattleDialog = null
                            battleState?.let { battleState ->
                                BattleStore.create(liveInfo.liveID)
                                    .rejectBattle(battleState.battleId, object : CompletionHandler {
                                        override fun onSuccess() {
                                            anchorStore?.getAnchorBattleStore()?.onResponseBattle()
                                        }

                                        override fun onFailure(code: Int, desc: String) {
                                            logger.error("respondToBattle failed:code:$code,desc:$desc")
                                            ErrorLocalized.onError(code)
                                        }
                                    })
                            }
                        }
                    }
                }
                processBattleDialog?.show()
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

    private suspend fun onBattleStartChange() {
        battleState?.isBattleRunning?.collect { it ->
            if (it == true) {
                battleState?.battledUsers?.value?.let { battledUsers ->
                    for (user in battledUsers) {
                        if (TextUtils.equals(LoginStore.shared.loginState.loginUserInfo.value?.userID, user.userId)) {
                            enableView(viewCoHost, false)
                            break
                        }
                    }
                }
            } else {
                enableView(viewCoHost, true)
                if (anchorEndBattleDialog?.isShowing == true) {
                    anchorEndBattleDialog?.dismiss()
                }
                if (realEndBattleDialog?.isShowing() == true) {
                    realEndBattleDialog?.dismiss()
                }
            }
        }
    }

    private suspend fun onBattleResultDisplay() {
        battleState?.isOnDisplayResult?.collect {
            post { updateBattleView() }
        }
    }

    private fun updateBattleView() {
        val battleIconView = viewBattle.findViewById<View>(R.id.v_battle_icon)
        val battleResultDisplay = battleState?.isOnDisplayResult?.value
        if (anchorCoHostStore == null || anchorBattleStore == null) {
            return
        }
        if (anchorCoHostStore!!.isSelfInCoHost()) {
            if (anchorBattleStore!!.isSelfInBattle()) {
                battleIconView.setBackgroundResource(R.drawable.livekit_function_battle_exit)
            } else {
                battleIconView.setBackgroundResource(R.drawable.livekit_function_battle)
            }
            if (battleResultDisplay == true) {
                battleIconView.setBackgroundResource(R.drawable.livekit_function_battle_disable)
            } else {
                battleIconView.setBackgroundResource(R.drawable.livekit_function_battle)
            }
        } else {
            battleIconView.setBackgroundResource(R.drawable.livekit_function_battle_disable)
        }
    }

    private fun enableView(view: View, enable: Boolean) {
        view.isEnabled = enable
        view.alpha = if (enable) 1.0f else 0.5f
    }

    private fun parseParams(params: Map<String, Any>?) {
        if (params == null) return

        params["coHostTemplateId"]?.let { coHostTemplateId ->
            if (coHostTemplateId is Int) {
                anchorStore?.getAnchorCoHostStore()?.setCoHostTemplateId(coHostTemplateId)
            }
        }
    }

    private fun destroy() {
        if (isDestroy) return
        isDestroy = true

        DeviceStore.shared().closeLocalCamera()
        DeviceStore.shared().closeLocalMicrophone()
        BeautyUtils.resetBeauty()
        TEBeautyStore.unInit()
        anchorStore?.destroy()
        stopForegroundService()
    }

    private suspend fun onDisableLiveDataChange() {
        AnchorConfig.disableHeaderLiveData.collect {
            layoutHeaderContainer.visibility = if (it) GONE else VISIBLE
        }
    }

    private suspend fun onDisableVisitorCntChange() {
        AnchorConfig.disableHeaderVisitorCnt.collect {
            audienceListView.visibility = if (it) GONE else VISIBLE
        }
    }

    private suspend fun ondDisableCoGuestChange() {
        AnchorConfig.disableFooterCoGuest.collect {
            viewCoGuest.visibility = if (it) GONE else VISIBLE
        }
    }

    private suspend fun onDisableCoHostChange() {
        AnchorConfig.disableFooterCoHost.collect {
            viewCoHost.visibility = if (it) GONE else VISIBLE
        }
    }

    private suspend fun onDisableBattleChange() {
        AnchorConfig.disableFooterBattle.collect {
            viewBattle.visibility = if (it) GONE else VISIBLE
        }
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

    private fun onCoGuestApplicantsChange(applicants: List<LiveUserInfo>) {
        enableView(viewCoHost, applicants.isEmpty())
        anchorCoHostManageDialog?.dismiss()
    }

    private suspend fun onPipModeObserver() {
        mediaState?.isPipModeEnabled?.collect { enable ->
            if (!enable && liveInfo.liveID.isNotEmpty()) {
                postDelayed({
                    onReceivedCoHostRequest(CoHostStore.create(liveInfo.liveID).coHostState.applicant.value)
                    onReceivedBattleRequestChange(battleState?.receivedBattleRequest?.value)
                    checkCameraStateAndRestore()
                }, if (isPipModeAbnormalPhoneModel()) 500 else 0)
            }
        }
    }

    private fun isPipModeAbnormalPhoneModel(): Boolean {
        return Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) || Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    private fun checkCameraStateAndRestore() {
        mediaState?.let {
            if (DeviceStore.shared().deviceState.cameraLastError.value == DeviceError.OCCUPIED_ERROR &&
                DeviceStore.shared().deviceState.cameraStatus.value == DeviceStatus.ON
            ) {
                DeviceStore.shared().closeLocalCamera()
                postDelayed({
                    DeviceStore.shared().openLocalCamera(DeviceStore.shared().deviceState.isFrontCamera.value, null)
                }, 500)
            }
        }
    }

    private fun endLive(reason: LiveEndedReason = LiveEndedReason.ENDED_BY_HOST, isFinish: Boolean = true) {
        PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
        liveCoreView.setLocalVideoMuteImage(null, null)
        LiveListStore.shared().endLive(null)
        TUICore.notifyEvent(
            TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED, TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP, null
        )
        TUICore.notifyEvent(EVENT_KEY_TIME_LIMIT, EVENT_SUB_KEY_COUNTDOWN_END, null)
        anchorStore?.notifyRoomExit(reason)
        if (isFinish) {
            finishActivity()
        }
    }

    private fun leaveLive(isFinish: Boolean = true) {
        PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
        liveCoreView.setLocalVideoMuteImage(null, null)
        LiveListStore.shared().leaveLive(null)
        TUICore.notifyEvent(
            TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED, TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP, null
        )
        TUICore.notifyEvent(EVENT_KEY_TIME_LIMIT, EVENT_SUB_KEY_COUNTDOWN_END, null)
        if (isFinish) {
            finishActivity()
        }
    }

    private fun showEndBattleDialog() {
        realEndBattleDialog = AtomicAlertDialog(context)
        realEndBattleDialog?.init {
            init(
                title = context.getString(R.string.common_battle_end_pk_tips),
                content = null,
                iconView = null,
            )

            cancelButton(context.getString(R.string.common_disconnect_cancel)) {
                it.dismiss()
            }

            confirmButton(
                text = context.getString(R.string.common_battle_end_pk),
                type = AtomicAlertDialog.TextColorPreset.RED
            ) {
                it.dismiss()
                val battleId = anchorStore?.getBattleState()?.battleId
                BattleStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
                    .exitBattle(battleId, object : CompletionHandler {
                        override fun onSuccess() {}

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("AtomicAlertDialog terminateBattle failed:code:$code,desc:$desc")
                        }
                    })
            }
        }
        realEndBattleDialog?.show()
    }
}