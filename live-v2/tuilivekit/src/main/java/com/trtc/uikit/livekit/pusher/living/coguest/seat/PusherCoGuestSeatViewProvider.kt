package com.trtc.uikit.livekit.pusher.living.coguest.seat

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveSeat
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestStore.Layout
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.SuspendStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveViewChangeAdapter
import io.trtc.tuikit.atomicxcore.api.view.UnitInfo
import java.lang.ref.WeakReference

internal class PusherCoGuestSeatViewProvider(
    private val context: Context,
) : AtomicLiveViewChangeAdapter {
    var onGeometryChanged: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var geometryNotificationPending = false
    private val managedViews = mutableListOf<WeakReference<PusherCoGuestUnitView>>()
    private var isLiving = false
    private var connectedGuestCount = 0
    private var showsEmptySeats = false
    private var showsHostOverlay = false
    private var usesSmallWindows = false
    private var localCameraStatus: DeviceStatus? = null

    fun updateLocalCameraStatus(status: DeviceStatus) {
        if (localCameraStatus == status) return
        localCameraStatus = status
        // Reused seats may later represent the host, so keep every presentation current.
        currentViews().forEach(::updatePresentation)
    }

    fun update(isLiving: Boolean, layout: Layout, connectedGuestCount: Int) {
        usesSmallWindows = layout == Layout.FLOATING || layout == Layout.FIXED
        this.isLiving = isLiving
        this.connectedGuestCount = connectedGuestCount
        showsEmptySeats = layout.showsEmptySeats
        showsHostOverlay = layout == Layout.GRID_9 || (layout == Layout.GRID_2 && connectedGuestCount > 0)
        currentViews().forEach(::updatePresentation)
        notifyGeometryChanged()
    }

    override fun onCreateUnitView(unitInfo: UnitInfo?): View? {
        unitInfo ?: return null
        return PusherCoGuestUnitView(context).also { view ->
            updatePresentation(view)
            view.setUnitInfo(unitInfo)
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> notifyGeometryChanged() }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = notifyGeometryChanged()
                override fun onViewDetachedFromWindow(v: View) = notifyGeometryChanged()
            })
            currentViews()
            managedViews += WeakReference(view)
            notifyGeometryChanged()
        }
    }

    override fun onUpdateUnitView(unitInfo: UnitInfo?, view: View?) {
        val unit = view as? PusherCoGuestUnitView ?: return
        val update = {
            unit.setUnitInfo(unitInfo ?: UnitInfo())
            notifyGeometryChanged()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update() else mainHandler.post { update() }
    }

    override fun onVideoViewFrameChanged(left: Float, top: Float, right: Float, bottom: Float) {
        if (Looper.myLooper() == Looper.getMainLooper()) notifyGeometryChanged()
        else mainHandler.post { notifyGeometryChanged() }
    }

    fun guestUserIDAt(point: PointF, sourceView: View): String? {
        if (!isLiving || connectedGuestCount == 0) return null
        return currentViews().asReversed().firstOrNull { view ->
            view.isGuest && isVisible(view, sourceView) && region(view, sourceView).contains(point.x, point.y)
        }?.unitInfo?.userID
    }

    fun smallWindowRegions(target: View): List<RectF> {
        if (!isLiving || !usesSmallWindows) return emptyList()
        return currentViews().filter { !it.isHost && isVisible(it, target) }.map { region(it, target) }
    }

    private fun currentViews(): List<PusherCoGuestUnitView> {
        managedViews.removeAll { it.get() == null }
        return managedViews.mapNotNull { it.get() }
    }

    private fun isVisible(view: View, target: View): Boolean {
        if (!view.isAttachedToWindow || view.windowToken != target.windowToken ||
            !view.isShown || view.width <= 0 || view.height <= 0) return false
        var ancestor: View? = view
        while (ancestor != null) {
            if (ancestor.alpha <= 0.01f) return false
            ancestor = ancestor.parent as? View
        }
        return true
    }

    private fun region(view: View, target: View): RectF {
        val origin = IntArray(2)
        val location = IntArray(2)
        target.getLocationInWindow(origin)
        view.getLocationInWindow(location)
        val x = (location[0] - origin[0]).toFloat()
        val y = (location[1] - origin[1]).toFloat()
        return RectF(x, y, x + view.width, y + view.height)
    }

    private fun updatePresentation(view: PusherCoGuestUnitView) {
        view.setPresentation(isLiving, showsEmptySeats, connectedGuestCount > 0, showsHostOverlay, localCameraStatus)
    }

    private fun notifyGeometryChanged() {
        if (geometryNotificationPending) return
        geometryNotificationPending = true
        mainHandler.post {
            geometryNotificationPending = false
            onGeometryChanged?.invoke()
        }
    }
}

/** SDK-owned seat geometry with a reusable cover, badge and empty-seat placeholder. */
private class PusherCoGuestUnitView(context: Context) : LiveSeat(
    context, context.getString(R.string.livekit_pusher_coguest_invite_to_seat),
) {
    private var currentUnitInfo = UnitInfo()
    val unitInfo: UnitInfo get() = currentUnitInfo
    val isHost: Boolean get() = unitInfo.userID.isNotEmpty() &&
        unitInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
    val isGuest: Boolean get() = unitInfo.userID.isNotEmpty() && !isHost
    private var isLiving = false
    private var showsEmptySeats = false
    private var hasGuests = false
    private var showsHostOverlay = false
    private var localCameraStatus: DeviceStatus? = null

    fun setUnitInfo(unitInfo: UnitInfo) {
        currentUnitInfo = unitInfo
        updateContent(
            unitInfo.userName.ifEmpty { unitInfo.userID },
            unitInfo.avatarURL,
            unitInfo.microphoneStatus == DeviceStatus.ON,
        )
        refreshVisibility()
    }

    fun setPresentation(isLiving: Boolean, showsEmptySeats: Boolean, hasGuests: Boolean, showsHostOverlay: Boolean, localCameraStatus: DeviceStatus?) {
        this.isLiving = isLiving
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
