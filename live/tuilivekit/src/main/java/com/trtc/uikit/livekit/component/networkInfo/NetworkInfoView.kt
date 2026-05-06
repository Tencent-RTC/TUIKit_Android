package com.trtc.uikit.livekit.component.networkInfo

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ui.BasicView
import com.trtc.uikit.livekit.component.networkInfo.store.NetworkInfoState
import com.trtc.uikit.livekit.component.networkInfo.store.NetworkInfoStore
import com.trtc.uikit.livekit.component.networkInfo.view.NetworkBadTipsDialog
import com.trtc.uikit.livekit.component.networkInfo.view.NetworkInfoPanel
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.min

class NetworkInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BasicView(context, attrs, defStyleAttr) {
    private val networkInfoStore: NetworkInfoStore = NetworkInfoStore(context)
    private val state: NetworkInfoState = networkInfoStore.networkInfoState
    private lateinit var imageNetworkStatus: ImageView
    private lateinit var textCreateTime: TextView
    private var networkInfoPanel: NetworkInfoPanel? = null
    private lateinit var layoutNetworkInfo: LinearLayout
    private var createTime: Long = 0L
    private lateinit var liveInfo: LiveInfo
    private var liveTimeRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            if (networkInfoPanel?.isShowing == true) {
                networkInfoPanel?.dismiss()
            }
        }
    }

    init {
        initView()
    }

    fun init(liveInfo: LiveInfo) {
        this.liveInfo = liveInfo
        networkInfoStore.networkInfoState.liveInfo = liveInfo
        init(liveInfo.liveID)
        val now = System.currentTimeMillis()
        this@NetworkInfoView.createTime = min(liveInfo.createTime, now)
        startLiveTimer()
    }

    override fun initStore() {
        LiveSeatStore.create(liveInfo.liveID)
    }

    fun setScreenOrientation(isPortrait: Boolean) {
        layoutNetworkInfo.isEnabled = isPortrait
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.network_info_view, this, true)
        bindViewId()
        initNetworkView()
    }

    private fun bindViewId() {
        layoutNetworkInfo = findViewById(R.id.ll_network_info)
        imageNetworkStatus = findViewById(R.id.iv_network_status)
        textCreateTime = findViewById(R.id.tv_live_time)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLiveTimer()
    }

    override fun addObserver() {
        networkInfoStore.addObserver()
        LiveListStore.shared().addLiveListListener(liveListListener)
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                state.networkStatus.collect {
                    onNetworkQualityChange(it)
                }
            }

            launch {
                state.isDisplayNetworkWeakTips.collect {
                    onNetworkWeakTipsChange(it)
                }
            }

            launch {
                LiveSeatStore.create(liveInfo.liveID).liveSeatState.avStatistics.collect {
                    networkInfoStore.handleStatisticsChanged(it)
                }
            }

            launch {
                LiveSeatStore.create(liveInfo.liveID).liveSeatState.seatList.collect {
                    networkInfoStore.handleSeatListChanged(it)
                }
            }

            launch {
                DeviceStore.shared().deviceState.networkInfo.collect {
                    networkInfoStore.handleNetworkQualityChange(it)
                }
            }
        }
    }

    override fun removeObserver() {
        networkInfoStore.removeObserver()
        LiveListStore.shared().removeLiveListListener(liveListListener)
        subscribeStateJob?.cancel()
    }

    private fun initNetworkView() {
        layoutNetworkInfo.setOnClickListener {
            networkInfoPanel = NetworkInfoPanel(
                context,
                networkInfoStore,
                state.isTakeInSeat.value
            )
            networkInfoPanel?.show()
        }
    }

    private fun startLiveTimer() {
        liveTimeRunnable?.let { handler.removeCallbacks(it) }

        liveTimeRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val duration = now - createTime
                textCreateTime.text = formatDuration(duration)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(liveTimeRunnable!!)
    }

    private fun stopLiveTimer() {
        liveTimeRunnable?.let {
            handler.removeCallbacks(it)
            liveTimeRunnable = null
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun onNetworkQualityChange(networkQuality: NetworkQuality) {
        val resId = when (networkQuality) {
            NetworkQuality.POOR -> R.drawable.network_info_network_status_poor
            NetworkQuality.BAD -> R.drawable.network_info_network_status_very_bad
            NetworkQuality.VERY_BAD,
            NetworkQuality.DOWN -> R.drawable.network_info_network_status_down

            else -> R.drawable.network_info_network_status_good
        }
        imageNetworkStatus.setImageResource(resId)
    }

    private fun onNetworkWeakTipsChange(isShow: Boolean?) {
        if (isShow == true) {
            val dialog = NetworkBadTipsDialog(context)
            dialog.show()
        }
    }
}