package com.trtc.uikit.livekit.player.video

import android.annotation.SuppressLint
import android.content.Context
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveSeat
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SuspendStatus
import io.trtc.tuikit.atomicxcore.api.view.UnitInfo

/** Player role projection; seat visuals are inherited from LiveSeat. */
@SuppressLint("ViewConstructor")
class UnitView(
    context: Context,
    unitInfo: UnitInfo,
    private val liveInfo: LiveInfo? = null,
) : LiveSeat(context, context.getString(R.string.common_apply_link_mic)) {

    init {
        setUnitInfo(unitInfo)
    }

    fun setUnitInfo(info: UnitInfo) {
        val ownerID = liveInfo?.liveOwner?.userID
        val isOwner = !ownerID.isNullOrEmpty() && ownerID == info.userID
        updateContent(
            info.userName.ifEmpty { info.userID },
            info.avatarURL,
            info.microphoneStatus == DeviceStatus.ON,
        )
        updateVisibility(
            isEmpty = info.userID.isEmpty(),
            showsNoVideo = info.cameraStatus != DeviceStatus.ON || info.userSuspendStatus == SuspendStatus.IN_BACKGROUND,
            showsOverlay = !isOwner,
        )
    }
}
