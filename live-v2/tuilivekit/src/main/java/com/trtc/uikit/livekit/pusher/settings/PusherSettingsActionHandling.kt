package com.trtc.uikit.livekit.pusher.settings

import io.trtc.tuikit.atomicxcore.api.device.VideoQuality

internal data class PusherSettingsState(
    val videoQuality: VideoQuality = VideoQuality.QUALITY_720P,
    val isMirrorEnabled: Boolean = true,
    val isMuted: Boolean = false,
    val captureVolume: Int = 100,
)

internal interface PusherSettingsActionHandling {
    fun toggleMirror()
    fun setCaptureVolume(volume: Int)
}
