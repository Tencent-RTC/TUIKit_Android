package com.trtc.uikit.roomkit.inroom.features.roominfo.currentroominfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import java.util.IdentityHashMap
import com.trtc.uikit.roomkit.R
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomInfoSheet(context: Context) : LinearLayout(context) {

    private val roomStore = RoomStore.shared()
    private val titleText: TextView
    private val hostName: TextView
    private val idValue: TextView
    private val passwordRow: View
    private val passwordValue: TextView
    private val invite: TextView
    private var roomID: String = ""
    private var invitationText: String = ""
    private var subscribeJob: Job? = null
    private val copyHandler = Handler(Looper.getMainLooper())
    private val restoreTasks = IdentityHashMap<View, Runnable>()

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_room_info_sheet, this, true)
        titleText = findViewById(R.id.info_title)
        hostName = findViewById(R.id.host_name)
        idValue = findViewById(R.id.id_value)
        passwordRow = findViewById(R.id.password_row)
        passwordValue = findViewById(R.id.password_value)
        invite = findViewById(R.id.info_invite)

        bindCopyIcon(findViewById(R.id.id_copy_icon), idValue)
        bindCopyIcon(findViewById(R.id.password_copy_icon), passwordValue)
        invite.setOnClickListener { handleInviteTap() }
    }

    fun bind(roomID: String) {
        this.roomID = roomID
        observeRoom()
    }

    private fun observeRoom() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            roomStore.state.currentRoom.collect { room ->
                if (room == null || room.roomID != roomID) return@collect
                render(room)
            }
        }
    }

    private fun render(room: RoomInfo) {
        titleText.text = room.roomName.ifEmpty { roomID }
        idValue.text = room.roomID
        val password = room.password
        passwordRow.visibility = if (password.isNullOrEmpty()) View.GONE else View.VISIBLE
        passwordValue.text = password.orEmpty()
        hostName.text = room.roomOwner.userName.ifEmpty { room.roomOwner.userID }
        invitationText = buildString {
            append(room.roomName)
            append('\n').append(context.getString(R.string.roomkit_invitation_room_id))
            append(": ").append(room.roomID)
            if (!password.isNullOrEmpty()) {
                append('\n').append(context.getString(R.string.roomkit_schedule_password))
                append(": ").append(password)
            }
        }
    }

    private fun bindCopyIcon(icon: AppCompatImageView, value: TextView) {
        icon.setBackgroundResource(R.drawable.roomkit_bg_info_copy)
        icon.clipToOutline = true
        icon.setOnClickListener {
            copyToClipboard(value.text.toString())
            markCopied(
                icon,
                onCopied = {
                    icon.setImageResource(R.drawable.roomkit_icon_check_success)
                    icon.setBackgroundResource(R.drawable.roomkit_bg_info_copy_copied)
                },
                onRestore = {
                    icon.setImageResource(R.drawable.roomkit_icon_copy_accent)
                    icon.setBackgroundResource(R.drawable.roomkit_bg_info_copy)
                }
            )
        }
    }

    private fun handleInviteTap() {
        copyToClipboard(invitationText)
        markCopied(
            invite,
            onCopied = {
                invite.text = context.getString(R.string.roomkit_room_common_copied)
                invite.setTextColor(COPIED_TEXT_COLOR)
                invite.setBackgroundResource(R.drawable.roomkit_bg_info_invite_copied)
            },
            onRestore = {
                invite.setText(R.string.roomkit_room_info_invitation)
                invite.setTextColor(INVITE_TEXT_COLOR)
                invite.setBackgroundResource(R.drawable.roomkit_bg_info_invite)
            }
        )
    }

    private fun markCopied(target: View, onCopied: () -> Unit, onRestore: () -> Unit) {
        restoreTasks[target]?.let { copyHandler.removeCallbacks(it) }
        val restore = Runnable {
            onRestore()
            restoreTasks.remove(target)
        }
        restoreTasks[target] = restore
        onCopied()
        copyHandler.postDelayed(restore, COPIED_MILLIS)
    }

    private fun copyToClipboard(text: String) {
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("room", text))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeJob?.cancel()
        subscribeJob = null
        restoreAllCopied()
    }

    private fun restoreAllCopied() {
        val tasks = restoreTasks.values.toList()
        restoreTasks.clear()
        tasks.forEach { task ->
            copyHandler.removeCallbacks(task)
            task.run()
        }
    }

    private companion object {
        const val COPIED_MILLIS = 2000L
        val COPIED_TEXT_COLOR = 0xFF2DAB4A.toInt()
        val INVITE_TEXT_COLOR = 0xFFFFFFFF.toInt()
        val ACCENT_ICON_COLOR = 0xFF0071E3.toInt()
    }
}
