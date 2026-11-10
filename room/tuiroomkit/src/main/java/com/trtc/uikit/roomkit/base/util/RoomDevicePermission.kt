package com.trtc.uikit.roomkit.base.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val REQUEST_CODE_MICROPHONE = 101
private const val REQUEST_CODE_CAMERA = 102

private var pendingContinuation: Continuation<Boolean>? = null

object RoomDevicePermission {

    suspend fun requestMicrophonePermission(activity: Activity): Boolean =
        request(activity, Manifest.permission.RECORD_AUDIO, REQUEST_CODE_MICROPHONE)

    suspend fun requestCameraPermission(activity: Activity): Boolean =
        request(activity, Manifest.permission.CAMERA, REQUEST_CODE_CAMERA)

    private suspend fun request(activity: Activity, permission: String, requestCode: Int): Boolean {
        val granted = ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return true
        return suspendCoroutine { continuation ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                continuation.resume(true)
                return@suspendCoroutine
            }
            pendingContinuation?.resume(false)
            pendingContinuation = continuation
            RoomPermissionActivity.start(activity, arrayOf(permission), requestCode)
        }
    }
}

class RoomPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            handlePermissionResult(requestCode, true)
            finish()
            return
        }
        val permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS)
        if (permissions.isNullOrEmpty()) {
            handlePermissionResult(requestCode, false)
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
        val granted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        handlePermissionResult(requestCode, granted)
        finish()
    }

    companion object {
        private const val EXTRA_PERMISSIONS = "permissions"
        private const val EXTRA_REQUEST_CODE = "request_code"

        fun start(context: Context, permissions: Array<String>, requestCode: Int) {
            context.startActivity(
                Intent(context, RoomPermissionActivity::class.java).apply {
                    putExtra(EXTRA_PERMISSIONS, permissions)
                    putExtra(EXTRA_REQUEST_CODE, requestCode)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}

private fun handlePermissionResult(requestCode: Int, granted: Boolean) {
    if (requestCode != REQUEST_CODE_MICROPHONE && requestCode != REQUEST_CODE_CAMERA) return
    val continuation = pendingContinuation ?: return
    pendingContinuation = null
    continuation.resume(granted)
}
