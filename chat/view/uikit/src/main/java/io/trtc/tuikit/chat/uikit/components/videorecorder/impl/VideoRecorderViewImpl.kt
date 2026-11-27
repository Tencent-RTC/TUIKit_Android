package io.trtc.tuikit.chat.uikit.components.videorecorder.impl
import android.content.Context
import android.content.Intent
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecordListener
import io.trtc.tuikit.chat.uikit.components.videorecorder.RecordMode
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecorderConfig
import io.trtc.tuikit.chat.uikit.components.videorecorder.config.VideoRecorderConfigInternal
import io.trtc.tuikit.chat.uikit.components.videorecorder.core.VideoRecorderSignatureChecker
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import io.trtc.tuikit.chat.uikit.components.videorecorder.view.VideoRecorderBridgeActivity

class VideoRecorderViewImpl {
    companion object {
        init {
            VideoRecorderSignatureChecker.getInstance().startUpdateSignature()
        }
    }

    private val TAG = "VideoRecorderImpl"

    fun takeVideo(config: VideoRecorderConfig?, callback: VideoRecordListener?) {
        val context = ContextProvider.getApplicationContext()
        if (callback == null) {
            Log.e(TAG, "start record fail. context or callback is null")
            return
        }

        val isPhotoOnly = config?.recordMode == RecordMode.PHOTO_ONLY
        videoRecodePermissionRequest(isPhotoOnly, object : PermissionCallback() {
            override fun onGranted() {
                VideoRecorderConfigInternal.getInstance().setConfig(config)
                VideoRecorderConfigInternal.getInstance().setThemeColor(AppBuilderConfig.primaryColor)
                startRecordInternal(context, callback)
            }

            override fun onDenied() {
                print("Failed to obtain device permissions")
                if (isPhotoOnly) {
                    callback.onPhotoCaptured(null)
                } else {
                    callback.onVideoCaptured(null, 0, null)
                }
            }
        })
    }

    private fun startRecordInternal(context: Context?, callback: VideoRecordListener) {
        val ctx = context ?: return
        val intent = Intent(ctx, VideoRecorderBridgeActivity::class.java)
        VideoRecorderBridgeActivity.callback = callback
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private fun videoRecodePermissionRequest(
        isOnlyPhoto: Boolean,
        callback: PermissionCallback
    ) {
        ChatPermissionHelper.requestPermission(
            ChatPermissionHelper.PERMISSION_CAMERA,
            object : PermissionCallback() {
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
        ChatPermissionHelper.requestPermission(
            ChatPermissionHelper.PERMISSION_MICROPHONE,
            object : PermissionCallback() {
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
