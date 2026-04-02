package com.trtc.uikit.roomkit.base.operator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.common.permission.PermissionRequester
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Device operator for managing camera and microphone operations with permission handling.
 */
class DeviceOperator(context: Context) {

    enum class DeviceOperatorType {
        MICROPHONE,
        CAMERA,
    }

    private val logger = RoomKitLogger.getLogger("DeviceOperator")
    private val contextRef = WeakReference(context)
    private val deviceStore = DeviceStore.shared()

    suspend fun unmuteMicrophone(participantStore: RoomParticipantStore?) {
        val hasPermission = requestPermission(DeviceOperatorType.MICROPHONE)
        if (!hasPermission) return

        awaitCompletion { deviceStore.openLocalMicrophone(it) }

        participantStore?.let { store ->
            awaitCompletion { store.unmuteMicrophone(it) }
        }
    }

    fun muteMicrophone(participantStore: RoomParticipantStore?) {
        participantStore?.muteMicrophone()
    }

    suspend fun openCamera() {
        val hasPermission = requestPermission(DeviceOperatorType.CAMERA)
        if (!hasPermission) return

        awaitCompletion {
            val isFrontCamera = deviceStore.deviceState.isFrontCamera.value
            deviceStore.openLocalCamera(isFrontCamera, it)
        }
    }

    fun closeCamera() {
        deviceStore.closeLocalCamera()
    }

    fun startScreenShare() {
        val context = contextRef.get() ?: return
        if (!checkDrawOverlaysPermission(context)) {
            requestDrawOverlaysPermission(context)
            return
        }
        deviceStore.startScreenShare()
    }

    fun stopScreenShare() {
        deviceStore.stopScreenShare()
    }

    suspend fun requestPermission(type: DeviceOperatorType): Boolean {
        val context = contextRef.get() ?: return false
        val appName = context.packageManager.getApplicationLabel(context.applicationInfo).toString()

        val (permission, deviceTitle, reason) = when (type) {
            DeviceOperatorType.MICROPHONE -> Triple(
                Manifest.permission.RECORD_AUDIO,
                context.getString(R.string.roomkit_permission_microphone),
                context.getString(R.string.roomkit_permission_mic_reason)
            )

            DeviceOperatorType.CAMERA -> Triple(
                Manifest.permission.CAMERA,
                context.getString(R.string.roomkit_permission_camera),
                context.getString(R.string.roomkit_permission_camera_reason)
            )
        }

        val title = context.getString(R.string.roomkit_permission_title, appName, deviceTitle)
        val settingsTip = context.getString(R.string.roomkit_permission_tips, title) + "\n" + reason

        return suspendCancellableCoroutine { continuation ->
            var isCompleted = false
            PermissionRequester.newInstance(permission)
                .title(title)
                .description(reason)
                .settingsTip(settingsTip)
                .callback(object : PermissionCallback() {
                    override fun onGranted() {
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resume(true)
                        }
                    }

                    override fun onDenied() {
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resume(false)
                        }
                    }
                })
                .request()

            continuation.invokeOnCancellation {
                isCompleted = true
            }
        }
    }

    private suspend fun awaitCompletion(block: (CompletionHandler) -> Unit) {
        suspendCancellableCoroutine { continuation ->
            var isCompleted = false
            block(object : CompletionHandler {
                override fun onSuccess() {
                    if (!isCompleted) {
                        isCompleted = true
                        continuation.resume(Unit)
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    if (!isCompleted) {
                        isCompleted = true
                        contextRef.get()?.let { ErrorLocalized.showError(it, code) }
                        continuation.resumeWithException(Exception(desc))
                    }
                }
            })

            continuation.invokeOnCancellation {
                isCompleted = true
            }
        }
    }

    private fun checkDrawOverlaysPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun requestDrawOverlaysPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                logger.error("No activity found to handle overlay permission request")
            }
        }
    }
}
