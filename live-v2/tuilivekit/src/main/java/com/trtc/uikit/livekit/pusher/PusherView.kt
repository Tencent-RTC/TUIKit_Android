package com.trtc.uikit.livekit.pusher

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.trtc.uikit.livekit.common.basic.foregroundservice.AudioForegroundService
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.basic.COMPONENT_LIVE_STREAM
import com.trtc.uikit.livekit.common.basic.ErrorLocalized
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.basic.LIVE_INTEGRATION_SUCCESSFUL
import com.trtc.uikit.livekit.common.basic.permission.PermissionCallback
import com.trtc.uikit.livekit.common.basic.permission.PermissionRequest
import com.trtc.uikit.livekit.common.basic.reportAtomicMetrics
import com.trtc.uikit.livekit.common.basic.setComponent
import com.trtc.uikit.livekit.common.theme.toast.LiveToast
import com.trtc.uikit.livekit.pusher.living.PusherLivingView
import com.trtc.uikit.livekit.pusher.prepare.PusherPrepareView
import com.trtc.uikit.livekit.pusher.video.PusherVideoView
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestStore
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestStore.Layout
import com.trtc.uikit.livekit.pusher.living.status.PusherDeviceStatusState
import com.trtc.uikit.livekit.pusher.summary.PusherSummaryView
import com.trtc.uikit.livekit.pusher.summary.PusherSummaryContext
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsActionHandling
import com.trtc.uikit.livekit.pusher.settings.AudioEffectView
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsState
import com.trtc.uikit.livekit.common.beauty.BeautyPreferences
import com.trtc.uikit.livekit.common.beauty.BeautyPanel
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.MirrorType
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import io.trtc.tuikit.atomicxcore.api.device.NetworkType
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.AtomicPusher
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.PusherConnectState
import io.trtc.tuikit.atomicxcore.api.live.PusherListener
import io.trtc.tuikit.atomicxcore.api.live.PusherCode
import io.trtc.tuikit.atomicxcore.api.live.SeatMode
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import kotlinx.coroutines.flow.combine
import io.trtc.tuikit.atomicxcore.api.live.PusherInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

private enum class Phase {
    PREPARE,
    LIVING,
    SUMMARY,
}

private data class PusherPresentationState(
    val hostName: String = "",
    val hostAvatarURL: String = "",
    val settings: PusherSettingsState = PusherSettingsState(),
    val deviceStatus: PusherDeviceStatusState = PusherDeviceStatusState(),
)

/**
 * Public host entry. Switches between the prepare, living and summary stages.
 * Call [bind] with the live ID before the view is attached. Leaving the broadcast is reported
 * through [onExit].
 */
class PusherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private companion object {
        const val ONE_SECOND_MS = 1_000L
    }

    /** Raised when the host leaves the broadcast, from either the prepare or the summary stage. */
    var onExit: (() -> Unit)? = null

    private val videoView = PusherVideoView(context)
    private val coGuestBackgroundView = ImageView(context).apply {
        setImageResource(R.drawable.livekit_video_background)
        scaleType = ImageView.ScaleType.FIT_XY
        isClickable = false
        isVisible = false
    }
    private val beautyPanel = BeautyPanel(context).apply {
        setOptionTitles(mapOf(
            BeautyPanel.Kind.SMOOTH to R.string.livekit_pusher_beauty_smooth,
            BeautyPanel.Kind.WHITENESS to R.string.livekit_pusher_beauty_whiteness,
        ))
    }
    private val prepareView = PusherPrepareView(context)
    private val livingView = PusherLivingView(context)
    private val summaryView = PusherSummaryView(context)
    private lateinit var endingOverlay: View
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logger = LiveKitLogger.getComponentLogger("PusherView")
    private var seatStore: LiveSeatStore? = null
    private var coGuestStore: PusherCoGuestStore? = null
    private var presentationState = PusherPresentationState()
    private val settingsActionHandler = object : PusherSettingsActionHandling {
        override fun toggleMirror() {
            val isEnabled = DeviceStore.shared().deviceState.localMirrorType.value != MirrorType.DISABLE
            DeviceStore.shared().switchMirror(if (isEnabled) MirrorType.DISABLE else MirrorType.ENABLE)
            updateSettings { copy(isMirrorEnabled = !isEnabled) }
        }

        override fun setCaptureVolume(volume: Int) {
            val value = volume.coerceIn(0, 100)
            DeviceStore.shared().setCaptureVolume(value)
            updateSettings { copy(captureVolume = value) }
        }


    }

    private var liveID = ""
    private var phase = Phase.PREPARE
    private var liveTitle = context.getString(R.string.livekit_edittopicview_default_title)
    private var coverURL = LiveKitResources.defaultCoverURL
    private var liveStartTimeMillis = 0L
    private var liveEndTimeMillis = 0L
    private var elapsedSeconds = 0
    private var isPaused = false
    private var pausedSeconds = 0
    private var accumulatedPausedMillis = 0L
    private var pauseStartTimeMillis = 0L
    private var elapsedTimer: Job? = null
    private var pauseTimer: Job? = null
    private var startLiveRequestID: UUID? = null
    private var isPusherAttached = false
    private var hasConnected = false
    private var didInitializeVideoQuality = false
    private var interruptionMessage: String? = null
    private var pusherListener: PusherListener? = null
    private var accountSubscriptionID: UUID? = null
    private val loginListener = object : LoginListener() {
        override fun onKickedOffline() {
            val subscriptionID = accountSubscriptionID ?: return
            mainHandler.post {
                if (accountSubscriptionID != subscriptionID || !isAttachedToWindow) return@post
                when (phase) {
                    Phase.LIVING -> {
                        interruptionMessage = context.getString(R.string.livekit_pusher_interruption_account)
                        endLive()
                    }
                    Phase.PREPARE -> mainHandler.post {
                        if (accountSubscriptionID == subscriptionID && phase == Phase.PREPARE) exitPage()
                    }
                    Phase.SUMMARY -> Unit
                }
            }
        }
    }
    private var previewOperationID: UUID? = null
    private var unmuteOperationID: UUID? = null
    private var didInitializeAudioSettings = false
    private var didInitializePreviewSettings = false
    private var didStartPreview = false
    private var isPreviewStarting = false
    private var needsDeviceCleanup = false
    private var isEndingLive = false
    private var didReleaseSession = false
    private var presentationStateScope: CoroutineScope? = null
    private var coGuestStateJob: Job? = null
    private var pusherStateJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private data class BackgroundDevices(val camera: Boolean, val microphone: Boolean, val isFront: Boolean)
    private var backgroundDevices: BackgroundDevices? = null
    private var ownsAudioService = false
    private var thermalStateJob: Job? = null
    private val captureLifecycleObserver = LifecycleEventObserver { owner, event ->
        when (event) {
            Lifecycle.Event.ON_START -> subscribeThermalState(owner)
            Lifecycle.Event.ON_RESUME -> {
                startPreviewIfNeeded()
                startAudioServiceIfNeeded()
            }
            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                suspendCapture()
                thermalStateJob?.cancel()
                thermalStateJob = null
            }
            else -> Unit
        }
    }
    private var connectedGuestCount = 0
    private var coGuestLayout = Layout.GRID_2

    init {
        setBackgroundColor(Color.BLACK)
        // The cover thumbnail and the start-live button cast shadows outside their boxes.
        clipChildren = false
        clipToPadding = false

        val bottomMargin =
            resources.getDimensionPixelSize(R.dimen.livekit_pusher_bottom_bar_height)
        addView(
            coGuestBackgroundView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            videoView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                this.bottomMargin = bottomMargin
            }
        )
        listOf(prepareView, livingView, summaryView).forEach {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_ending_overlay, this, true)
        endingOverlay = findViewById(R.id.livekit_pusher_ending_overlay)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            videoView.updateLayoutParams<LayoutParams> {
                this.bottomMargin = PusherVideoView.bottomInset(context, insets)
            }
            insets
        }
        updateHostProfile()
        presentationState = presentationState.copy(
            settings = makeSettingsState(),
            deviceStatus = makeDeviceStatusState(),
        )
        bindActions()
        render()
    }

    /** Binds the broadcast this view drives. Must be called before attaching the view. */
    fun bind(liveID: String) {
        if (didReleaseSession) return
        coGuestStore?.deactivate()
        coGuestStateJob?.cancel()
        coGuestStateJob = null
        this.liveID = liveID
        if (!didInitializeVideoQuality) {
            didInitializeVideoQuality = true
            DeviceStore.shared().updateVideoQuality(VideoQuality.QUALITY_720P)
            updateSettings { copy(videoQuality = VideoQuality.QUALITY_720P) }
        }
        livingView.bind(liveID)
        summaryView.hide()
        summaryView.updateSummary(null)
        connectedGuestCount = 0
        coGuestLayout = Layout.GRID_2
        if (liveID.isNotEmpty()) {
            val seatStore = LiveSeatStore.create(liveID)
            this.seatStore = seatStore
            val store = PusherCoGuestStore(
                liveID = liveID,
                seatStore = seatStore,
            )
            coGuestStore = store
            livingView.coGuestActionHandler = store
            livingView.updateCoGuest(store.state.value)
        } else {
            seatStore = null
            coGuestStore = null
            livingView.coGuestActionHandler = null
            livingView.updateCoGuest(PusherCoGuestStore.State())
        }
        subscribeCoGuestState()
        subscribePresentationState()
        updateCoGuestPresentation()
    }

    /** Closes transient UI and consumes system back while the broadcast is living. */
    fun handleBackPressed(): Boolean {
        if (!isEndingLive && !didReleaseSession &&
            (dismissBeautyIfShowing() || prepareView.handleBackPressed() || livingView.handleBackPressed())) {
            return true
        }
        if (phase == Phase.LIVING && !didReleaseSession) return true
        cleanupSession()
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (didReleaseSession) return
        ViewCompat.requestApplyInsets(this)
        if (phase != Phase.SUMMARY) {
            AtomicPusher.shared().setView(videoView.coreView)
            isPusherAttached = true
        }
        LoginStore.shared.removeLoginListener(loginListener)
        accountSubscriptionID = UUID.randomUUID()
        LoginStore.shared.addLoginListener(loginListener)
        subscribePresentationState()
        subscribeCoGuestState()

        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        this.lifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(captureLifecycleObserver)
        render()
        startPreviewIfNeeded()
    }

    override fun onDetachedFromWindow() {
        cleanupSession()
        summaryView.hide()
        super.onDetachedFromWindow()
    }

    private fun bindActions() {
        prepareView.onExit = { exitPage() }
        prepareView.onCountdownFinished = ::startLive
        prepareView.onSelectCover = ::updateCoverURL
        prepareView.onTitleChanged = { title ->
            if (phase == Phase.PREPARE && !didReleaseSession) liveTitle = title
        }
        prepareView.onShowBeauty = { showBeauty(Phase.PREPARE) }
        livingView.onShowBeauty = { showBeauty(Phase.LIVING) }
        prepareView.onRefreshSettings = ::refreshSettings
        prepareView.onFlipCamera = {
            DeviceStore.shared().switchCamera(!DeviceStore.shared().deviceState.isFrontCamera.value)
        }
        prepareView.settingsActionHandler = settingsActionHandler
        livingView.onRefreshSettings = ::refreshSettings
        livingView.onEnd = ::endLive
        livingView.onToggleMute = ::toggleMute
        livingView.onPause = ::pauseLive
        livingView.onResume = ::resumeLive
        livingView.onFlipCamera = {
            DeviceStore.shared().switchCamera(!DeviceStore.shared().deviceState.isFrontCamera.value)
        }
        livingView.coGuestSmallWindowRegions = { videoView.coGuestSmallWindowRegions(livingView) }
        livingView.onCoGuestStageTap = { point ->
            val userID = videoView.coGuestUserIDAt(point)
            if (userID == null) {
                false
            } else {
                livingView.showCoGuestActions(userID)
                true
            }
        }
        videoView.onCoGuestGeometryChanged = { livingView.refreshCoGuestGeometry() }
        livingView.settingsActionHandler = settingsActionHandler
        summaryView.onExit = { exitPage() }
    }

    private fun subscribeCoGuestState() {
        coGuestStateJob?.cancel()
        coGuestStateJob = null
        val store = coGuestStore ?: return
        coGuestStateJob = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            launch { store.toasts.collect { if (phase == Phase.LIVING) livingView.showCoGuestToast(it) } }
            store.state.collectLatest { state ->
                if (connectedGuestCount != state.connected.size || coGuestLayout != state.layout) {
                    connectedGuestCount = state.connected.size
                    coGuestLayout = state.layout
                    updateCoGuestPresentation()
                }
                livingView.updateCoGuest(state)
            }
        }
    }

    private fun subscribePresentationState() {
        presentationStateScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        presentationStateScope = scope
        LoginStore.shared.loginState.loginUserInfo.onEach {
            if (!didReleaseSession && presentationStateScope === scope) updateHostProfile()
        }.launchIn(scope)
        val deviceState = DeviceStore.shared().deviceState
        deviceState.cameraStatus.onEach { status ->
            videoView.updateLocalCameraStatus(status)
            updateDeviceStatus()
        }.launchIn(scope)
        listOf(
            deviceState.cameraLastError,
            deviceState.microphoneStatus,
            deviceState.microphoneLastError,
            deviceState.localVideoQuality,
            deviceState.networkType,
            deviceState.networkInfo,
        ).forEach { flow ->
            flow.onEach { updateDeviceStatus() }.launchIn(scope)
        }
        seatStore?.liveSeatState?.avStatistics?.onEach { statistics ->
            val value = statistics.firstOrNull { it.userID.isEmpty() }
            presentationState = presentationState.copy(
                deviceStatus = presentationState.deviceStatus.copy(
                    audioSampleRate = value?.audioSampleRate ?: 0,
                ),
            )
            updateDevicePresentation()
        }?.launchIn(scope)
        updateDeviceStatus()
    }

    private fun updateDeviceStatus() {
        val status = makeDeviceStatusState()
        if (presentationState.deviceStatus == status) return
        presentationState = presentationState.copy(deviceStatus = status)
        updateDevicePresentation()
    }

    private fun subscribeThermalState(owner: LifecycleOwner) {
        thermalStateJob?.cancel()
        thermalStateJob = owner.lifecycleScope.launch {
            thermalStatusChanges().collectLatest { status ->
                val temperature = when (status) {
                    PowerManager.THERMAL_STATUS_LIGHT,
                    PowerManager.THERMAL_STATUS_MODERATE ->
                        PusherDeviceStatusState.TemperatureStatus.FAIR
                    PowerManager.THERMAL_STATUS_SEVERE,
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN ->
                        PusherDeviceStatusState.TemperatureStatus.SERIOUS
                    else -> PusherDeviceStatusState.TemperatureStatus.NORMAL
                }
                if (presentationState.deviceStatus.temperatureStatus != temperature) {
                    presentationState = presentationState.copy(
                        deviceStatus = presentationState.deviceStatus.copy(temperatureStatus = temperature),
                    )
                    updateDevicePresentation()
                }
            }
        }
    }

    private fun thermalStatusChanges() = callbackFlow {
        val manager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || manager == null) {
            trySend(0) // No thermal status API is available on older versions.
            close()
            return@callbackFlow
        }
        // Read again on every foreground subscription; cancelled channels reject late callbacks.
        val listener = PowerManager.OnThermalStatusChangedListener { trySend(it) }
        manager.addThermalStatusListener(listener)
        trySend(manager.currentThermalStatus)
        awaitClose { manager.removeThermalStatusListener(listener) }
    }

    private fun makeDeviceStatusState(): PusherDeviceStatusState {
        val deviceState = DeviceStore.shared().deviceState
        val networkInfo = deviceState.networkInfo.value
        return presentationState.deviceStatus.copy(
            networkKind = when (deviceState.networkType.value) {
                NetworkType.WIFI -> PusherDeviceStatusState.NetworkKind.WIFI
                NetworkType.CELLULAR -> PusherDeviceStatusState.NetworkKind.CELLULAR
                NetworkType.UNKNOWN -> PusherDeviceStatusState.NetworkKind.UNKNOWN
            },
            networkQuality = when (networkInfo.quality) {
                NetworkQuality.EXCELLENT -> 1
                NetworkQuality.GOOD -> 2
                NetworkQuality.POOR -> 3
                NetworkQuality.BAD -> 4
                NetworkQuality.VERY_BAD -> 5
                NetworkQuality.DOWN -> 6
                NetworkQuality.UNKNOWN -> 0
            },
            delay = networkInfo.delay,
            downLoss = networkInfo.downLoss,
            upLoss = networkInfo.upLoss,
            cameraStatus = deviceState.cameraStatus.value,
            microphoneStatus = deviceState.microphoneStatus.value,
            cameraError = deviceState.cameraLastError.value,
            microphoneError = deviceState.microphoneLastError.value,
            videoQuality = deviceState.localVideoQuality.value,
        )
    }

    private fun makeSettingsState(): PusherSettingsState {
        val deviceState = DeviceStore.shared().deviceState
        return PusherSettingsState(
            videoQuality = deviceState.localVideoQuality.value,
            isMirrorEnabled = deviceState.localMirrorType.value != MirrorType.DISABLE,
            isMuted = presentationState.settings.isMuted,
            captureVolume = deviceState.captureVolume.value,
        )
    }

    private fun updateSettings(transform: PusherSettingsState.() -> PusherSettingsState) {
        val previous = presentationState.settings
        val settings = previous.transform()
        presentationState = presentationState.copy(settings = settings)
        updateSettingsPresentation()
        if (previous.videoQuality != settings.videoQuality || previous.isMuted != settings.isMuted ||
            previous.captureVolume != settings.captureVolume) {
            updateDevicePresentation()
        }
    }

    private fun startPreviewIfNeeded() {
        if (!canCapture() || didStartPreview || isPreviewStarting) return
        if (!didInitializeAudioSettings) {
            didInitializeAudioSettings = true
            AudioEffectView.resetForNewSession()
            settingsActionHandler.setCaptureVolume(100)
        }
        val operationID = UUID.randomUUID()
        previewOperationID = operationID
        isPreviewStarting = true
        if (backgroundDevices?.camera == false) {
            requestMicrophoneAccess(operationID)
            return
        }
        PermissionRequest.requestCameraPermissions(context, object : PermissionCallback() {
            override fun onGranted() {
                mainHandler.post {
                    if (!validatePreviewOperation(operationID)) return@post
                    val isFront = backgroundDevices?.isFront
                        ?: if (didInitializePreviewSettings) DeviceStore.shared().deviceState.isFrontCamera.value else true
                    if (!didInitializePreviewSettings) {
                        didInitializePreviewSettings = true
                        DeviceStore.shared().switchMirror(MirrorType.ENABLE)
                        updateSettings { copy(isMirrorEnabled = true) }
                    }
                    BeautyPreferences.restore(context)
                    needsDeviceCleanup = true
                    DeviceStore.shared().openLocalCamera(isFront, cameraPreviewHandler(operationID))
                }
            }

            override fun onDenied() {
                super.onDenied()
                mainHandler.post { finishPreview(operationID) }
            }
        })
    }

    private fun cameraPreviewHandler(operationID: UUID): CompletionHandler =
        object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post {
                    if (!validatePreviewOperation(operationID)) {
                        return@post
                    }
                    requestMicrophoneAccess(operationID)
                }
            }

            override fun onFailure(code: Int, desc: String) {
                mainHandler.post {
                    if (!validatePreviewOperation(operationID)) return@post
                    finishPreview(operationID)
                    ErrorLocalized.onError(code)
                }
            }
        }

    private fun requestMicrophoneAccess(operationID: UUID) {
        if (!validatePreviewOperation(operationID)) return
        if (backgroundDevices?.microphone == false) {
            finishPreview(operationID)
            didStartPreview = true
            backgroundDevices = null
            return
        }
        PermissionRequest.requestMicrophonePermissions(context, object : PermissionCallback() {
            override fun onGranted() {
                mainHandler.post {
                    if (!validatePreviewOperation(operationID)) return@post
                    needsDeviceCleanup = true
                    val callback = microphonePreviewHandler(operationID)
                    if (DeviceStore.shared().deviceState.microphoneStatus.value == DeviceStatus.ON) {
                        if (presentationState.settings.isMuted) {
                            callback.onSuccess()
                        } else {
                            seatStore?.unmuteMicrophone(callback)
                        }
                    } else {
                        if (presentationState.settings.isMuted) seatStore?.muteMicrophone()
                        DeviceStore.shared().openLocalMicrophone(callback)
                    }
                }
            }

            override fun onDenied() {
                super.onDenied()
                mainHandler.post { finishPreview(operationID) }
            }
        })
    }

    private fun microphonePreviewHandler(operationID: UUID): CompletionHandler =
        object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post {
                    if (!validatePreviewOperation(operationID)) {
                        if (!didReleaseSession && (isPaused ||
                                lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != true)) {
                            seatStore?.muteMicrophone()
                        }
                        return@post
                    }
                    previewOperationID = null
                    isPreviewStarting = false
                    didStartPreview = true
                    backgroundDevices = null
                    startAudioServiceIfNeeded()
                    if (presentationState.settings.isMuted) seatStore?.muteMicrophone()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                mainHandler.post {
                    if (!validatePreviewOperation(operationID)) return@post
                    finishPreview(operationID)
                    ErrorLocalized.onError(code)
                }
            }
        }

    private fun validatePreviewOperation(operationID: UUID): Boolean {
        if (previewOperationID != operationID) return false
        if (!canCapture()) {
            // A permission Activity may return before the host reaches RESUMED.
            finishPreview(operationID)
            return false
        }
        return true
    }

    private fun finishPreview(operationID: UUID) {
        if (previewOperationID != operationID) return
        previewOperationID = null
        isPreviewStarting = false
    }

    private fun stopPreview(closeMicrophone: Boolean = false) {
        previewOperationID = null
        if (needsDeviceCleanup) {
            DeviceStore.shared().closeLocalCamera()
            if (closeMicrophone) {
                needsDeviceCleanup = false
                DeviceStore.shared().closeLocalMicrophone()
            } else {
                seatStore?.muteMicrophone()
            }
        }
        didStartPreview = false
        isPreviewStarting = false
    }

    private fun canCapture(): Boolean =
        isAttachedToWindow && !didReleaseSession && !isEndingLive && !isPaused && phase != Phase.SUMMARY &&
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

    private fun suspendCapture() {
        if (!isAttachedToWindow || didReleaseSession) return
        if (backgroundDevices == null && !isPaused && phase != Phase.SUMMARY &&
            (didStartPreview || isPreviewStarting || needsDeviceCleanup)) {
            val state = DeviceStore.shared().deviceState
            backgroundDevices = BackgroundDevices(
                camera = state.cameraStatus.value == DeviceStatus.ON || isPreviewStarting,
                microphone = state.microphoneStatus.value == DeviceStatus.ON || isPreviewStarting,
                isFront = if (didInitializePreviewSettings) state.isFrontCamera.value else true,
            )
        }
        stopPreview()
    }

    private fun startAudioServiceIfNeeded() {
        if (phase != Phase.LIVING || didReleaseSession || isEndingLive || !isAttachedToWindow ||
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true ||
            DeviceStore.shared().deviceState.microphoneStatus.value != DeviceStatus.ON) return
        ownsAudioService = true
        AudioForegroundService.start(
            context, context.applicationInfo.loadLabel(context.packageManager).toString(),
            context.getString(R.string.livekit_coguest_service_description), R.drawable.livekit_coguest_idle,
        )
    }

    private fun stopAudioService() {
        if (!ownsAudioService) return
        ownsAudioService = false
        AudioForegroundService.stop(context)
    }

    private fun refreshSettings() {
        presentationState = presentationState.copy(settings = makeSettingsState())
        updateSettingsPresentation()
        updateDevicePresentation()
    }

    private fun updateCoverURL(value: String) {
        if (value == coverURL || value !in LiveKitResources.presetCoverURLs) return
        coverURL = value
        if (phase == Phase.PREPARE) prepareView.bind(liveTitle, coverURL)
    }

    private fun startLive(title: String) {
        if (phase != Phase.PREPARE || didReleaseSession || !isPusherAttached || startLiveRequestID != null) return
        liveTitle = title
        if (liveID.isEmpty() || liveID.toByteArray(Charsets.UTF_8).size > 48) {
            LiveToast.show(context, context.getString(R.string.livekit_pusher_error_start_failed))
            return
        }
        enterLivingPhase()
        requestStartLive()
    }

    private fun enterLivingPhase() {
        livingView.activateAudience()
        livingView.activateLikes()
        livingView.activateComments()
        livingView.activateMusic()
        coGuestStore?.activate()
        liveStartTimeMillis = System.currentTimeMillis()
        liveEndTimeMillis = 0L
        elapsedSeconds = 0
        isPaused = false
        pausedSeconds = 0
        accumulatedPausedMillis = 0L
        pauseStartTimeMillis = 0L
        phase = Phase.LIVING
        startAudioServiceIfNeeded()
        render()

        elapsedTimer?.cancel()
        elapsedTimer = lifecycleOwner?.lifecycleScope?.launch {
            while (phase == Phase.LIVING) {
                delay(ONE_SECOND_MS)
                if (phase != Phase.LIVING || isEndingLive || didReleaseSession) break
                updateElapsedTime()
                livingView.updateElapsedSeconds(elapsedSeconds)
            }
        }
    }

    private fun requestStartLive() {
        if (startLiveRequestID != null || !isPusherAttached) return
        val sessionID = UUID.randomUUID()
        startLiveRequestID = sessionID
        hasConnected = false
        summaryView.updateSummary(null)
        val pusher = AtomicPusher.shared()
        val listener = object : PusherListener() {
            override fun pusherError(code: PusherCode, message: String) {
                mainHandler.post {
                    handlePushError(sessionID, code, message)
                }
            }

            override fun liveEnded(reason: EndedReason, summary: PusherSummary?) {
                mainHandler.post {
                    if (startLiveRequestID != sessionID) return@post
                    summaryView.updateSummary(summary)
                    clearPushSubscriptions()
                    if (phase != Phase.LIVING) return@post
                    if (!isEndingLive) {
                        interruptionMessage = context.getString(
                            if (reason == EndedReason.ENDED_BY_SERVER) R.string.livekit_pusher_interruption_server
                            else R.string.livekit_pusher_interruption_ended,
                        )
                    }
                    settleLive()
                }
            }

            override fun kickedOut(reason: KickedOutReason, message: String) {
                val reasonID = when (reason) {
                    KickedOutReason.BY_ADMIN -> R.string.livekit_pusher_interruption_admin
                    KickedOutReason.BY_LOGGED_ON_OTHER_DEVICE -> R.string.livekit_pusher_interruption_other_device
                    KickedOutReason.BY_SERVER -> R.string.livekit_pusher_interruption_server
                    KickedOutReason.FOR_NETWORK_DISCONNECTED -> R.string.livekit_pusher_interruption_network
                    KickedOutReason.FOR_LIVE_CHANNEL_EXPIRED -> R.string.livekit_pusher_interruption_expired
                }
                finishExternalLive(sessionID, reasonID)
            }
        }
        pusherListener = listener
        pusher.addListener(listener)
        pusherStateJob?.cancel()
        pusherStateJob = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            combine(pusher.state.networkState, pusher.state.statistics, pusher.state.pusherInfo) {
                    network, statistics, info -> Triple(network, statistics, info.liveID)
            }.collect { (network, statistics, roomID) ->
                if (!isCurrentPush(sessionID) || roomID != liveID) return@collect
                if (network == PusherConnectState.CONNECTED && !hasConnected) {
                    hasConnected = true
                    seatStore?.setSeatMode(SeatMode.APPLY, object : CompletionHandler {
                        override fun onSuccess() = Unit
                        override fun onFailure(code: Int, desc: String) {
                            logger.error("set seat mode failed: code=$code, desc=$desc")
                            mainHandler.post {
                                if (isCurrentPush(sessionID)) {
                                    LiveToast.show(context, context.getString(R.string.livekit_pusher_error_seat_mode))
                                }
                            }
                        }
                    })
                    coGuestStore?.didConnect()
                    livingView.refreshAudience()
                }
                val status = presentationState.deviceStatus.copy(
                    videoWidth = statistics.videoWidth.coerceAtLeast(0),
                    videoHeight = statistics.videoHeight.coerceAtLeast(0),
                    frameRate = statistics.videoFps.coerceAtLeast(0),
                    videoBitrate = statistics.videoBitrate.coerceAtLeast(0),
                    audioBitrate = statistics.audioBitrate.coerceAtLeast(0),
                    hasAVStatistics = network == PusherConnectState.CONNECTED,
                )
                if (status != presentationState.deviceStatus) {
                    presentationState = presentationState.copy(deviceStatus = status)
                    updateDeviceStatus()
                    updateDevicePresentation()
                }
            }
        }
        reportAtomicMetrics(LIVE_INTEGRATION_SUCCESSFUL)
        setComponent(COMPONENT_LIVE_STREAM)
        pusher.startPush(PusherInfo().apply {
            liveID = this@PusherView.liveID
            liveName = liveTitle.trim().ifEmpty {
                context.getString(R.string.livekit_edittopicview_default_title)
            }
            coverURL = this@PusherView.coverURL
            backgroundURL = "https://liteav.sdk.qcloud.com/app/res/picture/livekit/coGuestBackGroud.png"
        })
    }

    private fun handlePushError(sessionID: UUID, code: PusherCode, message: String) {
        if (!isCurrentPush(sessionID) || code == PusherCode.OK) return
        logger.error("pusher error: code=$code, message=$message")
        val text = pushErrorMessage(code, message)
        if (code == PusherCode.ERROR_START_PUSH_REQUEST_FAILED && !hasConnected) {
            returnToPrepareAfterStartFailure()
        }
        LiveToast.show(context, text)
    }

    private fun returnToPrepareAfterStartFailure() {
        // The SDK already stops the failed attempt; keep the shared pusher and preview.
        clearPushSubscriptions()
        stopAudioService()
        unmuteOperationID = null
        phase = Phase.PREPARE
        elapsedTimer?.cancel()
        elapsedTimer = null
        finishPause()
        liveStartTimeMillis = 0L
        liveEndTimeMillis = 0L
        elapsedSeconds = 0
        accumulatedPausedMillis = 0L
        interruptionMessage = null
        livingView.resetAfterStartFailure()
        coGuestStore?.deactivate()
        prepareView.deactivate()
        render()
        // Existing or in-flight preview is retained; paused/background capture resumes
        // through the original lifecycle gate when the host is in the foreground.
        startPreviewIfNeeded()
    }

    private fun pushErrorMessage(code: PusherCode, message: String): String {
        val resourceID = when (code) {
            PusherCode.ERROR_START_PUSH_REQUEST_FAILED,
            PusherCode.ERROR_UPDATE_CONFIG_REQUEST_FAILED -> {
                val isContentRejected = Regex("""^\s*error_code:\s*100026(?:\s*[,|]|\s*$)""")
                    .containsMatchIn(message)
                when {
                    isContentRejected -> R.string.livekit_pusher_error_content_rejected
                    code == PusherCode.ERROR_START_PUSH_REQUEST_FAILED -> R.string.livekit_pusher_error_start_failed
                    else -> R.string.livekit_pusher_error_update_failed
                }
            }
            else -> R.string.livekit_pusher_error_operation_failed
        }
        return context.getString(resourceID)
    }

    private fun isCurrentPush(sessionID: UUID): Boolean =
        startLiveRequestID == sessionID && phase == Phase.LIVING && !isEndingLive && !didReleaseSession

    private fun finishExternalLive(sessionID: UUID, reasonID: Int) {
        mainHandler.post {
            if (startLiveRequestID != sessionID || didReleaseSession || phase != Phase.LIVING) return@post
            interruptionMessage = context.getString(reasonID)
            clearPushSubscriptions()
            settleLive()
        }
    }

    private fun clearPushSubscriptions() {
        startLiveRequestID = null
        pusherStateJob?.cancel()
        pusherStateJob = null
        pusherListener?.let { AtomicPusher.shared().removeListener(it) }
        pusherListener = null
    }

    private fun releasePusher() {
        val hadSession = startLiveRequestID != null
        clearPushSubscriptions()
        if (!isPusherAttached) return
        isPusherAttached = false
        if (hadSession && !isEndingLive) AtomicPusher.shared().stopPush()
        AtomicPusher.shared().release()
    }

    private fun pauseLive() {
        if (phase != Phase.LIVING || isEndingLive || didReleaseSession || isPaused) return
        pauseStartTimeMillis = System.currentTimeMillis()
        isPaused = true
        updateElapsedTime()
        livingView.updateElapsedSeconds(elapsedSeconds)
        backgroundDevices = null
        pausedSeconds = 0
        livingView.pauseComments()
        stopPreview()
        livingView.updatePause(isPaused, pausedSeconds)

        pauseTimer?.cancel()
        pauseTimer = lifecycleOwner?.lifecycleScope?.launch {
            while (isPaused) {
                delay(ONE_SECOND_MS)
                if (!isPaused) break
                pausedSeconds = (currentPauseMillis() / ONE_SECOND_MS).toInt()
                livingView.updatePause(isPaused, pausedSeconds)
            }
        }
    }

    private fun resumeLive() {
        if (phase != Phase.LIVING || isEndingLive || didReleaseSession || !isPaused) return
        finishPause()
        livingView.resumeComments()
        startPreviewIfNeeded()
        livingView.updatePause(isPaused, pausedSeconds)
    }

    private fun toggleMute() {
        if (phase != Phase.LIVING || isEndingLive || didReleaseSession || isPaused) return
        if (unmuteOperationID != null) return
        val seatStore = seatStore ?: return
        val wasMuted = presentationState.settings.isMuted
        updateSettings { copy(isMuted = !wasMuted) }
        if (!wasMuted) {
            seatStore.muteMicrophone()
            return
        }
        val operationID = UUID.randomUUID()
        unmuteOperationID = operationID
        seatStore.unmuteMicrophone(object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post {
                    if (unmuteOperationID != operationID) return@post
                    unmuteOperationID = null
                    if (!didReleaseSession && (isPaused ||
                            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != true)) {
                        seatStore.muteMicrophone()
                    }
                }
            }

            override fun onFailure(code: Int, desc: String) {
                mainHandler.post {
                    if (unmuteOperationID != operationID) return@post
                    unmuteOperationID = null
                    if (phase != Phase.LIVING || isEndingLive || didReleaseSession) return@post
                    updateSettings { copy(isMuted = wasMuted) }
                    LiveToast.show(context, context.getString(R.string.livekit_pusher_error_mute_switch_failed))
                }
            }
        })
    }

    private fun endLive() {
        if (phase != Phase.LIVING || isEndingLive || didReleaseSession) return
        isEndingLive = true
        // Invalidate pending opens without closing the currently displayed preview.
        previewOperationID = null
        isPreviewStarting = false
        beautyPanel.dismiss()
        livingView.dismissTransientUi()
        endingOverlay.isVisible = true
        endingOverlay.bringToFront()
        AtomicPusher.shared().stopPush()
    }

    private fun finishLivePresentation() {
        if (liveEndTimeMillis != 0L) return
        livingView.deactivateAudience(resetState = false)
        livingView.deactivateLikes(resetState = false)
        livingView.deactivateComments(resetState = false)
        livingView.deactivateMusic()
        coGuestStore?.deactivate()
        finishPause()
        elapsedTimer?.cancel()
        elapsedTimer = null
        liveEndTimeMillis = System.currentTimeMillis()
        updateElapsedTime()
    }

    private fun settleLive() {
        if (phase != Phase.LIVING || didReleaseSession) return
        finishLivePresentation()
        phase = Phase.SUMMARY
        render()
        cleanupSession()
    }

    private fun exitPage() {
        cleanupSession()
        onExit?.invoke()
    }

    private fun cleanupSession() {
        if (didReleaseSession) return
        didReleaseSession = true
        stopAudioService()
        backgroundDevices = null
        unmuteOperationID = null
        previewOperationID = null
        beautyPanel.dismiss()
        if (phase == Phase.LIVING) finishLivePresentation()
        accountSubscriptionID = null
        LoginStore.shared.removeLoginListener(loginListener)
        lifecycleOwner?.lifecycle?.removeObserver(captureLifecycleObserver)
        thermalStateJob?.cancel()
        thermalStateJob = null
        lifecycleOwner = null
        presentationStateScope?.cancel()
        presentationStateScope = null
        coGuestStateJob?.cancel()
        coGuestStateJob = null
        coGuestStore?.deactivate()
        livingView.deactivateAudience(resetState = true)
        livingView.deactivateLikes(resetState = true)
        livingView.deactivateComments(resetState = true)
        livingView.deactivateMusic()
        prepareView.deactivate()
        prepareView.dismissTransientUi()
        livingView.dismissTransientUi()
        // Detach SDK-backed children before a later page can bind the shared pusher.
        removeView(livingView)
        removeView(videoView)
        stopPreview(closeMicrophone = true)
        releasePusher()
        invalidateLifecycle()
        endingOverlay.isVisible = false
    }

    private fun updateElapsedTime() {
        if (liveStartTimeMillis == 0L) return
        val endTimeMillis = liveEndTimeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val activeEndTimeMillis = if (isPaused) pauseStartTimeMillis else endTimeMillis
        elapsedSeconds = (max(0L, activeEndTimeMillis - liveStartTimeMillis - accumulatedPausedMillis) / ONE_SECOND_MS).toInt()
    }

    private fun currentPauseMillis(): Long =
        if (isPaused) max(0L, System.currentTimeMillis() - pauseStartTimeMillis) else 0L

    private fun finishPause() {
        pauseTimer?.cancel()
        pauseTimer = null
        accumulatedPausedMillis += currentPauseMillis()
        pauseStartTimeMillis = 0L
        pausedSeconds = 0
        isPaused = false
    }

    private fun invalidateLifecycle() {
        startLiveRequestID = null
        elapsedTimer?.cancel()
        elapsedTimer = null
        pauseTimer?.cancel()
        pauseTimer = null
    }

    private fun summaryContext(): PusherSummaryContext = PusherSummaryContext(
        startTimeMillis = liveStartTimeMillis.takeIf { it > 0L },
        endTimeMillis = liveEndTimeMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
        pausedSeconds = ((accumulatedPausedMillis + currentPauseMillis()) / ONE_SECOND_MS).toInt(),
        interruptionMessage = interruptionMessage,
    )

    private fun showBeauty(requestedPhase: Phase) {
        if (!isAttachedToWindow || isEndingLive || didReleaseSession || phase != requestedPhase || phase == Phase.SUMMARY || beautyPanel.isShowing) return
        beautyPanel.show()
    }

    private fun dismissBeautyIfShowing(): Boolean {
        if (!beautyPanel.isShowing) return false
        beautyPanel.dismiss()
        return true
    }

    private fun render() {
        beautyPanel.dismiss()
        updateCoGuestPresentation()
        prepareView.isVisible = phase == Phase.PREPARE
        livingView.isVisible = phase == Phase.LIVING
        summaryView.isVisible = phase == Phase.SUMMARY
        if (phase == Phase.PREPARE) {
            prepareView.bind(liveTitle, coverURL)
        } else if (phase == Phase.LIVING) {
            livingView.updateHost(presentationState.hostName, presentationState.hostAvatarURL)
            livingView.updateElapsedSeconds(elapsedSeconds)
            livingView.updatePause(isPaused, pausedSeconds)
        }
        updateSettingsPresentation()
        updateDevicePresentation()
        if (phase == Phase.SUMMARY && isAttachedToWindow) {
            summaryView.show(summaryContext())
        } else {
            summaryView.hide()
        }
        videoView.isVisible = phase != Phase.SUMMARY
        if (phase != Phase.PREPARE) {
            prepareView.deactivate()
            prepareView.dismissTransientUi()
        }
        if (phase != Phase.LIVING) {
            livingView.dismissTransientUi()
        }
    }

    private fun updateHostProfile() {
        val profile = LoginStore.shared.loginState.loginUserInfo.value
        val name = profile?.nickname?.trim().orEmpty().ifEmpty {
            profile?.userID.orEmpty().ifEmpty { context.getString(R.string.live_barrage_anchor) }
        }
        val avatarURL = profile?.avatarURL.orEmpty()
        if (presentationState.hostName == name && presentationState.hostAvatarURL == avatarURL) return
        presentationState = presentationState.copy(hostName = name, hostAvatarURL = avatarURL)
        if (phase == Phase.LIVING) livingView.updateHost(name, avatarURL)
    }

    private fun updateSettingsPresentation() {
        when (phase) {
            Phase.PREPARE -> prepareView.bindSettings(presentationState.settings)
            Phase.LIVING -> livingView.updateSettings(presentationState.settings)
            Phase.SUMMARY -> Unit
        }
    }

    private fun updateDevicePresentation() {
        if (phase != Phase.LIVING) return
        val settings = presentationState.settings
        livingView.updateDeviceStatus(
            presentationState.deviceStatus, settings.videoQuality, settings.isMuted, settings.captureVolume,
        )
    }

    private fun updateCoGuestPresentation() {
        val isLiving = phase == Phase.LIVING
        val isStageActive = isLiving &&
            (connectedGuestCount > 0 || coGuestLayout != Layout.FLOATING)
        videoView.updateCoGuestVideoState(isLiving, coGuestLayout, connectedGuestCount)
        coGuestBackgroundView.isVisible = isStageActive
        livingView.setCoGuestStageActive(isStageActive)
    }
}
