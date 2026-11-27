package com.trtc.uikit.livekit.player.living.coguest

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.trtc.uikit.livekit.common.beauty.BeautyPreferences
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.LiveDevicePermission
import com.trtc.uikit.livekit.common.beauty.BeautyPanel
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.MirrorType
import io.trtc.tuikit.atomicxcore.api.view.CameraView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@SuppressLint("InflateParams")
class SettingsPanel(
    context: Context,
    private val mode: Mode,
) : BottomSheetView(context) {

    enum class Mode {
        PREVIEW, CONNECTED,
    }

    var onDismissRequest: (() -> Unit)? = null
    var onRequestCoGuest: (() -> Unit)? = null

    private val root: LinearLayout = LayoutInflater.from(context)
        .inflate(R.layout.livekit_coguest_settings_panel, contentView, false) as LinearLayout
    private val previewContainer: FrameLayout = root.findViewById(R.id.preview_container)
    private val cameraView: CameraView = root.findViewById(R.id.camera_view)
    private val mirrorAction: View = root.findViewById(R.id.action_mirror)
    private val mirrorIcon: ImageView = root.findViewById(R.id.iv_action_mirror)
    private val applyButton: View = root.findViewById(R.id.btn_apply)

    private val previewCornerRadius: Float = 20f * context.resources.displayMetrics.density

    private companion object {
        const val DISABLED_ALPHA = 0.46f
    }

    private val beautyPanel = BeautyPanel(context)
    private var cameraStarted = false
    private var requestingCameraPermission = false
    private var previewGeneration = 0

    private val panelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var subscribeJob: Job? = null

    init {
        previewContainer.visibility = if (mode == Mode.PREVIEW) View.VISIBLE else View.GONE
        applyButton.visibility = if (mode == Mode.PREVIEW) View.VISIBLE else View.GONE
        applyPreviewCornerRadius()

        root.findViewById<View>(R.id.iv_back).setDebounceClickListener {
            onDismissRequest?.invoke()
            dismiss()
        }
        root.findViewById<View>(R.id.action_beauty).setDebounceClickListener {
            beautyPanel.show()
        }
        mirrorAction.setDebounceClickListener {
            val deviceState = DeviceStore.shared().deviceState
            if (deviceState.isFrontCamera.value) {
                val mirrorType = if (deviceState.localMirrorType.value != MirrorType.DISABLE) {
                    MirrorType.DISABLE
                } else {
                    MirrorType.ENABLE
                }
                DeviceStore.shared().switchMirror(mirrorType)
            }
        }
        root.findViewById<View>(R.id.action_flip).setDebounceClickListener {
            val deviceState = DeviceStore.shared().deviceState
            val nextFront = !deviceState.isFrontCamera.value
            if (!nextFront && deviceState.localMirrorType.value != MirrorType.DISABLE) {
                DeviceStore.shared().switchMirror(MirrorType.DISABLE)
            }
            DeviceStore.shared().switchCamera(nextFront)
        }
        applyButton.setDebounceClickListener {
            onRequestCoGuest?.invoke()
        }

        contentView.addView(root)
    }

    private fun applyPreviewCornerRadius() {
        previewContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, previewCornerRadius)
            }
        }
        previewContainer.clipToOutline = true
        previewContainer.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            view.invalidateOutline()
        }
    }

    override fun show() {
        super.show()
        if (mode == Mode.PREVIEW) {
            startCameraTest()
        }
        subscribeStates()
    }

    override fun dismiss() {
        beautyPanel.dismiss()
        subscribeJob?.cancel()
        subscribeJob = null
        stopCameraTest()
        super.dismiss()
    }

    private fun subscribeStates() {
        subscribeJob?.cancel()
        val deviceState = DeviceStore.shared().deviceState
        subscribeJob = panelScope.launch {
            combine(deviceState.localMirrorType, deviceState.isFrontCamera) { mirrorType, isFront ->
                mirrorType to isFront
            }.collect { (mirrorType, isFront) ->
                mirrorIcon.setImageResource(
                    if (mirrorType != MirrorType.DISABLE) R.drawable.livekit_ic_setting_mirror_on
                    else R.drawable.livekit_ic_setting_mirror_off
                )
                mirrorAction.isEnabled = isFront
                mirrorAction.alpha = if (isFront) 1f else DISABLED_ALPHA
            }
        }
    }

    private fun startCameraTest() {
        if (cameraStarted || requestingCameraPermission) return
        val generation = ++previewGeneration
        requestingCameraPermission = true
        panelScope.launch {
            val cameraGranted = LiveDevicePermission.requestCameraPermission(context)
            if (generation != previewGeneration) return@launch
            if (!cameraGranted || cameraStarted || !isShowing) {
                requestingCameraPermission = false
                return@launch
            }
            val microphoneGranted = LiveDevicePermission.requestMicrophonePermission(context)
            if (generation != previewGeneration) return@launch
            requestingCameraPermission = false
            if (!microphoneGranted || cameraStarted || !isShowing) return@launch
            cameraStarted = true
            if (mode == Mode.PREVIEW) {
                DeviceStore.shared().switchCamera(true)
                DeviceStore.shared().switchMirror(MirrorType.ENABLE)
            }
            BeautyPreferences.restore(context)
            DeviceStore.shared().startCameraTest(cameraView, null)
        }
    }

    private fun stopCameraTest() {
        previewGeneration++
        requestingCameraPermission = false
        if (!cameraStarted) return
        cameraStarted = false
        DeviceStore.shared().stopCameraTest()
    }
}
