package com.trtc.uikit.livekit.pusher.settings

import com.trtc.uikit.livekit.common.theme.LiveSwitch
import androidx.constraintlayout.widget.ConstraintLayout
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicxcore.api.device.AudioEffectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import io.trtc.tuikit.atomicxcore.api.device.AudioChangerType
import io.trtc.tuikit.atomicxcore.api.device.AudioReverbType
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import android.widget.TextView
import com.trtc.uikit.livekit.common.theme.LiveSlider

/** Audio-effect controls own their SDK operations and visible-page subscriptions. */
class AudioEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        internal fun resetForNewSession() {
            AudioEffectStore.shared().setVoiceEarMonitorEnable(false)
            AudioEffectStore.shared().setVoiceEarMonitorVolume(100)
            AudioEffectStore.shared().setAudioChangerType(AudioChangerType.NONE)
            AudioEffectStore.shared().setAudioReverbType(AudioReverbType.NONE)
        }
    }

    var onSetCaptureVolume: ((Int) -> Unit)? = null

    private class Option<T>(
        val value: T,
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
    )

    private val changerOptions = listOf(
        Option(
            AudioChangerType.NONE,
            R.drawable.livekit_pusher_voice_original,
            R.string.livekit_pusher_settings_voice_original
        ),
        Option(
            AudioChangerType.CHILD,
            R.drawable.livekit_pusher_voice_child,
            R.string.livekit_pusher_settings_voice_child
        ),
        Option(
            AudioChangerType.LITTLE_GIRL,
            R.drawable.livekit_pusher_voice_loli,
            R.string.common_change_voice_girl
        ),
        Option(
            AudioChangerType.MAN,
            R.drawable.livekit_pusher_voice_uncle,
            R.string.livekit_pusher_settings_voice_uncle
        ),
        Option(
            AudioChangerType.ETHEREAL,
            R.drawable.livekit_pusher_voice_spirit,
            R.string.common_change_voice_ethereal
        )
    )

    private val reverbOptions = listOf(
        Option(
            AudioReverbType.NONE,
            R.drawable.livekit_pusher_reverb_none,
            R.string.livekit_pusher_settings_reverb_none
        ),
        Option(
            AudioReverbType.KTV,
            R.drawable.livekit_pusher_reverb_ktv,
            R.string.livekit_pusher_settings_reverb_ktv
        ),
        Option(
            AudioReverbType.METALLIC,
            R.drawable.livekit_pusher_reverb_metal,
            R.string.common_reverb_metallic_sound
        ),
        Option(
            AudioReverbType.DEEP,
            R.drawable.livekit_pusher_reverb_deep,
            R.string.livekit_pusher_settings_reverb_deep
        ),
        Option(
            AudioReverbType.LOUD,
            R.drawable.livekit_pusher_reverb_bright,
            R.string.livekit_pusher_settings_reverb_bright
        )
    )

    private var isActive = false
    private var scope: CoroutineScope? = null

    private val earSwitch: LiveSwitch
    private val earVolume: SettingsVolumeView
    private val captureVolume: SettingsVolumeView

    private val changerSection: OptionSection<AudioChangerType>
    private val reverbSection: OptionSection<AudioReverbType>

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_audio_effect_view, this, true)

        earSwitch = findViewById(R.id.livekit_pusher_audio_ear_switch)
        earVolume = findViewById(R.id.livekit_pusher_audio_ear_volume)
        captureVolume = findViewById(R.id.livekit_pusher_audio_capture_volume)

        earVolume.setTitle(R.string.livekit_pusher_settings_ear_monitor_volume)
        captureVolume.setTitle(R.string.common_people_volume)

        earSwitch.onCheckedChange = {
            if (scope != null) AudioEffectStore.shared().setVoiceEarMonitorEnable(it)
        }
        earVolume.onValueChanged = {
            if (scope != null) AudioEffectStore.shared().setVoiceEarMonitorVolume(it.coerceIn(0, 100))
        }
        captureVolume.onValueChanged = { onSetCaptureVolume?.invoke(it) }

        val changerRow = findViewById<LinearLayout>(R.id.livekit_pusher_audio_changer_row)
        changerSection = OptionSection(context, changerRow, changerOptions) {
            if (scope != null) AudioEffectStore.shared().setAudioChangerType(it)
        }

        val reverbRow = findViewById<LinearLayout>(R.id.livekit_pusher_audio_reverb_row)
        reverbSection = OptionSection(context, reverbRow, reverbOptions) {
            if (scope != null) AudioEffectStore.shared().setAudioReverbType(it)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSubscription()
    }

    override fun onDetachedFromWindow() {
        isActive = false
        updateSubscription()
        super.onDetachedFromWindow()
    }

    internal fun setActive(active: Boolean) {
        isActive = active
        updateSubscription()
    }

    fun bindCaptureVolume(volume: Int) = captureVolume.setValue(volume)

    private fun updateSubscription() {
        if (!isActive || !isAttachedToWindow) {
            scope?.cancel()
            scope = null
            return
        }
        if (scope != null) return
        val current = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = current
        val state = AudioEffectStore.shared().audioEffectState
        state.isEarMonitorOpened.onEach {
            if (scope === current) earSwitch.setChecked(it, animate = false)
        }.launchIn(current)
        state.earMonitorVolume.onEach {
            if (scope === current) earVolume.setValue(it)
        }.launchIn(current)
        state.audioChangerType.onEach {
            if (scope === current) changerSection.bind(
                if (changerOptions.any { option -> option.value == it }) it else AudioChangerType.NONE,
            )
        }.launchIn(current)
        state.audioReverbType.onEach {
            if (scope === current) reverbSection.bind(
                if (reverbOptions.any { option -> option.value == it }) it else AudioReverbType.NONE,
            )
        }.launchIn(current)
    }

    private class OptionSection<T>(
        context: Context,
        row: LinearLayout,
        options: List<Option<T>>,
        private val onSelect: (T) -> Unit,
    ) {
        private val items = options.mapIndexed { index, option ->
            val item = PusherSettingsItemView(context).apply {
                configure(
                    option.titleRes,
                    option.iconRes,
                    style = PusherSettingsItemView.Style.SOUND_OPTION,
                )
                setOnClickListener {
                    bind(option.value)
                    onSelect(option.value)
                }
            }
            val spacing = context.resources.getDimensionPixelSize(
                R.dimen.livekit_pusher_sound_option_spacing,
            )
            row.addView(
                item,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (index > 0) marginStart = spacing
                },
            )
            option.value to item
        }

        fun bind(selectedValue: T) {
            items.forEach { (value, item) -> item.isSelected = value == selectedValue }
        }
    }
}
/**
 * One labelled volume row of the audio-effect page. Mirrors the SettingsVolumeView of
 * ios/live/.../Pusher/Settings/AudioEffect/AudioEffectView.swift.
 *
 * The value label and business callback follow each snapped slider value.
 */
class SettingsVolumeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var onValueChanged: ((Int) -> Unit)? = null

    private val labelView: TextView
    private val valueView: TextView
    private val slider: LiveSlider

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_volume_view, this, true)
        labelView = findViewById(R.id.livekit_pusher_volume_label)
        valueView = findViewById(R.id.livekit_pusher_volume_value)
        slider = findViewById(R.id.livekit_pusher_volume_slider)

        slider.onValueChanged = { value ->
            valueView.text = value.toString()
            onValueChanged?.invoke(value)
        }
    }

    fun setTitle(@StringRes titleRes: Int) {
        labelView.setText(titleRes)
    }

    /** Applies a value coming from the SDK without emitting the business callback. */
    fun setValue(value: Int) {
        slider.setValue(value)
        valueView.text = value.coerceIn(0, 100).toString()
    }
}
