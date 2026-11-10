package com.trtc.uikit.livekit.common.theme.permission.event

fun interface INotification {
    fun onNotifyEvent(key: String, subKey: String, param: Map<String, Any?>?)
}
