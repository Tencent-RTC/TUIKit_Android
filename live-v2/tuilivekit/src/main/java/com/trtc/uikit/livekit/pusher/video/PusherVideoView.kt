package com.trtc.uikit.livekit.pusher.video

import android.content.Context
import android.graphics.Outline
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestStore.Layout
import com.trtc.uikit.livekit.pusher.living.coguest.seat.PusherCoGuestSeatViewProvider
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveView
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveViewType

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
            context.resources.getDimensionPixelSize(R.dimen.livekit_pusher_bottom_bar_height) +
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
    }

    internal val coreView = AtomicLiveView(context, null, 0, AtomicLiveViewType.PUSH_VIEW)
    private val coGuestSeatViewProvider = PusherCoGuestSeatViewProvider(context)

    private val cornerRadius =
        resources.getDimension(R.dimen.livekit_pusher_stage_corner_radius)

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.livekit_pusher_video_background))
        coreView.setBackgroundColor(0)
        coreView.setViewChangeAdapter(coGuestSeatViewProvider)
        addView(coreView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // Pulling the top edge above the view keeps only the bottom corners rounded.
                outline.setRoundRect(
                    0,
                    -cornerRadius.toInt(),
                    view.width,
                    view.height,
                    cornerRadius
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
        layout: Layout,
        connectedGuestCount: Int,
    ) {
        coGuestSeatViewProvider.update(isLiving, layout, connectedGuestCount)
        updateCoGuestStageVisible(isLiving && (connectedGuestCount > 0 || layout != Layout.FLOATING))
    }

    internal fun updateLocalCameraStatus(status: DeviceStatus) {
        coGuestSeatViewProvider.updateLocalCameraStatus(status)
    }

    internal var onCoGuestGeometryChanged: (() -> Unit)?
        get() = coGuestSeatViewProvider.onGeometryChanged
        set(value) { coGuestSeatViewProvider.onGeometryChanged = value }

    internal fun coGuestSmallWindowRegions(target: View) = coGuestSeatViewProvider.smallWindowRegions(target)

    fun coGuestUserIDAt(point: PointF): String? =
        coGuestSeatViewProvider.guestUserIDAt(point, this)
}
