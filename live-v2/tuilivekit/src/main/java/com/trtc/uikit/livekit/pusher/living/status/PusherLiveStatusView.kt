package com.trtc.uikit.livekit.pusher.living.status

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsState
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryData
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryStore
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PusherLiveStatusView(context: Context) : BottomSheetView(context) {
    internal var onSetCaptureVolume: ((Int) -> Unit)? = null
    private val tabs: View
    private val selection: View
    private val realtimeTab: TextView
    private val deviceTab: TextView
    private val durationView: TextView
    private val realtimeStatisticsView: PusherLiveStatisticsView
    private val deviceStatusView: PusherDeviceStatusView
    private var summaryStore: LiveSummaryStore? = null
    private var summaryScope: CoroutineScope? = null
    private var elapsedSeconds = 0
    private var viewerCount = 0
    private var likeCount = 0
    private var summaryData = LiveSummaryData()
    private var devicePresentation = PusherDeviceStatusView.Presentation()
    private var renderedDevicePresentation: PusherDeviceStatusView.Presentation? = null
    private var statusBarTop = 0

    override val sheetHeight: Int
        get() = minOf(dp(DESIGN_HEIGHT_DP), context.resources.displayMetrics.heightPixels - statusBarTop)

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_live_status_view, contentView, true)
        tabs = contentView.findViewById(R.id.livekit_pusher_status_tabs)
        selection = contentView.findViewById(R.id.livekit_pusher_status_tab_selection)
        realtimeTab = contentView.findViewById(R.id.livekit_pusher_status_realtime_tab)
        deviceTab = contentView.findViewById(R.id.livekit_pusher_status_device_tab)
        durationView = contentView.findViewById(R.id.livekit_pusher_status_duration)
        realtimeStatisticsView = contentView.findViewById(R.id.livekit_pusher_status_metrics_page)
        deviceStatusView = contentView.findViewById(R.id.livekit_pusher_status_device_page)
        deviceStatusView.onSetCaptureVolume = { onSetCaptureVolume?.invoke(it) }
        realtimeTab.setOnClickListener { showPage(false) }
        deviceTab.setOnClickListener { showPage(true) }
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val scroll = contentView.findViewById<View>(R.id.livekit_pusher_status_scroll)
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (scroll.paddingBottom != bottom) scroll.setPadding(0, 0, 0, bottom)
            updateSheetHeight(animated = false)
            insets
        }
        updateDuration(0)
    }

    internal fun bind(liveID: String) {
        stopObservingStatistics()
        summaryStore = liveID.takeIf(String::isNotEmpty)?.let(LiveSummaryStore::create)
        summaryData = summaryStore?.liveSummaryState?.summaryData?.value ?: LiveSummaryData()
    }

    fun updateElapsedSeconds(seconds: Int) {
        val value = seconds.coerceAtLeast(0)
        if (elapsedSeconds == value) return
        elapsedSeconds = value
        if (isShowing) updateDuration(value)
    }

    internal fun bind(
        settings: PusherSettingsState,
        deviceStatus: PusherDeviceStatusState,
    ) {
        updateDeviceStatus(deviceStatus, settings.videoQuality, settings.isMuted, settings.captureVolume)
    }

    internal fun updateDeviceStatus(
        deviceStatus: PusherDeviceStatusState,
        videoQuality: VideoQuality,
        isMuted: Boolean,
        captureVolume: Int,
    ) {
        devicePresentation = PusherDeviceStatusView.Presentation(deviceStatus, videoQuality, isMuted, captureVolume)
        renderDevice()
    }

    fun updateAudienceCount(count: Int) {
        val value = count.coerceAtLeast(0)
        if (viewerCount == value) return
        viewerCount = value
        renderStatistics()
    }

    fun updateLikeCount(count: Int) {
        val value = count.coerceAtLeast(0)
        if (likeCount == value) return
        likeCount = value
        renderStatistics()
    }

    override fun show() {
        showPage(false)
        super.show()
        updateDuration(elapsedSeconds)
        startObservingStatistics()
    }

    override fun sheetWillDismiss() {
        stopObservingStatistics()
    }

    override fun sheetDidDismiss() {
        stopObservingStatistics()
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    private fun showPage(deviceSelected: Boolean) {
        realtimeStatisticsView.isVisible = !deviceSelected
        deviceStatusView.isVisible = deviceSelected
        realtimeTab.setTextColor(if (deviceSelected) ContextCompat.getColor(context, R.color.livekit_pusher_status_tab_idle) else 0xFFFFFFFF.toInt())
        deviceTab.setTextColor(if (deviceSelected) 0xFFFFFFFF.toInt() else ContextCompat.getColor(context, R.color.livekit_pusher_status_tab_idle))
        tabs.post {
            val itemWidth = (tabs.width - dp(12)) / 2
            selection.layoutParams = selection.layoutParams.apply { width = itemWidth }
            selection.translationX = if (deviceSelected) (itemWidth + dp(4)).toFloat() else 0f
        }
    }

    private fun updateDuration(seconds: Int) {
        val value = seconds.coerceAtLeast(0)
        durationView.text = String.format(
            Locale.US,
            "%02d:%02d:%02d",
            value / 3600,
            value % 3600 / 60,
            value % 60,
        )
    }

    private fun startObservingStatistics() {
        summaryData = summaryStore?.liveSummaryState?.summaryData?.value ?: LiveSummaryData()
        renderStatistics()
        renderDevice()
        if (summaryScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        summaryScope = scope
        summaryStore?.liveSummaryState?.summaryData?.onEach { data ->
            summaryData = data
            renderStatistics()
        }?.launchIn(scope)

    }

    private fun stopObservingStatistics() {
        summaryScope?.cancel()
        summaryScope = null
    }

    private fun renderStatistics() {
        if (!isShowing) return
        realtimeStatisticsView.bind(
            data = summaryData,
            onlineCount = viewerCount,
            likeCount = likeCount,
        )
    }

    private fun renderDevice() {
        if (!isShowing) return
        val presentation = devicePresentation
        if (renderedDevicePresentation == presentation) return
        renderedDevicePresentation = presentation
        deviceStatusView.bind(presentation)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val DESIGN_HEIGHT_DP = 656
    }
}
