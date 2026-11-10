package com.trtc.uikit.livekit.player.video

import android.content.Context
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import com.trtc.uikit.livekit.common.theme.imageloader.ImageOptions
import com.trtc.uikit.livekit.player.PlayerStatus
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveView
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveViewChangeAdapter
import io.trtc.tuikit.atomicxcore.api.view.UnitInfo
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

class PlayerVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal val coreView = AtomicLiveView(context)
    internal var onGeometryChanged: (() -> Unit)? = null
    private var backgroundView = AppCompatImageView(context)
    private var liveInfo: LiveInfo = LiveInfo()
    private val logger = LiveKitLog.Companion.getComponentLogger("VideoView")
    private var currentStatus: PlayerStatus = PlayerStatus.IDLE
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var geometryGeneration = 0L
    private var geometryNotificationPending = false
    private val activeUnits: MutableSet<PlayerUnitView> = Collections.newSetFromMap(WeakHashMap())

    val unitAdapter = object : AtomicLiveViewChangeAdapter {
        override fun onCreateUnitView(unitInfo: UnitInfo?): View? {
            unitInfo ?: return null
            if (unitInfo.liveID.isNotEmpty() && unitInfo.liveID != liveInfo.liveID) return null
            logger.info("onCreateUnitView: $unitInfo")
            return PlayerUnitView(context, unitInfo, liveInfo, currentStatus).also(::track)
        }

        override fun onUpdateUnitView(
            unitInfo: UnitInfo?, view: View?
        ) {
            logger.info("onUpdateUnitView: view=$view, unitInfo=$unitInfo")
            val unit = view as? PlayerUnitView ?: return
            val snapshot = unitInfo?.copy()
            updateOnMain {
                if (snapshot == null && unit.representedLiveID != liveInfo.liveID) return@updateOnMain
                if (snapshot != null && snapshot.liveID.isNotEmpty() && snapshot.liveID != liveInfo.liveID) {
                    return@updateOnMain
                }
                unit.setUnitInfo(snapshot ?: UnitInfo(), liveInfo)
                unit.setPlayerStatus(currentStatus)
                track(unit)
            }
        }

        override fun onVolumeChanged(unitInfo: UnitInfo?, volume: Int, view: View?) {
            val unit = view as? PlayerUnitView ?: return
            val snapshot = unitInfo?.copy()
            updateOnMain {
                if (unit.representedLiveID == liveInfo.liveID) unit.updateSpeakingVolume(snapshot, volume)
            }
        }

        override fun onVideoViewFrameChanged(
            left: Float, top: Float, right: Float, bottom: Float
        ) {
            updateOnMain { notifyGeometryChanged() }
        }
    }

    init {
        addView(backgroundView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        coreView.setViewChangeAdapter(unitAdapter)
        addView(coreView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setLiveInfo(liveInfo: LiveInfo) {
        if (this.liveInfo.liveID != liveInfo.liveID) {
            geometryGeneration++
            geometryNotificationPending = false
            activeUnits.clear()
        }
        this.liveInfo = liveInfo
        setBackground(liveInfo)
        notifyGeometryChanged()
    }

    fun setPlayerStatus(status: PlayerStatus) {
        if (currentStatus == status) return
        currentStatus = status
        activeUnits.toList().forEach { it.setPlayerStatus(status) }
        notifyGeometryChanged()
    }

    override fun onDetachedFromWindow() {
        geometryGeneration++
        geometryNotificationPending = false
        super.onDetachedFromWindow()
    }

    internal fun visibleSeatRegions(target: View): List<RectF> {
        if (currentStatus != PlayerStatus.IN_ROOM && currentStatus != PlayerStatus.CO_GUEST) return emptyList()
        val targetLocation = IntArray(2)
        target.getLocationInWindow(targetLocation)
        return activeUnits.toList().mapNotNull { view ->
            if (view.representedLiveID != liveInfo.liveID) return@mapNotNull null
            if (!view.isAttachedToWindow || view.windowToken != target.windowToken) return@mapNotNull null
            if (!view.isShown || view.width <= 0 || view.height <= 0) return@mapNotNull null
            var ancestor: View? = view
            while (ancestor != null) {
                if (ancestor.alpha <= 0.01f) return@mapNotNull null
                ancestor = ancestor.parent as? View
            }
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val left = (location[0] - targetLocation[0]).toFloat()
            val top = (location[1] - targetLocation[1]).toFloat()
            RectF(left, top, left + view.width, top + view.height)
        }
    }

    private fun track(view: PlayerUnitView) {
        activeUnits.add(view)
        val owner = WeakReference(this)
        val unit = WeakReference(view)
        view.onGeometryChanged = {
            val video = owner.get()
            if (video != null && unit.get()?.representedLiveID == video.liveInfo.liveID) {
                video.notifyGeometryChanged()
            }
        }
        notifyGeometryChanged()
    }

    private fun updateOnMain(update: PlayerVideoView.() -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update()
            return
        }
        val generation = geometryGeneration
        val owner = WeakReference(this)
        mainHandler.post {
            owner.get()?.takeIf { it.geometryGeneration == generation }?.update()
        }
    }

    private fun notifyGeometryChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            updateOnMain { notifyGeometryChanged() }
            return
        }
        if (geometryNotificationPending) return
        geometryNotificationPending = true
        val generation = geometryGeneration
        val owner = WeakReference(this)
        mainHandler.post {
            val video = owner.get() ?: return@post
            if (video.geometryGeneration != generation) return@post
            video.geometryNotificationPending = false
            if (video.isAttachedToWindow) video.onGeometryChanged?.invoke()
        }
    }

    private fun setBackground(liveInfo: LiveInfo) {
        val url = liveInfo.backgroundURL.ifEmpty {
            liveInfo.coverURL.ifEmpty { LiveKitResources.defaultBackgroundURL }
        }

        val config = ImageOptions.Builder().setBlurEffect(80f).setPlaceImage(R.drawable.livekit_video_background)
            .setErrorImage(R.drawable.livekit_video_background).build()
        ImageLoader.load(context, backgroundView, url, config = config)
    }
}
