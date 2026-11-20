package com.trtc.uikit.livekit.voicelounge.living.status

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.pusher.living.status.PusherLiveStatisticsView
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLiveClock
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import io.trtc.tuikit.atomicxcore.api.live.AtomicPusher
import io.trtc.tuikit.atomicxcore.api.live.LikeStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryData
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

internal class VoiceLoungeStatusPanel(
    context: Context,
    liveID: String,
    private val clock: VoiceLoungeLiveClock,
) : BottomSheetView(context) {
    private val summaryStore = LiveSummaryStore.create(liveID)
    private val likeStore = LikeStore.create(liveID)
    private var scope: CoroutineScope? = null
    private var summaryData = LiveSummaryData()
    private var onlineCount = 0
    private var likeCount = 0

    private val titleLabel = voiceLoungeText(
        context, LiveTheme.TextRole.PAGE_TITLE, text = context.getString(R.string.livekit_voiceroom_status_title),
    )
    private val durationDot = View(context)
    private val durationLabel = TextView(context)
    private val statisticsView = PusherLiveStatisticsView(context)
    private val ticker = object : Runnable {
        override fun run() {
            refreshDuration()
            contentView.postDelayed(this, TICK_MS)
        }
    }

    override val sheetHeight: Int
        get() {
            val design = context.dp(DESIGN_HEIGHT)
            val screen = context.resources.displayMetrics.heightPixels
            return if (screen > 0) minOf(design, screen) else design
        }

    init {
        contentView.addView(
            titleLabel, frameParams(WRAP, context.dp(28), Gravity.TOP, start = context.dp(26), top = context.dp(40))
        )
        contentView.addView(
            durationLabel,
            frameParams(
                context.dp(79), context.dp(24), Gravity.TOP or Gravity.END, end = context.dp(16), top = context.dp(41)
            ),
        )
        contentView.addView(
            durationDot,
            frameParams(
                context.dp(6), context.dp(6), Gravity.TOP or Gravity.END, end = context.dp(16 + 79 - 3),
                top = context.dp(41 + 9)
            ),
        )
        contentView.addView(statisticsView, frameParams(MATCH, WRAP, Gravity.TOP, top = context.dp(40 + 28 + 24)))
        durationDot.background = ovalDrawable(VoiceLoungeColor.brandBlue)
        durationLabel.setTextColor(Color.rgb(230, 230, 230))
        durationLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        durationLabel.typeface = Typeface.MONOSPACE
        durationLabel.gravity = Gravity.CENTER
        durationLabel.includeFontPadding = false
        durationLabel.text = "00:00:00"
    }

    override fun sheetWillPresent() = startObserving()

    override fun sheetWillDismiss() = stopObserving()

    override fun sheetDidDismiss() = stopObserving()

    private fun startObserving() {
        refreshDuration()
        if (scope != null) return
        contentView.removeCallbacks(ticker)
        contentView.postDelayed(ticker, TICK_MS)
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        activeScope.launch {
            summaryStore.liveSummaryState.summaryData.collect {
                summaryData = it
                refreshStatistics()
            }
        }
        activeScope.launch {
            AtomicPusher.shared().state.totalViewerCount.map { maxOf(0, it) }.distinctUntilChanged().collect {
                onlineCount = it
                refreshStatistics()
            }
        }
        activeScope.launch {
            likeStore.likeState.totalLikeCount.map { it.toInt() }.distinctUntilChanged().collect {
                likeCount = it
                refreshStatistics()
            }
        }
    }

    private fun stopObserving() {
        scope?.cancel()
        scope = null
        contentView.removeCallbacks(ticker)
    }

    private fun refreshDuration() {
        durationLabel.text = formatDuration(clock.elapsedSeconds)
    }

    private fun refreshStatistics() = statisticsView.bind(summaryData, onlineCount, likeCount)

    private companion object {
        const val DESIGN_HEIGHT = 460
        const val TICK_MS = 1_000L

        fun formatDuration(seconds: Int): String =
            String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
    }
}
