package com.trtc.uikit.roomkit.view.schedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo

/**
 * Room-info bottom sheet: shows room ID / password / time, and provides copy actions for the room
 * ID, the password, and a formatted invitation text.
 *
 * Reused in two flows, differentiated by the [show] title parameter: right after scheduling
 * (default title) and the "invite members" entry on the detail page.
 * A single instance can be [show]n multiple times; while a dialog is already displayed further
 * calls are ignored.
 */
class RoomScheduleInfoDialog(private val context: Context) {

    companion object {
        private const val CLIPBOARD_LABEL = "room"
    }

    private var currentDialog: BottomSheetDialog? = null

    /** @param title top-bar title; if null, keeps the xml default. */
    fun show(roomInfo: RoomInfo, title: String? = null) {
        if (currentDialog?.isShowing == true) return

        val dialog = BottomSheetDialog(context, R.style.RoomkitBottomSheetDialog)
        val view = View.inflate(context, R.layout.roomkit_dialog_schedule_info, null)

        val dragIndicator: View = view.findViewById(R.id.drag_indicator)
        dragIndicator.setOnClickListener { dialog.dismiss() }

        val tvTitle: TextView = view.findViewById(R.id.tv_dialog_title)
        val tvRoomName: TextView = view.findViewById(R.id.tv_room_name)
        val tvRoomId: TextView = view.findViewById(R.id.tv_room_id)
        val tvRoomTime: TextView = view.findViewById(R.id.tv_room_time)
        val tvPasswordTitle: TextView = view.findViewById(R.id.tv_room_password_title)
        val tvPassword: TextView = view.findViewById(R.id.tv_room_password)
        val btnCopyRoomId: View = view.findViewById(R.id.btn_copy_room_id)
        val btnCopyPassword: View = view.findViewById(R.id.btn_copy_password)
        val btnCopyLink: TextView = view.findViewById(R.id.btn_copy_link)
        if (title != null) tvTitle.text = title

        val roomID = roomInfo.roomID
        val roomName = roomInfo.roomName
        tvRoomName.text = roomName
        tvRoomId.text = addSpacesEveryThreeChars(roomID)

        val timeText = ScheduleDateFormatter.formatDateTimeRange(
            context, roomInfo.scheduledStartTime, roomInfo.scheduledEndTime
        )
        tvRoomTime.text = timeText

        val password = roomInfo.password.orEmpty()
        val hasPassword = password.isNotEmpty()
        val passwordVisibility = if (hasPassword) View.VISIBLE else View.GONE
        tvPasswordTitle.visibility = passwordVisibility
        tvPassword.visibility = passwordVisibility
        btnCopyPassword.visibility = passwordVisibility
        tvPassword.text = password

        btnCopyRoomId.setOnClickListener {
            copyToClipboard(roomID)
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_toast_room_id_copied),
                AtomicToast.Style.INFO
            )
        }

        btnCopyPassword.setOnClickListener {
            copyToClipboard(password)
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_toast_room_password_copied),
                AtomicToast.Style.INFO
            )
        }

        btnCopyLink.setOnClickListener {
            val nickname = LoginStore.shared.loginState.loginUserInfo.value?.getDisplayName().orEmpty()
            val inviteContent = buildInviteContent(nickname, roomName, timeText, roomID, password, hasPassword)
            copyToClipboard(inviteContent)
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_toast_room_info_copied),
                AtomicToast.Style.INFO
            )
        }

        dialog.setContentView(view)
        // Make the BottomSheetDialog's default white background transparent so the top corners round correctly.
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
        dialog.setOnDismissListener {
            if (currentDialog === dialog) currentDialog = null
        }
        currentDialog = dialog
        dialog.show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_LABEL, text))
    }

    private fun addSpacesEveryThreeChars(roomId: String): String =
        roomId.chunked(3).joinToString(" ")

    private fun buildInviteContent(
        nickname: String,
        roomName: String,
        timeText: String,
        roomID: String,
        password: String,
        hasPassword: Boolean
    ): String {
        val lines = mutableListOf<String>()
        lines += context.getString(R.string.roomkit_format_invite_to_conference, nickname)
        lines += "${context.getString(R.string.roomkit_scheduled_room_name)}: $roomName"
        lines += "${context.getString(R.string.roomkit_room_time)}: $timeText"
        lines += "${context.getString(R.string.roomkit_scheduled_room_id)}: $roomID"
        if (hasPassword) {
            lines += "${context.getString(R.string.roomkit_room_password_title)}: $password"
        }
        return lines.joinToString("\n")
    }
}
