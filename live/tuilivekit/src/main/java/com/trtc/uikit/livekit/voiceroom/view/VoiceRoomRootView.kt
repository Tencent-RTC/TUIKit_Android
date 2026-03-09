package com.trtc.uikit.livekit.voiceroom.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import com.tencent.cloud.tuikit.engine.extension.TUILiveListManager
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import io.trtc.tuikit.atomicx.karaoke.view.KaraokeControlView
import io.trtc.tuikit.atomicx.karaoke.view.KaraokeFloatingView
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuicore.interfaces.ITUINotification
import com.trtc.tuikit.common.imageloader.ImageLoader
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.COMPONENT_VOICE_ROOM
import com.trtc.uikit.livekit.common.DEFAULT_BACKGROUND_URL
import com.trtc.uikit.livekit.common.DEFAULT_COVER_URL
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_CLOSE_VOICE_ROOM
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_FINISH_ACTIVITY
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.common.setComponent
import com.trtc.uikit.livekit.component.barrage.BarrageStreamView
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
import com.trtc.uikit.livekit.voiceroom.interaction.common.CoHostView
import com.trtc.uikit.livekit.voiceroom.interaction.common.LocalCoHostEmptyView
import com.trtc.uikit.livekit.voiceroom.interaction.common.RemoteCoHostEmptyView
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroom.store.LayoutType
import com.trtc.uikit.livekit.voiceroom.store.LiveStatus
import com.trtc.uikit.livekit.voiceroom.store.LiveStreamPrivacyStatus
import com.trtc.uikit.livekit.voiceroom.view.TUIVoiceRoomFragment.RoomBehavior
import com.trtc.uikit.livekit.voiceroom.view.basic.BasicView
import com.trtc.uikit.livekit.voiceroom.view.bottommenu.BottomMenuView
import com.trtc.uikit.livekit.voiceroom.view.dashboard.AnchorDashboardView
import com.trtc.uikit.livekit.voiceroom.view.dashboard.AudienceDashboardView
import com.trtc.uikit.livekit.voiceroom.view.preview.AnchorPreviewView
import com.trtc.uikit.livekit.voiceroom.view.seatmanager.ListMenuInfo
import com.trtc.uikit.livekit.voiceroom.view.seatmanager.SeatActionSheetDialog
import com.trtc.uikit.livekit.voiceroom.view.seatmanager.SeatActionSheetGenerator
import com.trtc.uikit.livekit.voiceroom.view.topview.TopView
import com.trtc.uikit.livekit.voiceroomcore.SeatGridView
import com.trtc.uikit.livekit.voiceroomcore.SeatGridViewObserver
import com.trtc.uikit.livekit.voiceroomcore.VoiceRoomDefine
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.addItem
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.init
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStatus
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.GuestListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveKickedOutReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatListener
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.MetaDataCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.live.StopLiveCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.TakeSeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class VoiceRoomRootView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BasicView(context, attrs, defStyleAttr), ITUINotification {

    private val rootViewContext = context
    private var invitationDialog: AtomicAlertDialog? = null
    private var seatActionSheetGenerator: SeatActionSheetGenerator? = null
    private var seatActionSheetDialog: SeatActionSheetDialog? = null
    private var coHostStore: CoHostStore? = null
    private var battleStore: BattleStore? = null
    private lateinit var roomBehavior: RoomBehavior
    private lateinit var roomParams: TUIVoiceRoomFragment.RoomParams
    private lateinit var layoutEndViewContainer: RelativeLayout
    private lateinit var anchorPreviewView: AnchorPreviewView
    private lateinit var topView: TopView
    private lateinit var seatGridContainer: FrameLayout
    private lateinit var bottomMenuView: BottomMenuView
    private lateinit var rootBg: ImageView
    private lateinit var barrageStreamView: BarrageStreamView
    private lateinit var giftPlayView: GiftPlayView
    private lateinit var giftCacheService: GiftCacheService
    private lateinit var karaokeFloatingView: KaraokeFloatingView
    private lateinit var karaokeControlView: KaraokeControlView
    private lateinit var layoutRoot: ViewGroup
    private lateinit var liveAudienceStore: LiveAudienceStore
    private lateinit var liveListStore: LiveListStore
    private lateinit var liveSeatStore: LiveSeatStore
    private lateinit var coGuestStore: CoGuestStore
    private lateinit var deviceStore: DeviceStore
    private var anchorExitConfirmDialog: AtomicAlertDialog? = null
    private val roomEngine = TUIRoomEngine.sharedInstance()

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_voiceroom_root_view, this, true)
        bindViewId()
    }

    fun init(
        voiceRoomManager: VoiceRoomManager,
        behavior: RoomBehavior,
        params: TUIVoiceRoomFragment.RoomParams,
    ) {
        super.init(
            voiceRoomManager.prepareStore.prepareState.liveInfo.value.liveID,
            voiceRoomManager,
            SeatGridView(rootViewContext)
        )
        roomBehavior = behavior
        roomParams = params
        giftCacheService = GiftStore.getInstance().giftCacheService

        initGiftView()
        setCoHostViewAdapter()
        enterRoom()
    }

    fun updateStatus(status: VoiceRoomViewStatus) {
        if (RoomBehavior.JOIN != roomBehavior) {
            return
        }
        when (status) {
            VoiceRoomViewStatus.DISPLAY_COMPLETE -> displayComplete()
            VoiceRoomViewStatus.END_DISPLAY -> endDisplay()
            else -> {}
        }
    }

    override fun initStore() {
        liveListStore = LiveListStore.shared()
        liveSeatStore = LiveSeatStore.create(liveID)
        liveAudienceStore = LiveAudienceStore.create(liveID)
        coGuestStore = CoGuestStore.create(liveID)
        deviceStore = DeviceStore.shared()
        coHostStore = CoHostStore.create(liveID)
        battleStore = BattleStore.create(liveID)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        voiceRoomManager?.destroy()
    }

    private fun bindViewId() {
        layoutRoot = findViewById(R.id.cl_root)
        rootBg = findViewById(R.id.root_bg)
        topView = findViewById(R.id.top_view)
        seatGridContainer = findViewById(R.id.seat_grid_container)
        bottomMenuView = findViewById(R.id.bottom_menu)
        barrageStreamView = findViewById(R.id.barrage_stream_view)
        giftPlayView = findViewById(R.id.gift_play_view)
        layoutEndViewContainer = findViewById(R.id.rl_end_view)
        anchorPreviewView = findViewById(R.id.anchor_preview_view)
        karaokeControlView = findViewById(R.id.ktv_view)
        karaokeFloatingView = KaraokeFloatingView(rootViewContext)
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                voiceRoomManager?.prepareStore?.prepareState?.layoutType?.collect {
                    onVoiceRoomLayoutChanged(it)
                }
            }
            launch {
                voiceRoomManager?.prepareStore?.prepareState?.liveStatus?.collect {
                    onLiveStateChanged(it)
                }
            }
            launch {
                voiceRoomManager?.prepareStore?.prepareState?.liveInfo
                    ?.map { it.backgroundURL }
                    ?.distinctUntilChanged()
                    ?.collect {
                        if (it.isBlank()) return@collect
                        updateRoomBackground(it)
                    }
            }
            launch {
                liveListStore.liveState.currentLive
                    .map { it.backgroundURL }
                    .distinctUntilChanged()
                    .collect {
                        if (it.isBlank()) return@collect
                        updateRoomBackground(it)
                    }
            }
            launch {
                liveSeatStore.liveSeatState.seatList
                    .map { seatList ->
                        seatList.find { it.userInfo.userID == TUIRoomEngine.getSelfInfo().userId }?.userInfo?.userID
                    }
                    .distinctUntilChanged()
                    .collect { userID ->
                        if (userID == null) return@collect
                        onLinkStateChanged(userID.isNotBlank())
                    }
            }
            launch {
                launch {
                    coHostStore?.coHostState?.connected?.collect {
                        onConnectedListChanged(it)
                    }
                }
            }
            launch {
                launch {
                    battleStore?.battleState?.battleUsers?.collect {
                        onBattleListChanged(it)
                    }
                }
            }
        }
        roomEngine.addObserver(roomEngineObserver)
        coGuestStore.addGuestListener(guestListener)
        seatGridView?.addObserver(seatGridViewObserver)
        liveListStore.addLiveListListener(liveListListener)
        liveSeatStore.addLiveSeatEventListener(liveSeatListener)
        TUICore.registerEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_CLOSE_VOICE_ROOM, this)
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
        roomEngine.removeObserver(roomEngineObserver)
        coGuestStore.removeGuestListener(guestListener)
        seatGridView?.removeObserver(seatGridViewObserver)
        liveListStore.removeLiveListListener(liveListListener)
        liveSeatStore.removeLiveSeatEventListener(liveSeatListener)
        TUICore.unRegisterEvent(this)
    }

    private fun showMainView() {
        if (voiceRoomManager == null || seatGridView == null) {
            return
        }
        seatGridView?.setLayoutMode(VoiceRoomDefine.LayoutMode.GRID, null)
        topView.init(liveID, voiceRoomManager!!, seatGridView!!)
        topView.visibility = VISIBLE
        bottomMenuView.init(liveID, voiceRoomManager!!, seatGridView!!)
        bottomMenuView.visibility = VISIBLE
        karaokeFloatingView.init(
            liveID,
            isOwner()
        )
        karaokeControlView.init(
            liveID,
            isOwner()
        )
    }

    private fun onLinkStateChanged(isLinking: Boolean) {
        if (isLinking) {
            unmuteMicrophone()
            startMicrophone()
        }
    }

    private fun onConnectedListChanged(connectedRoomList: List<SeatUserInfo>) {
        val layoutType = voiceRoomManager?.prepareStore?.prepareState?.layoutType?.value
        val isConnected = connectedRoomList.any { it.liveID == liveID }
        if (LayoutType.KTV_ROOM == layoutType) {
            if (isConnected) {
                karaokeControlView.visibility = GONE
            } else {
                karaokeControlView.visibility = VISIBLE
            }
        }
        if (isConnected && isOwner()) {
            anchorExitConfirmDialog?.dismiss()
        }
    }

    private fun onBattleListChanged(battleRoomList: List<SeatUserInfo>) {
        val isBattle = battleRoomList.any { it.liveID == liveID }
        if (isBattle && isOwner()) {
            anchorExitConfirmDialog?.dismiss()
        }
    }

    private fun unmuteMicrophone() {
        liveSeatStore.unmuteMicrophone(completionHandler {
            onError { error, message ->
                LOGGER.error("unmuteMicrophone failed, error: $error, message: $message")
                ErrorLocalized.onError(error)
            }
        })
    }

    private fun startMicrophone() {
        PermissionRequest.requestMicrophonePermissions(
            rootViewContext, object : PermissionCallback() {
                override fun onRequesting() {
                    LOGGER.info("requestMicrophonePermissions")
                }

                override fun onGranted() {
                    LOGGER.info("requestMicrophonePermissions:[onGranted]")
                    startMicrophoneInternal()
                }

                override fun onDenied() {
                    LOGGER.info("onDenied")
                }
            })
    }

    private fun startMicrophoneInternal() {
        deviceStore.openLocalMicrophone(completionHandler {
            onSuccess {
                liveSeatStore.unmuteMicrophone(null)
            }
            onError { error, message ->
                LOGGER.error("openLocalMicrophone failed, error: $error, message: $message")
                ErrorLocalized.onError(error)
            }
        })
    }

    private fun updateRoomBackground(url: String?) {
        if (rootViewContext is Activity && rootViewContext.isDestroyed) {
            return
        }
        ImageLoader.load(rootViewContext, rootBg, url, R.drawable.livekit_voiceroom_bg)
    }

    private fun showPreviewView() {
        if (voiceRoomManager == null || seatGridView == null) return
        anchorPreviewView.init(liveID, voiceRoomManager!!, seatGridView!!)
        anchorPreviewView.visibility = VISIBLE
    }

    private fun removePreviewView() {
        anchorPreviewView.visibility = GONE
    }

    private fun initGiftView() {
        giftPlayView.init(liveID)
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
                barrage.sender.userName = if (TextUtils.isEmpty(sender.userName)) {
                    sender.userID
                } else {
                    sender.userName
                }
                barrage.sender.avatarURL = sender.avatarURL
                val extInfo = HashMap<String, String>()
                extInfo[GIFT_VIEW_TYPE] = GIFT_VIEW_TYPE_1.toString()
                extInfo[GIFT_NAME] = gift.name
                extInfo[GIFT_COUNT] = giftCount.toString()
                extInfo[GIFT_ICON_URL] = gift.iconURL
                val ownerInfo = liveListStore.liveState.currentLive.value.liveOwner
                val receiverName = if (TextUtils.isEmpty(ownerInfo.userName)) {
                    ownerInfo.userID
                } else {
                    ownerInfo.userName
                }
                val finalReceiverName =
                    if (TextUtils.equals(ownerInfo.userID, TUILogin.getUserId())) {
                        rootViewContext.getString(R.string.common_gift_me)
                    } else {
                        receiverName
                    }
                extInfo[GIFT_RECEIVER_USERNAME] = finalReceiverName
                barrage.extensionInfo = extInfo
                barrageStreamView.insertBarrages(barrage)
            }

            override fun onPlayGiftAnimation(view: GiftPlayView?, gift: Gift) {
                giftCacheService.request(
                    gift.resourceURL,
                    object : GiftCacheService.Callback<String> {
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
    }

    private fun initBarrageView() {
        barrageStreamView.init(
            liveID,
            liveListStore.liveState.currentLive.value.liveOwner.userID
        )
        barrageStreamView.setItemTypeDelegate(BarrageViewTypeDelegate())
        barrageStreamView.setItemAdapter(GIFT_VIEW_TYPE_1, GiftBarrageAdapter(rootViewContext))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initAnchorEndView() {
        if (voiceRoomManager == null) return
        layoutEndViewContainer.removeAllViews()
        val anchorEndView = AnchorDashboardView(rootViewContext)
        anchorEndView.init(liveID, voiceRoomManager!!)
        anchorEndView.setOnTouchListener { _, _ -> true }
        val layoutParams = RelativeLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        layoutEndViewContainer.addView(anchorEndView, layoutParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initAudienceEndView() {
        if (voiceRoomManager == null) return
        layoutEndViewContainer.removeAllViews()
        val audienceEndView = AudienceDashboardView(rootViewContext)
        audienceEndView.init(liveID, voiceRoomManager!!)
        audienceEndView.setOnTouchListener { _, _ -> true }
        val layoutParams = RelativeLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        layoutEndViewContainer.addView(audienceEndView, layoutParams)
    }

    private fun enterRoom() {
        when (roomBehavior) {
            RoomBehavior.AUTO_CREATE -> {
                voiceRoomManager?.prepareStore?.initCreateRoomState(
                    liveID,
                    roomParams.roomName,
                    roomParams.seatMode,
                    roomParams.maxSeatCount
                )
                start()
            }

            RoomBehavior.JOIN -> enter()
            RoomBehavior.PREPARE_CREATE -> {
                voiceRoomManager?.prepareStore?.initCreateRoomState(
                    liveID,
                    roomParams.roomName,
                    roomParams.seatMode,
                    roomParams.maxSeatCount
                )
                voiceRoomManager?.prepareStore
                    ?.updateLiveStatus(LiveStatus.PREVIEWING)
            }
        }
    }

    private fun start() {
        setComponent(COMPONENT_VOICE_ROOM)
        val prepareState = voiceRoomManager?.prepareStore?.prepareState
        val seatCount = prepareState?.liveInfo?.value?.maxSeatCount ?: 9
        val liveInfo = LiveInfo(seatTemplate = SeatLayoutTemplate.AudioSalon(seatCount))
        liveInfo.keepOwnerOnSeat = true
        liveInfo.liveID = prepareState?.liveInfo?.value?.liveID ?: ""
        liveInfo.liveName = prepareState?.liveInfo?.value?.liveName ?: ""
        liveInfo.seatMode = prepareState?.liveInfo?.value?.seatMode ?: TakeSeatMode.FREE
        liveInfo.backgroundURL =
            prepareState?.liveInfo?.value?.backgroundURL ?: DEFAULT_BACKGROUND_URL
        liveInfo.coverURL = prepareState?.liveInfo?.value?.coverURL ?: DEFAULT_COVER_URL
        liveInfo.isPublicVisible =
            voiceRoomManager?.prepareStore?.prepareState?.liveExtraInfo?.value?.liveMode == LiveStreamPrivacyStatus.PUBLIC
        liveListStore.createLive(
            liveInfo,
            object : LiveInfoCompletionHandler {
                override fun onSuccess(liveInfo: LiveInfo) {
                    LOGGER.info("create room success")
                    voiceRoomManager?.prepareStore?.updateLiveInfo(liveInfo)
                    voiceRoomManager?.prepareStore?.updateLiveStatus(LiveStatus.PUSHING)
                    getAudienceList()
                }

                override fun onFailure(code: Int, desc: String) {
                    LOGGER.error("create room failed, error: $code, message: $desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    fun enter() {
        setComponent(COMPONENT_VOICE_ROOM)
        liveListStore.joinLive(liveID, object : LiveInfoCompletionHandler {
            override fun onSuccess(liveInfo: LiveInfo) {
                voiceRoomManager?.prepareStore?.updateLiveInfo(liveInfo)
                getAudienceList()
                voiceRoomManager?.prepareStore
                    ?.updateLiveStatus(LiveStatus.PLAYING)
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("enter room failed, error: $code, message: $desc")
                ErrorLocalized.onError(code)
                voiceRoomManager?.prepareStore
                    ?.updateLiveStatus(LiveStatus.NONE)
                if (rootViewContext is Activity) {
                    rootViewContext.finish()
                }
            }
        })
    }

    private fun addEnterBarrage() {
        val userInfo = liveListStore.liveState.currentLive.value.liveOwner
        val barrage = Barrage().apply {
            liveID = liveID
            textContent = context.getString(R.string.common_entered_room)
            sender.apply {
                userID = userInfo.userID
                userName = userInfo.userName
                avatarURL = userInfo.avatarURL
            }
        }
        barrageStreamView.insertBarrages(barrage)
    }

    private fun showEndView() {
        layoutEndViewContainer.removeAllViews()
        invitationDialog?.let { dialog ->
            if (dialog.isShowing()) {
                dialog.dismiss()
            }
        }

        voiceRoomManager?.prepareStore
            ?.updateMessageCount(barrageStreamView.getBarrageCount())
        if (isOwner()) {
            initAnchorEndView()
        } else {
            initAudienceEndView()
        }
    }

    private fun setCoHostViewAdapter() {
        seatGridView?.setCoHostViewAdapter(object : VoiceRoomDefine.CoHostViewAdapter {
            override fun createOccupiedSeatView(seatInfo: SeatInfo, isMyRoom: Boolean): View {
                val coHostView = CoHostView(context)
                coHostView.init(seatInfo)
                return coHostView
            }

            override fun createAvailableSeatView(seatInfo: SeatInfo): View {
                val view = LocalCoHostEmptyView(context)
                view.init(seatInfo, voiceRoomManager)
                return view
            }

            override fun createRemoteSeatPlaceholderView(seatInfo: SeatInfo): View {
                val view = RemoteCoHostEmptyView(context)
                view.init(seatInfo)
                return view
            }
        })
    }

    private fun hideKTVView() {
        karaokeControlView.visibility = GONE
        karaokeFloatingView.detachFromFloating()
    }

    private fun exit() {
        voiceRoomManager?.prepareStore
            ?.updateMessageCount(barrageStreamView.getBarrageCount())
        TUICore.notifyEvent(
            TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
            TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP,
            null
        )
        if (isOwner()) {
            liveListStore.endLive(object : StopLiveCompletionHandler {
                override fun onSuccess(statisticsData: TUILiveListManager.LiveStatisticsData) {
                    voiceRoomManager?.prepareStore
                        ?.updateStatistics(
                            audienceCount = statisticsData.totalViewers,
                            messageCount = statisticsData.totalMessageCount,
                            giftIncome = statisticsData.totalGiftCoins,
                            giftSenderCount = statisticsData.totalUniqueGiftSenders,
                            likeCount = statisticsData.totalLikesReceived
                        )
                    voiceRoomManager?.prepareStore
                        ?.updateLiveStatus(LiveStatus.DASHBOARD)
                }

                override fun onFailure(code: Int, desc: String) {
                    LOGGER.error(
                        "stopVoiceRoom onError:error:$code, errorCode:${code}, message:$desc"
                    )
                    if (rootViewContext is Activity) {
                        rootViewContext.finish()
                    }
                }
            })

        } else {
            liveListStore.leaveLive(null)
            if (rootViewContext is Activity) {
                rootViewContext.finish()
            }
        }
        karaokeFloatingView.release()
        karaokeControlView.release()
    }

    private fun onLiveStateChanged(status: LiveStatus) {
        when (status) {
            LiveStatus.PUSHING -> {
                handleLiveStarted()
                addEnterBarrage()
            }

            LiveStatus.PLAYING -> {
                handleLiveStarted()
            }

            LiveStatus.DASHBOARD -> {
                showEndView()
                hideKTVView()
            }

            LiveStatus.PREVIEWING -> {
                showPreviewView()
                hideKTVView()
            }

            else -> {}
        }
    }

    private fun handleLiveStarted() {
        showSeatGridView()
        initBarrageView()
        showMainView()
        removePreviewView()

        voiceRoomManager?.prepareStore?.setLayoutMetaData(
            voiceRoomManager?.prepareStore?.prepareState?.layoutType?.value
                ?: LayoutType.VOICE_ROOM
        )
        liveListStore.queryMetaData(
            listOf(KEY_LAYOUT_TYPE),
            object : MetaDataCompletionHandler {
                override fun onSuccess(metaData: HashMap<String, String>) {
                    val layoutType: String? =
                        if (true) metaData.get(KEY_LAYOUT_TYPE) else null
                    if (layoutType.isNullOrEmpty() || TextUtils.equals(
                            voiceRoomManager?.prepareStore?.prepareState?.layoutType?.value?.desc,
                            layoutType
                        )
                    ) {
                        onVoiceRoomLayoutChanged(voiceRoomManager?.prepareStore?.prepareState?.layoutType?.value!!)
                        return
                    }
                    if (TextUtils.equals(LayoutType.KTV_ROOM.desc, layoutType)) {
                        voiceRoomManager?.prepareStore?.updateLayoutType(LayoutType.KTV_ROOM)
                    } else {
                        voiceRoomManager?.prepareStore?.updateLayoutType(LayoutType.VOICE_ROOM)
                    }
                }

                override fun onFailure(code: Int, desc: String) {

                }
            })
    }

    private fun showSeatGridView() {
        seatGridContainer.removeAllViews()
        val layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        seatGridContainer.addView(seatGridView, layoutParams)
    }

    private fun onSeatInvitationReceived(hostUser: LiveUserInfo) {
        showInvitationDialog(hostUser)
    }

    private fun onSeatInvitationCanceled() {
        invitationDialog?.let { dialog ->
            if (dialog.isShowing()) {
                dialog.dismiss()
            }
        }
    }

    private fun onVoiceRoomLayoutChanged(layoutType: LayoutType) {
        val liveStatus = voiceRoomManager?.prepareStore?.prepareState?.liveStatus?.value
        if (liveStatus == LiveStatus.PUSHING || liveStatus == LiveStatus.PLAYING) {
            if (LayoutType.KTV_ROOM == layoutType) {
                karaokeControlView.visibility = VISIBLE
                karaokeFloatingView.detachFromFloating()
            } else {
                karaokeFloatingView.attachAsFloating(
                    layoutRoot,
                    KaraokeFloatingView.FloatingMode.RIGHT_HALF_MOVE
                )
                karaokeControlView.visibility = GONE
            }
        }
    }

    private fun showInvitationDialog(hostUser: LiveUserInfo) {
        val avatarView = AtomicAvatar(rootViewContext).apply {
            setContent(
                AtomicAvatar.AvatarContent.URL(
                    hostUser.avatarURL ?: "",
                    R.drawable.livekit_ic_avatar
                )
            )
        }

        val dialog = invitationDialog ?: AtomicAlertDialog(rootViewContext).also {
            invitationDialog = it
        }

        val inviterName =
            if (TextUtils.isEmpty(liveListStore.liveState.currentLive.value.liveOwner.userName)) {
                liveListStore.liveState.currentLive.value.liveOwner.userID
            } else {
                liveListStore.liveState.currentLive.value.liveOwner.userName
            }
        val title = rootViewContext.getString(
            R.string.common_voiceroom_receive_seat_invitation,
            inviterName
        )
        val rejectText: String = rootViewContext.getString(R.string.common_reject)
        val receiveText: String = rootViewContext.getString(R.string.common_receive)

        dialog.init {
            init(title, iconView = avatarView)
            countdownDuration = 10

            cancelButton(rejectText) {
                rejectSeatInvitation(hostUser.userID)
            }

            confirmButton(receiveText) {
                acceptSeatInvitation(hostUser.userID)
            }
        }
        dialog.show()
    }

    private fun acceptSeatInvitation(userId: String) {
        coGuestStore.acceptInvitation(userId, completionHandler {
            onError { code, _ ->
                ErrorLocalized.onError(code)
            }
        })
    }

    private fun rejectSeatInvitation(userId: String) {
        coGuestStore.rejectInvitation(userId, completionHandler {
            onError { code, _ ->

                ErrorLocalized.onError(code)
            }
        })
    }

    private fun displayComplete() {
        barrageStreamView.visibility = VISIBLE
        giftPlayView.visibility = VISIBLE
        topView.visibility = VISIBLE
        bottomMenuView.visibility = VISIBLE
    }

    private fun endDisplay() {
        barrageStreamView.visibility = GONE
        giftPlayView.visibility = GONE
        topView.visibility = GONE
        bottomMenuView.visibility = GONE
    }

    override fun onNotifyEvent(key: String, subKey: String, param: Map<String, Any>?) {
        if (EVENT_SUB_KEY_CLOSE_VOICE_ROOM == subKey) {
            if (isOwner()) {
                showExitConfirmDialog()
            } else if (!liveSeatStore.liveSeatState.seatList.value.none { it.userInfo.userID == TUIRoomEngine.getSelfInfo().userId }) {
                showExitSeatDialog()
            } else {
                exit()
            }
        }
    }

    private fun showExitSeatDialog() {
        val dialog = AtomicAlertDialog(context)
        val title = resources.getString(R.string.common_audience_end_link_tips)

        dialog.init {
            init(title)
            addItem(
                text = resources.getString(R.string.common_end_link),
                type = AtomicAlertDialog.TextColorPreset.RED,
            ) { dialog ->
                val liveInfo = liveListStore.liveState.currentLive.value
                if (liveInfo.seatMode == TakeSeatMode.FREE || isOwner()) {
                    liveSeatStore.leaveSeat(null)
                } else {
                    coGuestStore.disconnect(null)
                }
                dialog.dismiss()
            }
            addItem(
                text = getResources().getString(R.string.common_exit_live),
                type = AtomicAlertDialog.TextColorPreset.PRIMARY
            ) {
                exit()
                it.dismiss()
            }
            addItem(
                text = getResources().getString(R.string.common_cancel),
                type = AtomicAlertDialog.TextColorPreset.PRIMARY
            ) { dialog ->
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showExitConfirmDialog() {
        val isInBattle = battleStore?.battleState?.battleUsers?.value?.isEmpty() == false
        val isInConnection = coHostStore?.coHostState?.coHostStatus?.value == CoHostStatus.CONNECTED

        AtomicAlertDialog(rootViewContext).let { dialog ->
            anchorExitConfirmDialog = dialog

            if (isInBattle) {
                val title = context.getString(R.string.common_end_pk_tips)

                dialog.init {
                    init(title)

                    addItem(
                        text = context.getString(R.string.common_battle_end_pk),
                        type = AtomicAlertDialog.TextColorPreset.RED,
                    ) {
                        battleStore?.exitBattle(
                            battleStore?.battleState?.currentBattleInfo?.value?.battleID,
                            object : CompletionHandler {
                                override fun onSuccess() {
                                    it.dismiss()
                                }

                                override fun onFailure(code: Int, desc: String) {
                                    ErrorLocalized.onError(code)
                                    it.dismiss()
                                }
                            })
                    }

                    addItem(
                        text = context.getString(R.string.common_end_live),
                        type = AtomicAlertDialog.TextColorPreset.PRIMARY
                    ) {
                        exit()
                        it.dismiss()
                    }

                    addItem(
                        text = context.getString(R.string.common_cancel),
                        type = AtomicAlertDialog.TextColorPreset.PRIMARY
                    ) {
                        it.dismiss()
                    }
                }
            } else if (isInConnection) {
                val title = context.getString(R.string.common_end_connection_tips)

                dialog.init {
                    init(title)

                    addItem(
                        text = context.getString(R.string.common_end_connect),
                        type = AtomicAlertDialog.TextColorPreset.RED,
                    ) {
                        coHostStore?.exitHostConnection(object : CompletionHandler {
                            override fun onSuccess() {
                                it.dismiss()
                            }

                            override fun onFailure(code: Int, desc: String) {
                                it.dismiss()
                                ErrorLocalized.onError(code)
                            }
                        })
                    }

                    addItem(
                        text = rootViewContext.getString(R.string.common_end_live),
                        type = AtomicAlertDialog.TextColorPreset.PRIMARY
                    ) {
                        exit()
                        it.dismiss()
                    }

                    addItem(
                        text = context.getString(R.string.common_cancel),
                        type = AtomicAlertDialog.TextColorPreset.PRIMARY
                    ) {
                        it.dismiss()
                    }
                }
            } else {
                val title = context.getString(R.string.live_end_live_tips)
                val negativeText = rootViewContext.getString(R.string.common_cancel)
                val positiveText = rootViewContext.getString(R.string.common_end_live)

                dialog.init {
                    init(title)

                    cancelButton(
                        text = negativeText,
                        type = AtomicAlertDialog.TextColorPreset.GREY
                    ) {
                        it.dismiss()
                    }

                    confirmButton(
                        text = positiveText,
                        type = AtomicAlertDialog.TextColorPreset.RED
                    ) {
                        exit()
                    }
                }
            }

            dialog.show()
        }
    }

    enum class VoiceRoomViewStatus {
        START_DISPLAY,
        DISPLAY_COMPLETE,
        END_DISPLAY,
    }

    private fun isOwner(): Boolean {
        return voiceRoomManager?.prepareStore?.prepareState?.liveInfo?.value?.liveOwner?.userID == TUIRoomEngine.getSelfInfo().userId
    }

    private fun getAudienceList() {
        liveAudienceStore.fetchAudienceList(completionHandler {
            onError { code, desc ->
                LOGGER.error("getUserList,error:$code,message:$desc")
                ErrorLocalized.onError(code)
            }
        })
    }

    private val guestListener = object : GuestListener() {
        override fun onHostInvitationReceived(hostUser: LiveUserInfo) {
            onSeatInvitationReceived(hostUser)
        }

        override fun onHostInvitationCancelled(hostUser: LiveUserInfo) {
            onSeatInvitationCanceled()
        }

        override fun onKickedOffSeat(seatIndex: Int, hostUser: LiveUserInfo) {
            AtomicToast.show(
                context,
                context.getString(
                    R.string.common_voiceroom_kicked_out_of_seat
                ),
                AtomicToast.Style.INFO
            )
        }
    }

    private val seatGridViewObserver = object : SeatGridViewObserver() {
        override fun onSeatViewClicked(seatView: View, seatInfo: TUIRoomDefine.SeatInfo) {
            LOGGER.info("onSeatViewClicked userId:  ${seatInfo.userId} ")
            if (voiceRoomManager == null) {
                return
            }
            if (seatActionSheetGenerator == null) {
                seatActionSheetGenerator =
                    SeatActionSheetGenerator(context, voiceRoomManager!!)
            }
            val listMenuInfoList: List<ListMenuInfo> =
                seatActionSheetGenerator?.generate(seatInfo) ?: emptyList()
            if (listMenuInfoList.isEmpty()) {
                return
            }
            if (seatActionSheetDialog == null) {
                seatActionSheetDialog = SeatActionSheetDialog(context)
            }
            seatActionSheetDialog?.updateActionButton(listMenuInfoList)
            seatActionSheetDialog?.show()
        }
    }

    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            if (reason == LiveEndedReason.ENDED_BY_HOST && isOwner()) return
            AtomicToast.show(
                context,
                context.getString(R.string.common_room_destroy),
                AtomicToast.Style.INFO
            )
            voiceRoomManager?.prepareStore?.updateLiveStatus(LiveStatus.DASHBOARD)
            seatActionSheetGenerator?.destroy()
            if (seatActionSheetDialog != null) {
                seatActionSheetDialog?.dismiss()
            }
        }

        override fun onKickedOutOfLive(
            liveID: String,
            reason: LiveKickedOutReason,
            message: String,
        ) {
            LOGGER.info("onKickedOutOfRoom:[roomId:${this@VoiceRoomRootView.liveID},reason:$reason,message:$message]")
            if (LiveKickedOutReason.BY_LOGGED_ON_OTHER_DEVICE !== reason) {
                AtomicToast.show(
                    context,
                    context.getString(R.string.common_kicked_out_of_room_by_owner),
                    AtomicToast.Style.INFO
                )
                val params: HashMap<String, Any> = HashMap()
                params.put("roomId", this@VoiceRoomRootView.liveID)
                TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_FINISH_ACTIVITY, params)
            }
        }
    }

    private val liveSeatListener = object : LiveSeatListener() {
        override fun onLocalMicrophoneClosedByAdmin() {
            AtomicToast.show(context,context.getString(R.string.common_mute_audio_by_master), AtomicToast.Style.INFO)
        }

        override fun onLocalMicrophoneOpenedByAdmin(policy: DeviceControlPolicy) {
            AtomicToast.show(context,context.getString(R.string.common_un_mute_audio_by_master), AtomicToast.Style.INFO)
        }
    }

    private val roomEngineObserver = object : TUIRoomObserver() {
        override fun onKickedOffLine(message: String?) {
            AtomicToast.show(context, message ?: "", AtomicToast.Style.INFO)
            val params: MutableMap<String?, Any?> = java.util.HashMap<String?, Any?>()
            params.put("roomId", liveID)
            TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_FINISH_ACTIVITY, params)
        }
    }

    companion object {
        private val LOGGER = LiveKitLogger.getVoiceRoomLogger("VoiceRoomRootView")
        private const val KEY_LAYOUT_TYPE: String = "LayoutType"
    }
}