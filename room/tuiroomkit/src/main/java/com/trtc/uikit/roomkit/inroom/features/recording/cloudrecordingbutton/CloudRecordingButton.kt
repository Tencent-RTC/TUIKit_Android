package com.trtc.uikit.roomkit.inroom.features.recording.cloudrecordingbutton

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.inroom.common.moremenu.MoreMenuCell
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

class CloudRecordingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), MoreMenuCell {

    private var subscribeJob: Job? = null

    init {
        visibility = GONE
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(context.dp(2), context.dp(10), context.dp(2), context.dp(10))
        LayoutInflater.from(context).inflate(R.layout.roomkit_more_menu_cell_content, this, true)
        setOnClickListener { onMenuSelected() }
    }

    override fun onMenuSelected() {
        val recording = RoomStore.shared().state.currentRoom.value?.recordingInfo?.status == RecordingStatus.RECORDING
        if (recording) CloudRecordingConfirm.stop(context) else CloudRecordingConfirm.start(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            RoomStore.shared().state.currentRoom.collect { room ->
                renderRecording(room)
                renderPermission(room)
            }
        }
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun renderRecording(room: RoomInfo?) {
        val recording = room?.recordingInfo?.status == RecordingStatus.RECORDING
        findViewById<AppCompatImageView>(R.id.cell_icon).setImageResource(
            if (recording) R.drawable.roomkit_icon_recording else R.drawable.roomkit_icon_record,
        )
        findViewById<AppCompatTextView>(R.id.cell_label).setText(
            if (recording) {
                R.string.roomkit_cloud_recording_stop_confirm
            } else {
                R.string.roomkit_cloud_recording_record_label
            },
        )
    }

    private fun renderPermission(room: RoomInfo?) {
        val roomID = room?.roomID ?: return
        val role = RoomParticipantStore.create(roomID).state.localParticipant.value?.role
        visibility = if (role == ParticipantRole.OWNER || role == ParticipantRole.ADMIN) VISIBLE else GONE
    }
}
