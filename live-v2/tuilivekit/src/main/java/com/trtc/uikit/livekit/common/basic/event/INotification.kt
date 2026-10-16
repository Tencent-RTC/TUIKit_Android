package com.trtc.uikit.livekit.common.basic.event

fun interface INotification {
    fun onNotifyEvent(key: String, subKey: String, param: Map<String, Any?>?)
}
