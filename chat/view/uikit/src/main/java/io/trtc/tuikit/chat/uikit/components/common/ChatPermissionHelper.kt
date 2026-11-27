package io.trtc.tuikit.chat.uikit.components.common

import android.Manifest
import android.os.Build
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.common.permission.PermissionRequester
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
import io.trtc.tuikit.chat.uikit.R

object ChatPermissionHelper {

    const val PERMISSION_MICROPHONE = 1
    const val PERMISSION_CAMERA = 2
    const val PERMISSION_STORAGE = 3

    @JvmStatic
    fun requestPermission(@PermissionType type: Int, callback: PermissionCallback) {
        val context = ContextProvider.getApplicationContext()
        if (context == null) {
            callback.onDenied()
            return
        }

        val appName = runCatching {
            context.applicationInfo.loadLabel(context.packageManager)?.toString()
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_APP_NAME

        val permission: String
        val title: String
        val description: String
        val settingsTip: String
        when (type) {
            PERMISSION_CAMERA -> {
                permission = Manifest.permission.CAMERA
                title = context.getString(R.string.uikit_chat_permission_camera_reason_title, appName)
                description = context.getString(R.string.uikit_chat_permission_camera_reason)
                settingsTip = context.getString(R.string.uikit_chat_permission_camera_dialog_alert, appName)
            }

            PERMISSION_STORAGE -> {
                // On Android 10 (API 29)+ media is written to public collections through
                // MediaStore, which needs no runtime permission. WRITE_EXTERNAL_STORAGE is also
                // capped at maxSdkVersion=28 in the manifest, so requesting it on API 29+ would
                // be denied immediately. Short-circuit to granted to keep this helper safe on all
                // versions regardless of the caller.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    callback.onGranted()
                    return
                }
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                title = context.getString(R.string.uikit_chat_permission_storage_reason_title, appName)
                description = context.getString(R.string.uikit_chat_permission_storage_reason)
                settingsTip = context.getString(R.string.uikit_chat_permission_storage_dialog_alert, appName)
            }

            else -> {
                permission = Manifest.permission.RECORD_AUDIO
                title = context.getString(R.string.uikit_chat_permission_mic_reason_title, appName)
                description = context.getString(R.string.uikit_chat_permission_mic_reason)
                settingsTip = context.getString(R.string.uikit_chat_permission_mic_dialog_alert, appName)
            }
        }

        PermissionRequester.newInstance(permission)
            .title(title)
            .description(description)
            .settingsTip(settingsTip)
            .callback(callback)
            .request()
    }

    private const val DEFAULT_APP_NAME = "App"

    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
    annotation class PermissionType
}
