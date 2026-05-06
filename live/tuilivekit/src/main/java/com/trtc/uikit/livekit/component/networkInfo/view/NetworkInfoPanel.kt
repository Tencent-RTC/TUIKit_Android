package com.trtc.uikit.livekit.component.networkInfo.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.networkInfo.store.NetworkInfoState
import com.trtc.uikit.livekit.component.networkInfo.store.NetworkInfoStore
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NetworkInfoPanel(
    private val context: Context,
    private val service: NetworkInfoStore,
    private var isTakeSeat: Boolean
) : AtomicPopover(context) {

    private val state: NetworkInfoState = service.networkInfoState
    private val colorNormal: Int = ContextCompat.getColor(context, R.color.common_text_color_normal)
    private val colorAbnormal: Int = ContextCompat.getColor(context, R.color.common_text_color_abnormal)
    private val isRtl: Boolean = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private lateinit var imageVideoStatus: ImageView
    private lateinit var imageAudioStatus: ImageView
    private lateinit var imageDeviceTemp: ImageView
    private lateinit var imageNetworkStatus: ImageView
    private lateinit var textVideoStatus: TextView
    private lateinit var textAudioStatus: TextView
    private lateinit var textDeviceTemp: TextView
    private lateinit var textNetworkStatus: TextView
    private lateinit var textResolution: TextView
    private lateinit var textAudioMode: TextView
    private lateinit var textVideoDescription: TextView
    private lateinit var layoutStreamStatus: LinearLayout
    private lateinit var layoutVideoStatus: LinearLayout
    private lateinit var textRTT: TextView
    private lateinit var textDownLoss: TextView
    private lateinit var textUpLoss: TextView
    private lateinit var seekVolume: SeekBar
    private lateinit var textVolume: TextView
    private lateinit var layoutAudioMode: LinearLayout
    private var subscribeStateJob: Job? = null

    init {
        initView()
    }

    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.network_info_panel, null)
        bindViewId(view)
        initVolumeView()
        setContent(view)
    }

    private fun bindViewId(view: View) {
        layoutStreamStatus = view.findViewById(R.id.ll_host_stream_status)
        layoutVideoStatus = view.findViewById(R.id.ll_video_status)
        imageVideoStatus = view.findViewById(R.id.iv_video_status)
        imageAudioStatus = view.findViewById(R.id.iv_audio_status)
        imageDeviceTemp = view.findViewById(R.id.iv_device_temp)
        imageNetworkStatus = view.findViewById(R.id.iv_network_status)
        textVideoStatus = view.findViewById(R.id.tv_video_status)
        textAudioStatus = view.findViewById(R.id.tv_audio_status)
        textDeviceTemp = view.findViewById(R.id.tv_device_status)
        textNetworkStatus = view.findViewById(R.id.tv_network_status)
        textResolution = view.findViewById(R.id.tv_resolution)
        textAudioMode = view.findViewById(R.id.tv_audio_mode)
        textVideoDescription = view.findViewById(R.id.tv_video_quality)
        textRTT = view.findViewById(R.id.tv_rtt)
        textDownLoss = view.findViewById(R.id.tv_down_loss)
        textUpLoss = view.findViewById(R.id.tv_up_loss)
        seekVolume = view.findViewById(R.id.sb_audio_volume)
        textVolume = view.findViewById(R.id.tv_audio_volume)
        layoutAudioMode = view.findViewById(R.id.ll_audio_mode)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        service.initAudioCaptureVolume()
        addObserver()
        initChildViewVisible()
        initDeviceTempView()
        initAudioModeView()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                state.videoStatus.collect {
                    onVideoStatusChange(it)
                }
            }

            launch {
                state.resolution.collect {
                    onVideoResolutionChange(it)
                }
            }

            launch {
                state.audioStatus.collect {
                    onAudioStatusChange(it)
                }
            }

            launch {
                state.audioMode.collect {
                    onAudioQualityChange(it)
                }
            }

            launch {
                state.audioCaptureVolume.collect {
                    onVolumeChange(it)
                }
            }

            launch {
                state.networkStatus.collect {
                    onNetWorkStatusChange(it)
                }
            }

            launch {
                state.rtt.collect {
                    onRTTChange(it)
                }
            }

            launch {
                state.upLoss.collect {
                    onUpLossChange(it)
                }
            }

            launch {
                state.downLoss.collect {
                    onDownLossChange(it)
                }
            }

            launch {
                state.isTakeInSeat.collect {
                    onTakeSeatStatusChange(it)
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun initVolumeView() {
        seekVolume.max = 100
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                textVolume.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Empty implementation
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                service.setAudioCaptureVolume(seekBar.progress)
                service.updateAudioStatusByVolume(seekBar.progress)
            }
        })
    }

    private fun initChildViewVisible() {
        layoutStreamStatus.visibility = if (isTakeSeat) View.VISIBLE else View.GONE
        layoutVideoStatus.visibility = if (state.liveInfo?.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats
            && state.liveInfo?.keepOwnerOnSeat == true
        ) View.GONE else View.VISIBLE
    }

    private fun initAudioModeView() {
        val audioMode = state.audioMode.value
        val textResId = when (audioMode) {
            TUIRoomDefine.AudioQuality.SPEECH -> R.string.common_audio_mode_speech
            TUIRoomDefine.AudioQuality.MUSIC -> R.string.common_audio_mode_music
            else -> R.string.common_audio_mode_default
        }
        textAudioMode.setText(textResId)

        layoutAudioMode.setOnClickListener {
            hide()
            val audioModePanel = AudioModePanel(context)
            audioModePanel.setAudioModeListener(object : OnAudioModeListener {
                override fun onAudioModeChecked(audioQuality: TUIRoomDefine.AudioQuality) {
                    service.updateAudioMode(audioQuality)
                }
            })
            audioModePanel.show()
        }
    }

    private fun initDeviceTempView() {
        service.checkDeviceTemperature(context)
        val imageRes = if (state.isDeviceThermal) {
            R.drawable.network_info_device_temp_abnormal
        } else {
            R.drawable.network_info_device_temp_normal
        }
        imageDeviceTemp.setImageResource(imageRes)

        val textRes = if (state.isDeviceThermal) {
            R.string.common_exception
        } else {
            R.string.common_normal
        }
        textDeviceTemp.setText(textRes)
    }

    private fun onVideoResolutionChange(resolution: String?) {
        textResolution.text = resolution
    }

    private fun onVideoStatusChange(videoStatus: NetworkInfoState.Status?) {
        when (videoStatus) {
            NetworkInfoState.Status.Normal -> {
                imageVideoStatus.setImageResource(R.drawable.network_info_video_status_normal)
                textVideoStatus.setText(R.string.common_normal)
                textVideoDescription.setText(R.string.common_video_stream_smooth)
            }

            NetworkInfoState.Status.Abnormal -> {
                imageVideoStatus.setImageResource(R.drawable.network_info_video_status_abnormal)
                textVideoStatus.setText(R.string.common_exception)
                textVideoDescription.setText(R.string.common_video_stream_freezing)
            }

            else -> {
                imageVideoStatus.setImageResource(R.drawable.network_info_video_status_abnormal)
                textVideoStatus.setText(R.string.common_close)
                textVideoDescription.setText(R.string.common_video_capture_closed)
            }
        }
    }

    private fun onAudioStatusChange(audioStatus: NetworkInfoState.Status?) {
        when (audioStatus) {
            NetworkInfoState.Status.Normal -> {
                imageAudioStatus.setImageResource(R.drawable.network_info_audio_status_normal)
                textAudioStatus.setText(R.string.common_normal)
            }

            NetworkInfoState.Status.Abnormal -> {
                imageAudioStatus.setImageResource(R.drawable.network_info_audio_status_abnormal)
                textAudioStatus.setText(R.string.common_exception)
            }

            else -> {
                imageAudioStatus.setImageResource(R.drawable.network_info_audio_status_abnormal)
                textAudioStatus.setText(R.string.common_close)
            }
        }
    }

    private fun onAudioQualityChange(audioQuality: TUIRoomDefine.AudioQuality?) {
        val resId = when (audioQuality) {
            TUIRoomDefine.AudioQuality.SPEECH -> R.string.common_audio_mode_speech
            TUIRoomDefine.AudioQuality.DEFAULT -> R.string.common_audio_mode_default
            else -> R.string.common_audio_mode_music
        }
        textAudioMode.setText(resId)
    }

    private fun onVolumeChange(volume: Int?) {
        volume?.let {
            seekVolume.progress = it
            textVolume.text = it.toString()
        }
    }

    private fun onNetWorkStatusChange(networkQuality: NetworkQuality?) {
        when (networkQuality) {
            NetworkQuality.POOR -> {
                imageNetworkStatus.setImageResource(R.drawable.network_info_network_status_poor)
                textNetworkStatus.setText(R.string.common_exception)
            }

            NetworkQuality.BAD -> {
                imageNetworkStatus.setImageResource(R.drawable.network_info_network_status_very_bad)
                textNetworkStatus.setText(R.string.common_exception)
            }

            NetworkQuality.VERY_BAD,
            NetworkQuality.DOWN -> {
                imageNetworkStatus.setImageResource(R.drawable.network_info_network_status_down)
                textNetworkStatus.setText(R.string.common_exception)
            }

            else -> {
                imageNetworkStatus.setImageResource(R.drawable.network_info_network_status_good)
                textNetworkStatus.setText(R.string.common_normal)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun onRTTChange(rtt: Int?) {
        rtt?.let {
            textRTT.text = "${it}ms"
            textRTT.setTextColor(if (it > 100) colorAbnormal else colorNormal)
        }
    }

    private fun onUpLossChange(upLoss: Int?) {
        upLoss?.let {
            textUpLoss.text = if (isRtl) "$it%" else "%$it"
            textUpLoss.setTextColor(if (it > 10) colorAbnormal else colorNormal)
        }
    }

    private fun onDownLossChange(downLoss: Int?) {
        downLoss?.let {
            textDownLoss.text = if (isRtl) "$it%" else "%$it"
            textDownLoss.setTextColor(if (it > 10) colorAbnormal else colorNormal)
        }
    }

    private fun onTakeSeatStatusChange(isTakeSeat: Boolean?) {
        layoutStreamStatus.visibility = if (isTakeSeat == true) View.VISIBLE else View.GONE
    }

    interface OnAudioModeListener {
        fun onAudioModeChecked(audioQuality: TUIRoomDefine.AudioQuality)
    }
}