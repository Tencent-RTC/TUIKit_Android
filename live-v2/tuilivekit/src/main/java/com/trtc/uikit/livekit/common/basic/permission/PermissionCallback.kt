package com.trtc.uikit.livekit.common.basic.permission

abstract class PermissionCallback {
    abstract fun onGranted()

    open fun onRequesting() {}

    open fun onDenied() {}
}
