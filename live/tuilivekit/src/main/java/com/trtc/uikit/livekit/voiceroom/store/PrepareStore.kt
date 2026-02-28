package com.trtc.uikit.livekit.voiceroom.store

import android.text.TextUtils
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine.SeatMode
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.DEFAULT_BACKGROUND_URL
import com.trtc.uikit.livekit.common.DEFAULT_COVER_URL
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.common.seatModeFromEngineSeatMode
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.TakeSeatMode
import kotlinx.coroutines.flow.MutableStateFlow

enum class LiveStatus {
    NONE,
    PREVIEWING,
    PUSHING,
    PLAYING,
    DASHBOARD
}

enum class LayoutType(val desc: String) {
    KTV_ROOM("KTVRoom"),
    VOICE_ROOM("ChatRoom");
}

enum class LiveStreamPrivacyStatus(val resId: Int) {
    PUBLIC(R.string.common_stream_privacy_status_default),
    PRIVACY(R.string.common_stream_privacy_status_privacy)
}

data class LiveExtraInfo(
    var liveMode: LiveStreamPrivacyStatus = LiveStreamPrivacyStatus.PUBLIC,
    var maxAudienceCount: Int = 0,
    var messageCount: Int = 0,
    var giftIncome: Int = 0,
    var giftSenderCount: Int = 0,
    var likeCount: Int = 0,
)

data class PrepareState(
    val liveInfo: MutableStateFlow<LiveInfo>,
    val layoutType: MutableStateFlow<LayoutType>,
    val liveExtraInfo: MutableStateFlow<LiveExtraInfo>,
    val liveStatus: MutableStateFlow<LiveStatus>
)

class PrepareStore {

    private val _liveInfo =
        MutableStateFlow(
            LiveInfo(
                coverURL = DEFAULT_COVER_URL,
                backgroundURL = DEFAULT_BACKGROUND_URL,
                seatTemplate = SeatLayoutTemplate.AudioSalon(0),
            )
        )
    private val _layoutType = MutableStateFlow(LayoutType.VOICE_ROOM)
    private val _liveExtraInfo = MutableStateFlow(LiveExtraInfo())
    private val _liveStatus = MutableStateFlow(LiveStatus.NONE)

    val prepareState = PrepareState(
        liveInfo = _liveInfo,
        layoutType = _layoutType,
        liveExtraInfo = _liveExtraInfo,
        liveStatus = _liveStatus
    )

    fun updateLiveName(liveName: String) {
        _liveInfo.value = _liveInfo.value.copy().apply {
            this.liveName = liveName
        }
    }

    fun initCreateRoomState(
        liveID: String,
        roomName: String,
        seatMode: SeatMode,
        maxSeatCount: Int
    ) {
        _liveInfo.value = _liveInfo.value.copy().apply {
            this.liveID = liveID
            this.liveName = roomName
            this.seatMode = seatModeFromEngineSeatMode(seatMode)
            this.maxSeatCount = maxSeatCount
        }
    }

    fun updateMessageCount(messageCount: Int) {
        _liveExtraInfo.value = _liveExtraInfo.value.copy().apply {
            this.messageCount = messageCount
        }
    }

    fun updateStatistics(
        audienceCount: Int,
        messageCount: Int,
        giftIncome: Int,
        giftSenderCount: Int,
        likeCount: Int
    ) {
        _liveExtraInfo.value = _liveExtraInfo.value.copy().apply {
            this.maxAudienceCount = audienceCount
            this.messageCount = messageCount
            this.giftIncome = giftIncome
            this.giftSenderCount = giftSenderCount
            this.likeCount = likeCount
        }
    }

    fun updateLiveCoverURL(coverURL: String) {
        _liveInfo.value = _liveInfo.value.copy().apply {
            this.coverURL = coverURL
        }
    }

    fun updateLiveBackgroundURL(backgroundURL: String) {
        _liveInfo.value = _liveInfo.value.copy().apply {
            this.backgroundURL = backgroundURL
        }
    }

    fun updateLiveID(liveID: String) {
        _liveInfo.value = _liveInfo.value.copy().apply {
            this.liveID = liveID
        }
    }

    fun updateLiveStatus(liveStatus: LiveStatus) {
        _liveStatus.value = liveStatus
    }

    fun updateLiveInfo(liveInfo: LiveInfo) {
        _liveInfo.value = liveInfo
    }

    fun updateLiveMode(liveMode: LiveStreamPrivacyStatus) {
        _liveExtraInfo.value = _liveExtraInfo.value.copy().apply {
            this.liveMode = liveMode
        }
    }

    fun updateSeatMode(seatMode: TakeSeatMode) {
        _liveInfo.value = _liveInfo.value.copy().apply {
            this.seatMode = seatMode
        }
    }

    fun updateLayoutType(layoutType: LayoutType) {
        _layoutType.value = layoutType
    }

    fun getDefaultRoomName(): String {
        val loginUserInfo = TUIRoomEngine.getSelfInfo()
        return if (TextUtils.isEmpty(loginUserInfo.userName)) {
            loginUserInfo.userId
        } else {
            loginUserInfo.userName
        }
    }

    fun setLayoutMetaData(layout: LayoutType) {
        if (_liveStatus.value === LiveStatus.PLAYING) return
        if (_liveStatus.value === LiveStatus.PUSHING) {
            val layoutStr: String = layout.desc
            val hashMap = HashMap<String, String>()
            hashMap.put(KEY_LAYOUT_TYPE, layoutStr)
            LiveListStore.shared().updateLiveMetaData(hashMap, completionHandler {
                onError { code, _ ->
                    ErrorLocalized.onError(code)
                }
            })
        }
        _layoutType.value = layout
    }

    fun destroy() {
        _liveInfo.value = LiveInfo(
            coverURL = DEFAULT_COVER_URL,
            backgroundURL = DEFAULT_BACKGROUND_URL,
            seatTemplate = SeatLayoutTemplate.AudioSalon(0),
        )
        _liveExtraInfo.value = LiveExtraInfo()
        _liveStatus.value = LiveStatus.NONE
        _layoutType.value = LayoutType.VOICE_ROOM
    }

    companion object {
        const val KEY_LAYOUT_TYPE: String = "LayoutType"
    }
}