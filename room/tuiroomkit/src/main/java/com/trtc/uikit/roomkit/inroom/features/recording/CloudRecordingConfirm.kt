package com.trtc.uikit.roomkit.inroom.features.recording

import android.content.Context
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RecordingStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal object CloudRecordingConfirm {

    private var startDialog: RoomDialog? = null
    private var stopDialog: RoomDialog? = null
    private var autoDismissJob: Job? = null

    fun showStartDialog(context: Context) {
        stopDialog?.dismiss()
        startDialog = buildStartDialog(context).also { dialog ->
            dialog.setOnDismissListener {
                if (startDialog === dialog) startDialog = null
                cancelAutoDismiss()
            }
            watchAutoDismiss(isStartDialog = true)
        }
    }

    fun showStopDialog(context: Context) {
        startDialog?.dismiss()
        stopDialog = buildStopDialog(context).also { dialog ->
            dialog.setOnDismissListener {
                if (stopDialog === dialog) stopDialog = null
                cancelAutoDismiss()
            }
            watchAutoDismiss(isStartDialog = false)
        }
    }

    fun dismiss() {
        startDialog?.dismiss()
        stopDialog?.dismiss()
        cancelAutoDismiss()
    }

    private fun watchAutoDismiss(isStartDialog: Boolean) {
        cancelAutoDismiss()
        val roomID = RoomStore.shared().state.currentRoom.value?.roomID ?: return
        autoDismissJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                RoomStore.shared().state.currentRoom,
                RoomParticipantStore.create(roomID).state.localParticipant,
            ) { room, local -> room to local }.collect { (room, local) ->
                val recording = room?.takeIf { it.roomID == roomID }?.recordingInfo?.status == RecordingStatus.RECORDING
                val canManage = local?.role == ParticipantRole.OWNER || local?.role == ParticipantRole.ADMIN
                when {
                    !canManage -> dismiss()
                    isStartDialog && recording -> dismiss()
                    !isStartDialog && !recording -> dismiss()
                }
            }
        }
    }

    private fun cancelAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = null
    }

    private fun buildStartDialog(context: Context): RoomDialog = RoomDialog.builder(context)
        .title(context.getString(R.string.roomkit_cloud_recording_start_title))
        .message(context.getString(R.string.roomkit_cloud_recording_start_content))
        .primaryAction(context.getString(R.string.roomkit_cloud_recording_start_confirm)) {
            RoomStore.shared().startRecording(
                completion = object : CompletionHandler {
                    override fun onSuccess() {}
                    override fun onFailure(code: Int, desc: String) { ErrorLocalized.showError(context, code, desc) }
                },
            )
        }
        .secondaryAction(context.getString(R.string.roomkit_room_common_cancel))
        .show()

    private fun buildStopDialog(context: Context): RoomDialog = RoomDialog.builder(context)
        .title(context.getString(R.string.roomkit_cloud_recording_stop_title))
        .message(context.getString(R.string.roomkit_cloud_recording_stop_content))
        .primaryAction(
            context.getString(R.string.roomkit_cloud_recording_stop_confirm),
            RoomDialog.Variant.DANGER,
        ) {
            RoomStore.shared().stopRecording(
                object : CompletionHandler {
                    override fun onSuccess() {}
                    override fun onFailure(code: Int, desc: String) { ErrorLocalized.showError(context, code, desc) }
                },
            )
        }
        .secondaryAction(context.getString(R.string.roomkit_room_common_cancel))
        .show()
}
