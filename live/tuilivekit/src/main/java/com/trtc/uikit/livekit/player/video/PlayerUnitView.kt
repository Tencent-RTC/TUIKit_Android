package com.trtc.uikit.livekit.player.video

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveSeat
import com.trtc.uikit.livekit.player.PlayerStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SuspendStatus
import io.trtc.tuikit.atomicxcore.api.view.UnitInfo

@SuppressLint("ViewConstructor")
class PlayerUnitView(
    context: Context,
    unitInfo: UnitInfo,
    private var liveInfo: LiveInfo? = null,
    initialStatus: PlayerStatus = PlayerStatus.IDLE,
) : LiveSeat(context, context.getString(R.string.common_apply_link_mic)) {

    internal var onGeometryChanged: (() -> Unit)? = null
    internal val representedLiveID: String get() = liveInfo?.liveID.orEmpty()
    private var currentUnitInfo: UnitInfo = unitInfo.copy()
    private var currentStatus: PlayerStatus = initialStatus

    init {
        apply()
    }

    internal fun setUnitInfo(info: UnitInfo, liveInfo: LiveInfo) {
        this.liveInfo = liveInfo
        if (currentUnitInfo.userID != info.userID || currentUnitInfo.liveID != info.liveID) updateVolume(0)
        currentUnitInfo = info.copy()
        apply()
    }

    fun setPlayerStatus(status: PlayerStatus) {
        if (currentStatus == status) return
        currentStatus = status
        if (status != PlayerStatus.IN_ROOM && status != PlayerStatus.CO_GUEST) updateVolume(0)
        apply()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onGeometryChanged?.invoke()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onGeometryChanged?.invoke()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        onGeometryChanged?.invoke()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        onGeometryChanged?.invoke()
    }

    override fun setTranslationX(translationX: Float) {
        val changed = this.translationX != translationX
        super.setTranslationX(translationX)
        if (changed) onGeometryChanged?.invoke()
    }

    override fun setTranslationY(translationY: Float) {
        val changed = this.translationY != translationY
        super.setTranslationY(translationY)
        if (changed) onGeometryChanged?.invoke()
    }

    internal fun updateSpeakingVolume(info: UnitInfo?, volume: Int) {
        if (currentStatus != PlayerStatus.IN_ROOM && currentStatus != PlayerStatus.CO_GUEST) return
        if (currentUnitInfo.userID.isEmpty()) return
        if (info != null && (info.userID != currentUnitInfo.userID || info.liveID != currentUnitInfo.liveID)) return
        updateVolume(volume)
    }

    private fun apply() {
        val info = currentUnitInfo
        val ownerID = liveInfo?.liveOwner?.userID
        val isOwner = !ownerID.isNullOrEmpty() && ownerID == info.userID
        val roomActive = currentStatus == PlayerStatus.IN_ROOM || currentStatus == PlayerStatus.CO_GUEST
        updateContent(
            info.userName.ifEmpty { info.userID },
            info.avatarURL,
            info.microphoneStatus == DeviceStatus.ON,
        )
        updateVisibility(
            isEmpty = info.userID.isEmpty(),
            showsNoVideo = info.cameraStatus != DeviceStatus.ON || info.userSuspendStatus == SuspendStatus.IN_BACKGROUND,
            showsOverlay = !isOwner && roomActive,
        )
    }
}
