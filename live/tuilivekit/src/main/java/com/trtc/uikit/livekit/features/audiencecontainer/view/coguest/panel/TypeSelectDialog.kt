package com.trtc.uikit.livekit.features.audiencecontainer.view.coguest.panel

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceStore
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast


@SuppressLint("ViewConstructor")
class TypeSelectDialog(
    context: Context,
    private val audienceStore: AudienceStore,
    private val seatIndex: Int,
) : AtomicPopover(context), AudienceStore.AudienceViewListener {

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("TypeSelectDialog")
    }

    private lateinit var imageLinkSettings: ImageView
    private lateinit var layoutLinkVideo: ConstraintLayout
    private lateinit var layoutLinkAudio: ConstraintLayout

    init {
        initView()
    }

    private fun initView() {
        val view =
            LayoutInflater.from(context).inflate(R.layout.livekit_dialog_link_mic_selector, null)
        bindViewId(view)

        initLinkSettingsView()
        initLinkVideoView()
        initLinkAudioView()

        setContent(view)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        audienceStore.addAudienceViewListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        audienceStore.removeAudienceViewListener(this)
    }

    private fun bindViewId(view: android.view.View) {
        imageLinkSettings = view.findViewById(R.id.iv_link_settings)
        layoutLinkVideo = view.findViewById(R.id.cl_link_video)
        layoutLinkAudio = view.findViewById(R.id.cl_link_audio)
    }

    private fun initLinkAudioView() {
        layoutLinkAudio.setOnClickListener { view ->
            if (!view.isEnabled) {
                return@setOnClickListener
            }
            view.isEnabled = false
            applyLinkMic(false)
        }
    }

    private fun initLinkVideoView() {
        layoutLinkVideo.setOnClickListener { view ->
            if (!view.isEnabled) {
                return@setOnClickListener
            }
            view.isEnabled = false
            applyLinkMic(true)
        }
    }

    private fun initLinkSettingsView() {
        imageLinkSettings.setOnClickListener {
            val settingsDialog = VideoCoGuestSettingsDialog(context, audienceStore, seatIndex)
            settingsDialog.show()
            dismiss()
        }
    }

    private fun applyLinkMic(openCamera: Boolean) {
        AtomicToast.show(
            context,
            context.getString(R.string.common_toast_apply_link_mic),
            AtomicToast.Style.INFO
        )
        ContextProvider.getApplicationContext()?.apply {
            PermissionRequest.requestMicrophonePermissions(
                this,
                object : PermissionCallback() {
                    override fun onGranted() {
                        if (openCamera) {
                            PermissionRequest.requestCameraPermissions(
                                this@apply,
                                object : PermissionCallback() {
                                    override fun onGranted() {
                                        LOGGER.info("requestCameraPermissions:[onGranted]")
                                        PermissionRequest.sendRequestCompleteEvent()
                                        audienceStore.getViewStore()
                                            .updateTakeSeatState(true)
                                        audienceStore.getViewStore()
                                            .updateOpenCameraAfterTakeSeatState(openCamera)
                                        audienceStore.getCoGuestStore().applyForSeat(
                                            seatIndex, 60, openCamera.toString(),
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
                        } else {
                            PermissionRequest.sendRequestCompleteEvent()
                            audienceStore.getViewStore()
                                .updateTakeSeatState(true)
                            audienceStore.getViewStore()
                                .updateOpenCameraAfterTakeSeatState(openCamera)
                            audienceStore.getCoGuestStore().applyForSeat(
                                seatIndex, 60, openCamera.toString(),
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
                    }

                    override fun onDenied() {
                        LOGGER.error("requestCameraPermissions:[onDenied]")
                        PermissionRequest.sendRequestCompleteEvent()
                    }
                })
        }
        dismiss()
    }

    override fun onRoomDismissed(roomId: String) {
        dismiss()
    }
}
