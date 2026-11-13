package com.tencent.qcloud.tuikit.tuicallkit.call.callview

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKeepAliveService
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.call.CallWindow
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitToast
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class CallActivity : AppCompatActivity() {

    private var didOpenCamera = false
    private var didOpenMicrophone = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val pendingPermissionCallbacks = ArrayDeque<Pair<Int, (Boolean) -> Unit>>()
    private var permissionRequestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenOrientation()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.tuicallkit_audio_background_bottom)
        // 全屏深色界面：导航栏同样深色 + 浅色图标，避免跟随宿主主题出现不协调底色
        window.navigationBarColor = ContextCompat.getColor(this, R.color.tuicallkit_audio_background_bottom)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        val callView = CallView(this)
        setContentView(callView)
        CallWindow.attach(this)
        wirePanelPermissions(callView)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        requestNotificationPermissionIfNeeded()
        ensureDevices()
    }

    override fun onStart() {
        super.onStart()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        activityScope.cancel()
        if (didOpenCamera) {
            didOpenCamera = false
            DeviceStore.shared().closeLocalCamera()
        }
        if (didOpenMicrophone) {
            didOpenMicrophone = false
            DeviceStore.shared().closeLocalMicrophone()
        }
        CallWindow.detach(this)
        super.onDestroy()
    }

    private fun applyScreenOrientation() {
        requestedOrientation = when (CallKitConfig.shared.screenOrientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun isIncomingRinging(): Boolean {
        val state = CallStore.shared.observerState
        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val inviter = state.activeCall.value.inviterId
        return state.selfInfo.value.status == CallParticipantStatus.Waiting
            && inviter.isNotEmpty()
            && inviter != selfID
    }

    private fun wirePanelPermissions(callView: CallView) {
        callView.setPermissionHandlers(
            onMicrophone = { open ->
                ensurePermissions(arrayOf(Manifest.permission.RECORD_AUDIO)) { granted ->
                    if (granted) {
                        didOpenMicrophone = true
                        open()
                    } else {
                        CallKitToast.show(getString(R.string.tuicallkit_permission_microphone_required))
                    }
                }
            },
            onCamera = { open ->
                ensurePermissions(arrayOf(Manifest.permission.CAMERA)) { granted ->
                    if (granted) {
                        didOpenCamera = true
                        open()
                    } else {
                        CallKitToast.show(getString(R.string.tuicallkit_permission_camera_required))
                    }
                }
            }
        )
    }

    private fun ensureDevices() {
        val isVideo = CallStore.shared.observerState.activeCall.value.mediaType == CallMediaType.Video
        if (isIncomingRinging()) {
            if (isVideo) {
                ensurePermissions(arrayOf(Manifest.permission.CAMERA)) { granted ->
                    if (granted) {
                        didOpenCamera = true
                        DeviceStore.shared().openLocalCamera(true, null)
                    }
                }
            }
            activityScope.launch {
                CallStore.shared.observerState.selfInfo.first { it.status == CallParticipantStatus.Accept }
                openMicrophoneWithPermission()
            }
            return
        }
        openMicrophoneWithPermission()
        if (isVideo) {
            ensurePermissions(arrayOf(Manifest.permission.CAMERA)) { granted ->
                if (granted) {
                    didOpenCamera = true
                    DeviceStore.shared().openLocalCamera(true, null)
                    startKeepAliveService()
                }
            }
        }
    }

    private fun openMicrophoneWithPermission() {
        if (didOpenMicrophone) {
            return
        }
        ensurePermissions(arrayOf(Manifest.permission.RECORD_AUDIO)) { granted ->
            if (granted) {
                didOpenMicrophone = true
                DeviceStore.shared().openLocalMicrophone(null)
                startKeepAliveService()
            }
        }
    }

    private fun startKeepAliveService() {
        val isVideo = CallStore.shared.observerState.activeCall.value.mediaType == CallMediaType.Video
        CallKeepAliveService.start(this, isVideo)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        ensurePermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { }
    }

    private fun ensurePermissions(permissions: Array<String>, onResult: (Boolean) -> Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (missing.isEmpty()) {
            onResult(true)
            return
        }
        val requestCode = nextRequestCode()
        pendingPermissionCallbacks.addLast(requestCode to onResult)
        if (!permissionRequestInFlight) {
            startNextPermissionRequest(missing, requestCode)
        } else {
            pendingPermissions[requestCode] = missing
        }
    }

    private val pendingPermissions = mutableMapOf<Int, Array<String>>()

    private fun startNextPermissionRequest(permissions: Array<String>, requestCode: Int) {
        permissionRequestInFlight = true
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val entry = pendingPermissionCallbacks.firstOrNull { it.first == requestCode }
        if (entry == null) {
            permissionRequestInFlight = false
            return
        }
        pendingPermissionCallbacks.remove(entry)
        val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        entry.second.invoke(allGranted)
        permissionRequestInFlight = false
        val next = pendingPermissionCallbacks.firstOrNull()
        if (next != null) {
            val nextPermissions = pendingPermissions.remove(next.first)
            if (nextPermissions != null) {
                startNextPermissionRequest(nextPermissions, next.first)
            }
        }
    }

    private fun nextRequestCode(): Int {
        nextCode += 1
        return nextCode
    }

    private var nextCode = PERMISSION_REQUEST_BASE

    private companion object {
        const val PERMISSION_REQUEST_BASE = 3201
    }
}
