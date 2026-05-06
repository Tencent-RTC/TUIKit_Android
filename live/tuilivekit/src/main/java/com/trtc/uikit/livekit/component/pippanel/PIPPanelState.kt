package com.trtc.uikit.livekit.component.pippanel

import kotlinx.coroutines.flow.MutableStateFlow

class PIPPanelState {
    val roomId = MutableStateFlow("")
    var anchorIsPictureInPictureMode = false
    var audienceIsPictureInPictureMode = false
    var isAnchorStreaming = false
    var enablePictureInPictureToggle = false
    var width = 720
    var height = 1280
}