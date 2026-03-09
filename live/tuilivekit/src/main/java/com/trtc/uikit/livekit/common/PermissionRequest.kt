package com.trtc.uikit.livekit.common

import android.Manifest
import android.content.Context
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.common.permission.PermissionRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

object PermissionRequest {
    val requestCompleteEvent = MutableSharedFlow<Boolean>(
        replay = 0,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    fun sendRequestCompleteEvent() {
        CoroutineScope(Dispatchers.Default).launch {
            requestCompleteEvent.emit(true)
        }
    }

    fun requestMicrophonePermissions(context: Context, callback: PermissionCallback) {
        val title = context.getString(R.string.common_permission_microphone)
        val reason = context.getString(R.string.common_permission_mic_reason)

        val permissionCallback = object : PermissionCallback() {
            override fun onGranted() {
                callback?.onGranted()
            }

            override fun onDenied() {
                super.onDenied()
                callback?.onDenied()
            }
        }

        val applicationInfo = context.applicationInfo
        val appName = context.packageManager.getApplicationLabel(applicationInfo).toString()

        PermissionRequester.newInstance(Manifest.permission.RECORD_AUDIO)
            .title(context.getString(R.string.common_permission_title, appName, title))
            .description(reason)
            .settingsTip(context.getString(R.string.common_permission_tips, title) + "\n" + reason)
            .callback(permissionCallback)
            .request()
    }

    fun requestCameraPermissions(context: Context, callback: PermissionCallback) {
        val title = context.getString(R.string.common_permission_camera)
        val reason = context.getString(R.string.common_permission_camera_reason)

        val permissionCallback = object : PermissionCallback() {
            override fun onGranted() {
                callback?.onGranted()
            }

            override fun onDenied() {
                super.onDenied()
                callback?.onDenied()
            }
        }

        val applicationInfo = context.applicationInfo
        val appName = context.packageManager.getApplicationLabel(applicationInfo).toString()

        PermissionRequester.newInstance(Manifest.permission.CAMERA)
            .title(context.getString(R.string.common_permission_title, appName, title))
            .description(reason)
            .settingsTip(context.getString(R.string.common_permission_tips, title) + "\n" + reason)
            .callback(permissionCallback)
            .request()
    }
}