package com.trtc.uikit.livekit.voicelounge

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveScreenAwake
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.common.theme.LiveForegroundService
import com.trtc.uikit.livekit.common.theme.LiveDevicePermission
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLiveClock
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLivingActions
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLivingView
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.living.aitranscriber.VoiceLoungeAITranscriberStore
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestStore
import com.trtc.uikit.livekit.voicelounge.living.confirm.VoiceLoungeAlertView
import com.trtc.uikit.livekit.voicelounge.living.confirm.VoiceLoungeEndPanel
import com.trtc.uikit.livekit.voicelounge.living.giftrank.VoiceLoungeGiftRankStore
import com.trtc.uikit.livekit.voicelounge.living.pause.VoiceLoungePauseView
import com.trtc.uikit.livekit.voicelounge.living.seat.VoiceLoungeMuteCache
import com.trtc.uikit.livekit.voicelounge.living.seat.VoiceLoungeSeatStore
import com.trtc.uikit.livekit.voicelounge.prepare.VoiceLoungePrepareView
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeConfig
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSettings
import com.trtc.uikit.livekit.voicelounge.summary.VoiceLoungeSummaryData
import com.trtc.uikit.livekit.voicelounge.summary.VoiceLoungeSummaryView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.AtomicPlayer
import io.trtc.tuikit.atomicxcore.api.live.AtomicPusher
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.LikeStore
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatListener
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.PlayerListener
import io.trtc.tuikit.atomicxcore.api.live.MetaDataCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.PusherConnectState
import io.trtc.tuikit.atomicxcore.api.live.PusherInfo
import io.trtc.tuikit.atomicxcore.api.live.PusherListener
import io.trtc.tuikit.atomicxcore.api.live.SeatMode
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VoiceLoungeView(context: Context, mode: Mode) : FrameLayout(context) {
    sealed class Mode {
        abstract val liveID: String

        data class Create(override val liveID: String) : Mode()
        data class Join(override val liveID: String) : Mode()
    }

    var onClose: (() -> Unit)? = null

    var onSwitchLive: ((LiveInfo) -> Unit)? = null

    var prepareAccessoryView: View?
        get() = if (isCreating) prepareView.accessoryView else null
        set(value) {
            if (isCreating && stage == Stage.PREPARE && !isReleased) prepareView.accessoryView = value
        }

    var prepareTitle: String
        get() = if (isCreating) prepareView.title else ""
        set(value) {
            if (isCreating && stage == Stage.PREPARE && !isReleased) prepareView.title = value
        }


    private val liveID = mode.liveID
    private val role = if (mode is Mode.Create) VoiceLoungeRole.HOST else VoiceLoungeRole.AUDIENCE
    private val isCreating get() = role == VoiceLoungeRole.HOST

    private val roomSettings = VoiceLoungeSettings()
    private val livingActions = VoiceLoungeLivingActions()
    private val giftRankStore = VoiceLoungeGiftRankStore()
    private val coGuest = VoiceLoungeCoGuestStore()
    private val transcriberStore = VoiceLoungeAITranscriberStore(roomSettings)
    private val muteCache = VoiceLoungeMuteCache()
    private val seatUIStore = VoiceLoungeSeatStore(liveID, role, coGuest, muteCache, roomSettings)
    private var seatStore: LiveSeatStore? = null
    private val liveClock = VoiceLoungeLiveClock()
    private val pauseView = VoiceLoungePauseView(context, role, liveClock)
    private val livingView = VoiceLoungeLivingView(
        context, liveID, seatUIStore, giftRankStore, liveClock, livingActions, transcriberStore,
    )
    private var sessionScope: CoroutineScope? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var hostUserID = ""
    private var hostName = ""
    private var hostAvatarURL = ""

    private enum class Stage { PREPARE, LIVING, SUMMARY }

    private var stage = Stage.PREPARE
    private val prepareView by lazy { VoiceLoungePrepareView(context) }
    private val endPanel by lazy { VoiceLoungeEndPanel(context) }
    private val summaryView by lazy { VoiceLoungeSummaryView(context) }
    private val audienceEndView by lazy {
        com.trtc.uikit.livekit.voicelounge.living.end.VoiceLoungeAudienceEndView(context).apply {
            onBack = { onClose?.invoke() }
            onSwipeUp = { switchToNextLive() }
        }
    }
    private var isFetchingNextLive = false
    private val alertView by lazy { VoiceLoungeAlertView(context) }

    private var pusherConfig: PusherInfo? = null
    private var pusherListener: PusherListener? = null
    private var hasConfiguredSeatMode = false
    private var endSummary: PusherListener.PusherSummary? = null
    private var totalViewerCount = 1
    private var isEndingLive = false
    private var isMuted = false
    private var isPaused = false
    private var wasMutedBeforePause = false
    private var ownsAudioService = false

    private val player by lazy { AtomicPlayer() }
    private var isLiving = false
    private var hasJoinedLive = false
    private var isPlaying = false
    private var sessionToken = 0
    private var seatListener: LiveSeatListener? = null
    private var liveListListener: LiveListListener? = null
    private var playerListener: PlayerListener? = null

    private var isReleased = false
    private val screenAwake = LiveScreenAwake(this)

    init {
        setBackgroundColor(Color.BLACK)
        stackedViews().forEach { addView(it, frameParams(MATCH, MATCH)) }
        if (isCreating) {
            livingView.visibility = View.GONE
            summaryView.visibility = View.GONE
        }
        coGuest.onToast = { LiveToast.show(context, it) }
        if (isCreating) bindCreateSession() else bindJoinSession()
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            pauseView.bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            insets
        }
        configureMode()
    }

    fun handleBackPressed(): Boolean {
        if (isCreating && alertView.visibility == View.VISIBLE) {
            alertView.dismiss()
            return true
        }
        if (livingView.handleBackPressed()) return true
        if (!isCreating) {
            leaveRoom()
            return true
        }
        when (stage) {
            Stage.PREPARE -> onClose?.invoke()
            Stage.LIVING -> livingView.onClose?.invoke()
            Stage.SUMMARY -> closeSummary()
        }
        return true
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        if (isCreating) {
            if (stage == Stage.LIVING && !isEndingLive) AtomicPusher.shared().stopPush()
            releasePushSession()
        } else {
            isLiving = false
            releasePlaySession()
        }
        liveClock.stop()
        livingView.release()
        seatUIStore.release()
        transcriberStore.release()
        mainHandler.removeCallbacksAndMessages(null)
        updateScreenAwake()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
        updateScreenAwake()
    }

    override fun onDetachedFromWindow() {
        screenAwake.setEnabled(false)
        super.onDetachedFromWindow()
    }

    private fun updateScreenAwake() {
        val inRoom = if (isCreating) stage == Stage.LIVING else isLiving
        screenAwake.setEnabled(isAttachedToWindow && !isReleased && inRoom)
    }

    private fun stackedViews(): List<View> = if (isCreating) {
        listOf(prepareView, livingView, summaryView, pauseView, alertView)
    } else {
        listOf(livingView, pauseView, audienceEndView)
    }

    private fun configureMode() {
        if (liveID.isEmpty()) {
            exitForEmptyLiveID()
            return
        }
        if (isCreating) loadHostUserInfo() else joinLiving()
    }

    private val selfUserID: String get() = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()

    private fun exitForEmptyLiveID() {
        LOGGER.error("liveID is empty, role=${if (isCreating) "host" else "audience"}")
        mainHandler.post {
            LiveToast.show(context, context.getString(R.string.livekit_voiceroom_error_empty_live_id))
            onClose?.invoke()
        }
    }

    private fun syncHostInfo() {
        roomSettings.updateHost(hostName, hostAvatarURL, hostUserID)
        giftRankStore.updateHostUserID(hostUserID)
    }

    private fun activateInteractions(hostUserID: String) {
        giftRankStore.activate(liveID, role, hostUserID)
        transcriberStore.activate(liveID)
        livingView.activateInteractions(hostUserID)
    }

    private fun deactivateInteractions() {
        giftRankStore.deactivate()
        transcriberStore.deactivate()
        livingView.deactivateInteractions()
    }

    private fun newSessionScope(): CoroutineScope {
        sessionScope?.cancel()
        return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { sessionScope = it }
    }

    private fun cancelSessionScope() {
        sessionScope?.cancel()
        sessionScope = null
    }

    private fun bindCreateSession() {
        prepareView.onExit = { onClose?.invoke() }
        prepareView.onStart = { input -> enterLiving(input) }
        livingView.onClose = {
            val online = LiveAudienceStore.create(liveID).liveAudienceState.audienceCount.value
            endPanel.show(maxOf(0, online))
        }
        endPanel.onConfirmEnd = { confirmEndLive() }
        summaryView.onExit = { closeSummary() }
        bindLivingActions()
        giftRankStore.onPublish = { syncRoomMetaToAudience() }
        pauseView.onResume = { resumeLive() }
    }

    private fun loadHostUserInfo() {
        val profile = LoginStore.shared.loginState.loginUserInfo.value
        val nickname = profile?.nickname.orEmpty()
        hostName = nickname.ifEmpty { selfUserID }
        hostAvatarURL = profile?.avatarURL.orEmpty()
        hostUserID = selfUserID
        prepareView.updateHost(hostAvatarURL, hostName)
        syncHostInfo()
    }

    private fun syncHostMicOff() {
        seatUIStore.isHostMicOff = isMuted || isPaused
    }

    private fun enterLiving(input: VoiceLoungeConfig) {
        stage = Stage.LIVING
        updateScreenAwake()
        liveClock.start()
        endSummary = null
        isEndingLive = false
        roomSettings.config = input
        prepareView.deactivate()
        hostUserID = selfUserID
        syncHostInfo()
        syncHostMicOff()

        livingView.visibility = View.VISIBLE
        summaryView.visibility = View.GONE
        prepareView.visibility = View.GONE

        startPush(roomSettings.displayRoomName)
    }

    private fun confirmEndLive() {
        if (isEndingLive) return
        isEndingLive = true
        AtomicPusher.shared().stopPush()
    }

    private fun enterSummary() {
        if (stage != Stage.LIVING) return
        stage = Stage.SUMMARY
        updateScreenAwake()
        val summary = endSummary
        val data = if (summary != null) {
            VoiceLoungeSummaryData(
                startTimeMillis = liveClock.startTimeMillis,
                viewers = summary.totalViewers,
                giftCount = summary.totalGiftsSent,
                gifters = summary.totalUniqueGiftSenders,
                likes = summary.totalLikesReceived,
            )
        } else {
            VoiceLoungeSummaryData(
                startTimeMillis = liveClock.startTimeMillis,
                viewers = totalViewerCount,
                likes = LikeStore.create(liveID).likeState.totalLikeCount.value.coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt(),
            )
        }
        summaryView.bind(data)
        if (endPanel.isShowing) endPanel.dismiss()
        alertView.dismiss()
        summaryView.visibility = View.VISIBLE
        livingView.visibility = View.GONE
        stopAudioService()
    }

    private fun closeSummary() {
        stage = Stage.PREPARE
        updateScreenAwake()
        summaryView.visibility = View.GONE
        livingView.visibility = View.GONE
        prepareView.visibility = View.VISIBLE
        liveClock.stop()
        releasePushSession()
        onClose?.invoke()
    }

    private fun startPush(liveName: String) {
        subscribePusher()
        AtomicPusher.shared().setView(livingView.coreView)
        val config = PusherInfo().apply {
            this.liveID = this@VoiceLoungeView.liveID
            this.liveName = liveName
            isPublicVisible = true
            metaData = roomSettings.config.metaData
        }
        pusherConfig = config
        AtomicPusher.shared().startPush(config)
        seatStore = LiveSeatStore.create(liveID)
        seatUIStore.activate()
        seatUIStore.activateAudienceList()
        openHostMicrophone()
    }

    private fun openHostMicrophone() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            if (LiveDevicePermission.requestMicrophonePermission(context)) {
                mainHandler.post { openGrantedMicrophone() }
            }
        }
    }

    private fun openGrantedMicrophone() {
        if (stage != Stage.LIVING || isReleased) return
        if (DeviceStore.shared().deviceState.microphoneStatus.value == DeviceStatus.ON) {
            if (!isMuted) seatStore?.unmuteMicrophone(null)
            startAudioService()
            return
        }
        DeviceStore.shared().openLocalMicrophone(object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post {
                    if (stage != Stage.LIVING || isReleased) return@post
                    if (isMuted || isPaused) seatStore?.muteMicrophone()
                    startAudioService()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("openLocalMicrophone failed code=$code desc=$desc")
            }
        })
    }

    private fun startAudioService() {
        if (ownsAudioService) return
        ownsAudioService = true
        LiveForegroundService.start(context)
    }

    private fun stopAudioService() {
        if (!ownsAudioService) return
        ownsAudioService = false
        LiveForegroundService.stop(context)
    }

    private fun syncRoomMetaToAudience() {
        val config = pusherConfig ?: return
        config.metaData =
            roomSettings.config.metaData + giftRankStore.publishedMetaData() + (VoiceLoungeConfig.PAUSED_META_KEY to if (isPaused) "1" else "0")
        AtomicPusher.shared().setMetaData(config.metaData)
    }

    private fun subscribePusher() {
        clearPusherSubscriptions()
        val pusher = AtomicPusher.shared()
        val listener = object : PusherListener() {
            override fun liveEnded(reason: EndedReason, summary: PusherSummary?) {
                mainHandler.post {
                    if (pusherListener !== this) return@post
                    endSummary = summary
                    enterSummary()
                }
            }

            override fun kickedOut(reason: KickedOutReason, message: String) {
                mainHandler.post {
                    if (pusherListener !== this) return@post
                    endSummary = null
                    enterSummary()
                }
            }
        }
        pusherListener = listener
        pusher.addListener(listener)
        val scope = newSessionScope()
        scope.launch {
            pusher.state.totalViewerCount.collect { totalViewerCount = maxOf(0, it) }
        }
        scope.launch {
            pusher.state.networkState.collect { network ->
                if (network == PusherConnectState.CONNECTED) onPusherConnected()
            }
        }
    }

    private fun onPusherConnected() {
        val seatStore = seatStore ?: return
        if (hasConfiguredSeatMode || stage != Stage.LIVING) return
        hasConfiguredSeatMode = true
        coGuest.activate(liveID, role)
        seatUIStore.enableStaleCleanup()
        activateInteractions(selfUserID)
        seatStore.setSeatMode(SeatMode.APPLY, object : CompletionHandler {
            override fun onSuccess() = Unit

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("setSeatMode failed code=$code desc=$desc")
                mainHandler.post { ErrorLocalized.onError(code) }
            }
        })
    }

    private fun clearPusherSubscriptions() {
        cancelSessionScope()
        pusherListener?.let { AtomicPusher.shared().removeListener(it) }
        pusherListener = null
    }

    private fun releasePushSession() {
        clearPusherSubscriptions()
        coGuest.deactivate()
        deactivateInteractions()
        DeviceStore.shared().closeLocalMicrophone()
        stopAudioService()
        hasConfiguredSeatMode = false
        pusherConfig = null
        seatStore = null
        seatUIStore.deactivate()
        isPaused = false
        pauseView.setPaused(false)
        AtomicPusher.shared().release()
    }

    private fun bindLivingActions() {
        livingActions.isMuted = { isMuted }
        livingActions.pauseLive = { pauseLive() }
        livingActions.toggleMute = { handleLivingToggleMute() }
        livingActions.changeLayout = { layout ->
            if (layout == VoiceLoungeSeatLayout.DUO) confirmSwitchToDuo(layout) else applyLivingLayout(layout)
        }
        livingActions.changeTopic = { topic -> commitTopic(topic) }
        livingActions.toggleTranscriber = { isOn -> setTranscriberEnabled(isOn) }
        livingActions.confirm = { title, message, confirmTitle, action ->
            alertView.show(title, message, confirmTitle, roomSettings.skin, action)
        }
    }

    private fun setTranscriberEnabled(isOn: Boolean) {
        if (roomSettings.config.isTranscriberEnabled == isOn) return
        roomSettings.config = roomSettings.config.copy(isTranscriberEnabled = isOn)
        syncRoomMetaToAudience()
    }

    private fun pauseLive() {
        if (isPaused) return
        isPaused = true
        liveClock.pause()
        pauseView.setPaused(true)
        livingView.setHostPaused(true)
        wasMutedBeforePause = isMuted
        if (!isMuted) seatStore?.muteMicrophone()
        isMuted = true
        DeviceStore.shared().closeLocalMicrophone()
        syncHostMicOff()
        coGuest.broadcastPause(true)
        syncRoomMetaToAudience()
    }

    private fun resumeLive() {
        if (!isPaused) return
        liveClock.resume()
        isPaused = false
        pauseView.setPaused(false)
        livingView.setHostPaused(false)
        isMuted = wasMutedBeforePause
        openHostMicrophone()
        if (!wasMutedBeforePause) seatStore?.unmuteMicrophone(null)
        syncHostMicOff()
        coGuest.broadcastPause(false)
        syncRoomMetaToAudience()
    }

    private fun handleLivingToggleMute() {
        isMuted = !isMuted
        seatStore?.let { if (isMuted) it.muteMicrophone() else it.unmuteMicrophone(null) }
        syncHostMicOff()
    }

    private fun applyLivingLayout(layout: VoiceLoungeSeatLayout) {
        roomSettings.config = roomSettings.config.copy(seatLayout = layout)
        syncRoomMetaToAudience()
    }

    private fun confirmSwitchToDuo(layout: VoiceLoungeSeatLayout) {
        alertView.show(
            context.getString(R.string.livekit_voiceroom_layout_duo_confirm_title),
            context.getString(R.string.livekit_voiceroom_layout_duo_confirm_message),
            context.getString(R.string.livekit_voiceroom_layout_duo_confirm_ok),
            roomSettings.skin,
        ) { kickAllGuestsThenApply(layout) }
    }

    private fun kickAllGuestsThenApply(layout: VoiceLoungeSeatLayout) {
        seatStore?.let { store ->
            val selfID = selfUserID
            seatUIStore.seatList.map { it.userInfo.userID }.filter { it.isNotEmpty() && it != selfID }
                .forEach { store.kickUserOutOfSeat(it, null) }
        }
        applyLivingLayout(layout)
    }

    private fun commitTopic(topic: String) {
        val trimmed = topic.trim()
        if (trimmed == roomSettings.topic) return
        roomSettings.config = roomSettings.config.copy(topic = trimmed)
        syncRoomMetaToAudience()
        val text = if (trimmed.isEmpty()) {
            context.getString(R.string.livekit_voiceroom_topic_cleared)
        } else {
            context.getString(R.string.livekit_voiceroom_topic_changed, trimmed)
        }
        BarrageStore.create(liveID).sendTextMessage(text, null, object : CompletionHandler {
            override fun onSuccess() = Unit

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("send topic barrage failed liveID=$liveID code=$code message=$desc")
            }
        })
    }

    private fun bindJoinSession() {
        livingView.onClose = { leaveRoom() }
        pauseView.onResume = { leaveRoom() }
        coGuest.onHostPauseChanged = { paused -> applyHostPause(paused) }
        coGuest.onMicOpenRequested = handler@{
            if (coGuest.guestStatus != VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT) return@handler
            val selfID = selfUserID
            val info = seatUIStore.seatList.firstOrNull { it.userInfo.userID == selfID }?.userInfo
            if (info?.allowOpenMicrophone == false || info?.microphoneStatus == DeviceStatus.ON) return@handler
            seatStore?.unmuteMicrophone(null)
            LiveToast.show(context, context.getString(R.string.livekit_player_coguest_microphone_unlocked))
        }
    }

    private fun joinLiving() {
        isLiving = true
        updateScreenAwake()
        liveClock.start()
        roomSettings.config =
            VoiceLoungeConfig(roomName = context.getString(R.string.livekit_voiceroom_room_loading_name))
        syncHostInfo()
        startPlay()
        subscribeRoom()
    }

    private fun leaveRoom() {
        if (!isLiving) return
        isLiving = false
        updateScreenAwake()
        liveClock.stop()
        releasePlaySession()
        onClose?.invoke()
    }

    private fun startPlay() {
        player.setView(livingView.coreView)
        if (!isPlaying) {
            isPlaying = true
            player.startPlay("livekit://liveid=$liveID")
        }
        sessionToken += 1
        val token = sessionToken
        LiveListStore.shared().joinLive(liveID, object : LiveInfoCompletionHandler {
            override fun onSuccess(liveInfo: LiveInfo) {
                mainHandler.post {
                    if (token != sessionToken) return@post
                    hasJoinedLive = true
                    activateInteractions(liveInfo.liveOwner.userID.ifEmpty { hostUserID })
                    coGuest.activate(liveID, role)
                    seatUIStore.activateAudienceList()
                    applyLiveInfo(liveInfo)
                    queryRoomMeta()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("joinLive failed liveID=$liveID code=$code message=$desc")
                mainHandler.post {
                    if (token == sessionToken) ErrorLocalized.onError(code)
                }
            }
        })
        LiveListStore.shared().fetchLiveInfo(liveID, object : LiveInfoCompletionHandler {
            override fun onSuccess(liveInfo: LiveInfo) {
                mainHandler.post { if (token == sessionToken) applyLiveInfo(liveInfo) }
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("fetchLiveInfo failed liveID=$liveID code=$code message=$desc")
            }
        })
        seatStore = LiveSeatStore.create(liveID)
        seatUIStore.activate()
    }

    private fun subscribeRoom() {
        val scope = newSessionScope()
        scope.launch {
            LiveListStore.shared().liveState.currentLive.collect { info ->
                if (info.liveID.isNotEmpty() && info.liveID == liveID) applyLiveInfo(info)
            }
        }

        val seatEvents = object : LiveSeatListener() {
            override fun onLocalMicrophoneClosedByAdmin() {
                mainHandler.post {
                    LiveToast.show(context, context.getString(R.string.livekit_player_coguest_microphone_locked))
                    seatStore?.muteMicrophone()
                }
            }

            override fun onLocalMicrophoneOpenedByAdmin(policy: DeviceControlPolicy) {
                mainHandler.post {
                    LiveToast.show(context, context.getString(R.string.livekit_player_coguest_microphone_unlocked))
                    if (coGuest.guestStatus != VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT) return@post
                    seatStore?.unmuteMicrophone(null)
                }
            }
        }
        seatListener = seatEvents
        seatStore?.addLiveSeatEventListener(seatEvents)

        val listEvents = object : LiveListListener() {
            override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
                mainHandler.post {
                    if (liveID != this@VoiceLoungeView.liveID) return@post
                    if (isCreating) leaveRoom() else showAudienceEnd(message)
                }
            }
        }
        liveListListener = listEvents
        LiveListStore.shared().addLiveListListener(listEvents)

        val playerEvents = object : PlayerListener() {
            override fun liveEnded(reason: EndedReason) {
                mainHandler.post {
                    if (playerListener !== this) return@post
                    showAudienceEnd(null)
                }
            }

            override fun kickedOut(reason: KickedOutReason, message: String) {
                mainHandler.post {
                    if (playerListener !== this) return@post
                    val resID = when (reason) {
                        KickedOutReason.BY_ADMIN -> R.string.livekit_player_interruption_removed
                        KickedOutReason.BY_LOGGED_ON_OTHER_DEVICE -> R.string.livekit_player_interruption_other_device
                        KickedOutReason.BY_SERVER -> R.string.livekit_player_interruption_server
                        KickedOutReason.FOR_NETWORK_DISCONNECTED -> R.string.livekit_player_interruption_network
                        KickedOutReason.FOR_LIVE_CHANNEL_EXPIRED -> R.string.livekit_player_interruption_expired
                    }
                    val text = context.getString(resID)
                    showAudienceEnd(text)
                    LiveToast.show(context, text)
                }
            }
        }
        playerListener?.let { player.removeListener(it) }
        playerListener = playerEvents
        player.addListener(playerEvents)
    }

    private fun applyLiveInfo(info: LiveInfo) {
        hostName = info.liveOwner.userName
        hostAvatarURL = info.liveOwner.avatarURL
        val ownerID = info.liveOwner.userID
        if (ownerID.isNotEmpty() && ownerID != hostUserID) {
            hostUserID = ownerID
            livingView.updateHostUserID(ownerID)
        }
        syncHostInfo()

        var config = roomSettings.config.copy(roomName = info.liveName.ifEmpty { liveID })
        if (VoiceLoungeConfig.hasMeta(info.metaData)) config = config.applyingMeta(info.metaData)
        roomSettings.config = config
        info.metaData[VoiceLoungeConfig.PAUSED_META_KEY]?.let { applyHostPause(it == "1") }
    }

    private fun queryRoomMeta() {
        val keys = VoiceLoungeConfig.metaKeys + VoiceLoungeConfig.PAUSED_META_KEY
        val token = sessionToken
        LiveListStore.shared().queryMetaData(keys, object : MetaDataCompletionHandler {
            override fun onSuccess(metaData: HashMap<String, String>) {
                mainHandler.post {
                    if (token != sessionToken) return@post
                    metaData[VoiceLoungeConfig.PAUSED_META_KEY]?.let { applyHostPause(it == "1") }
                    if (VoiceLoungeConfig.hasMeta(metaData)) {
                        roomSettings.config = roomSettings.config.applyingMeta(metaData)
                    }
                    livingView.showOpeningTopic()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("queryMetaData failed liveID=$liveID code=$code message=$desc")
                mainHandler.post { if (token == sessionToken) livingView.showOpeningTopic() }
            }
        })
    }

    private fun applyHostPause(paused: Boolean) {
        if (!isLiving) return
        pauseView.setPaused(paused)
        if (paused) livingView.dismissOverlays()
    }

    private fun showAudienceEnd(reason: String?) {
        if (isCreating || !isLiving) return
        isLiving = false
        updateScreenAwake()
        liveClock.stop()
        livingView.dismissOverlays()
        livingView.deactivateInteractions()
        val info = LiveListStore.shared().liveState.currentLive.value
        val resolved = if (info.liveID.isNotEmpty()) info else LiveInfo().also { it.liveID = liveID }
        audienceEndView.show(resolved, reason?.ifEmpty { null })
        audienceEndView.bringToFront()
    }

    private fun switchToNextLive() {
        if (isFetchingNextLive) return
        isFetchingNextLive = true
        val currentID = liveID
        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        LiveListStore.shared().fetchLiveList("", FETCH_LIST_COUNT, object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post {
                    isFetchingNextLive = false
                    val next = LiveListStore.shared().liveState.liveList.value.firstOrNull {
                        it.liveID.isNotEmpty() && it.liveID != currentID && it.liveOwner.userID != selfID
                    }
                    if (next != null && onSwitchLive != null) onSwitchLive?.invoke(next)
                    else LiveToast.show(context, context.getString(R.string.livekit_voiceroom_no_more_live))
                }
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("fetchLiveList failed code=$code message=$desc")
                mainHandler.post {
                    isFetchingNextLive = false
                    ErrorLocalized.onError(code)
                }
            }
        })
    }

    private fun releasePlaySession() {
        sessionToken += 1
        coGuest.deactivate()
        deactivateInteractions()
        cancelSessionScope()
        seatListener?.let { seatStore?.removeLiveSeatEventListener(it) }
        seatListener = null
        liveListListener?.let { LiveListStore.shared().removeLiveListListener(it) }
        liveListListener = null
        playerListener?.let { player.removeListener(it) }
        playerListener = null
        pauseView.setPaused(false)
        if (isPlaying) {
            player.stopPlay()
            isPlaying = false
        }
        if (hasJoinedLive) {
            LiveListStore.shared().leaveLive(null)
            hasJoinedLive = false
        }
        seatStore = null
        seatUIStore.deactivate()
    }

    private companion object {
        val LOGGER = LiveKitLog.getComponentLogger("VoiceLoungeView")
        const val FETCH_LIST_COUNT = 20
    }
}
