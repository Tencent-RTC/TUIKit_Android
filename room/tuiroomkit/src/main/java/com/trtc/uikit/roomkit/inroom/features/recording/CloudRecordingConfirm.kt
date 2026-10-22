package com.trtc.uikit.roomkit.inroom.features.recording

import android.content.Context
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.toast.RoomToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.RoomStore

internal object CloudRecordingConfirm {

    fun start(context: Context) {
        RoomDialog.builder(context)
            .title(context.getString(R.string.roomkit_cloud_recording_start_title))
            .message(context.getString(R.string.roomkit_cloud_recording_start_content))
            .closable(false)
            .primaryAction(context.getString(R.string.roomkit_cloud_recording_start_confirm)) {
                RoomStore.shared().startRecording(
                    completion = object : CompletionHandler {
                        override fun onSuccess() {
                            RoomToast.show(context, context.getString(R.string.roomkit_cloud_recording_started_toast))
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    },
                )
            }
            .secondaryAction(context.getString(R.string.roomkit_room_common_cancel))
            .show()
    }

    fun stop(context: Context) {
        RoomDialog.builder(context)
            .title(context.getString(R.string.roomkit_cloud_recording_stop_title))
            .message(context.getString(R.string.roomkit_cloud_recording_stop_content))
            .closable(false)
            .primaryAction(
                context.getString(R.string.roomkit_cloud_recording_stop_confirm),
                RoomDialog.Variant.DANGER,
            ) {
                RoomStore.shared().stopRecording(
                    object : CompletionHandler {
                        override fun onSuccess() {
                            RoomToast.show(context, context.getString(R.string.roomkit_cloud_recording_stopped_toast))
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    },
                )
            }
            .secondaryAction(context.getString(R.string.roomkit_room_common_cancel))
            .show()
    }
}
