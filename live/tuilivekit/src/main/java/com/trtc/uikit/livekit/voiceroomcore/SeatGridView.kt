package com.trtc.uikit.livekit.voiceroomcore

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_SET_LAYOUT_MODE
import com.trtc.uikit.livekit.common.LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_SET_SEAT_VIEW_ADAPTER
import com.trtc.uikit.livekit.common.LIVEKIT_METRICS_PANEL_HIDE_SEAT_GRID_VIEW
import com.trtc.uikit.livekit.common.LIVEKIT_METRICS_PANEL_SHOW_SEAT_GRID_VIEW
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.convertToSeatInfo
import com.trtc.uikit.livekit.common.reportEventData
import com.trtc.uikit.livekit.voiceroom.interaction.battle.BattleInfoView
import com.trtc.uikit.livekit.voiceroomcore.impl.SeatGridLayout
import com.trtc.uikit.livekit.voiceroomcore.impl.SeatGridViewObserverManager
import com.trtc.uikit.livekit.voiceroomcore.impl.SeatInfoWrapper
import com.trtc.uikit.livekit.voiceroomcore.impl.SeatLayoutConfigManager
import com.trtc.uikit.livekit.voiceroomcore.view.SeatInfoView
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.init
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.BattleConfig
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleListener
import io.trtc.tuikit.atomicxcore.api.live.BattleRequestCallback
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostListener
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.HostListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.NoResponseReason
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class SeatGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), LifecycleOwner {
    private var extensionInfo: String = "needRequestBattle"
    private val connectionUserCountPreRoom = 6
    private val BATTLE_DURATION: Int = 30
    private val seatGridLayout = SeatGridLayout(context)
    private var seatViewAdapter: VoiceRoomDefine.SeatViewAdapter? = null
    private var observerManager = SeatGridViewObserverManager()
    private val seatLayoutConfigManager = SeatLayoutConfigManager()
    private val deviceStore: DeviceStore
    private val liveListStore: LiveListStore = LiveListStore.shared()
    private var coGuestStore: CoGuestStore? = null
    private var seatStore: LiveSeatStore? = null
    private var coHostStore: CoHostStore? = null
    private var battleStore: BattleStore? = null
    private var liveAudienceStore: LiveAudienceStore? = null
    private var battleInvitationDialog: AtomicAlertDialog? = null
    private var connectionInvitationDialog: AtomicAlertDialog? = null
    private var coHostContainerView: ConstraintLayout? = null
    private var coHostViewAdapter: VoiceRoomDefine.CoHostViewAdapter? = null
    private val isCoHostingState = MutableStateFlow(false)
    private var battleInfoView: BattleInfoView? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val collectJobs = mutableListOf<Job>()

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    init {
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        deviceStore = DeviceStore.shared()
    }

    fun setLayoutMode(
        layoutMode: VoiceRoomDefine.LayoutMode,
        layoutConfig: VoiceRoomDefine.SeatViewLayoutConfig?,
    ) {
        LOGGER.info(
            "API setLayoutMode layoutMode: layoutMode $layoutMode: + ${
                Gson().toJson(
                    layoutConfig
                )
            }"
        )
        reportEventData(LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_SET_LAYOUT_MODE)
        seatLayoutConfigManager.setLayoutMode(layoutMode, layoutConfig)
        val seatSize = seatLayoutConfigManager.seatList.size
        if (seatSize > 0) {
            initSeatGridLayout(seatSize)
        }
    }

    fun setCoHostViewAdapter(adapter: VoiceRoomDefine.CoHostViewAdapter?) {
        coHostViewAdapter = adapter
    }

    fun setSeatViewAdapter(adapter: VoiceRoomDefine.SeatViewAdapter?) {
        LOGGER.info("API setSeatViewAdapter adapter:$adapter")
        reportEventData(LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_SET_SEAT_VIEW_ADAPTER)
        seatViewAdapter = adapter
        initSeatGridLayout(seatLayoutConfigManager.seatList.size)
    }

    fun addObserver(observer: SeatGridViewObserver) {
        observerManager.addObserver(observer)
    }

    fun removeObserver(observer: SeatGridViewObserver) {
        observerManager.removeObserver(observer)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reportEventData(LIVEKIT_METRICS_PANEL_SHOW_SEAT_GRID_VIEW)
        addView(seatGridLayout)
        val currentLive = liveListStore.liveState.currentLive.value
        init(currentLive.liveID)
        seatLayoutConfigManager.initSeatList(currentLive.maxSeatCount)
        initSeatGridLayout(currentLive.maxSeatCount)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unInit()
        reportEventData(LIVEKIT_METRICS_PANEL_HIDE_SEAT_GRID_VIEW)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    private fun init(liveId: String) {
        coGuestStore = CoGuestStore.create(liveId)
        seatStore = LiveSeatStore.create(liveId)
        coHostStore = CoHostStore.create(liveId)
        battleStore = BattleStore.create(liveId)
        liveAudienceStore = LiveAudienceStore.create(liveId)
        seatLayoutConfigManager.setOnItemUpdateListener(onItemUpdateListener)
        coHostStore?.addCoHostListener(mCoHostListener)
        battleStore?.addBattleListener(mBattleListener)
        coGuestStore?.addHostListener(mHostListener)
        observeSeatList()
        observeMaxSeatCount()
        observeCoHostConnection()
        observeFinalCoHostState()
        observeSpeakingUsers()
    }

    private fun unInit() {
        seatLayoutConfigManager.setOnItemUpdateListener(null)
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        coHostStore?.removeCoHostListener(mCoHostListener);
        battleStore?.removeBattleListener(mBattleListener);
        connectionInvitationDialog?.dismiss()
        battleInvitationDialog?.dismiss()
        coHostStore = null
        battleStore = null
        coGuestStore = null
        seatStore = null
    }

    private fun observeSeatList() {
        val job = lifecycleScope.launch {
            seatStore?.liveSeatState?.seatList?.collect { seatList ->
                seatLayoutConfigManager.updateSeatList(seatList)
                if (isCoHostingState.value) {
                    createCoHostView(seatList)
                }
            }
        }
        collectJobs.add(job)
    }

    private fun observeMaxSeatCount() {
        val job = lifecycleScope.launch {
            liveListStore.liveState.currentLive.map {
                it.maxSeatCount
            }.distinctUntilChanged().collect { seatCount ->
                if (seatCount <= 0)return@collect
                seatLayoutConfigManager.initSeatList(seatCount)
                initSeatGridLayout(seatCount)
            }
        }
        collectJobs.add(job)
    }

    private fun observeCoHostConnection() {
        val job = lifecycleScope.launch {
            coHostStore?.coHostState?.connected?.collect { connectedRoomList ->
                val currentLiveId = liveListStore.liveState.currentLive.value.liveID
                if (currentLiveId.isEmpty()) return@collect
                val isConnected = connectedRoomList.any { it.liveID == currentLiveId }
                isCoHostingState.value = isConnected
            }
        }
        collectJobs.add(job)
    }

    private fun observeFinalCoHostState() {
        val job = lifecycleScope.launch {
            isCoHostingState.collect { isInCoHost ->
                if (isInCoHost) {
                    val currentSeatList = seatStore?.liveSeatState?.seatList?.value ?: emptyList()
                    if (isRoomOwner()) kickUsersFromSeatsAfterLimit()
                    createCoHostView(currentSeatList)
                    seatGridLayout.visibility = GONE
                    coHostContainerView?.visibility = VISIBLE
                } else {
                    coHostContainerView?.let {
                        removeView(it)
                        coHostContainerView = null
                    }
                    seatGridLayout.visibility = VISIBLE
                    coHostContainerView?.visibility = GONE
                }
            }
        }
        collectJobs.add(job)
    }

    private fun isRoomOwner(): Boolean {
        return LiveListStore.shared().liveState.currentLive.value.liveOwner.userID == TUIRoomEngine.getSelfInfo().userId
    }

    private fun kickUsersFromSeatsAfterLimit() {
        val seatList = seatStore?.liveSeatState?.seatList?.value
        if (seatList.isNullOrEmpty() || seatList.size != 20) {
            return
        }
        val currentRoomId = liveListStore.liveState.currentLive.value.liveID

        val myRoomSeats = if (seatList.subList(0, 10)
                .any { it.userInfo.liveID == currentRoomId && it.userInfo.userID.isNotEmpty() }
        ) {
            seatList.subList(0, 10)
        } else {
            seatList.subList(10, 20)
        }

        val targetSeats = myRoomSeats.takeLast(4)
        val usersToKick = targetSeats.filter { it.userInfo.userID.isNotEmpty() }

        if (usersToKick.isNotEmpty()) {
            usersToKick.forEach { seatInfo ->
                seatStore?.kickUserOutOfSeat(seatInfo.userInfo.userID, object : CompletionHandler {
                    override fun onSuccess() {
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }

                })
            }
            AtomicToast.show(
                context,
                context.getString(R.string.common_host_kick_user_after_connect),
                duration = AtomicToast.Duration.LONG,
                style = AtomicToast.Style.INFO
            )
        }
    }

    @SuppressLint("ResourceType")
    private fun createCoHostView(seatList: List<SeatInfo>) {
        cleanupOldView()
        val layoutConfig = calculateLayoutConfig()
        val seatData = prepareSeatData(seatList) ?: return
        val (container, columns) = createContainerAndColumns(layoutConfig.columnCount)

        populateMyRoomSeats(seatData.myRoomSeats, columns, layoutConfig)
        populateOtherRoomSeats(seatData.otherRoomSeats, columns, layoutConfig)

        applyConstraints(container, columns, layoutConfig)

        battleInfoView?.bringToFront()
    }

    private fun cleanupOldView() {
        coHostContainerView?.let { removeView(it) }
        coHostContainerView = null
    }

    private data class LayoutConfig(
        val columnCount: Int,
        val seatSideLength: Int,
        val rowSpacing: Int,
    )

    private fun calculateLayoutConfig(): LayoutConfig {
        val columnCount = 4
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val seatSideLength = screenWidth / columnCount
        val rowSpacing = 0
        return LayoutConfig(columnCount, seatSideLength, rowSpacing)
    }

    private data class SeatData(
        val myRoomSeats: List<SeatInfo>,
        val otherRoomSeats: List<SeatInfo>,
    )

    private fun prepareSeatData(seatList: List<SeatInfo>): SeatData? {
        val myLiveId = liveListStore.liveState.currentLive.value.liveID
        if (myLiveId.isEmpty()) {
            return null
        }
        val (myRoomSeats, otherRoomSeats) = seatList.partition { it.userInfo.liveID == myLiveId }
        return SeatData(
            myRoomSeats.take(connectionUserCountPreRoom),
            otherRoomSeats.take(connectionUserCountPreRoom)
        )
    }

    private fun createContainerAndColumns(columnCount: Int): Pair<ConstraintLayout, List<LinearLayout>> {
        val container = ConstraintLayout(context).apply {
            id = generateViewId()
        }
        coHostContainerView = container
        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val columns = List(columnCount) {
            LinearLayout(context).apply {
                id = generateViewId()
                orientation = LinearLayout.VERTICAL
            }
        }
        columns.forEach { container.addView(it) }
        return Pair(container, columns)
    }

    private fun populateMyRoomSeats(
        seats: List<SeatInfo>,
        columns: List<LinearLayout>,
        config: LayoutConfig,
    ) {
        seats.forEachIndexed { index, seatInfo ->
            val view = if (seatInfo.userInfo.userID.isNullOrEmpty()) {
                coHostViewAdapter?.createAvailableSeatView(seatInfo)
            } else {
                coHostViewAdapter?.createOccupiedSeatView(seatInfo, true)
            }
            view?.let {
                val columnIndex = index % 2
                addSeatViewToColumn(it, columns[columnIndex], config)
            }
        }
    }

    private fun populateOtherRoomSeats(
        seats: List<SeatInfo>,
        columns: List<LinearLayout>,
        config: LayoutConfig,
    ) {
        seats.forEachIndexed { index, seatInfo ->
            val view = if (seatInfo.userInfo.userID.isNullOrEmpty()) {
                coHostViewAdapter?.createRemoteSeatPlaceholderView(seatInfo)
            } else {
                coHostViewAdapter?.createOccupiedSeatView(seatInfo, false)
            }
            view?.let {
                val columnIndex = 2 + (index % 2)
                addSeatViewToColumn(it, columns[columnIndex], config)
            }
        }
    }

    private fun addSeatViewToColumn(view: View, column: LinearLayout, config: LayoutConfig) {
        val params = LinearLayout.LayoutParams(config.seatSideLength, config.seatSideLength).apply {
            if (column.childCount > 0) {
                topMargin = config.rowSpacing
            }
        }
        column.addView(view, params)
    }

    private fun applyConstraints(
        container: ConstraintLayout,
        columns: List<LinearLayout>,
        config: LayoutConfig,
    ) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(container)

        val columnIds = columns.map { it.id }.toIntArray()
        constraintSet.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            columnIds,
            null,
            ConstraintSet.CHAIN_SPREAD_INSIDE
        )

        columns.forEach {
            constraintSet.connect(
                it.id,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP
            )
            constraintSet.constrainWidth(it.id, config.seatSideLength)
            constraintSet.constrainHeight(it.id, ConstraintSet.WRAP_CONTENT)
        }
        constraintSet.applyTo(container)
    }

    private fun observeSpeakingUsers() {
        val job = lifecycleScope.launch {
            seatStore?.liveSeatState?.speakingUsers?.collect { speakingUsers ->
                speakingUsers.forEach { (userId, volume) ->
                    onUserVolumeChanged(userId, volume)
                }
            }
        }
        collectJobs.add(job)
    }

    private fun initSeatGridLayout(maxSeatCount: Int) {
        seatGridLayout.clearAllViews()
        val config = seatLayoutConfigManager.layoutConfig ?: return
        if (maxSeatCount > 0) {
            seatGridLayout.layout(config, maxSeatCount, seatGridLayoutAdapter)
        }
    }

    private fun onUserVolumeChanged(userId: String, volume: Int) {
        val seatInfoWrapper = seatLayoutConfigManager.seatUserMap[userId]
        val seatGridView = this@SeatGridView
        seatInfoWrapper?.let { seat ->
            seatGridLayout.getSeatView(seat.rowIndex, seat.columnIndex)?.let { seatView ->
                seatViewAdapter?.updateUserVolume(seatGridView, volume, seatView)
                ?: (seatView as? SeatInfoView)?.updateUserVolume(seat.seatInfo, volume)
            }
        }
    }

    private val seatGridLayoutAdapter = object : SeatGridLayout.Adapter {
        override fun createView(index: Int): View {
            val seatInfo = seatLayoutConfigManager.seatList[index].seatInfo
            val convertSeatInfo = seatInfo?.let {
                convertToSeatInfo(it)
            } ?: run {
                TUIRoomDefine.SeatInfo()
            }
            return seatViewAdapter?.createSeatView(this@SeatGridView, convertSeatInfo)
                   ?: SeatInfoView(context, observerManager, seatInfo)
        }
    }

    private val onItemUpdateListener = object : SeatLayoutConfigManager.OnItemUpdateListener {
        override fun onItemUpdate(seat: SeatInfoWrapper) {
            val seatGridView = this@SeatGridView
            seat.seatInfo?.let {
                val tuiSeatInfo = convertToSeatInfo(it)
                seatGridLayout.getSeatView(seat.rowIndex, seat.columnIndex)?.let { seatView ->
                    seatViewAdapter?.updateSeatView(seatGridView, tuiSeatInfo, seatView)
                    ?: (seatView as? SeatInfoView)?.updateSeatView(seat.seatInfo)
                }
            }
        }
    }

    private val mCoHostListener: CoHostListener = object : CoHostListener() {
        override fun onCoHostRequestReceived(inviter: SeatUserInfo, extensionInfo: String) {
            if (TextUtils.equals(extensionInfo, this@SeatGridView.extensionInfo)) {
                val content = getContext().getString(
                    R.string.common_battle_inviting,
                    inviter.userName
                )
                showBattleInviteDialog(content, inviter, extensionInfo)
            } else {
                val content = getContext().getString(
                    R.string.common_connect_inviting_append,
                    inviter.userName
                )
                showConnectionInviteDialog(content, inviter, extensionInfo)
            }
        }

        override fun onCoHostRequestCancelled(
            inviter: SeatUserInfo,
            invitee: SeatUserInfo?,
        ) {
            connectionInvitationDialog?.dismiss()
            battleInvitationDialog?.dismiss()
            val content = getContext().getString(
                R.string.live_cancel_request,
                inviter.userName
            )
            AtomicToast.show(context, content, AtomicToast.Style.INFO)
        }

        override fun onCoHostRequestAccepted(invitee: SeatUserInfo) {

        }

        override fun onCoHostRequestRejected(invitee: SeatUserInfo) {
            var content = getContext().getString(
                R.string.common_request_rejected,
                invitee.userName
            )
            AtomicToast.show(context, content, AtomicToast.Style.INFO)
        }

        override fun onCoHostRequestTimeout(inviter: SeatUserInfo, invitee: SeatUserInfo) {
            connectionInvitationDialog?.dismiss()
            battleInvitationDialog?.dismiss()
            if (TextUtils.equals(inviter.userID, TUIRoomEngine.getSelfInfo().userId)) {
                AtomicToast.show(
                    context,
                    context.getString(R.string.common_connect_invitation_timeout),
                    AtomicToast.Style.INFO
                )
            }
        }

        override fun onCoHostUserJoined(userInfo: SeatUserInfo) {

        }

        override fun onCoHostUserLeft(userInfo: SeatUserInfo) {
        }
    }

    private val mHostListener: HostListener = object : HostListener() {
        override fun onHostInvitationResponded(isAccept: Boolean, guestUser: LiveUserInfo) {
            if (!isAccept) {
                val userName =
                    liveAudienceStore?.liveAudienceState?.audienceList?.value?.find { it.userID == guestUser.userID }?.userName
                val content = context.getString(
                    R.string.common_request_rejected,
                    if (userName.isNullOrEmpty()) guestUser.userID else userName
                )
                AtomicToast.show(context, content, AtomicToast.Style.INFO)
            }
        }

        override fun onHostInvitationNoResponse(
            guestUser: LiveUserInfo,
            reason: NoResponseReason,
        ) {
            if (liveListStore.liveState.currentLive.value.liveOwner.userID == TUIRoomEngine.getSelfInfo().userId) {
                AtomicToast.show(
                    context,
                    context.getString(R.string.common_connect_invitation_timeout),
                    AtomicToast.Style.INFO
                )
            }
        }
    }

    private val mBattleListener: BattleListener = object : BattleListener() {
        override fun onBattleStarted(
            battleInfo: BattleInfo,
            inviter: SeatUserInfo,
            invitees: List<SeatUserInfo>,
        ) {
            if (battleInfoView != null) {
                return
            }
            battleInfoView = BattleInfoView(context).apply {
                init(liveId = liveListStore.liveState.currentLive.value.liveID)
                setBattleEndListener { view ->
                    this@SeatGridView.removeView(view)
                    if (battleInfoView == view) {
                        battleInfoView = null
                    }
                }
            }
            val layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
            }
            this@SeatGridView.addView(battleInfoView, layoutParams)
        }

        override fun onBattleRequestReceived(
            battleId: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            val content = context.getString(
                R.string.common_battle_inviting,
                inviter.userName
            )
            showDirectPKDialog(content, inviter.avatarURL, battleId)
        }

        override fun onBattleRequestCancelled(
            battleId: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            battleInvitationDialog?.dismiss()
            val content = context.getString(
                R.string.common_battle_inviter_cancel,
                inviter.userName
            )
            AtomicToast.show(context, content, AtomicToast.Style.INFO)
        }

        override fun onBattleRequestTimeout(
            battleId: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            if (TextUtils.equals(inviter.userID, TUIRoomEngine.getSelfInfo().userId)) {
                AtomicToast.show(
                    context,
                    context.getString(R.string.common_connect_invitation_timeout),
                    AtomicToast.Style.INFO
                )
            }
        }

        override fun onBattleRequestReject(
            battleId: String,
            inviter: SeatUserInfo,
            invitee: SeatUserInfo,
        ) {
            val content = context.getString(
                R.string.common_battle_invitee_reject,
                invitee.userName
            )
            AtomicToast.show(context, content, AtomicToast.Style.INFO)
        }
    }

    private fun showBattleInviteDialog(
        content: String?,
        inviter: SeatUserInfo,
        extensionInfo: String,
    ) {
        val avatarView = AtomicAvatar(context).apply {
            setContent(
                AtomicAvatar.AvatarContent.URL(
                    inviter.avatarURL ?: "",
                    R.drawable.livekit_ic_avatar
                )
            )
        }

        connectionInvitationDialog?.dismiss()
        val dialog = AtomicAlertDialog(context).also {
            connectionInvitationDialog = it
        }

        dialog.init {
            init(
                title = content ?: "",
                iconView = avatarView,
            )
            countdownDuration = 10
            val rejectText: String = context.getString(R.string.common_reject)
            cancelButton(rejectText, AtomicAlertDialog.TextColorPreset.GREY, isBold = false) {
                coHostStore?.rejectHostConnection(inviter.liveID, object : CompletionHandler {
                    override fun onSuccess() {
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }
                })
            }

            val receiveText: String? = context.getString(R.string.common_receive)
            confirmButton(
                receiveText ?: "",
                AtomicAlertDialog.TextColorPreset.BLUE,
                isBold = true
            ) {
                if (isBackSeatsOccupied()) {
                    AtomicToast.show(
                        context,
                        context.getString(R.string.common_back_seats_occupied),
                        AtomicToast.Style.WARNING
                    )
                    coHostStore?.rejectHostConnection(inviter.liveID, object : CompletionHandler {
                        override fun onSuccess() {}
                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.onError(code)
                        }
                    })
                    return@confirmButton
                }
                coHostStore?.acceptHostConnection(inviter.liveID, object : CompletionHandler {
                    override fun onSuccess() {
                        if (TextUtils.equals(extensionInfo, this@SeatGridView.extensionInfo)) {
                            val needResponse = false
                            val extensionInfo = this@SeatGridView.extensionInfo
                            val config = BattleConfig(BATTLE_DURATION, needResponse, extensionInfo)

                            val list = mutableListOf<String>()
                            list.add(inviter.userID)
                            battleStore?.requestBattle(
                                config,
                                list,
                                0,
                                object : BattleRequestCallback {
                                    override fun onSuccess(
                                        battleInfo: BattleInfo,
                                        resultMap: Map<String, Int>,
                                    ) {

                                    }

                                    override fun onError(code: Int, desc: String) {
                                        ErrorLocalized.onError(code)
                                    }
                                }
                            )
                        } else {

                        }
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }
                })
            }
        }
        dialog.show()
    }

    private fun showConnectionInviteDialog(
        content: String?,
        inviter: SeatUserInfo,
        extensionInfo: String,
    ) {
        val avatarView = AtomicAvatar(context).apply {
            setContent(
                AtomicAvatar.AvatarContent.URL(
                    inviter.avatarURL ?: "",
                    R.drawable.livekit_ic_avatar
                )
            )
        }

        connectionInvitationDialog?.dismiss()
        val dialog = AtomicAlertDialog(context).also {
            connectionInvitationDialog = it
        }

        dialog.init {
            init(
                title = content ?: "",
                iconView = avatarView,
            )
            countdownDuration = 10
            val rejectText: String = context.getString(R.string.common_reject)
            cancelButton(rejectText, AtomicAlertDialog.TextColorPreset.GREY, isBold = false) {
                coHostStore?.rejectHostConnection(inviter.liveID, object : CompletionHandler {
                    override fun onSuccess() {
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }
                })
            }

            val receiveText: String? = context.getString(R.string.common_receive)
            confirmButton(
                receiveText ?: "",
                AtomicAlertDialog.TextColorPreset.BLUE,
                isBold = true
            ) {
                if (isBackSeatsOccupied()) {
                    AtomicToast.show(
                        context,
                        context.getString(R.string.common_back_seats_occupied),
                        AtomicToast.Style.WARNING
                    )
                    coHostStore?.rejectHostConnection(inviter.liveID, object : CompletionHandler {
                        override fun onSuccess() {}
                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.onError(code)
                        }
                    })
                    return@confirmButton
                }
                coHostStore?.acceptHostConnection(inviter.liveID, object : CompletionHandler {
                    override fun onSuccess() {
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }
                })
            }
        }
        dialog.show()
    }

    private fun isBackSeatsOccupied(): Boolean {
        val seatList = seatStore?.liveSeatState?.seatList?.value ?: return false
        return seatList.any { it.index >= 6 && it.userInfo.userID.isNotEmpty() }
    }

    private fun showDirectPKDialog(content: String?, avatarUrl: String?, battleId: String?) {
        val avatarView = AtomicAvatar(context).apply {
            setContent(
                AtomicAvatar.AvatarContent.URL(
                    avatarUrl ?: "",
                    R.drawable.livekit_ic_avatar
                )
            )
        }

        battleInvitationDialog?.dismiss()
        val dialog = AtomicAlertDialog(context).also {
            battleInvitationDialog = it
        }

        dialog.init {
            init(
                title = content ?: "",
                iconView = avatarView,
            )
            countdownDuration = 10
            val rejectText: String = context.getString(R.string.common_reject)
            cancelButton(rejectText, AtomicAlertDialog.TextColorPreset.GREY, isBold = false) {
                battleStore?.rejectBattle(battleId, object : CompletionHandler {
                    override fun onSuccess() {
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }
                })
            }

            val receiveText: String? = context.getString(R.string.common_receive)
            confirmButton(
                receiveText ?: "",
                AtomicAlertDialog.TextColorPreset.BLUE,
                isBold = true
            ) {
                battleStore?.acceptBattle(battleId, object : CompletionHandler {
                    override fun onSuccess() {
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }
                })
            }
        }
        dialog.show()
    }

    companion object {
        private val LOGGER = LiveKitLogger.Companion.getComponentLogger("SeatGridView")
    }
}