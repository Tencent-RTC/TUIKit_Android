package com.trtc.uikit.livekit.features.audienceview

import android.view.View
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

object AudienceViewDefine {
    interface AudienceViewListener {
        fun onLiveEnded(roomId: String, ownerName: String, ownerAvatarUrl: String)
        fun onClickFloatWindow()

        fun onCreateLiveView(audienceView: AudienceLiveView, liveInfo: LiveInfo) {}

        fun onLiveViewDidAppear(audienceView: AudienceLiveView, liveInfo: LiveInfo) {}

        fun onLiveViewDidDisappear(audienceView: AudienceLiveView, liveInfo: LiveInfo) {}
    }

    interface LiveListCallback {
        fun onSuccess(cursor: String, liveInfoList: List<LiveInfo>)
        fun onError(code: Int, message: String)
    }

    interface LiveListDataSource {
        fun fetchLiveList(cursor: String, callback: LiveListCallback)
    }

    enum class AudienceNode {
        LIVE_INFO,
        TOP_RIGHT_BUTTONS,
        NETWORK_INFO,
        BARRAGE_INPUT,
        BOTTOM_RIGHT_BAR,
    }

    sealed class AudienceBottomItem {
        data object Gift : AudienceBottomItem()
        data object CoGuest : AudienceBottomItem()
        data object Like : AudienceBottomItem()
        data object More : AudienceBottomItem()
        data class Custom(val view: View) : AudienceBottomItem()
    }

    sealed class AudienceTopRightItem {
        data object AudienceCount : AudienceTopRightItem()
        data object FloatWindow : AudienceTopRightItem()
        data object Close : AudienceTopRightItem()
        data class Custom(val view: View) : AudienceTopRightItem()
    }

    enum class AudienceAction {
        SHOW_LIVE_INFO,
        SHOW_AUDIENCE_LIST,
        SHOW_GIFT_PANEL,
        SHOW_CO_GUEST_PANEL,
        SHOW_MORE_PANEL,
        SHOW_FLOAT_WINDOW,
        EXIT_LIVE,
    }
}
