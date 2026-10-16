package com.trtc.uikit.livekit.pusher.living.status

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryData

class PusherLiveStatisticsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val metrics: List<LiveStatusMetricView>

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_status_metrics_view, this, true)
        metrics = listOf(
            findViewById(R.id.livekit_pusher_status_gift_score),
            findViewById(R.id.livekit_pusher_status_gifters),
            findViewById(R.id.livekit_pusher_status_gifts),
            findViewById(R.id.livekit_pusher_status_online),
            findViewById(R.id.livekit_pusher_status_likes),
            findViewById(R.id.livekit_pusher_status_messages),
        )
        bind(LiveSummaryData(), 0, 0)
    }

    fun bind(
        data: LiveSummaryData,
        onlineCount: Int,
        likeCount: Int,
    ) {
        val titles = intArrayOf(
            R.string.livekit_pusher_status_metric_gift_score,
            R.string.livekit_pusher_status_metric_gifters,
            R.string.livekit_pusher_status_metric_gifts,
            R.string.livekit_pusher_status_metric_online,
            R.string.livekit_pusher_status_metric_likes,
            R.string.livekit_pusher_status_metric_messages,
        )
        val units = intArrayOf(
            R.string.livekit_pusher_status_unit_point,
            R.string.livekit_pusher_status_unit_person,
            R.string.livekit_pusher_status_unit_count,
            R.string.livekit_pusher_status_unit_person,
            R.string.livekit_pusher_status_unit_time,
            R.string.livekit_pusher_status_unit_messages,
        )
        val values = longArrayOf(
            data.totalGiftCoins.toLong(),
            data.totalGiftUniqueSenders.toLong(),
            data.totalGiftsSent.toLong(),
            onlineCount.toLong(),
            likeCount.toLong(),
            data.totalMessageSent.toLong(),
        )
        metrics.indices.forEach { metrics[it].bind(titles[it], units[it], values[it]) }
    }
}

class LiveStatusMetricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val titleView: TextView
    private val valueView: TextView
    private val unitView: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_status_metric_item, this, true)
        titleView = findViewById(R.id.livekit_pusher_status_metric_title)
        valueView = findViewById(R.id.livekit_pusher_status_metric_value)
        unitView = findViewById(R.id.livekit_pusher_status_metric_unit)
    }

    fun bind(@StringRes title: Int, @StringRes unit: Int, value: Long) {
        titleView.setText(title)
        unitView.setText(unit)
        valueView.text = value.coerceAtLeast(0L).toString()
    }
}
