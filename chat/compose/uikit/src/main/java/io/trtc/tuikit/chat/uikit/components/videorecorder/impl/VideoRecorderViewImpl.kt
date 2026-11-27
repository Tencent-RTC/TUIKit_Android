package io.trtc.tuikit.chat.uikit.components.videorecorder.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.theme.ThemeState
import io.trtc.tuikit.chat.uikit.components.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecordListener
import io.trtc.tuikit.chat.uikit.components.videorecorder.RecordMode
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecorderConfig
import io.trtc.tuikit.chat.uikit.components.videorecorder.config.VideoRecorderConfigInternal
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecorderSignatureChecker
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderPermissionHelper
import io.trtc.tuikit.chat.uikit.components.videorecorder.utils.VideoRecorderPermissionHelper.PermissionCallback
import io.trtc.tuikit.chat.uikit.components.videorecorder.view.VideoRecorderBridgeActivity

class VideoRecorderViewImpl {
    companion object {
        init {
            VideoRecorderSignatureChecker.getInstance().startUpdateSignature()
        }
    }

    private val TAG = "VideoRecorderImpl"

    /**
     * Launches the camera capture interface
     * @param config Capture configuration settings
     * @param callback Callback interface for receiving capture results, including:
     *                 - Photo capture success
     *                 - Video recording success
     *                 - Error events
     */
    fun takeVideo(config: VideoRecorderConfig?, callback: VideoRecordListener?) {
        val context = ContextProvider.appContext
        if (callback == null) {
            Log.e(TAG, "start record fail. context or callback is null")
            return
        }

        val isPhotoOnly = config?.recordMode == RecordMode.PHOTO_ONLY
        videoRecodePermissionRequest(isPhotoOnly, object : PermissionCallback {
            override fun onGranted() {
                VideoRecorderConfigInternal.getInstance().setConfig(config)
                VideoRecorderConfigInternal.getInstance().setThemeColor(
                    ThemeState.shared.currentPrimaryColor ?: AppBuilderConfig.primaryColor
                )
                startRecordInternal(context, callback)
            }

            override fun onDenied() {
                print("Failed to obtain device permissions");
                if (isPhotoOnly) {
                    callback.onPhotoCaptured(null)
                } else {
                    callback.onVideoCaptured(null, 0, null)
                }
            }
        })
    }

    private fun startRecordInternal(context: Context, callback: VideoRecordListener) {
        val intent = Intent(context, VideoRecorderBridgeActivity::class.java)
        VideoRecorderBridgeActivity.callback = callback
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun videoRecodePermissionRequest(
        isOnlyPhoto: Boolean,
        callback: PermissionCallback
    ) {
        VideoRecorderPermissionHelper.requestPermission(
            VideoRecorderPermissionHelper.PERMISSION_CAMERA,
            object : PermissionCallback {
                override fun onGranted() {
                    if (isOnlyPhoto) {
                        callback.onGranted()
                    } else {
                        microphonePermissionRequest(callback)
                    }
                }

                override fun onDenied() {
                    callback.onDenied()
                    Log.e(TAG, "openVideoRecorder checkPermission failed, camera permission denied")
                }
            }
        )
    }

    private fun microphonePermissionRequest(callback: PermissionCallback) {
        VideoRecorderPermissionHelper.requestPermission(
            VideoRecorderPermissionHelper.PERMISSION_MICROPHONE,
            object : PermissionCallback {
                override fun onGranted() {
                    callback.onGranted()
                }

                override fun onDenied() {
                    callback.onDenied()
                    Log.e(TAG, "openVideoRecorder checkPermission failed, Microphone permission denied")
                }
            }
        )
    }
}