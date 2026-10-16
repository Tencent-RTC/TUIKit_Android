package com.trtc.uikit.livekit.pusher.living.status

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveSlider
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.common.theme.LiveTheme
import io.trtc.tuikit.atomicxcore.api.device.DeviceError
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality

class PusherDeviceStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal data class Presentation(
        val status: PusherDeviceStatusState = PusherDeviceStatusState(),
        val videoQuality: VideoQuality = VideoQuality.QUALITY_720P,
        val isMuted: Boolean = false,
        val captureVolume: Int = 100,
    )

    internal var onSetCaptureVolume: ((Int) -> Unit)? = null
    private val videoRow: LiveStatusDeviceRowView
    private val audioRow: LiveStatusDeviceRowView
    private val temperatureRow: LiveStatusDeviceRowView
    private val networkRow: LiveStatusDeviceRowView
    private val videoResolutionLabel: TextView
    private val volumeSlider: LiveSlider
    private val volumeValue: TextView
    private val delayView: TextView
    private val downLossView: TextView
    private val upLossView: TextView
    private val videoInfoViews: List<TextView>
    private val audioInfoViews: List<TextView>

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_status_device_view, this, true)
        videoRow = findViewById(R.id.livekit_pusher_status_video_row)
        audioRow = findViewById(R.id.livekit_pusher_status_audio_row)
        temperatureRow = findViewById(R.id.livekit_pusher_status_temperature_row)
        networkRow = findViewById(R.id.livekit_pusher_status_network_row)
        videoResolutionLabel = findViewById(R.id.livekit_pusher_status_video_resolution)
        volumeSlider = findViewById(R.id.livekit_pusher_status_volume)
        volumeSlider.contentDescription = context.getString(R.string.common_people_volume)
        volumeValue = findViewById(R.id.livekit_pusher_status_volume_value)
        delayView = findViewById(R.id.livekit_pusher_status_delay)
        downLossView = findViewById(R.id.livekit_pusher_status_down_loss)
        upLossView = findViewById(R.id.livekit_pusher_status_up_loss)
        videoInfoViews = createInformationRows(
            findViewById(R.id.livekit_pusher_status_video_info_rows),
            R.string.livekit_pusher_status_video_fields,
        )
        audioInfoViews = createInformationRows(
            findViewById(R.id.livekit_pusher_status_audio_info_rows),
            R.string.livekit_pusher_status_audio_fields,
        )
        volumeSlider.onValueChanged = { value ->
            volumeValue.text = value.toString()
            onSetCaptureVolume?.invoke(value)
        }
    }

    internal fun bind(presentation: Presentation) {
        val state = presentation.status
        val isMuted = presentation.isMuted
        val captureVolume = presentation.captureVolume
        val videoStatus = state.videoStatus
        val audioStatus = if (isMuted || captureVolume == 0) {
            PusherDeviceStatusState.MediaStatus.OFF
        } else {
            state.audioStatus
        }
        bindRow(videoRow, video(videoStatus))
        bindRow(audioRow, audio(audioStatus))
        bindRow(
            temperatureRow,
            temperature(state.temperatureStatus),
        )
        bindRow(networkRow, network(state))
        videoResolutionLabel.text = if (state.hasAVStatistics && state.videoWidth > 0) {
            "${state.videoWidth}P"
        } else {
            "--"
        }
        val volume = captureVolume.coerceIn(0, 100)
        volumeSlider.setValue(volume)
        volumeValue.text = volume.toString()
        delayView.text = context.getString(R.string.livekit_pusher_status_latency_value, state.delay)
        downLossView.text = context.getString(R.string.livekit_pusher_status_loss_value, state.downLoss)
        upLossView.text = context.getString(R.string.livekit_pusher_status_loss_value, state.upLoss)
        videoInfoViews.zip(videoInformation(state).lines()).forEach { (view, value) -> view.text = value }
        audioInfoViews.zip(audioInformation(state).lines()).forEach { (view, value) -> view.text = value }
    }

    // Each field and value share a row, so wrapping or font scaling cannot shift later fields.
    private fun createInformationRows(container: LinearLayout, @StringRes fields: Int): List<TextView> {
        val labels = context.getString(fields).lines().map { title ->
            LiveText(context).apply {
                role = LiveTheme.TextRole.METADATA
                tone = LiveTheme.TextTone.DISABLED
                includeFontPadding = false
                gravity = Gravity.TOP or Gravity.START
                text = title
            }
        }
        val labelWidth = labels.maxOf { label ->
            label.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            label.measuredWidth
        }
        return labels.map { label ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                minimumHeight = dp(19)
            }
            row.addView(label, LinearLayout.LayoutParams(labelWidth, LayoutParams.WRAP_CONTENT))
            val value = LiveText(context).apply {
                role = LiveTheme.TextRole.METADATA
                tone = LiveTheme.TextTone.SECONDARY
                includeFontPadding = false
                gravity = Gravity.TOP or Gravity.START
            }
            row.addView(value, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            })
            container.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            value
        }
    }

    private fun bindRow(view: LiveStatusDeviceRowView, model: DeviceStatusRowModel) {
        view.bind(
            icon = checkNotNull(model.icon),
            title = model.titleText ?: context.getString(model.title),
            detail = context.getString(model.detail),
            level = model.level,
            tinted = !model.usesOriginalColors,
        )
    }

    private fun video(status: PusherDeviceStatusState.MediaStatus): DeviceStatusRowModel =
        when (status) {
            PusherDeviceStatusState.MediaStatus.NORMAL -> DeviceStatusRowModel(
                R.string.livekit_pusher_status_video_normal,
                R.string.livekit_pusher_status_video_normal_copy,
                LiveStatusLevel.NORMAL,
                R.drawable.livekit_pusher_status_ic_video,
            )
            PusherDeviceStatusState.MediaStatus.OFF -> DeviceStatusRowModel(
                R.string.livekit_pusher_status_video_off,
                R.string.livekit_pusher_status_video_off_copy,
                LiveStatusLevel.WARNING,
                R.drawable.livekit_pusher_status_ic_video,
            )
            PusherDeviceStatusState.MediaStatus.DEVICE_ERROR -> DeviceStatusRowModel(
                R.string.livekit_pusher_status_video_error,
                R.string.livekit_pusher_status_video_device_error_copy,
                LiveStatusLevel.WARNING,
                R.drawable.livekit_pusher_status_ic_video,
            )
            PusherDeviceStatusState.MediaStatus.STREAM_ERROR -> DeviceStatusRowModel(
                R.string.livekit_pusher_status_video_error,
                R.string.livekit_pusher_status_video_stream_error_copy,
                LiveStatusLevel.CRITICAL,
                R.drawable.livekit_pusher_status_ic_video,
            )
        }

    private fun audio(status: PusherDeviceStatusState.MediaStatus): DeviceStatusRowModel =
        when (status) {
            PusherDeviceStatusState.MediaStatus.NORMAL -> DeviceStatusRowModel(
                R.string.livekit_pusher_status_audio_normal,
                R.string.livekit_pusher_status_audio_normal_copy,
                LiveStatusLevel.NORMAL,
                R.drawable.livekit_pusher_status_ic_audio,
            )
            PusherDeviceStatusState.MediaStatus.OFF -> DeviceStatusRowModel(
                R.string.livekit_pusher_status_audio_off,
                R.string.livekit_pusher_status_audio_off_copy,
                LiveStatusLevel.WARNING,
                R.drawable.livekit_pusher_status_ic_audio,
            )
            PusherDeviceStatusState.MediaStatus.DEVICE_ERROR -> DeviceStatusRowModel(
                R.string.livekit_pusher_status_audio_error,
                R.string.livekit_pusher_status_audio_device_error_copy,
                LiveStatusLevel.WARNING,
                R.drawable.livekit_pusher_status_ic_audio,
            )
            PusherDeviceStatusState.MediaStatus.STREAM_ERROR -> DeviceStatusRowModel(
                R.string.livekit_pusher_status_audio_error,
                R.string.livekit_pusher_status_audio_stream_error_copy,
                LiveStatusLevel.CRITICAL,
                R.drawable.livekit_pusher_status_ic_audio,
            )
        }

    private fun temperature(
        status: PusherDeviceStatusState.TemperatureStatus,
    ): DeviceStatusRowModel = when (status) {
        PusherDeviceStatusState.TemperatureStatus.NORMAL -> DeviceStatusRowModel(
            R.string.livekit_pusher_status_temperature_normal,
            R.string.livekit_pusher_status_temperature_normal_copy,
            LiveStatusLevel.NORMAL,
            R.drawable.livekit_pusher_status_ic_temperature,
        )
        PusherDeviceStatusState.TemperatureStatus.FAIR -> DeviceStatusRowModel(
            R.string.livekit_pusher_status_temperature_fair,
            R.string.livekit_pusher_status_temperature_fair_copy,
            LiveStatusLevel.WARNING,
            R.drawable.livekit_pusher_status_ic_temperature,
        )
        PusherDeviceStatusState.TemperatureStatus.SERIOUS -> DeviceStatusRowModel(
            R.string.livekit_pusher_status_temperature_serious,
            R.string.livekit_pusher_status_temperature_serious_copy,
            LiveStatusLevel.CRITICAL,
            R.drawable.livekit_pusher_status_ic_temperature,
        )
    }

    private fun network(
        status: PusherDeviceStatusState,
    ): DeviceStatusRowModel {
        val typeName = context.getString(when (status.networkKind) {
            PusherDeviceStatusState.NetworkKind.WIFI -> R.string.livekit_pusher_status_network_wifi
            PusherDeviceStatusState.NetworkKind.CELLULAR -> R.string.livekit_pusher_status_network_cellular
            PusherDeviceStatusState.NetworkKind.UNKNOWN -> R.string.livekit_pusher_status_network_unknown
        })
        val quality = status.networkQuality
        val qualityName = context.getString(when (quality) {
                1 -> R.string.livekit_pusher_status_network_quality_excellent_label
                2 -> R.string.livekit_pusher_status_network_good
                3 -> R.string.livekit_pusher_status_network_poor
                4 -> R.string.livekit_pusher_status_network_bad
                5 -> R.string.livekit_pusher_status_network_very_bad
                6 -> R.string.livekit_pusher_status_network_quality_down
                else -> R.string.livekit_pusher_status_network_checking
            })
        val detail = when (quality) {
            1, 2 -> R.string.livekit_pusher_status_network_copy
            3 -> R.string.livekit_pusher_status_network_fluctuating_copy
            4, 5 -> R.string.livekit_pusher_status_network_warning_copy
            6 -> R.string.livekit_pusher_status_network_disconnected_copy
            else -> R.string.livekit_pusher_status_network_checking_copy
        }
        val level = when (quality) {
            1, 2 -> LiveStatusLevel.NORMAL
            3 -> LiveStatusLevel.WARNING
            4, 5, 6 -> LiveStatusLevel.CRITICAL
            else -> LiveStatusLevel.UNKNOWN
        }
        val icon = when (quality) {
            3 -> R.drawable.livekit_pusher_status_ic_network_poor
            4, 5 -> R.drawable.livekit_pusher_status_ic_network_bad
            6 -> R.drawable.livekit_pusher_status_ic_network_down
            else -> R.drawable.livekit_pusher_status_ic_network
        }
        return DeviceStatusRowModel(
            detail = detail,
            level = level,
            icon = icon,
            usesOriginalColors = quality in 3..6,
            titleText = context.getString(
                R.string.livekit_pusher_status_network_title,
                typeName,
                qualityName,
            ),
        )
    }

    private fun videoInformation(state: PusherDeviceStatusState): String {
        if (!state.hasAVStatistics) return "--\n--\n--"
        val resolution = if (state.videoWidth > 0 && state.videoHeight > 0) {
            context.getString(
                R.string.livekit_pusher_status_resolution_value,
                state.videoWidth,
                state.videoHeight,
            )
        } else "--"
        val bitrate = if (state.videoBitrate > 0) {
            context.getString(R.string.livekit_pusher_status_bitrate_value, state.videoBitrate)
        } else "--"
        val frameRate = if (state.frameRate > 0) {
            context.getString(R.string.livekit_pusher_status_frame_rate_value, state.frameRate)
        } else "--"
        return "$resolution\n$bitrate\n$frameRate"
    }

    private fun audioInformation(state: PusherDeviceStatusState): String {
        if (!state.hasAVStatistics) return "--\n--"
        val sampleRate = if (state.audioSampleRate > 0) {
            context.getString(R.string.livekit_pusher_status_sample_rate_value, state.audioSampleRate)
        } else "--"
        val bitrate = if (state.audioBitrate > 0) {
            context.getString(R.string.livekit_pusher_status_bitrate_value, state.audioBitrate)
        } else "--"
        return "$sampleRate\n$bitrate"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

internal data class PusherDeviceStatusState(
    val temperatureStatus: TemperatureStatus = TemperatureStatus.NORMAL,
    val networkKind: NetworkKind = NetworkKind.UNKNOWN,
    val networkQuality: Int = 0,
    val delay: Int = 0,
    val downLoss: Int = 0,
    val upLoss: Int = 0,
    val videoBitrate: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val frameRate: Int = 0,
    val audioSampleRate: Int = 0,
    val audioBitrate: Int = 0,
    val hasAVStatistics: Boolean = false,
    val cameraStatus: DeviceStatus = DeviceStatus.OFF,
    val microphoneStatus: DeviceStatus = DeviceStatus.OFF,
    val cameraError: DeviceError = DeviceError.NO_ERROR,
    val microphoneError: DeviceError = DeviceError.NO_ERROR,
    val videoQuality: VideoQuality = VideoQuality.QUALITY_720P,
) {
    internal enum class MediaStatus { NORMAL, OFF, DEVICE_ERROR, STREAM_ERROR }
    internal enum class TemperatureStatus { NORMAL, FAIR, SERIOUS }
    internal enum class NetworkKind { UNKNOWN, WIFI, CELLULAR }

    internal val videoStatus: MediaStatus
        get() {
            val streamIsAbnormal = networkQuality == NETWORK_QUALITY_DOWN ||
                (hasAVStatistics && frameRate < MINIMUM_FRAME_RATE)
            return mediaStatus(cameraStatus, cameraError, streamIsAbnormal)
        }

    internal val audioStatus: MediaStatus
        get() = mediaStatus(
            microphoneStatus,
            microphoneError,
            hasAVStatistics && (audioSampleRate == 0 || audioBitrate == 0),
        )

    private fun mediaStatus(
        status: DeviceStatus,
        error: DeviceError,
        streamIsAbnormal: Boolean,
    ): MediaStatus = when {
        error != DeviceError.NO_ERROR -> MediaStatus.DEVICE_ERROR
        status != DeviceStatus.ON -> MediaStatus.OFF
        streamIsAbnormal -> MediaStatus.STREAM_ERROR
        else -> MediaStatus.NORMAL
    }

    private companion object {
        const val MINIMUM_FRAME_RATE = 15
        const val NETWORK_QUALITY_DOWN = 6
    }
}

internal enum class LiveStatusLevel {
    NORMAL,
    WARNING,
    CRITICAL,
    UNKNOWN,
}

class LiveStatusDeviceRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val iconBackground: FrameLayout
    private val iconView: ImageView
    private val titleView: TextView
    private val detailView: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_status_device_row, this, true)
        iconBackground = findViewById(R.id.livekit_pusher_status_device_icon_bg)
        iconView = findViewById(R.id.livekit_pusher_status_device_icon)
        titleView = findViewById(R.id.livekit_pusher_status_device_title)
        detailView = findViewById(R.id.livekit_pusher_status_device_detail)
    }

    internal fun bind(
        @DrawableRes icon: Int,
        title: String,
        detail: String,
        level: LiveStatusLevel,
        tinted: Boolean,
    ) {
        titleView.text = title
        detailView.text = detail
        applyIcon(icon, level, tinted)
    }

    private fun applyIcon(@DrawableRes icon: Int, level: LiveStatusLevel, tinted: Boolean) {
        iconView.setImageResource(icon)
        if (tinted) {
            iconView.setColorFilter(ContextCompat.getColor(context, level.colorRes))
        } else {
            iconView.clearColorFilter()
        }
        iconBackground.setBackgroundResource(level.backgroundRes)
    }

    private val LiveStatusLevel.colorRes: Int
        get() = when (this) {
            LiveStatusLevel.NORMAL -> R.color.livekit_pusher_status_normal
            LiveStatusLevel.WARNING -> R.color.livekit_pusher_status_warning
            LiveStatusLevel.CRITICAL -> R.color.livekit_pusher_status_critical
            LiveStatusLevel.UNKNOWN -> R.color.livekit_pusher_status_unknown
        }

    private val LiveStatusLevel.backgroundRes: Int
        get() = when (this) {
            LiveStatusLevel.NORMAL -> R.drawable.livekit_pusher_status_icon_bg
            LiveStatusLevel.WARNING -> R.drawable.livekit_pusher_status_icon_bg_warning
            LiveStatusLevel.CRITICAL -> R.drawable.livekit_pusher_status_icon_bg_critical
            LiveStatusLevel.UNKNOWN -> R.drawable.livekit_pusher_status_icon_bg_unknown
        }
}

private data class DeviceStatusRowModel(
    @StringRes val title: Int = 0,
    @StringRes val detail: Int,
    val level: LiveStatusLevel,
    @DrawableRes val icon: Int? = null,
    val usesOriginalColors: Boolean = false,
    val titleText: String? = null,
)
