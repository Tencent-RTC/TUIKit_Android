package com.trtc.uikit.roomkit.base.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.FailureHandler
import com.trtc.uikit.roomkit.base.service.RoomForegroundService
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.inroom.common.devicefaultsheet.RoomDeviceFaultSheet
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.AudioRoute
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import kotlinx.coroutines.flow.MutableStateFlow

enum class RoomAudioOutput {
    SPEAKER,
    EARPIECE,
}

enum class RoomDevice {
    MICROPHONE,
    CAMERA,
    ;

    val localizedNameRes: Int
        get() = when (this) {
            MICROPHONE -> R.string.roomkit_main_device_name_microphone
            CAMERA -> R.string.roomkit_main_device_name_camera
        }

    val iconRes: Int
        get() = when (this) {
            MICROPHONE -> R.drawable.roomkit_icon_microphone_on
            CAMERA -> R.drawable.roomkit_icon_camera_on
        }
}

object RoomLocalMediaUtils {

    private const val REQUEST_CODE_MICROPHONE = 101
    private const val REQUEST_CODE_CAMERA = 102

    private var volumeBeforeMute: Int? = null
    private val deviceStore = DeviceStore.shared()
    private val faultSheets = mutableMapOf<RoomDevice, RoomDeviceFaultSheet>()
    private var pendingResult: ((Boolean) -> Unit)? = null

    val isAudioOutputMuted = MutableStateFlow(false)

    fun isPermissionDenied(context: Context, kind: RoomDevice): Boolean {
        val permission = when (kind) {
            RoomDevice.MICROPHONE -> Manifest.permission.RECORD_AUDIO
            RoomDevice.CAMERA -> Manifest.permission.CAMERA
        }
        return ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    fun permissionOf(kind: RoomDevice): String = when (kind) {
        RoomDevice.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        RoomDevice.CAMERA -> Manifest.permission.CAMERA
    }

    fun openSystemSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    fun shouldRequestPermission(context: Context, kind: RoomDevice): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(askedKey(kind), false)
    }

    fun markPermissionRequested(context: Context, kind: RoomDevice) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(askedKey(kind), true).apply()
    }

    fun runWithPermission(context: Context, kind: RoomDevice, action: () -> Unit) {
        if (!isPermissionDenied(context, kind)) {
            action()
            return
        }
        requestPermission(context, kind) { granted ->
            if (granted) action()
        }
    }

    /// 统一的本地设备开启入口：权限引导 → 开启 → 成功后确保采集前台服务就位。
    fun openMicrophone(context: Context, onResult: CompletionHandler = FailureHandler(context)) {
        runWithPermission(context, RoomDevice.MICROPHONE) {
            deviceStore.openLocalMicrophone(object : CompletionHandler {
                override fun onSuccess() {
                    RoomForegroundService.start(context)
                    onResult.onSuccess()
                }

                override fun onFailure(code: Int, desc: String) {
                    onResult.onFailure(code, desc)
                }
            })
        }
    }

    fun openCamera(context: Context, onResult: CompletionHandler = FailureHandler(context)) {
        runWithPermission(context, RoomDevice.CAMERA) {
            deviceStore.openLocalCamera(
                deviceStore.deviceState.isFrontCamera.value,
                object : CompletionHandler {
                    override fun onSuccess() {
                        RoomForegroundService.start(context)
                        onResult.onSuccess()
                    }

                    override fun onFailure(code: Int, desc: String) {
                        onResult.onFailure(code, desc)
                    }
                },
            )
        }
    }

    fun requestPermission(context: Context, kind: RoomDevice, onResult: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (!shouldRequestPermission(context, kind)) {
            showFaultSheet(context, kind)
            return
        }
        markPermissionRequested(context, kind)
        val activity = context as? Activity
        if (activity == null) {
            showFaultSheet(context, kind)
            return
        }
        pendingResult = onResult
        activity.requestPermissions(
            arrayOf(permissionOf(kind)),
            if (kind == RoomDevice.MICROPHONE) REQUEST_CODE_MICROPHONE else REQUEST_CODE_CAMERA,
        )
    }

    fun handlePermissionResult(requestCode: Int, granted: Boolean) {
        if (requestCode != REQUEST_CODE_MICROPHONE && requestCode != REQUEST_CODE_CAMERA) return
        val onResult = pendingResult ?: return
        pendingResult = null
        onResult(granted)
    }

    private fun showFaultSheet(context: Context, kind: RoomDevice) {
        if (faultSheets[kind] != null) return
        val sheet = RoomDeviceFaultSheet(context, kind)
        sheet.onPrimary = {
            RoomToast.show(context, context.getString(R.string.roomkit_main_device_open_settings_toast))
            openSystemSettings(context)
        }
        sheet.onDismiss = { faultSheets.remove(kind) }
        faultSheets[kind] = sheet
        sheet.show()
    }

    private fun askedKey(kind: RoomDevice): String = "permission_asked_${kind.name.lowercase()}"

    private const val PREF_NAME = "roomkit_local_media"
    private const val DEFAULT_VOLUME = 100

    fun currentAudioOutput(): RoomAudioOutput {
        return if (deviceStore.deviceState.currentAudioRoute.value == AudioRoute.SPEAKERPHONE) {
            RoomAudioOutput.SPEAKER
        } else {
            RoomAudioOutput.EARPIECE
        }
    }

    fun setAudioOutput(output: RoomAudioOutput) {
        if (isAudioOutputMuted.value) {
            deviceStore.setOutputVolume(volumeBeforeMute ?: DEFAULT_VOLUME)
            volumeBeforeMute = null
            isAudioOutputMuted.value = false
        }
        deviceStore.setAudioRoute(
            if (output == RoomAudioOutput.SPEAKER) AudioRoute.SPEAKERPHONE else AudioRoute.EARPIECE,
        )
    }
}
