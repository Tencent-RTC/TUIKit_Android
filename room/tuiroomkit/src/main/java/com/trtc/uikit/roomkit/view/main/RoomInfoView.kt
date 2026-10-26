package com.trtc.uikit.roomkit.view.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import com.trtc.uikit.roomkit.base.ui.BaseView
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.view.isVisible

/**
 * Room information view displaying room details with copy-to-clipboard functionality.
 */
class RoomInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val tvRoomName: TextView by lazy { findViewById(R.id.tv_room_name) }
    private val tvRoomOwner: TextView by lazy { findViewById(R.id.tv_room_owner) }
    private val tvRoomID: TextView by lazy { findViewById(R.id.tv_room_ID) }
    private val btnCopyRoomID: LinearLayout by lazy { findViewById(R.id.btn_copy_room_ID) }
    private val llPasswordRow: LinearLayout by lazy { findViewById(R.id.ll_password_row) }
    private val tvRoomPassword: TextView by lazy { findViewById(R.id.tv_room_password) }
    private val btnCopyPassword: LinearLayout by lazy { findViewById(R.id.btn_copy_password) }
    private val btnCopyInvitationLink: AppCompatButton by lazy { findViewById(R.id.btn_copy_invitation_link) }

    private var roomStore = RoomStore.shared()
    private var subscribeJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_room_info, this)
        setupListeners()
    }

    public override fun init(roomID: String) {
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        roomStore = RoomStore.shared()
    }

    override fun addObserver() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            roomStore.state.currentRoom.collect { roomInfo ->
                roomInfo?.let {
                    updateRoomInfo(roomInfo)
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
    }

    private fun updateRoomInfo(roomInfo: RoomInfo) {
        tvRoomName.text = roomInfo.getDisplayName()
        tvRoomOwner.text = roomInfo.roomOwner.getDisplayName()
        tvRoomID.text = roomInfo.roomID
        val password = roomInfo.password.orEmpty()
        if (password.isNotEmpty()) {
            llPasswordRow.visibility = VISIBLE
            tvRoomPassword.text = password
        } else {
            llPasswordRow.visibility = GONE
        }
    }

    private fun setupListeners() {
        btnCopyRoomID.setOnClickListener {
            copyToClipboard(tvRoomID.text.toString())
            AtomicToast.show(context, context.getString(R.string.roomkit_toast_room_id_copied), AtomicToast.Style.INFO)
        }

        btnCopyPassword.setOnClickListener {
            copyToClipboard(tvRoomPassword.text.toString())
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_toast_room_password_copied),
                AtomicToast.Style.INFO
            )
        }

        btnCopyInvitationLink.setOnClickListener {
            val invitationText = generateInvitationText()
            copyToClipboard(invitationText)
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_toast_room_info_copied),
                AtomicToast.Style.INFO
            )
        }
    }

    private fun generateInvitationText(): String {
        val lines = mutableListOf<String>()
        lines += "${context.getString(R.string.roomkit_room_name)}: ${tvRoomName.text}"
        lines += "${context.getString(R.string.roomkit_room_id)}: ${tvRoomID.text}"
        if (llPasswordRow.isVisible) {
            lines += "${context.getString(R.string.roomkit_room_password_title)}: ${tvRoomPassword.text}"
        }
        return lines.joinToString("\n")
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Label", text)
        clipboard.setPrimaryClip(clip)
    }
}