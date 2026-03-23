package com.example.atomicxcore.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Global permission management utility
 *
 * Provides unified permission checking and requesting capabilities, covering the following scenarios:
 * - Camera + Microphone (streamer going live / audience co-hosting)
 * - Bluetooth connection (Android 12+ audio devices)
 * - Network connectivity check (before login)
 *
 * Usage:
 * 1. Call registerPermissions() during the Activity's property declaration phase to register the launcher
 * 2. Call requestCameraAndMicrophone() etc. when permissions are needed
 */
object PermissionHelper {

    /** Camera + Microphone permissions */
    val CAMERA_AND_MICROPHONE = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    /** Bluetooth connection permission (Android 12+) */
    val BLUETOOTH_CONNECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }

    /**
     * Register permission request launcher (must be called before Activity CREATED, i.e., during property initialization)
     *
     * @param activity Host Activity
     * @param onResult Permission request result callback: allGranted indicates if all permissions are granted, denied is the list of denied permissions
     */
    fun registerPermissions(
        activity: ComponentActivity,
        onResult: (allGranted: Boolean, denied: List<String>) -> Unit
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val denied = permissions.filter { !it.value }.keys.toList()
            onResult(denied.isEmpty(), denied)
        }
    }

    /**
     * Check if all specified permissions are already granted
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request camera and microphone permissions; calls onGranted directly if already authorized
     *
     * @param context Context (for permission check)
     * @param launcher Registered permission request launcher
     * @param onGranted Callback when all permissions are granted
     */
    fun requestCameraAndMicrophone(
        context: Context,
        launcher: ActivityResultLauncher<Array<String>>,
        onGranted: (() -> Unit)? = null
    ) {
        val needed = CAMERA_AND_MICROPHONE.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            onGranted?.invoke()
        } else {
            launcher.launch(needed.toTypedArray())
        }
    }

    /**
     * Check network connectivity
     *
     * INTERNET and ACCESS_NETWORK_STATE are normal permissions that don't require runtime requests,
     * but network availability should be checked before use.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
