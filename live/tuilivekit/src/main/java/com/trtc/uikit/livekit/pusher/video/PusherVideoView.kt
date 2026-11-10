package com.trtc.uikit.livekit.pusher.video

import android.content.Context
import android.graphics.Outline
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.pusher.living.coguest.pusherShowsEmptySeats
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.CoGuestLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveView
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveViewChangeAdapter
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveViewType
import io.trtc.tuikit.atomicxcore.api.view.UnitInfo
import java.lang.ref.WeakReference

/**
 * Hosts the AtomicXCore push-side renderer with rounded bottom corners
 * and square top corners against the screen edge.
 */
class PusherVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        internal fun bottomInset(context: Context, insets: WindowInsetsCompat): Int =
            context.resources.getDimensionPixelSize(R.dimen.livekit_pusher_bottom_bar_height) + insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom
    }

    internal val coreView = AtomicLiveView(context, null, 0, AtomicLiveViewType.PUSH_VIEW)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val managedViews = mutableListOf<WeakReference<PusherUnitView>>()
    private var geometryNotificationPending = false
    private var isLiving = false
    private var connectedGuestCount = 0
    private var showsEmptySeats = false
    private var showsHostOverlay = false
    private var usesSmallWindows = false
    private var usesGrid = false
    private var localCameraStatus: DeviceStatus? = null
    private val cornerRadius = resources.getDimension(R.dimen.livekit_pusher_stage_corner_radius)

    private val unitAdapter = object : AtomicLiveViewChangeAdapter {
        override fun onCreateUnitView(unitInfo: UnitInfo?): View? {
            unitInfo ?: return null
            return PusherUnitView(context).also { view ->
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
            val unit = view as? PusherUnitView ?: return
            val update = {
                unit.setUnitInfo(unitInfo ?: UnitInfo())
                notifyGeometryChanged()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) update() else mainHandler.post { update() }
        }

        override fun onVolumeChanged(unitInfo: UnitInfo?, volume: Int, view: View?) {
            val unit = view as? PusherUnitView ?: return
            val snapshot = unitInfo?.copy()
            val update = {
                if (isLiving && unit.unitInfo.userID.isNotEmpty() && (snapshot == null || (snapshot.userID == unit.unitInfo.userID && snapshot.liveID == unit.unitInfo.liveID))) {
                    unit.updateVolume(volume)
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) update() else mainHandler.post { update() }
        }

        override fun onVideoViewFrameChanged(left: Float, top: Float, right: Float, bottom: Float) {
            if (Looper.myLooper() == Looper.getMainLooper()) notifyGeometryChanged()
            else mainHandler.post { notifyGeometryChanged() }
        }
    }

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.livekit_pusher_video_background))
        coreView.setBackgroundColor(0)
        coreView.setViewChangeAdapter(unitAdapter)
        addView(coreView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // Pulling the top edge above the view keeps only the bottom corners rounded.
                outline.setRoundRect(
                    0, -cornerRadius.toInt(), view.width, view.height, cornerRadius
                )
            }
        }
        clipToOutline = true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        invalidateOutline()
    }

    /**
     * The stage background shows through once co-guest seats are laid out, so the local
     * placeholder colour must disappear at the same moment.
     */
    fun updateCoGuestStageVisible(isVisible: Boolean) {
        setBackgroundColor(
            if (isVisible) {
                0
            } else {
                ContextCompat.getColor(context, R.color.livekit_pusher_video_background)
            }
        )
    }

    fun updateCoGuestVideoState(
        isLiving: Boolean,
        layout: CoGuestLayoutTemplate,
        connectedGuestCount: Int,
    ) {
        usesSmallWindows = layout == CoGuestLayoutTemplate.floatLayout || layout == CoGuestLayoutTemplate.columnLayout
        usesGrid =
            layout == CoGuestLayoutTemplate.gridLayout || (layout == CoGuestLayoutTemplate.dynamicLayout && connectedGuestCount > 0)
        this.isLiving = isLiving
        this.connectedGuestCount = connectedGuestCount
        showsEmptySeats = layout.pusherShowsEmptySeats
        showsHostOverlay =
            layout == CoGuestLayoutTemplate.gridLayout || (layout == CoGuestLayoutTemplate.dynamicLayout && connectedGuestCount > 0)
        currentViews().forEach(::updatePresentation)
        notifyGeometryChanged()
        updateCoGuestStageVisible(isLiving && (connectedGuestCount > 0 || layout != CoGuestLayoutTemplate.floatLayout))
    }

    internal fun updateLocalCameraStatus(status: DeviceStatus) {
        if (localCameraStatus == status) return
        localCameraStatus = status
        // Reused seats may later represent the host, so keep every presentation current.
        currentViews().forEach(::updatePresentation)
    }

    internal var onCoGuestGeometryChanged: (() -> Unit)? = null

    internal fun coGuestSmallWindowRegions(target: View): List<RectF> {
        if (!isLiving || !usesSmallWindows) return emptyList()
        return currentViews().filter { !it.isHost && isVisible(it, target) }.map { region(it, target) }
    }

    internal fun coGuestGridRegions(target: View): List<RectF> {
        if (!isLiving || !usesGrid) return emptyList()
        // The grid boundary includes the host and visible empty seats as well as connected guests.
        return currentViews().filter { isVisible(it, target) }.map { region(it, target) }
    }

    fun coGuestUserIDAt(point: PointF): String? {
        if (!isLiving || connectedGuestCount == 0) return null
        return currentViews().asReversed().firstOrNull { view ->
            view.isGuest && isVisible(view, this) && region(view, this).contains(point.x, point.y)
        }?.unitInfo?.userID
    }

    private fun currentViews(): List<PusherUnitView> {
        managedViews.removeAll { it.get() == null }
        return managedViews.mapNotNull { it.get() }
    }

    private fun isVisible(view: View, target: View): Boolean {
        if (!view.isAttachedToWindow || view.windowToken != target.windowToken || !view.isShown || view.width <= 0 || view.height <= 0) return false
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

    private fun updatePresentation(view: PusherUnitView) {
        view.setPresentation(isLiving, showsEmptySeats, connectedGuestCount > 0, showsHostOverlay, localCameraStatus)
    }

    private fun notifyGeometryChanged() {
        if (geometryNotificationPending) return
        geometryNotificationPending = true
        mainHandler.post {
            geometryNotificationPending = false
            onCoGuestGeometryChanged?.invoke()
        }
    }
}
