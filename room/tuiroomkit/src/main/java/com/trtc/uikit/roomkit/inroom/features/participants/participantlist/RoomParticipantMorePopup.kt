package com.trtc.uikit.roomkit.inroom.features.participants.participantlist

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.toast.RoomToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore

class RoomParticipantMorePopup(context: Context, roomID: String) {

    private val popup = RoomPopup(context)

    private val participantStore = RoomParticipantStore.create(roomID)
    private val content = LayoutInflater.from(context).inflate(R.layout.roomkit_participant_more_popup, null)
    private val chatMuteRow: TextView = content.findViewById(R.id.more_chat_mute)
    private val shareRow: TextView = content.findViewById(R.id.more_share)

    fun show(room: RoomInfo) {
        bindChatMute(room.isAllMessageDisabled)
        bindShare(room.isAllScreenShareDisabled)
        popup.setContentView(content, RoomPopup.ContentHeight.AUTO)
        popup.show()
    }

    private fun bindChatMute(chatMuted: Boolean) {
        chatMuteRow.text = content.context.getString(
            if (chatMuted) R.string.roomkit_participants_unmute_all_chat else R.string.roomkit_participants_mute_all_chat,
        )
        chatMuteRow.setTextColor(
            ContextCompat.getColor(
                content.context,
                if (chatMuted) R.color.roomkit_accent else R.color.roomkit_fg_primary,
            ),
        )
        chatMuteRow.setOnClickListener {
            popup.dismiss()
            participantStore.disableAllMessages(
                !chatMuted,
                object : CompletionHandler {
                    override fun onSuccess() {
                        RoomToast.show(
                            content.context,
                            content.context.getString(
                                if (chatMuted) R.string.roomkit_participants_unmute_all_chat_done
                                else R.string.roomkit_participants_mute_all_chat_done,
                            ),
                        )
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.showError(content.context, code, desc)
                    }
                },
            )
        }
    }

    private fun bindShare(shareDisabled: Boolean) {
        shareRow.text = content.context.getString(
            if (shareDisabled) R.string.roomkit_participants_share_everyone else R.string.roomkit_participants_share_owner_only,
        )
        shareRow.setTextColor(
            ContextCompat.getColor(
                content.context,
                if (shareDisabled) R.color.roomkit_accent else R.color.roomkit_fg_primary,
            ),
        )
        shareRow.setOnClickListener {
            popup.dismiss()
            participantStore.disableAllDevices(
                DeviceType.SCREEN_SHARE,
                !shareDisabled,
                object : CompletionHandler {
                    override fun onSuccess() {
                        RoomToast.show(
                            content.context,
                            content.context.getString(
                                if (shareDisabled) R.string.roomkit_participants_share_everyone_done
                                else R.string.roomkit_participants_share_owner_only_done,
                            ),
                        )
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.showError(content.context, code, desc)
                    }
                },
            )
        }
    }
}
