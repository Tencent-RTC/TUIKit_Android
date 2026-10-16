package com.trtc.uikit.livekit.player.living.coguest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.trtc.uikit.livekit.common.beauty.BeautyPreferences
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.ScreenUtil
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.basic.permission.PermissionCallback
import com.trtc.uikit.livekit.common.basic.permission.PermissionRequest
import com.trtc.uikit.livekit.common.theme.toast.LiveToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CoGuestingPanel(
    context: Context,
    private val liveID: String,
    private val openCameraAfterAccept: StateFlow<Boolean>,
) : BottomSheetView(context) {

    override val sheetHeight: Int
        get() = ScreenUtil.dip2px(160f)

    private val micButton: ImageView
    private val cameraButton: ImageView
    private val settingsButton: ImageView
    private val settingsGap: android.view.View
    private val hangupButton: ImageView

    private var settingsDialog: SettingsPanel? = null
    private val logger = LiveKitLogger.getComponentLogger("CoGuestingPanel")
    private var panelScope: CoroutineScope? = null
    private var seatJob: Job? = null
    private var requestingCameraPermission = false
    private var cameraRequestID = 0
    private var isOpeningCamera = false
    private var allowCameraRestore = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun invalidateCameraRequest(allowRestore: Boolean = false): Boolean {
        val wasOpening = isOpeningCamera
        cameraRequestID++
        requestingCameraPermission = false
        isOpeningCamera = false
        allowCameraRestore = allowRestore
        return wasOpening
    }

    init {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val paddingH = ScreenUtil.dip2px(12f)
            setPadding(paddingH, 0, paddingH, 0)
        }
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_coguest_control_bar, container, true)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        contentView.addView(container, lp)

        micButton = container.findViewById(R.id.livekit_coguest_ctrl_mic)
        cameraButton = container.findViewById(R.id.livekit_coguest_ctrl_camera)
        settingsButton = container.findViewById(R.id.livekit_coguest_ctrl_settings)
        settingsGap = container.findViewById(R.id.livekit_coguest_ctrl_settings_gap)
        hangupButton = container.findViewById(R.id.livekit_coguest_ctrl_hangup)

        container.findViewById<ImageView>(R.id.livekit_coguest_ctrl_hide).visibility =
            android.view.View.GONE

        micButton.setDebounceClickListener { toggleMic() }
        cameraButton.setDebounceClickListener { toggleCamera() }
        settingsButton.setDebounceClickListener { showSettingsDialog() }
        hangupButton.setDebounceClickListener { disconnect() }
    }

    override fun show() {
        super.show()
        startDeviceObservers()
    }

    override fun dismiss() {
        stopDeviceObservers()
        settingsDialog?.dismiss()
        invalidateCameraRequest(allowCameraRestore)
        super.dismiss()
    }

    private fun startDeviceObservers() {
        stopDeviceObservers()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        panelScope = scope
        seatJob = scope.launch {
            launch {
                CoGuestStore.create(liveID).coGuestState.connected.collectLatest { seatList ->
                    val selfUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
                    val seatUserInfo = seatList.find {
                        it.userID == selfUserID
                    } ?: return@collectLatest
                    updateMicState(seatUserInfo.microphoneStatus)
                    updateCameraState(seatUserInfo.cameraStatus)
                }
            }
            launch {
                openCameraAfterAccept.collectLatest { showVideo ->
                    val visibility =
                        if (showVideo) android.view.View.VISIBLE else android.view.View.GONE
                    cameraButton.visibility = visibility
                    settingsButton.visibility = visibility
                    settingsGap.visibility = visibility
                }
            }
        }
    }

    private fun stopDeviceObservers() {
        seatJob?.cancel()
        seatJob = null
        panelScope?.cancel()
        panelScope = null
    }

    private fun updateMicState(status: DeviceStatus) {
        val enabled = status == DeviceStatus.ON
        micButton.setImageResource(
            if (enabled) R.drawable.livekit_ic_player_connected_mic_on
            else R.drawable.livekit_ic_player_connected_mic_off
        )
        micButton.setBackgroundResource(
            if (enabled) R.drawable.livekit_coguest_control_btn_bg
            else R.drawable.livekit_coguest_control_btn_active_bg
        )
    }

    private fun updateCameraState(status: DeviceStatus) {
        val enabled = status == DeviceStatus.ON
        cameraButton.setImageResource(
            if (enabled) R.drawable.livekit_ic_player_connected_camera_on
            else R.drawable.livekit_ic_player_connected_camera_off
        )
        cameraButton.setBackgroundResource(
            if (enabled) R.drawable.livekit_coguest_control_btn_bg
            else R.drawable.livekit_coguest_control_btn_active_bg
        )
    }

    private fun toggleMic() {
        val selfUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val seatInfo = CoGuestStore.create(liveID).coGuestState.connected.value.find {
            it.userID == selfUserID
        }
        if (seatInfo?.microphoneStatus == DeviceStatus.ON) {
            LiveSeatStore.create(liveID).muteMicrophone()
        } else if (seatInfo?.allowOpenMicrophone == true) {
            LiveSeatStore.create(liveID).unmuteMicrophone(null)
        } else if (seatInfo?.allowOpenMicrophone == false) {
            LiveToast.show(
                context,
                context.getString(R.string.livekit_player_coguest_microphone_locked),
                LiveToast.Style.WARNING
            )
        }
    }

    private fun toggleCamera() {
        val selfUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val isCameraOpened = CoGuestStore.create(liveID).coGuestState.connected.value.find {
            it.userID == selfUserID
        }?.cameraStatus == DeviceStatus.ON
        if (isCameraOpened) {
            invalidateCameraRequest()
            DeviceStore.shared().closeLocalCamera()
            return
        }
        if (requestingCameraPermission || isOpeningCamera) return
        val requestID = ++cameraRequestID
        requestingCameraPermission = true
        isOpeningCamera = true
        PermissionRequest.requestCameraPermissions(context, object : PermissionCallback() {
            override fun onGranted() {
                if (requestID != cameraRequestID) return
                requestingCameraPermission = false
                if (!isShowing) {
                    isOpeningCamera = false
                    return
                }
                BeautyPreferences.restore(context)
                DeviceStore.shared().openLocalCamera(
                    DeviceStore.shared().deviceState.isFrontCamera.value,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            mainHandler.post {
                                if (requestID == cameraRequestID) {
                                    isOpeningCamera = false
                                    if (!isShowing) {
                                        DeviceStore.shared().closeLocalCamera()
                                    }
                                } else if (!allowCameraRestore && !isOpeningCamera) {
                                    DeviceStore.shared().closeLocalCamera()
                                }
                            }
                        }

                        override fun onFailure(code: Int, desc: String) {
                            mainHandler.post { if (requestID == cameraRequestID) isOpeningCamera = false }
                        }
                    }
                )
            }

            override fun onDenied() {
                if (requestID != cameraRequestID) return
                super.onDenied()
                requestingCameraPermission = false
                isOpeningCamera = false
            }
        })
    }

    private fun showSettingsDialog() {
        val dialog = settingsDialog ?: SettingsPanel(context, SettingsPanel.Mode.CONNECTED).also {
            settingsDialog = it
        }
        dialog.show()
    }

    private fun disconnect() {
        val activeScope = panelScope
        CoGuestStore.create(liveID).disconnect(object : CompletionHandler {
            override fun onSuccess() {
                dismiss()
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("disconnect failed: code=$code, desc=$desc")
                activeScope?.launch {
                    if (panelScope !== activeScope || !isShowing) return@launch
                    LiveToast.show(context, context.getString(R.string.livekit_coguest_disconnect_failed), LiveToast.Style.WARNING)
                }
            }
        })
    }
}
