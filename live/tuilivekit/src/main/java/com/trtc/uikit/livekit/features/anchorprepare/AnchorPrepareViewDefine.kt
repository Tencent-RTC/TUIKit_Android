package com.trtc.uikit.livekit.features.anchorprepare

import com.trtc.uikit.livekit.R
import kotlinx.coroutines.flow.StateFlow

data class PrepareState(
    @JvmField val coverURL: StateFlow<String>,
    @JvmField val liveMode: StateFlow<LiveStreamPrivacyStatus>,
    @JvmField val roomName: StateFlow<String>,
    @JvmField val coGuestTemplateId: StateFlow<Int>,
    @JvmField val coHostTemplateId: StateFlow<Int>,
    @JvmField val videoStreamSource: StateFlow<VideoStreamSource>
)

interface AnchorPrepareViewListener {
    fun onClickStartButton()
    fun onClickBackButton()
}

enum class LiveStreamPrivacyStatus(val resId: Int) {
    PUBLIC(R.string.common_stream_privacy_status_default),
    PRIVACY(R.string.common_stream_privacy_status_privacy)
}

enum class VideoStreamSource(val id: Int) {
    CAMERA(0),
    SCREEN_SHARE(1)
}