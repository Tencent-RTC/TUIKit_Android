package com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.panel

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.common.ui.RoundFrameLayout
import com.trtc.uikit.livekit.component.beauty.BeautyUtils
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceStore
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.view.CameraView
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

@SuppressLint("ViewConstructor")
class VideoCoGuestSettingsDialog(
    context: Context,
    private val audienceStore: AudienceStore,
    private val seatIndex: Int,
) : AtomicPopover(context), AudienceStore.AudienceViewListener {

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("VideoCoGuestSettingsDialog")
    }

    private lateinit var roundFrameLayout: RoundFrameLayout
    private lateinit var cameraView: CameraView
    private lateinit var buttonApplyLinkMic: Button
    private lateinit var recycleSettingsOption: RecyclerView

    init {
        initView()
    }

    @SuppressLint("InflateParams")
    protected fun initView() {
        val view =
            LayoutInflater.from(context).inflate(R.layout.livekit_dialog_link_video_settings, null)
        bindViewId(view)

        initRecycleSettingsOption()
        initCameraView()
        initApplyLinkMicButton()
        initRoundFrameLayout()

        setContent(view)
    }

    private fun bindViewId(view: android.view.View) {
        cameraView = view.findViewById(R.id.preview_audience_video)
        buttonApplyLinkMic = view.findViewById(R.id.btn_apply_link_mic)
        recycleSettingsOption = view.findViewById(R.id.video_settings_options)
        roundFrameLayout = view.findViewById(R.id.fl_preview_audience_video)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        audienceStore.addAudienceViewListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        audienceStore.removeAudienceViewListener(this)
        audienceStore.getDeviceStore().stopCameraTest()
    }

    private fun initRoundFrameLayout() {
        roundFrameLayout.setRadius(ScreenUtil.dip2px(16f))
    }

    private fun initApplyLinkMicButton() {
        buttonApplyLinkMic.setOnClickListener { view ->
            if (!view.isEnabled) {
                return@setOnClickListener
            }
            view.isEnabled = false
            AtomicToast.show(context, context.getString(R.string.common_toast_apply_link_mic), AtomicToast.Style.INFO)
            LOGGER.info("requestMicrophonePermissions success")
            ContextProvider.getApplicationContext()?.apply {
                PermissionRequest.requestCameraPermissions(
                    this,
                    object : PermissionCallback() {
                        override fun onGranted() {
                            LOGGER.info("requestCameraPermissions:[onGranted]")
                            PermissionRequest.requestMicrophonePermissions(
                                this@apply,
                                object : PermissionCallback() {
                                    override fun onGranted() {
                                        PermissionRequest.sendRequestCompleteEvent()
                                        audienceStore.getViewStore()
                                            .updateTakeSeatState(true)
                                        audienceStore.getViewStore()
                                            .updateOpenCameraAfterTakeSeatState(true)
                                        this@VideoCoGuestSettingsDialog.audienceStore.getCoGuestStore()
                                            .applyForSeat(
                                                seatIndex,
                                                60,
                                                true.toString(),
                                                completionHandler {
                                                    onSuccess {
                                                        audienceStore.getViewStore()
                                                            .updateTakeSeatState(false)
                                                    }
                                                    onError { code, _ ->
                                                        audienceStore.getViewStore()
                                                            .updateTakeSeatState(false)
                                                        ErrorLocalized.onError(code)
                                                    }
                                                })
                                    }

                                    override fun onDenied() {
                                        LOGGER.error("requestCameraPermissions:[onDenied]")
                                        PermissionRequest.sendRequestCompleteEvent()
                                    }
                                })
                        }

                        override fun onDenied() {
                            LOGGER.error("requestCameraPermissions:[onDenied]")
                            PermissionRequest.sendRequestCompleteEvent()
                        }
                    })
            }
            dismiss()
        }
    }

    private fun initCameraView() {
        ContextProvider.getApplicationContext()?.apply {
            PermissionRequest.requestCameraPermissions(
                this,
                object : PermissionCallback() {
                    override fun onGranted() {
                        audienceStore.getDeviceStore().switchCamera(true)
                        DeviceStore.shared().startCameraTest(cameraView, null)
                    }
                })
        }
    }

    private fun initRecycleSettingsOption() {
        recycleSettingsOption.layoutManager = GridLayoutManager(context, 2)
        val adapter = VideoCoGuestSettingsAdapter(context)
        adapter.setOnItemClickListener(object : VideoCoGuestSettingsAdapter.OnItemClickListener {
            override fun onBeautyItemClicked() {
                BeautyUtils.showBeautyDialog(context)
            }

            override fun onFlipItemClicked() {
                val isFront = audienceStore.getDeviceStore().deviceState.isFrontCamera.value
                audienceStore.getDeviceStore().switchCamera(!isFront)
            }
        })
        recycleSettingsOption.adapter = adapter
    }

    override fun onRoomDismissed(roomId: String) {
        dismiss()
        BeautyUtils.resetBeauty()
        BeautyUtils.dismissBeautyDialog()
    }

    private val onMyItemClickListener = object : VideoCoGuestSettingsAdapter.OnItemClickListener {
        override fun onBeautyItemClicked() {
            BeautyUtils.showBeautyDialog(context)
        }

        override fun onFlipItemClicked() {
            val isFront = audienceStore.getDeviceStore().deviceState.isFrontCamera.value
            audienceStore.getDeviceStore().switchCamera(!isFront)
        }
    }
}
