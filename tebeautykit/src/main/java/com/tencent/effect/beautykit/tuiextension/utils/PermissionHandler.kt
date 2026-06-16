package com.tencent.effect.beautykit.tuiextension.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

abstract class PermissionHandler(private val mActivity: Activity) {

    companion object {
        private const val TAG = "PermissionHandler"
    }

    private var mRequestCode = 0

    fun start() {
        val permissions = onGetPermissions()
        val checkPermission = permissions.filter {
            it != Manifest.permission.SYSTEM_ALERT_WINDOW
        }.toTypedArray()
        start(checkPermission)
    }

    fun start(permissions: Array<String>) {
        val no = getNoGrantPermissions(permissions)
        if (no.isEmpty()) {
            onAllPermissionGranted()
        } else {
            doRequestPermissions(no)
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == mRequestCode) {
            val declinedPermissions = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    if (!shouldIgnore(permissions[i])) {
                        declinedPermissions.add(permissions[i])
                    }
                }
            }
            if (declinedPermissions.isEmpty()) {
                onAllPermissionGranted()
            } else {
                onPermissionsDecline(declinedPermissions.toTypedArray())
            }
        }
    }

    protected open fun shouldIgnore(permission: String): Boolean {
        return false
    }

    @Keep
    protected open fun onGetPermissions(): Array<String> {
        val info = try {
            mActivity.packageManager.getPackageInfo(mActivity.packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace() // not possible
            null
        }
        if (info?.requestedPermissions == null) {
            Log.w(TAG, "android.content.pm.PackageInfo.requestedPermissions == null, this app does not require any permissions?")
        }
        return info?.requestedPermissions ?: emptyArray()
    }

    @Keep
    protected open fun onPermissionsDecline(permissions: Array<String>) {
        val msg = "permission declined: ${permissions.contentToString()}"
        Log.w(TAG, msg)
        Toast.makeText(mActivity, msg, Toast.LENGTH_LONG).show()
    }

    @Keep
    protected abstract fun onAllPermissionGranted()

    private fun getNoGrantPermissions(permissions: Array<String>?): Array<String> {
        if (permissions == null) {
            return emptyArray()
        }
        return permissions.filter { !hasPermission(it) }.toTypedArray()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(mActivity, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun doRequestPermissions(permissions: Array<String>) {
        mRequestCode = 1024
        ActivityCompat.requestPermissions(mActivity, permissions, mRequestCode)
    }
}
