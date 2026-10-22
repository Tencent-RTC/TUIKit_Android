package com.trtc.uikit.roomkit.inroom.features.recording.cloudrecordingstatus

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.inroom.features.recording.CloudRecordingConfirm
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RecordingStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CloudRecordingStatus @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dot: View
    private val label: View

    private var roomID = ""
    private var subscribeJob: Job? = null
    private var pulseAnimator: ObjectAnimator? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_cloud_recording_status_content, this, true)
        dot = findViewById(R.id.recording_dot)
        label = findViewById(R.id.recording_label)

        setOnClickListener { requestStopRecording() }
    }

    fun bind(roomID: String) {
        this.roomID = roomID
        observeRecording()
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun observeRecording() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            RoomStore.shared().state.currentRoom.collect { room -> render(room) }
        }
    }

    private fun render(room: RoomInfo?) {
        val info = room?.takeIf { it.roomID == roomID }?.recordingInfo
            ?.takeIf { it.status == RecordingStatus.RECORDING }
        updateRecording(info != null)
    }

    private fun updateRecording(recording: Boolean) {
        visibility = if (recording) VISIBLE else GONE
        if (recording) startPulse() else stopPulse()
    }

    private fun requestStopRecording() {
        val role = RoomParticipantStore.create(roomID).state.localParticipant.value?.role ?: return
        if (role != ParticipantRole.OWNER && role != ParticipantRole.ADMIN) return
        CloudRecordingConfirm.stop(context)
    }

    private fun startPulse() {
        if (pulseAnimator != null) return
        pulseAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.35f, 1f).apply {
            duration = 1200L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        dot.alpha = 1f
    }
}
