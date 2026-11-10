package com.trtc.uikit.livekit.player.living.coguest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.trtc.uikit.livekit.common.beauty.BeautyPreferences
import com.trtc.uikit.livekit.common.theme.foregroundservice.AudioForegroundService
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import com.trtc.uikit.livekit.common.theme.imageloader.ImageOptions
import com.trtc.uikit.livekit.common.theme.permission.PermissionCallback
import com.trtc.uikit.livekit.common.theme.permission.PermissionRequest
import com.trtc.uikit.livekit.common.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.LiveToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.GuestListener
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.NoResponseReason
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.logging.Logger

enum class CoGuestStatus {
    IDLE,
    REQUESTING,
    COGUESTING
}

class CoGuestIcon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    val uiState: MutableStateFlow<CoGuestStatus> = MutableStateFlow(CoGuestStatus.IDLE)
    val openCameraAfterAccept: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var onUiStateChanged: ((CoGuestStatus) -> Unit)? = null

    private var liveInfo: LiveInfo? = null

    private var applyDialog: RequestingPanel? = null
    private var controlDialog: CoGuestingPanel? = null
    private var connectedJob: Job? = null
    private var uiStateIconJob: Job? = null
    private var connected: List<SeatUserInfo> = emptyList()
    private var isOpeningCamera = false
    private var allowCameraRestore = false
    private var isOpeningMicrophone = false
    private var microphoneOpened = false
    private var audioServiceStarted = false
    private var awaitingAcceptedSeat = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logger = Logger.getLogger("CoGuestIcon")

    private fun updateUiState(state: CoGuestStatus) {
        if (state == CoGuestStatus.IDLE) awaitingAcceptedSeat = false
        if (uiState.value == state) return
        if (uiState.value == CoGuestStatus.COGUESTING && state == CoGuestStatus.IDLE) {
            invalidateMicrophoneRequests()
            microphoneOpened = false
            stopAudioService()
            DeviceStore.shared().closeLocalMicrophone()
        }
        uiState.value = state
        onUiStateChanged?.invoke(state)
    }

    fun startAudioServiceIfNeeded() {
        if (!microphoneOpened || !isAttachedToWindow ||
            uiState.value != CoGuestStatus.COGUESTING || liveInfo == null ||
            findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return
        AudioForegroundService.start(
            context, context.applicationInfo.loadLabel(context.packageManager).toString(),
            context.getString(R.string.livekit_coguest_service_description), R.drawable.livekit_coguest_idle
        )
        audioServiceStarted = true
    }

    private fun stopAudioService() {
        if (!audioServiceStarted) return
        audioServiceStarted = false
        AudioForegroundService.stop(context)
    }

    private val guestListener = object : GuestListener() {
        override fun onGuestApplicationResponded(isAccept: Boolean, hostUser: LiveUserInfo) {
            logger.info("onGuestApplicationResponded: $isAccept, $hostUser")
            if (isAccept) {
                applyDialog?.dismiss()
                val openCamera = openCameraAfterAccept.value
                val selfUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
                val currentLiveID = liveInfo?.liveID
                awaitingAcceptedSeat = selfUserID.isEmpty() || currentLiveID == null ||
                    CoGuestStore.create(currentLiveID).coGuestState.connected.value.none { it.userID == selfUserID }
                updateUiState(CoGuestStatus.COGUESTING)
                requestMicThenCameraPermissions(openCamera)
            } else {
                LiveToast.show(
                    context,
                    context.getString(R.string.livekit_coguest_apply_rejected),
                    LiveToast.Style.INFO
                )
                openCameraAfterAccept.value = false
                updateUiState(CoGuestStatus.IDLE)
            }
        }

        override fun onGuestApplicationNoResponse(reason: NoResponseReason) {
            logger.info("onGuestApplicationNoResponse: $reason")
            val msgRes = when (reason) {
                NoResponseReason.ALREADY_SEATED -> R.string.livekit_coguest_apply_already_seated
                else -> R.string.livekit_coguest_apply_timeout
            }
            LiveToast.show(context, context.getString(msgRes), LiveToast.Style.INFO)
            openCameraAfterAccept.value = false
            updateUiState(CoGuestStatus.IDLE)
        }

        override fun onKickedOffSeat(seatIndex: Int, hostUser: LiveUserInfo) {
            logger.info("onKickedOffSeat: $seatIndex, $hostUser")
            invalidateCameraRequests(false)
            invalidateMicrophoneRequests()
            LiveToast.show(
                context,
                context.getString(R.string.livekit_coguest_kicked_off),
                LiveToast.Style.INFO
            )
            DeviceStore.shared().closeLocalMicrophone()
            DeviceStore.shared().closeLocalCamera()
            updateUiState(CoGuestStatus.IDLE)
        }
    }

    private fun requestMicThenCameraPermissions(openCameraAfterMic: Boolean) {
        if (openCameraAfterMic) isOpeningCamera = true
        isOpeningMicrophone = true
        PermissionRequest.requestMicrophonePermissions(context, object : PermissionCallback() {
            override fun onGranted() {
                if (isAttachedToWindow && uiState.value == CoGuestStatus.COGUESTING && liveInfo != null &&
                    findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) {
                    DeviceStore.shared().openLocalMicrophone(object : CompletionHandler {
                        override fun onSuccess() {
                            mainHandler.post {
                                isOpeningMicrophone = false
                                if (!isAttachedToWindow || liveInfo == null ||
                                    uiState.value != CoGuestStatus.COGUESTING) {
                                    DeviceStore.shared().closeLocalMicrophone()
                                } else {
                                    microphoneOpened = true
                                    startAudioServiceIfNeeded()
                                }
                            }
                        }

                        override fun onFailure(code: Int, desc: String) {
                            mainHandler.post { isOpeningMicrophone = false }
                        }
                    })
                } else {
                    isOpeningMicrophone = false
                }
                if (openCameraAfterAccept.value) requestCameraPermissionThenOpen()
            }

            override fun onDenied() {
                super.onDenied()
                isOpeningMicrophone = false
                if (openCameraAfterAccept.value) requestCameraPermissionThenOpen()
            }
        })
    }

    private fun requestCameraPermissionThenOpen() {
        if (!isAttachedToWindow || !openCameraAfterAccept.value) {
            isOpeningCamera = false
            return
        }
        PermissionRequest.requestCameraPermissions(context, object : PermissionCallback() {
            override fun onGranted() {
                if (!isAttachedToWindow || uiState.value != CoGuestStatus.COGUESTING ||
                    liveInfo == null || !openCameraAfterAccept.value) {
                    isOpeningCamera = false
                    return
                }
                BeautyPreferences.restore(context)
                DeviceStore.shared().openLocalCamera(
                    DeviceStore.shared().deviceState.isFrontCamera.value,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            mainHandler.post {
                                isOpeningCamera = false
                                if (!isAttachedToWindow || uiState.value != CoGuestStatus.COGUESTING ||
                                    liveInfo == null || !openCameraAfterAccept.value) {
                                    if (!allowCameraRestore) DeviceStore.shared().closeLocalCamera()
                                }
                            }
                        }

                        override fun onFailure(code: Int, desc: String) {
                            mainHandler.post { isOpeningCamera = false }
                        }
                    }
                )
            }

            override fun onDenied() {
                super.onDenied()
                isOpeningCamera = false
            }
        })
    }

    init {
        scaleType = ScaleType.FIT_CENTER
        isClickable = true
        isFocusable = true
        setImageResource(R.drawable.livekit_coguest_idle)
        setDebounceClickListener { onEntryClick() }
    }

    fun bind(liveInfo: LiveInfo) {
        if (this.liveInfo?.liveID == liveInfo.liveID && this.liveInfo?.liveID?.isNotEmpty() == true) {
            this.liveInfo = liveInfo
        }
        stopObserving()
        this.liveInfo = liveInfo
        startObserving()
    }

    fun invalidateCameraRequests(allowRestore: Boolean): Boolean {
        val wasOpening = isOpeningCamera
        val panelWasOpening = controlDialog?.invalidateCameraRequest(allowRestore) == true
        isOpeningCamera = false
        allowCameraRestore = allowRestore
        return wasOpening || panelWasOpening
    }

    private fun invalidateMicrophoneRequests() {
        isOpeningMicrophone = false
    }

    fun deactivate() {
        invalidateCameraRequests(false)
        invalidateMicrophoneRequests()
        microphoneOpened = false
        stopAudioService()
        stopObserving()
        connected = emptyList()
        liveInfo = null
        applyDialog?.dismissAll()
        applyDialog = null
        controlDialog?.dismiss()
        controlDialog = null
        openCameraAfterAccept.value = false
        setImageResource(R.drawable.livekit_coguest_idle)
        DeviceStore.shared().closeLocalMicrophone()
        DeviceStore.shared().closeLocalCamera()
        updateUiState(CoGuestStatus.IDLE)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        invalidateCameraRequests(false)
        invalidateMicrophoneRequests()
        microphoneOpened = false
        stopAudioService()
        stopObserving()
        applyDialog?.dismissAll()
        controlDialog?.dismiss()
        controlDialog = null
    }

    private fun startObserving() {
        val owner = findViewTreeLifecycleOwner() ?: return
        val store = CoGuestStore.create(this.liveInfo?.liveID!!)
        connected = store.coGuestState.connected.value
        store.addGuestListener(guestListener)
        connectedJob = owner.lifecycleScope.launch {
            store.coGuestState.connected.collectLatest { connectedList ->
                logger.info("connectedList: $connectedList")
                if (uiState.value == CoGuestStatus.REQUESTING) {
                    return@collectLatest
                }
                val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID
                val meConnected = selfID != null && connectedList.any { it.userID == selfID }
                if (awaitingAcceptedSeat) {
                    if (!meConnected) return@collectLatest
                    awaitingAcceptedSeat = false
                }
                updateUiState(
                    when {
                        meConnected -> CoGuestStatus.COGUESTING
                        else -> CoGuestStatus.IDLE
                    }
                )

                val lastSeatUserInfo = connected.find {
                    it.userID == selfID
                }
                connected = connectedList
                val seatUserInfo = connectedList.find {
                    it.userID == selfID
                }
                if (lastSeatUserInfo == null || seatUserInfo == null) return@collectLatest

                if (lastSeatUserInfo.allowOpenMicrophone != seatUserInfo.allowOpenMicrophone) {
                    if (seatUserInfo.allowOpenMicrophone) {
                        LiveToast.show(context, context.getString(R.string.livekit_player_coguest_microphone_unlocked))
                    } else {
                        LiveToast.show(context, context.getString(R.string.livekit_player_coguest_microphone_locked))
                    }
                }
            }
        }
        uiStateIconJob = owner.lifecycleScope.launch {
            uiState.collectLatest { state ->
                applyUiState(state)
            }
        }
    }

    private fun stopObserving() {
        this.liveInfo?.liveID?.let { CoGuestStore.create(it).removeGuestListener(guestListener) }
        connectedJob?.cancel()
        connectedJob = null
        uiStateIconJob?.cancel()
        uiStateIconJob = null
    }

    private fun onEntryClick() {
        when (uiState.value) {
            CoGuestStatus.IDLE -> showApplyDialog()
            CoGuestStatus.REQUESTING -> showApplyDialog()
            CoGuestStatus.COGUESTING -> showControlDialog()
        }
    }

    private fun showApplyDialog() {
        val info = liveInfo ?: return
        val dialog = applyDialog ?: RequestingPanel(
            context, info, uiState, openCameraAfterAccept, ::updateUiState
        ).also { applyDialog = it }
        dialog.show()
    }

    private fun showControlDialog() {
        val info = liveInfo ?: return
        val dialog = controlDialog ?: CoGuestingPanel(
            context, info.liveID, openCameraAfterAccept
        ).also { controlDialog = it }
        dialog.show()
    }

    private fun applyUiState(next: CoGuestStatus) {
        if (next == CoGuestStatus.IDLE) openCameraAfterAccept.value = false
        when (next) {
            CoGuestStatus.IDLE, CoGuestStatus.REQUESTING -> {
                setImageResource(R.drawable.livekit_coguest_idle)
                controlDialog?.dismiss()
            }
            CoGuestStatus.COGUESTING -> {
                applyDialog?.dismissAll()
                val options = ImageOptions.Builder().asGif(true).build()
                ImageLoader.load(context, this, R.drawable.livekit_coguest_wave, options)
            }
        }
    }
}
