package com.trtc.uikit.livekit.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.COMPONENT_LIVE_STREAM
import com.trtc.uikit.livekit.common.basic.LIVE_INTEGRATION_SUCCESSFUL
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.basic.reportAtomicMetrics
import com.trtc.uikit.livekit.common.basic.setComponent
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.beauty.BeautyPreferences
import com.trtc.uikit.livekit.common.theme.popover.AtomicPopover
import com.trtc.uikit.livekit.common.theme.toast.LiveToast
import com.trtc.uikit.livekit.player.living.LivingView
import com.trtc.uikit.livekit.player.living.coguest.CoGuestStatus
import com.trtc.uikit.livekit.player.preview.PreviewView
import com.trtc.uikit.livekit.player.summary.SummaryView
import com.trtc.uikit.livekit.player.video.PlayerVideoView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.AtomicPlayer
import io.trtc.tuikit.atomicxcore.api.live.PlayerListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PlayerViewImpl(
    private val host: PlayerView,
    private val liveID: String,
) {
    private val logger = LiveKitLogger.Companion.getComponentLogger("PlayerItemView")
    private val context: Context = host.context
    private val videoView: PlayerVideoView
    private val overlayView: LivingView
    private val summaryView: SummaryView
    private val previewView: PreviewView
    private val closeButton: ImageButton
    private val player: AtomicPlayer = AtomicPlayer()

    private var isPlaying = false
    private var isJoinedChannel = false
    private var liveInfo: LiveInfo? = null
    private var joinLiveEntranceEnabled = true
    private var isEntranceInitiatedJoin = false
    private data class BackgroundCamera(val liveID: String, val isFront: Boolean)
    private var backgroundCamera: BackgroundCamera? = null
    private var cameraRestoreID: UUID? = null
    private var backgroundMicrophone: String? = null
    private var microphoneRestoreID: UUID? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> suspendCapture()
            Lifecycle.Event.ON_RESUME -> {
                startCoGuestAudioServiceIfNeeded()
                restoreCaptureIfNeeded()
                restoreMicrophoneIfNeeded()
            }
            else -> Unit
        }
    }

    private val _status: MutableStateFlow<PlayerStatus> = MutableStateFlow(PlayerStatus.IDLE)
    val status: StateFlow<PlayerStatus> = _status.asStateFlow()

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_player_item, host, true)
        videoView = host.findViewById(R.id.video_view)
        overlayView = host.findViewById(R.id.player_overlay_view)
        summaryView = host.findViewById(R.id.player_summary_view)
        previewView = host.findViewById(R.id.player_preview_view)
        closeButton = host.findViewById(R.id.iv_close_live)

        videoView.setLiveInfo(LiveInfo(liveID))
        closeButton.setDebounceClickListener {
            handleCloseClick()
        }
        previewView.onTapEnter = {
            handleJoinLiveEntranceTap()
        }
        overlayView.onCoGuestUiStateChanged = ::handleCoGuestUiState
        applyStatusPresentation()
    }

    fun setLiveInfo(liveInfo: LiveInfo) {
        if (this.liveInfo?.liveID != liveInfo.liveID) {
            backgroundCamera = null
            cameraRestoreID = null
            clearBackgroundMicrophone()
        }
        this.liveInfo = liveInfo
        videoView.setLiveInfo(liveInfo)
        previewView.bind(liveInfo)
        overlayView.bind(liveInfo)
    }

    fun setJoinLiveEntranceEnabled(enabled: Boolean) {
        if (joinLiveEntranceEnabled == enabled) return
        joinLiveEntranceEnabled = enabled
        applyStatusPresentation()
    }

    fun startPlay() {
        val currentLiveInfo = liveInfo ?: run {
            logger.error("startPlay failed, liveInfo is null")
            return
        }

        val liveID = currentLiveInfo.liveID
        if (TextUtils.isEmpty(liveID)) {
            logger.error("startPlay failed, roomId is empty")
            return
        }

        clearEndedState()

        if (isPlaying) {
            logger.info("startPlay skipped, already playing, roomId:$liveID")
            return
        }

        logger.info("startPlay, roomId:$liveID")
        player.setView(videoView.coreView)
        player.startPlay("livekit://liveid=${liveID}")

        isPlaying = true
        if (!isJoinedChannel && currentStatus() == PlayerStatus.IDLE) {
            updateStatus(PlayerStatus.PLAYING)
        }
    }

    fun stopPlay() {
        val currentLiveInfo = liveInfo ?: run {
            logger.error("stopPlay failed, liveInfo is null")
            return
        }

        val liveID = currentLiveInfo.liveID
        if (TextUtils.isEmpty(liveID)) {
            logger.error("stopPlay failed, roomId is empty")
            return
        }

        logger.info("stopPlay, roomId:$liveID, isPlaying:$isPlaying")
        if (isPlaying) {
            player.stopPlay()
            isPlaying = false
            if (currentStatus() == PlayerStatus.PLAYING) {
                updateStatus(PlayerStatus.IDLE)
            }
        }
    }

    fun joinChannel(callback: LiveInfoCompletionHandler?) {
        val currentLiveInfo = liveInfo ?: run {
            logger.error("joinChannel failed, liveInfo is null")
            return
        }
        if (isJoinedChannel) {
            callback?.onSuccess(currentLiveInfo)
            return
        }
        val joiningLiveID = currentLiveInfo.liveID
        reportAtomicMetrics(LIVE_INTEGRATION_SUCCESSFUL)
        setComponent(COMPONENT_LIVE_STREAM)
        player.joinChannel(joiningLiveID, object : CompletionHandler {
            override fun onSuccess() {
                if (liveInfo?.liveID != joiningLiveID) {
                    logger.info("joinChannel succeeded for outdated room, leaving, roomId:$joiningLiveID")
                    player.leaveChannel()
                    return
                }
                CoGuestStore.create(joiningLiveID).disconnect(null)
                val entranceInitiated = isEntranceInitiatedJoin
                isEntranceInitiatedJoin = false
                setLiveInfo(currentLiveInfo)
                setJoinedChannel(true)
                updateStatus(PlayerStatus.IN_ROOM)
                if (entranceInitiated) {
                    host.onJoinLiveClicked()
                }
                callback?.onSuccess(currentLiveInfo)
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("joinChannel failed, code:$code, desc:$desc")
                if (liveInfo?.liveID != joiningLiveID) return
                isEntranceInitiatedJoin = false
                showSummaryView()
                callback?.onFailure(code, desc)
            }
        })
    }

    fun leaveChannel() {
        performLeave()
    }

    fun setVolume(volume: Int) {
        player.setVolume(volume)
    }

    fun onAttached() {
        player.addListener(playerListener)
        host.findViewTreeLifecycleOwner()?.let { owner ->
            lifecycleOwner = owner
            owner.lifecycle.addObserver(captureLifecycleObserver)
            overlayView.startCoGuestAudioServiceIfNeeded()
            restoreCaptureIfNeeded()
            restoreMicrophoneIfNeeded()
        }
    }

    fun onDetached() {
        lifecycleOwner?.lifecycle?.removeObserver(captureLifecycleObserver)
        lifecycleOwner = null
        backgroundCamera = null
        cameraRestoreID = null
        clearBackgroundMicrophone()
        player.removeListener(playerListener)
        overlayView.deactivateCoGuest()
        overlayView.deactivateRoomFeatures()
    }

    private fun handleJoinLiveEntranceTap() {
        if (isJoinedChannel) return
        previewView.setJoinEnabled(false)
        isEntranceInitiatedJoin = true
        joinChannel(null)
    }

    private fun startCoGuestAudioServiceIfNeeded() = overlayView.startCoGuestAudioServiceIfNeeded()

    private fun isCurrentGuest(roomID: String): Boolean {
        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        return userID.isNotEmpty() && CoGuestStore.create(roomID).coGuestState.connected.value.any { it.userID == userID }
    }

    private fun suspendCapture() {
        val roomID = liveInfo?.liveID ?: return
        if (!host.isAttachedToWindow || !isJoinedChannel || currentStatus() != PlayerStatus.CO_GUEST) return
        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val seat = CoGuestStore.create(roomID).coGuestState.connected.value.find { it.userID == userID }
        val onSeat = seat != null
        val state = DeviceStore.shared().deviceState
        if (onSeat && backgroundCamera == null && state.cameraStatus.value == DeviceStatus.ON) {
            backgroundCamera = BackgroundCamera(roomID, state.isFrontCamera.value)
        }
        cameraRestoreID = null
        val wasOpening = overlayView.invalidateCoGuestCameraRequests(backgroundCamera != null)
        if ((onSeat && backgroundCamera != null) || wasOpening) {
            DeviceStore.shared().closeLocalCamera()
        }
        microphoneRestoreID = null
        if (seat?.microphoneStatus == DeviceStatus.ON) {
            if (backgroundMicrophone == null) backgroundMicrophone = roomID
            LiveSeatStore.create(roomID).muteMicrophone()
        }
    }

    private fun restoreCaptureIfNeeded() {
        val camera = backgroundCamera ?: return
        val owner = lifecycleOwner ?: return
        if (!host.isAttachedToWindow || cameraRestoreID != null || !isJoinedChannel ||
            currentStatus() != PlayerStatus.CO_GUEST || liveInfo?.liveID != camera.liveID ||
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !isCurrentGuest(camera.liveID)) return
        val operationID = UUID.randomUUID()
        cameraRestoreID = operationID
        BeautyPreferences.restore(context)
        DeviceStore.shared().openLocalCamera(camera.isFront, object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post { completeCameraRestore(camera, operationID, true) }
            }

            override fun onFailure(code: Int, desc: String) {
                mainHandler.post { completeCameraRestore(camera, operationID, false) }
            }
        })
    }

    private fun completeCameraRestore(camera: BackgroundCamera, operationID: UUID, succeeded: Boolean) {
        if (cameraRestoreID != operationID) {
            if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true ||
                (liveInfo?.liveID == camera.liveID &&
                    (!isJoinedChannel || currentStatus() != PlayerStatus.CO_GUEST))) {
                DeviceStore.shared().closeLocalCamera()
            }
            return
        }
        cameraRestoreID = null
        if (!succeeded || backgroundCamera != camera || !host.isAttachedToWindow ||
            !isJoinedChannel || currentStatus() != PlayerStatus.CO_GUEST || liveInfo?.liveID != camera.liveID ||
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true ||
            !isCurrentGuest(camera.liveID)) {
            backgroundCamera = null
            DeviceStore.shared().closeLocalCamera()
            return
        }
        backgroundCamera = null
    }

    private fun clearBackgroundMicrophone() {
        backgroundMicrophone = null
        microphoneRestoreID = null
    }

    private fun restoreMicrophoneIfNeeded() {
        val roomID = backgroundMicrophone ?: return
        val owner = lifecycleOwner ?: return
        if (!host.isAttachedToWindow || microphoneRestoreID != null || !isJoinedChannel ||
            currentStatus() != PlayerStatus.CO_GUEST || liveInfo?.liveID != roomID ||
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val seat = CoGuestStore.create(roomID).coGuestState.connected.value.find { it.userID == userID } ?: return
        if (!seat.allowOpenMicrophone) {
            clearBackgroundMicrophone()
            return
        }
        val operationID = UUID.randomUUID()
        microphoneRestoreID = operationID
        LiveSeatStore.create(roomID).unmuteMicrophone(object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post {
                    if (microphoneRestoreID != operationID) {
                        if (backgroundMicrophone == roomID &&
                            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) {
                            LiveSeatStore.create(roomID).muteMicrophone()
                        }
                        return@post
                    }
                    microphoneRestoreID = null
                    if (backgroundMicrophone != roomID || !host.isAttachedToWindow || !isJoinedChannel ||
                        currentStatus() != PlayerStatus.CO_GUEST || liveInfo?.liveID != roomID ||
                        lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true ||
                        !isCurrentGuest(roomID)) {
                        clearBackgroundMicrophone()
                        if (isCurrentGuest(roomID)) LiveSeatStore.create(roomID).muteMicrophone()
                        return@post
                    }
                    backgroundMicrophone = null
                }
            }

            override fun onFailure(code: Int, desc: String) {
                mainHandler.post { if (microphoneRestoreID == operationID) clearBackgroundMicrophone() }
            }
        })
    }

    private fun handleCoGuestUiState(uiState: CoGuestStatus) {
        if (liveInfo == null) return
        if (!isJoinedChannel) return
        when (uiState) {
            CoGuestStatus.REQUESTING, CoGuestStatus.COGUESTING -> {
                updateStatus(PlayerStatus.CO_GUEST)
                if (uiState == CoGuestStatus.COGUESTING) restoreMicrophoneIfNeeded()
            }

            CoGuestStatus.IDLE -> {
                backgroundCamera = null
                cameraRestoreID = null
                clearBackgroundMicrophone()
                if (currentStatus() == PlayerStatus.CO_GUEST) {
                    updateStatus(PlayerStatus.IN_ROOM)
                }
            }
        }
    }

    private fun setJoinedChannel(isJoinedChannel: Boolean) {
        if (this.isJoinedChannel == isJoinedChannel) return
        this.isJoinedChannel = isJoinedChannel
        overlayView.setJoinedChannel(isJoinedChannel)
    }

    private fun performLeave() {
        backgroundCamera = null
        cameraRestoreID = null
        clearBackgroundMicrophone()
        if (!isJoinedChannel) return
        logger.info("leaveChannel, roomId:${liveInfo?.liveID}")
        overlayView.deactivateCoGuest()
        overlayView.deactivateRoomFeatures()
        player.leaveChannel()
        setJoinedChannel(false)
        if (currentStatus() != PlayerStatus.END) {
            updateStatus(if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.IDLE)
        }
    }

    private fun showSummaryView(reason: String? = null) {
        val currentLiveInfo = liveInfo ?: return
        summaryView.bind(currentLiveInfo, reason)
        updateStatus(PlayerStatus.END)
        applyStatusPresentation()
    }

    private fun clearEndedState() {
        if (currentStatus() != PlayerStatus.END) return
        updateStatus(if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.IDLE)
    }

    private fun handleCloseClick() {
        performLeave()
        host.onCloseClicked()
    }

    private fun updateStatus(next: PlayerStatus) {
        if (_status.value == next) return
        _status.value = next
        applyStatusPresentation()
    }

    private fun applyStatusPresentation() {
        val current = currentStatus()
        val showsEntrance = joinLiveEntranceEnabled &&
            (current == PlayerStatus.IDLE || current == PlayerStatus.PLAYING)
        val showsOverlay = current == PlayerStatus.IN_ROOM || current == PlayerStatus.CO_GUEST
        val showsSummary = current == PlayerStatus.END

        previewView.visibility = if (showsEntrance) View.VISIBLE else View.GONE
        overlayView.visibility = if (showsOverlay) View.VISIBLE else View.GONE
        summaryView.visibility = if (showsSummary) View.VISIBLE else View.GONE
        videoView.visibility = if (showsSummary) View.GONE else View.VISIBLE
        closeButton.visibility = if (showsOverlay) View.VISIBLE else View.GONE

        if (showsSummary) {
            summaryView.bringToFront()
        }
        if (showsEntrance) {
            previewView.setJoinEnabled(true)
        }
    }

    private fun currentStatus(): PlayerStatus = _status.value

    private val playerListener = object : PlayerListener() {
        override fun liveEnded(reason: EndedReason) {
            logger.info("onLiveEnded, liveId:$liveID, reason:$reason,")
            if (currentStatus() == PlayerStatus.END) return
            finishRoom()
            showSummaryView()
            AtomicPopover.dismissAll()
        }

        override fun kickedOut(reason: KickedOutReason, message: String) {
            logger.info("onKickedOut, liveId:$liveID, reason:$reason, message:$message")
            if (!isJoinedChannel) return
            val messageResID = when (reason) {
                KickedOutReason.BY_ADMIN -> R.string.livekit_player_interruption_removed
                KickedOutReason.BY_LOGGED_ON_OTHER_DEVICE ->
                    R.string.livekit_player_interruption_other_device
                KickedOutReason.BY_SERVER -> R.string.livekit_player_interruption_server
                KickedOutReason.FOR_NETWORK_DISCONNECTED ->
                    R.string.livekit_player_interruption_network
                KickedOutReason.FOR_LIVE_CHANNEL_EXPIRED ->
                    R.string.livekit_player_interruption_expired
            }
            val reasonText = context.getString(messageResID)
            finishRoom()
            showSummaryView(reasonText)
            AtomicPopover.dismissAll()
            LiveToast.show(context, reasonText, LiveToast.Style.INFO)
        }
    }

    private fun finishRoom() {
        isEntranceInitiatedJoin = false
        backgroundCamera = null
        cameraRestoreID = null
        clearBackgroundMicrophone()
        overlayView.deactivateCoGuest()
        overlayView.deactivateRoomFeatures()
        if (isPlaying) {
            player.stopPlay()
            isPlaying = false
        }
        if (isJoinedChannel) {
            player.leaveChannel()
            setJoinedChannel(false)
        }
    }
}
