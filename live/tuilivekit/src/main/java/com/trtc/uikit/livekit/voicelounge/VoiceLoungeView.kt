package com.trtc.uikit.livekit.voicelounge

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.tencent.trtc.TRTCCloud
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
import io.trtc.tuikit.atomicxcore.api.live.ConnectState
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatListener
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryData
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryStore
import io.trtc.tuikit.atomicxcore.api.live.LoungeCode
import io.trtc.tuikit.atomicxcore.api.live.SeatMode
import io.trtc.tuikit.atomicxcore.api.live.VoiceLoungeInfo
import io.trtc.tuikit.atomicxcore.api.live.VoiceLoungeListener
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
    private val transcriberPanel by lazy {
        com.trtc.uikit.livekit.voicelounge.settings.aitranscriber.VoiceLoungeAITranscriberPanel(context)
    }

    private var loungeConfig: VoiceLoungeInfo? = null
    private var latestLoungeInfo = VoiceLoungeInfo()
    private var loungeListener: VoiceLoungeListener? = null
    private var hasConnected = false
    private var hasConfiguredSeatMode = false
    private var endSummary: LiveSummaryData? = null
    private var isEndingLive = false
    private var isMuted = false
    private var ownsAudioService = false

    private val lounge = VoiceLoungeSession.of(liveID)
    private var isLiving = false
    private var hasJoinedLive = false
    private var isPlaying = false
    private var sessionToken = 0
    private var seatListener: LiveSeatListener? = null

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
            if (stage == Stage.LIVING && !isEndingLive) lounge.end(liveID)
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
        listOf(prepareView, livingView, summaryView, alertView)
    } else {
        listOf(livingView, audienceEndView)
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
            LiveToast.show(context, context.getString(R.string.livekit_voicelounge_error_empty_live_id))
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
        seatUIStore.isHostMicOff = isMuted
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
        if (lounge.state.connectState.value == ConnectState.DISCONNECTED) {
            // stopPush is refused while disconnected and no liveEnded follows, so finish locally.
            endSummary = null
            enterSummary()
            return
        }
        lounge.end(liveID)
    }

    private fun enterSummary() {
        if (stage != Stage.LIVING) return
        stage = Stage.SUMMARY
        updateScreenAwake()
        summaryView.bind(
            VoiceLoungeSummaryData(
                startTimeMillis = liveClock.startTimeMillis,
                endTimeMillis = System.currentTimeMillis(),
                summary = endSummary,
            )
        )
        livingView.dismissOverlays()
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
        subscribeLounge()
        hasConnected = false
        val config = VoiceLoungeInfo().apply {
            this.loungeID = this@VoiceLoungeView.liveID
            this.loungeName = liveName
            this.coverURL = roomSettings.config.coverURL
            this.notice = roomSettings.config.topic
            metaData = roomSettings.config.metaData
        }
        loungeConfig = config
        lounge.create(config)
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
                    if (isMuted) seatStore?.muteMicrophone()
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
        val config = loungeConfig ?: return
        config.metaData = roomSettings.config.metaData + giftRankStore.publishedMetaData()
        lounge.setMetaData(config.metaData)
    }

    private fun subscribeLounge() {
        clearLoungeSubscriptions()
        val listener = object : VoiceLoungeListener() {
            override fun loungeError(code: LoungeCode, message: String) {
                mainHandler.post {
                    if (loungeListener !== this || code == LoungeCode.OK) return@post
                    LOGGER.error("lounge error code=$code message=$message")
                    if (code == LoungeCode.ERROR_CREATE_FAILED && !hasConnected && !isEndingLive) {
                        returnToPrepareAfterStartFailure()
                    }
                    LiveToast.show(context, loungeErrorMessage(code, message))
                }
            }

            override fun onLoungeEnded(reason: EndedReason) {
                mainHandler.post {
                    if (loungeListener !== this) return@post
                    endSummary = LiveSummaryStore.create(liveID).liveSummaryState.summaryData.value
                    enterSummary()
                }
            }

            override fun onKickedOut(reason: KickedOutReason, message: String) {
                mainHandler.post {
                    if (loungeListener !== this) return@post
                    endSummary = null
                    enterSummary()
                }
            }
        }
        loungeListener = listener
        lounge.addListener(listener)
        val scope = newSessionScope()
        scope.launch {
            lounge.state.connectState.collect { connect ->
                if (connect == ConnectState.CONNECTED) onLoungeConnected()
            }
        }
    }

    private fun onLoungeConnected() {
        val seatStore = seatStore ?: return
        hasConnected = true
        if (hasConfiguredSeatMode || stage != Stage.LIVING) return
        hasConfiguredSeatMode = true
        applyHifiAudioConfig()
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

    private fun loungeErrorMessage(code: LoungeCode, message: String): String {
        val resourceID = when (code) {
            LoungeCode.ERROR_CREATE_FAILED, LoungeCode.ERROR_UPDATE_FAILED -> {
                val isContentRejected = Regex("""^\s*error_code:\s*100026(?:\s*[,|]|\s*$)""").containsMatchIn(message)
                when {
                    isContentRejected -> R.string.livekit_voicelounge_error_content_rejected
                    code == LoungeCode.ERROR_CREATE_FAILED -> R.string.livekit_voicelounge_error_start_failed
                    else -> R.string.livekit_voicelounge_error_update_failed
                }
            }

            else -> R.string.livekit_voicelounge_error_operation_failed
        }
        return context.getString(resourceID)
    }

    private fun clearLoungeSubscriptions() {
        cancelSessionScope()
        loungeListener?.let { lounge.removeListener(it) }
        loungeListener = null
    }

    private fun returnToPrepareAfterStartFailure() {
        // The SDK already stops the failed attempt; keep the shared pusher available for a retry.
        clearLoungeSubscriptions()
        stopAudioService()
        coGuest.deactivate()
        deactivateInteractions()
        seatUIStore.deactivate()
        seatStore = null
        loungeConfig = null
        hasConnected = false
        hasConfiguredSeatMode = false
        liveClock.stop()
        stage = Stage.PREPARE
        updateScreenAwake()
        livingView.visibility = View.GONE
        summaryView.visibility = View.GONE
        prepareView.visibility = View.VISIBLE
    }

    private fun releasePushSession() {
        clearLoungeSubscriptions()
        coGuest.deactivate()
        deactivateInteractions()
        DeviceStore.shared().closeLocalMicrophone()
        stopAudioService()
        hasConnected = false
        hasConfiguredSeatMode = false
        loungeConfig = null
        seatStore = null
        seatUIStore.deactivate()
        VoiceLoungeSession.release(liveID)
    }

    private fun bindLivingActions() {
        livingActions.isMuted = { isMuted }
        livingActions.toggleMute = { handleLivingToggleMute() }
        livingActions.changeLayout = { layout ->
            if (layout == VoiceLoungeSeatLayout.DUO) {
                livingView.dismissOverlays()
                confirmSwitchToDuo(layout)
            } else {
                applyLivingLayout(layout)
            }
        }
        livingActions.changeTopic = { topic -> commitTopic(topic) }
        livingActions.updateTranscriber = { state -> applyTranscriberState(state) }
        livingActions.showTranscriberPanel = { presentTranscriberPanel() }
        livingActions.confirm = { title, message, confirmTitle, action ->
            alertView.show(title, message, confirmTitle, roomSettings.skin, action)
        }
    }

    private fun applyTranscriberState(
        state: com.trtc.uikit.livekit.voicelounge.settings.aitranscriber.VoiceLoungeAITranscriberPanel.State,
    ) {
        val current = roomSettings.config
        val next = current.copy(
            isTranscriberEnabled = state.isEnabled,
            transcriberShowOriginal = state.showOriginal,
            transcriberScale = state.scale,
            transcriberLanguage = state.language,
        )
        if (next != current) roomSettings.config = next
        if (state.saveAsDefault) {
            com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeTranscriberDefaults.save(
                context,
                com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeTranscriberDefaults(
                    isEnabled = state.isEnabled,
                    showOriginal = state.showOriginal,
                    scale = state.scale,
                    language = state.language,
                ),
            )
        }
    }

    private fun presentTranscriberPanel() {
        val panel = transcriberPanel
        val initialState =
            com.trtc.uikit.livekit.voicelounge.settings.aitranscriber.VoiceLoungeAITranscriberPanel.State(
                isEnabled = roomSettings.isTranscriberEnabled,
                showOriginal = roomSettings.transcriberShowOriginal,
                scale = roomSettings.transcriberScale,
                language = roomSettings.transcriberLanguage,
                saveAsDefault = false,
            )
        panel.onStateChanged = { state -> applyTranscriberState(state) }
        panel.show(initialState, roomSettings.skin)
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
            context.getString(R.string.livekit_voicelounge_layout_duo_confirm_title),
            context.getString(R.string.livekit_voicelounge_layout_duo_confirm_message),
            context.getString(R.string.livekit_voicelounge_layout_duo_confirm_ok),
            roomSettings.skin,
        ) { kickAllGuestsThenApply(layout) }
    }

    private fun kickAllGuestsThenApply(layout: VoiceLoungeSeatLayout) {
        seatStore?.let { store ->
            val selfID = selfUserID
            seatUIStore.seatList.map { it.userInfo.userID }.filter { it.isNotEmpty() && it != selfID }.forEach {
                store.kickUserOutOfSeat(it, null)
            }
        }
        applyLivingLayout(layout)
    }

    private fun commitTopic(topic: String) {
        val trimmed = topic.trim()
        if (trimmed == roomSettings.topic) return
        roomSettings.config = roomSettings.config.copy(topic = trimmed)
        syncRoomMetaToAudience()
        loungeConfig?.let { config ->
            config.notice = trimmed
            lounge.updateLoungeInfo(config, listOf(VoiceLoungeInfo.ModifyFlag.NOTICE))
        }
        val text = if (trimmed.isEmpty()) {
            context.getString(R.string.livekit_voicelounge_topic_cleared)
        } else {
            context.getString(R.string.livekit_voicelounge_topic_changed, trimmed)
        }
        BarrageStore.create(liveID).sendTextMessage(text, null, object : CompletionHandler {
            override fun onSuccess() = Unit

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("send topic barrage failed liveID=$liveID code=$code message=$desc")
            }
        })
    }

    private fun bindJoinSession() {
        livingActions.showTranscriberPanel = { presentTranscriberPanel() }
        livingView.onClose = { leaveRoom() }
        coGuest.onMicOpenRequested = handler@{
            if (coGuest.guestStatus != VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT) return@handler
            val selfID = selfUserID
            val info = seatUIStore.seatList.firstOrNull { it.userInfo.userID == selfID }?.userInfo
            if (info?.allowOpenMicrophone == false || info?.microphoneStatus == DeviceStatus.ON) return@handler
            seatStore?.unmuteMicrophone(null)
        }
    }

    private fun joinLiving() {
        isLiving = true
        updateScreenAwake()
        liveClock.start()
        roomSettings.config = VoiceLoungeConfig(
            roomName = context.getString(R.string.livekit_voicelounge_room_loading_name),
            isTranscriberEnabled = true,
        )
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
        sessionToken += 1
        isPlaying = true
        lounge.join(liveID)
        seatStore = LiveSeatStore.create(liveID)
        seatUIStore.activate()
    }

    private fun onAudienceJoined() {
        if (hasJoinedLive) return
        hasJoinedLive = true
        val info = lounge.state.currentLounge.value
        applyHifiAudioConfig()
        activateInteractions(info.ownerInfo.userID.ifEmpty { hostUserID })
        coGuest.activate(liveID, role)
        seatUIStore.activateAudienceList()
        applyLoungeInfo(info)
        livingView.showOpeningTopic()
    }

    private fun subscribeRoom() {
        val scope = newSessionScope()
        scope.launch {
            lounge.state.currentLounge.collect { info ->
                if (info.loungeID.isNotEmpty() && info.loungeID == liveID) applyLoungeInfo(info)
            }
        }
        if (!isCreating) {
            scope.launch {
                lounge.state.connectState.collect { connect ->
                    if (connect == ConnectState.CONNECTED) onAudienceJoined()
                }
            }
        }

        val seatEvents = object : LiveSeatListener() {
            override fun onLocalMicrophoneClosedByAdmin() {
                mainHandler.post {
                    seatStore?.muteMicrophone()
                }
            }

            override fun onLocalMicrophoneOpenedByAdmin(policy: DeviceControlPolicy) {
                mainHandler.post {
                    if (coGuest.guestStatus != VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT) return@post
                    seatStore?.unmuteMicrophone(null)
                }
            }
        }
        seatListener = seatEvents
        seatStore?.addLiveSeatEventListener(seatEvents)

        val loungeEvents = object : VoiceLoungeListener() {
            override fun onLoungeEnded(reason: EndedReason) {
                mainHandler.post {
                    if (loungeListener !== this) return@post
                    if (isCreating) leaveRoom() else showAudienceEnd(null)
                }
            }

            override fun onKickedOut(reason: KickedOutReason, message: String) {
                mainHandler.post {
                    if (loungeListener !== this) return@post
                    val resID = when (reason) {
                        KickedOutReason.BY_ADMIN -> R.string.livekit_player_interruption_removed
                        KickedOutReason.BY_LOGGED_ON_OTHER_DEVICE -> R.string.livekit_player_interruption_other_device
                        KickedOutReason.BY_SERVER -> R.string.livekit_player_interruption_server
                        KickedOutReason.FOR_NETWORK_DISCONNECTED -> R.string.livekit_player_interruption_network
                        KickedOutReason.FOR_LOUNGE_CHANNEL_EXPIRED -> R.string.livekit_player_interruption_expired
                    }
                    val text = context.getString(resID)
                    showAudienceEnd(text)
                    LiveToast.show(context, text)
                }
            }
        }
        loungeListener?.let { lounge.removeListener(it) }
        loungeListener = loungeEvents
        lounge.addListener(loungeEvents)
    }

    private fun applyLoungeInfo(info: VoiceLoungeInfo) {
        if (info.ownerInfo.userID.isNotEmpty()) latestLoungeInfo = info
        hostName = info.ownerInfo.userName
        hostAvatarURL = info.ownerInfo.avatarURL
        val ownerID = info.ownerInfo.userID
        if (ownerID.isNotEmpty() && ownerID != hostUserID) {
            hostUserID = ownerID
            livingView.updateHostUserID(ownerID)
        }
        syncHostInfo()

        var config = roomSettings.config.copy(roomName = info.loungeName.ifEmpty { liveID })
        if (VoiceLoungeConfig.hasMeta(info.metaData)) config = config.applyingMeta(info.metaData)
        if (!VoiceLoungeConfig.hasTopicMeta(info.metaData) && info.notice.isNotEmpty()) {
            config = config.copy(topic = info.notice)
        }
        roomSettings.config = config
    }

    private fun showAudienceEnd(reason: String?) {
        if (isCreating || !isLiving) return
        isLiving = false
        updateScreenAwake()
        liveClock.stop()
        livingView.dismissOverlays()
        livingView.deactivateInteractions()
        audienceEndView.show(latestLoungeInfo, reason?.ifEmpty { null })
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
                    else LiveToast.show(context, context.getString(R.string.livekit_voicelounge_no_more_live))
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
        loungeListener?.let { lounge.removeListener(it) }
        loungeListener = null
        isPlaying = false
        if (hasJoinedLive) {
            lounge.leave()
            hasJoinedLive = false
        }
        seatStore = null
        seatUIStore.deactivate()
        VoiceLoungeSession.release(liveID)
    }

    private fun applyHifiAudioConfig() {
        val cloud = TRTCCloud.sharedInstance(context) ?: return
        cloud.callExperimentalAPI(HIFI_AUDIO_QUALITY_JSON)
        cloud.callExperimentalAPI(HIFI_PRIVATE_CONFIG_JSON)
    }

    private companion object {
        val LOGGER = LiveKitLog.getComponentLogger("VoiceLoungeView")
        const val FETCH_LIST_COUNT = 20
        const val HIFI_AUDIO_QUALITY_JSON = "{\"api\":\"setAudioQualityEx\",\"params\":{\"audioScene\":18}}"
        const val HIFI_PRIVATE_CONFIG_JSON =
            "{\"api\":\"setPrivateConfig\",\"params\":{\"configs\":[" + "{\"key\":\"Liteav.Audio.common.agc.version\",\"default\":\"0\",\"value\":\"6\"}," + "{\"key\":\"Liteav.Audio.common.ai.ec.model.type\",\"default\":\"0\",\"value\":\"6\"}" + "]}}"
    }
}
