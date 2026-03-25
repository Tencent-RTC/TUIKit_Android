package com.trtc.uikit.livekit.features.anchorprepare.store

import com.trtc.uikit.livekit.common.DEFAULT_COVER_URL
import com.trtc.uikit.livekit.features.anchorprepare.LiveStreamPrivacyStatus
import kotlinx.coroutines.flow.MutableStateFlow

class AnchorPrepareState {
    companion object {
        const val MAX_INPUT_BYTE_LENGTH = 100
    }

    var selfUserId: String = ""
    var selfUserName: String = ""
    var roomId: String = ""
    val useFrontCamera = MutableStateFlow(true)
    val coverURL = MutableStateFlow(DEFAULT_COVER_URL)
    val liveMode = MutableStateFlow(LiveStreamPrivacyStatus.PUBLIC)
    val coGuestTemplateId = MutableStateFlow(600)
    val coHostTemplateId = MutableStateFlow(600)
    val roomName = MutableStateFlow("")
}