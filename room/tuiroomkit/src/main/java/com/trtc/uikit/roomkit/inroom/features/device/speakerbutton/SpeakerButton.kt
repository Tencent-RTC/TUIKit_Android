package com.trtc.uikit.roomkit.inroom.features.device.speakerbutton

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import com.trtc.uikit.roomkit.R
import io.trtc.tuikit.atomicxcore.api.device.AudioRoute
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SpeakerButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val deviceStore = DeviceStore.shared()
    private var subscribeJob: Job? = null
    private var routePopup: RoomSpeakerPopup? = null

    private val iconView: AppCompatImageView

    private var output: AudioRoute = DeviceStore.shared().deviceState.currentAudioRoute.value
        set(value) {
            if (field == value) return
            field = value
            applyIcon()
        }

    init {
        background = AppCompatResources.getDrawable(context, R.drawable.roomkit_bg_toolbar_button)
        LayoutInflater.from(context).inflate(R.layout.roomkit_speaker_button, this, true)
        iconView = findViewById(R.id.icon)
        setOnClickListener { showRoutePopup() }
        applyIcon()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            deviceStore.deviceState.currentAudioRoute.collect { output = it }
        }
        output = deviceStore.deviceState.currentAudioRoute.value
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) PRESSED_SCALE else 1f
        animate().setDuration(PRESSED_DURATION.toLong()).scaleX(scale).scaleY(scale)
    }

    private fun applyIcon() {
        iconView.setImageResource(
            when (output) {
                AudioRoute.SPEAKERPHONE -> R.drawable.roomkit_icon_speaker_primary
                else -> R.drawable.roomkit_icon_earpiece_primary
            },
        )
    }

    private fun showRoutePopup() {
        if (routePopup != null) return
        val popup = RoomSpeakerPopup(context, output)
        popup.onSelect = { selected ->
            routePopup = null
            deviceStore.setAudioRoute(selected)
        }
        popup.onDismiss = { routePopup = null }
        routePopup = popup
        popup.show()
    }

    private companion object {
        const val PRESSED_SCALE = 0.92f
        const val PRESSED_DURATION = 80
    }
}
