package com.trtc.uikit.roomkit.inroom.features.recording.cloudrecordingbutton

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.inroom.views.TitleDisplaying
import com.trtc.uikit.roomkit.inroom.features.recording.CloudRecordingConfirm
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RecordingStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CloudRecordingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), TitleDisplaying {

    var onRequestDismiss: (() -> Unit)? = null

    override var showsTitle: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            labelView.isVisible = value
        }

    private var roomID = ""
    private var isRecording = false
    private var subscribeJob: Job? = null

    private val iconView: AppCompatImageView
    private val labelView: AppCompatTextView

    fun isAvailable(): Boolean {
        val role = RoomParticipantStore.create(roomID).state.localParticipant.value?.role
        return role == ParticipantRole.OWNER || role == ParticipantRole.ADMIN
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_cloud_recording_button, this, true)
        iconView = findViewById(R.id.icon)
        labelView = findViewById(R.id.label)
        setOnClickListener { handleTap() }
    }

    fun bind(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) subscribe()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotEmpty() && subscribeJob == null) subscribe()
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

    private fun subscribe() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            RoomStore.shared().state.currentRoom.collect { room ->
                val recording = room?.recordingInfo?.status == RecordingStatus.RECORDING
                if (recording == isRecording) return@collect
                isRecording = recording
                render(recording)
            }
        }
    }

    private fun render(recording: Boolean) {
        iconView.setImageResource(
            if (recording) R.drawable.roomkit_icon_recording else R.drawable.roomkit_icon_record,
        )
        labelView.setText(
            if (recording) {
                R.string.roomkit_cloud_recording_stop_confirm
            } else {
                R.string.roomkit_cloud_recording_record_label
            },
        )
    }

    private fun handleTap() {
        onRequestDismiss?.invoke()
        if (isRecording) {
            CloudRecordingConfirm.showStopDialog(context)
        } else {
            CloudRecordingConfirm.showStartDialog(context)
        }
    }

    private companion object {
        const val PRESSED_SCALE = 0.92f
        const val PRESSED_DURATION = 80
    }
}
