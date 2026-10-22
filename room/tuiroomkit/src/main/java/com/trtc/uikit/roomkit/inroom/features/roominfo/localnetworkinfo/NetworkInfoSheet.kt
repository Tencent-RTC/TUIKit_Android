package com.trtc.uikit.roomkit.inroom.features.roominfo.localnetworkinfo

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NetworkInfoSheet(context: Context) : FrameLayout(context) {

    var onDismiss: (() -> Unit)? = null

    private val popup = RoomPopup(context)
    private val statusTitle: TextView
    private val latencyValue: TextView
    private val upLossValue: TextView
    private val downLossValue: TextView
    private val deviceStore = DeviceStore.shared()
    private var subscribeJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_network_info_sheet, this, true)
        statusTitle = findViewById(R.id.network_status_title)
        latencyValue = findViewById(R.id.latency_value)
        upLossValue = findViewById(R.id.uploss_value)
        downLossValue = findViewById(R.id.downloss_value)
        popup.setContentView(this, RoomPopup.ContentHeight.AUTO)
        popup.onDismiss = { onDismiss?.invoke() }
        observeNetwork()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    fun show() {
        observeNetwork()
        popup.show()
    }

    fun dismiss() {
        popup.dismiss()
    }

    private fun observeNetwork() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            deviceStore.deviceState.networkInfo.collect { info ->
                statusTitle.setText(statusRes(info.quality))
                bindMetric(latencyValue, info.delay, delayLevel(info.delay))
                bindMetric(upLossValue, info.upLoss, lossLevel(info.upLoss))
                bindMetric(downLossValue, info.downLoss, lossLevel(info.downLoss))
            }
        }
    }

    private fun bindMetric(view: TextView, value: Int, level: Int) {
        view.text = value.toString()
        view.setTextColor(
            ContextCompat.getColor(
                context,
                when (level) {
                    LEVEL_WARN -> R.color.roomkit_warning
                    LEVEL_BAD -> R.color.roomkit_danger
                    else -> R.color.roomkit_fg_primary
                },
            ),
        )
    }

    private fun statusRes(quality: NetworkQuality): Int = when (quality) {
        NetworkQuality.EXCELLENT, NetworkQuality.GOOD -> R.string.roomkit_network_good
        NetworkQuality.POOR -> R.string.roomkit_network_fair
        NetworkQuality.BAD, NetworkQuality.VERY_BAD -> R.string.roomkit_network_weak
        else -> R.string.roomkit_network_disconnected
    }

    private fun delayLevel(delay: Int): Int = when {
        delay >= DELAY_BAD_MAX -> LEVEL_BAD
        delay >= DELAY_WARN_MAX -> LEVEL_WARN
        else -> LEVEL_OK
    }

    private fun lossLevel(loss: Int): Int = when {
        loss >= LOSS_BAD_MAX -> LEVEL_BAD
        loss >= LOSS_WARN_MAX -> LEVEL_WARN
        else -> LEVEL_OK
    }

    private companion object {
        const val LEVEL_OK = 0
        const val LEVEL_WARN = 1
        const val LEVEL_BAD = 2
        const val DELAY_WARN_MAX = 100
        const val DELAY_BAD_MAX = 200
        const val LOSS_WARN_MAX = 2
        const val LOSS_BAD_MAX = 8
    }
}
