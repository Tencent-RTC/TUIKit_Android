package com.trtc.uikit.livekit.pusher.settings


internal data class PusherSettingsState(
    val isMirrorEnabled: Boolean = true,
    val isMuted: Boolean = false,
    val captureVolume: Int = 100,
)

internal interface PusherSettingsActionHandling {
    fun toggleMirror()
    fun setCaptureVolume(volume: Int)
}
