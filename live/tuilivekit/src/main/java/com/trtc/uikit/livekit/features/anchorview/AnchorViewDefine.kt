package com.trtc.uikit.livekit.features.anchorview

import android.view.View
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason

class AnchorState {
    var totalDuration: Long = 0
    var totalViewers: Long = 0
    var totalGiftUniqueSenders: Long = 0
    var totalGiftCoins: Long = 0
    var totalLikesReceived: Long = 0
    var totalMessageSent: Long = 0
    var liveEndedReason: LiveEndedReason = LiveEndedReason.ENDED_BY_HOST
}

interface AnchorViewListener {
    fun onEndLiving(state: AnchorState)
    fun onClickFloatWindow()
}

enum class RoomBehavior {
    CREATE_ROOM,
    ENTER_ROOM
}

enum class AnchorNode {
    LIVE_INFO,
    TOP_RIGHT_BUTTONS,
    NETWORK_INFO,
    BARRAGE_INPUT,
    BOTTOM_RIGHT_BAR,
}

sealed class AnchorBottomItem {
    data object CoGuest : AnchorBottomItem()
    data object CoHost : AnchorBottomItem()
    data object Battle : AnchorBottomItem()
    data object More : AnchorBottomItem()
    data class Custom(val view: View) : AnchorBottomItem()
}

sealed class AnchorTopRightItem {
    data object AudienceCount : AnchorTopRightItem()
    data object FloatWindow : AnchorTopRightItem()
    data object Close : AnchorTopRightItem()
    data class Custom(val view: View) : AnchorTopRightItem()
}

enum class AnchorAction {
    SHOW_LIVE_INFO,
    SHOW_AUDIENCE_LIST,
    SHOW_CO_GUEST_PANEL,
    SHOW_CO_HOST_PANEL,
    SHOW_MORE_PANEL,
    SHOW_FLOAT_WINDOW,
    REQUEST_BATTLE,
    END_LIVE,
}
