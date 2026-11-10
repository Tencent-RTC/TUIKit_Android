package com.trtc.uikit.roomkit.inroom.features.screenshare

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.trtc.uikit.roomkit.R
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomScreenSharingPlaceholderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val liveDot: View
    private val blinkAnimator: ObjectAnimator
    private var subscribeJob: Job? = null
    private var roomID: String = ""

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_screen_sharing_placeholder, this, true)
        visibility = GONE
        liveDot = findViewById(R.id.live_dot)
        blinkAnimator = ObjectAnimator.ofFloat(liveDot, View.ALPHA, 1f, BLINK_MIN_ALPHA, 1f).apply {
            duration = BLINK_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
        }
        findViewById<View>(R.id.stop_button).setOnClickListener { DeviceStore.shared().stopScreenShare() }
    }

    fun init(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) observeSharing()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotBlank()) observeSharing()
        if (isVisible) startBlink()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        stopBlink()
        super.onDetachedFromWindow()
    }

    private fun observeSharing() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            RoomParticipantStore.create(roomID).state.localParticipant.collect { local ->
                val sharing = local?.screenShareStatus == DeviceStatus.ON
                visibility = if (sharing) VISIBLE else GONE
                if (sharing) startBlink() else stopBlink()
            }
        }
    }

    private fun startBlink() {
        if (blinkAnimator.isRunning) return
        blinkAnimator.start()
    }

    private fun stopBlink() {
        blinkAnimator.cancel()
        liveDot.alpha = 1f
    }

    private companion object {
        const val BLINK_DURATION_MS = 1400L
        const val BLINK_MIN_ALPHA = 0.35f
    }
}
