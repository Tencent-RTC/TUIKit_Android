package com.trtc.uikit.livekit.pusher.video

import android.content.Context
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveSeat
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.SuspendStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.UnitInfo

/** SDK-owned seat geometry with a reusable cover, badge and empty-seat placeholder. */
internal class PusherUnitView(context: Context) : LiveSeat(
    context, context.getString(R.string.livekit_pusher_coguest_invite_to_seat),
) {
    private var currentUnitInfo = UnitInfo()
    val unitInfo: UnitInfo get() = currentUnitInfo
    val isHost: Boolean get() = unitInfo.userID.isNotEmpty() && unitInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
    val isGuest: Boolean get() = unitInfo.userID.isNotEmpty() && !isHost
    private var isLiving = false
    private var showsEmptySeats = false
    private var hasGuests = false
    private var showsHostOverlay = false
    private var localCameraStatus: DeviceStatus? = null

    fun setUnitInfo(unitInfo: UnitInfo) {
        if (currentUnitInfo.userID != unitInfo.userID || currentUnitInfo.liveID != unitInfo.liveID) updateVolume(0)
        currentUnitInfo = unitInfo.copy()
        updateContent(
            unitInfo.userName.ifEmpty { unitInfo.userID },
            unitInfo.avatarURL,
            unitInfo.microphoneStatus == DeviceStatus.ON,
        )
        refreshVisibility()
    }

    fun setPresentation(
        isLiving: Boolean, showsEmptySeats: Boolean, hasGuests: Boolean, showsHostOverlay: Boolean,
        localCameraStatus: DeviceStatus?
    ) {
        this.isLiving = isLiving
        if (!isLiving) updateVolume(0)
        this.showsEmptySeats = showsEmptySeats
        this.hasGuests = hasGuests
        this.showsHostOverlay = showsHostOverlay
        this.localCameraStatus = localCameraStatus
        refreshVisibility()
    }

    private fun refreshVisibility() {
        val isEmpty = unitInfo.userID.isEmpty()
        // Local preview is already active before the host's seat state catches up.
        val cameraStatus = if (isHost) localCameraStatus else unitInfo.cameraStatus
        val isGuestInBackground = isGuest && unitInfo.userSuspendStatus == SuspendStatus.IN_BACKGROUND
        // INVISIBLE preserves the SDK's measured seat geometry while hiding custom content.
        visibility = if (!isLiving || (isEmpty && !showsEmptySeats)) INVISIBLE else VISIBLE
        updateVisibility(
            isEmpty,
            showsNoVideo = isGuestInBackground || (cameraStatus != null && cameraStatus != DeviceStatus.ON),
            showsOverlay = (isGuest && hasGuests) || (isHost && showsHostOverlay),
        )
    }
}
