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
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.basic.permission.PermissionCallback
import com.trtc.uikit.livekit.common.basic.permission.PermissionRequest
import com.trtc.uikit.livekit.common.beauty.BeautyPanel
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.MirrorType
import io.trtc.tuikit.atomicxcore.api.view.CameraView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@SuppressLint("InflateParams")
class SettingsPanel(
    context: Context,
    private val mode: Mode,
) : BottomSheetView(context) {

    enum class Mode {
        PREVIEW,
        CONNECTED,
    }

    var onDismissRequest: (() -> Unit)? = null
    var onRequestCoGuest: (() -> Unit)? = null

    private val root: LinearLayout = LayoutInflater.from(context)
        .inflate(R.layout.livekit_coguest_settings_panel, contentView, false) as LinearLayout
    private val previewContainer: FrameLayout = root.findViewById(R.id.preview_container)
    private val cameraView: CameraView = root.findViewById(R.id.camera_view)
    private val mirrorIcon: ImageView = root.findViewById(R.id.iv_action_mirror)
    private val applyButton: View = root.findViewById(R.id.btn_apply)

    private val previewCornerRadius: Float = 20f * context.resources.displayMetrics.density

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
        root.findViewById<View>(R.id.action_mirror).setDebounceClickListener {
            if (DeviceStore.shared().deviceState.localMirrorType.value != MirrorType.DISABLE) {
                DeviceStore.shared().switchMirror(MirrorType.DISABLE)
            } else {
                DeviceStore.shared().switchMirror(MirrorType.ENABLE)
            }
        }
        root.findViewById<View>(R.id.action_flip).setDebounceClickListener {
            val nowFront = DeviceStore.shared().deviceState.isFrontCamera.value
            DeviceStore.shared().switchCamera(!nowFront)
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
        subscribeJob = panelScope.launch {
            DeviceStore.shared().deviceState.localMirrorType.collect { type ->
                mirrorIcon.setImageResource(
                    if (type != MirrorType.DISABLE) R.drawable.livekit_ic_setting_mirror_on
                    else R.drawable.livekit_ic_setting_mirror_off
                )
            }
        }
    }

    private fun startCameraTest() {
        if (cameraStarted || requestingCameraPermission) return
        val generation = ++previewGeneration
        requestingCameraPermission = true
        PermissionRequest.requestCameraPermissions(context, object : PermissionCallback() {
            override fun onGranted() {
                if (generation != previewGeneration) return
                requestingCameraPermission = false
                if (cameraStarted) return
                if (!isShowing) return
                cameraStarted = true
                if (mode == Mode.PREVIEW) {
                    DeviceStore.shared().switchCamera(true)
                    DeviceStore.shared().switchMirror(MirrorType.ENABLE)
                }
                BeautyPreferences.restore(context)
                DeviceStore.shared().startCameraTest(cameraView, null)
            }

            override fun onDenied() {
                if (generation != previewGeneration) return
                super.onDenied()
                requestingCameraPermission = false
            }
        })
    }

    private fun stopCameraTest() {
        previewGeneration++
        requestingCameraPermission = false
        if (!cameraStarted) return
        cameraStarted = false
        DeviceStore.shared().stopCameraTest()
    }
}
