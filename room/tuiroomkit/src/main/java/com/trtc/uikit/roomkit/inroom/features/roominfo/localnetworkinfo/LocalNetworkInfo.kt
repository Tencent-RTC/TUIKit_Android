package com.trtc.uikit.roomkit.inroom.features.roominfo.localnetworkinfo

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.base.util.dpF
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LocalNetworkInfo @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val chip: FrameLayout
    private val barsContainer: LinearLayout
    private val bars: List<View>
    private val deviceStore = DeviceStore.shared()
    private var subscribeJob: Job? = null
    private var networkSheet: NetworkInfoSheet? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_local_network_info, this, true)
        setOnClickListener { showNetworkSheet() }
        chip = findViewById(R.id.network_chip)
        barsContainer = findViewById(R.id.network_bars)
        bars = BAR_HEIGHTS_DP.mapIndexed { index, height ->
            View(context).apply {
                val params = LinearLayout.LayoutParams(context.dp(BAR_WIDTH_DP), context.dp(height))
                if (index > 0) params.marginStart = context.dp(BAR_GAP_DP)
                params.gravity = Gravity.BOTTOM
                layoutParams = params
                background = GradientDrawable().apply {
                    cornerRadius = context.dpF(BAR_CORNER_RADIUS_DP)
                    setColor(ContextCompat.getColor(context, R.color.roomkit_success))
                }
            }
        }
        bars.forEach { barsContainer.addView(it) }
        render(NetworkQuality.EXCELLENT)
    }

    private fun showNetworkSheet() {
        if (networkSheet != null) return
        val sheet = NetworkInfoSheet(context)
        sheet.onDismiss = { networkSheet = null }
        networkSheet = sheet
        sheet.show()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            deviceStore.deviceState.networkInfo.collect { info -> render(info.quality) }
        }
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun render(quality: NetworkQuality) {
        val level: Int
        val colorRes: Int
        when (quality) {
            NetworkQuality.EXCELLENT, NetworkQuality.GOOD -> {
                level = LEVEL_GOOD
                colorRes = R.color.roomkit_success
            }
            NetworkQuality.POOR -> {
                level = LEVEL_FAIR
                colorRes = R.color.roomkit_warning
            }
            NetworkQuality.BAD, NetworkQuality.VERY_BAD -> {
                level = LEVEL_WEAK
                colorRes = R.color.roomkit_danger
            }
            else -> {
                level = LEVEL_DOWN
                colorRes = R.color.roomkit_danger
            }
        }
        chip.background = if (level <= LEVEL_WEAK) {
            GradientDrawable().apply {
                cornerRadius = context.dp(CHIP_RADIUS_DP).toFloat()
                setColor(ContextCompat.getColor(context, R.color.roomkit_danger_faint))
            }
        } else {
            null
        }
        val color = ContextCompat.getColor(context, colorRes)
        bars.forEachIndexed { index, bar ->
            (bar.background as? GradientDrawable)?.setColor(color)
            bar.alpha = if (index < level) ALPHA_LIT else if (level == LEVEL_DOWN) ALPHA_DOWN else ALPHA_DIM
        }
    }

    private companion object {
        val BAR_HEIGHTS_DP = listOf(4, 8, 12)
        const val BAR_WIDTH_DP = 3
        const val BAR_GAP_DP = 2
        const val BAR_CORNER_RADIUS_DP = 1f
        const val LEVEL_GOOD = 3
        const val LEVEL_FAIR = 2
        const val LEVEL_WEAK = 1
        const val LEVEL_DOWN = 0
        const val ALPHA_LIT = 1f
        const val ALPHA_DIM = 0.28f
        const val ALPHA_DOWN = 0.18f
        const val CHIP_RADIUS_DP = 8
    }
}
