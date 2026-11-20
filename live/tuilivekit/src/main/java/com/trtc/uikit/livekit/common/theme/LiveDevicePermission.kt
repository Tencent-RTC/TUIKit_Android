package com.trtc.uikit.livekit.common.theme

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val REQUEST_CODE_MICROPHONE = 101
private const val REQUEST_CODE_CAMERA = 102

private data class PendingPermissionRequest(
    val requestCode: Int,
    val continuation: Continuation<Boolean>,
)

private val permissionRequestMutex = Mutex()
private val pendingRequests = ConcurrentHashMap<String, PendingPermissionRequest>()

object LiveDevicePermission {

    suspend fun requestMicrophonePermission(context: Context): Boolean =
        request(context, Manifest.permission.RECORD_AUDIO, REQUEST_CODE_MICROPHONE)

    suspend fun requestCameraPermission(context: Context): Boolean =
        request(context, Manifest.permission.CAMERA, REQUEST_CODE_CAMERA)

    private suspend fun request(context: Context, permission: String, requestCode: Int): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) return true
        permissionRequestMutex.lock()
        try {
            return suspendCoroutine { continuation ->
                val requestID = UUID.randomUUID().toString()
                pendingRequests[requestID] = PendingPermissionRequest(requestCode, continuation)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    handlePermissionResult(requestID, requestCode, true)
                    return@suspendCoroutine
                }
                LivePermissionActivity.start(context, arrayOf(permission), requestCode, requestID)
            }
        } finally {
            permissionRequestMutex.unlock()
        }
    }
}

class LivePermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val requestID = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            handlePermissionResult(requestID, requestCode, true)
            finish()
            return
        }
        val permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS)
        if (permissions.isNullOrEmpty()) {
            handlePermissionResult(requestID, requestCode, false)
            finish()
            return
        }
        requestPermissions(permissions, requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val requestID = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        handlePermissionResult(requestID, requestCode, granted)
        finish()
    }

    companion object {
        private const val EXTRA_PERMISSIONS = "permissions"
        private const val EXTRA_REQUEST_CODE = "request_code"
        private const val EXTRA_REQUEST_ID = "request_id"

        fun start(context: Context, permissions: Array<String>, requestCode: Int, requestID: String) {
            context.startActivity(
                Intent(context, LivePermissionActivity::class.java).apply {
                    putExtra(EXTRA_PERMISSIONS, permissions)
                    putExtra(EXTRA_REQUEST_CODE, requestCode)
                    putExtra(EXTRA_REQUEST_ID, requestID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}

private fun handlePermissionResult(requestID: String, requestCode: Int, granted: Boolean) {
    val request = pendingRequests[requestID] ?: return
    if (request.requestCode != requestCode) return
    pendingRequests.remove(requestID, request)
    request.continuation.resume(granted)
}
