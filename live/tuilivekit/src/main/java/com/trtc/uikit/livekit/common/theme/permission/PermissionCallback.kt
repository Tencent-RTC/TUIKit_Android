package com.trtc.uikit.livekit.common.theme.permission

abstract class PermissionCallback {
    abstract fun onGranted()

    open fun onRequesting() {}

    open fun onDenied() {}
}
