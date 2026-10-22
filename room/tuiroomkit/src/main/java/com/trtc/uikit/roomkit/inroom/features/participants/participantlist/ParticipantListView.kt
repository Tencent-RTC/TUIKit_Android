package com.trtc.uikit.roomkit.inroom.features.participants.participantlist

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.ActionCompletion
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ParticipantListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val searchInput: EditText
    private val titleText: TextView
    private val recyclerView: RecyclerView
    private val searchEmptyView: View
    private val searchEmptyDesc: TextView
    private val participantAdapter: ParticipantAdapter
    private val footer: View
    private val muteButton: TextView
    private val cameraButton: TextView

    private val roomStore = RoomStore.shared()
    private var roomID: String = ""
    private var participants: List<RoomParticipant> = emptyList()
    private var screenSharerID: String? = null
    private var keyword: String = ""
    private var subscribeJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_participant_list, this, true)
        searchInput = findViewById(R.id.participant_search_input)
        titleText = findViewById(R.id.participant_title)
        recyclerView = findViewById(R.id.participant_recycler)
        searchEmptyView = findViewById(R.id.participant_search_empty)
        searchEmptyDesc = findViewById(R.id.search_empty_desc)
        findViewById<View>(R.id.search_empty_clear_button).setOnClickListener { searchInput.setText("") }
        footer = findViewById(R.id.participant_footer)
        muteButton = findViewById(R.id.footer_mute_button)
        cameraButton = findViewById(R.id.footer_camera_button)
        participantAdapter = ParticipantAdapter { participant ->
            ParticipantActionSheet(context, roomID).show(participant)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = participantAdapter
        findViewById<View>(R.id.participant_invite_button).setOnClickListener {
            RoomToast.show(context, context.getString(R.string.roomkit_common_developing))
        }
        findViewById<View>(R.id.footer_more_button).setOnClickListener {
            roomStore.state.currentRoom.value?.let { room ->
                ParticipantMoreSheet(context, roomID).show(room)
            }
        }
        muteButton.setOnClickListener { toggleAllDevices(DeviceType.MICROPHONE) }
        cameraButton.setOnClickListener { toggleAllDevices(DeviceType.CAMERA) }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                keyword = s?.toString()?.trim().orEmpty()
                updateList()
            }
        })
    }

    fun bind(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) observeParticipants()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotBlank()) observeParticipants()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun observeParticipants() {
        subscribeJob?.cancel()
        val state = RoomParticipantStore.create(roomID).state
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                state.participantList,
                roomStore.state.currentRoom,
                state.participantWithScreen,
            ) { list, room, screenParticipant -> Triple(list, room, screenParticipant) }
                .collect { (list, room, screenParticipant) ->
                    if (room?.roomID != roomID) return@collect
                    screenSharerID = screenParticipant?.userID
                    render(list, room)
                }
        }
    }

    private fun render(list: List<RoomParticipant>, room: RoomInfo) {
        participants = list
        updateList()
        updateTitle(list.size)
        updateFooter(room.isAllMicrophoneDisabled, room.isAllCameraDisabled, list)
    }

    private fun updateTitle(count: Int) {
        titleText.text =
            if (count == 0) {
                context.getString(R.string.roomkit_participants_panel)
            } else {
                context.getString(R.string.roomkit_participants_panel_count, count.toString())
            }
    }

    private fun updateList() {
        val filtered = if (keyword.isEmpty()) {
            participants
        } else {
            participants.filter { it.getDisplayName().contains(keyword, ignoreCase = true) }
        }
        val showEmpty = filtered.isEmpty() && keyword.isNotEmpty()
        searchEmptyView.visibility = if (showEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (showEmpty) View.GONE else View.VISIBLE
        if (showEmpty) {
            val text = context.getString(R.string.roomkit_participants_search_empty_desc, keyword)
            val spannable = SpannableStringBuilder(text)
            text.indexOf(keyword).takeIf { it >= 0 }?.let { start ->
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD), start, start + keyword.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            searchEmptyDesc.text = spannable
        }
        val sectionTitle = context.getString(R.string.roomkit_participants_joined)
        participantAdapter.updateData(filtered, sectionTitle, screenSharerID)
    }

    private fun updateFooter(micDisabled: Boolean, camDisabled: Boolean, list: List<RoomParticipant>) {
        val localUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID
        val isPrivileged = list.any {
            it.userID == localUserID && (it.role == ParticipantRole.OWNER || it.role == ParticipantRole.ADMIN)
        }
        footer.visibility = if (isPrivileged) VISIBLE else GONE
        muteButton.text = context.getString(
            if (micDisabled) R.string.roomkit_participants_unmute_all else R.string.roomkit_participants_mute_all,
        )
        muteButton.isSelected = micDisabled
        cameraButton.text = context.getString(
            if (camDisabled) R.string.roomkit_participants_cam_on_all else R.string.roomkit_participants_cam_off_all,
        )
        cameraButton.isSelected = camDisabled
    }

    private fun toggleAllDevices(deviceType: DeviceType) {
        val room = roomStore.state.currentRoom.value ?: return
        val disable = if (deviceType == DeviceType.MICROPHONE) {
            !room.isAllMicrophoneDisabled
        } else {
            !room.isAllCameraDisabled
        }
        val res = if (deviceType == DeviceType.MICROPHONE) {
            if (disable) R.string.roomkit_participants_mute_all_done else R.string.roomkit_participants_unmute_all_done
        } else {
            if (disable) R.string.roomkit_participants_cam_off_all_done else R.string.roomkit_participants_cam_on_all_done
        }
        RoomParticipantStore.create(roomID).disableAllDevices(
            deviceType,
            disable,
            ActionCompletion(context, context.getString(res)),
        )
    }
}

class ParticipantMoreSheet(context: Context, roomID: String) {

    private val sheet = RoomPopup(context)

    private val participantStore = RoomParticipantStore.create(roomID)
    private val content = LayoutInflater.from(context).inflate(R.layout.roomkit_participant_more_sheet, null)
    private val chatMuteRow: TextView = content.findViewById(R.id.more_chat_mute)
    private val shareRow: TextView = content.findViewById(R.id.more_share)

    fun show(room: RoomInfo) {
        bindChatMute(room.isAllMessageDisabled)
        bindShare(room.isAllScreenShareDisabled)
        sheet.setContentView(content, RoomPopup.ContentHeight.AUTO)
        sheet.show()
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
            sheet.dismiss()
            participantStore.disableAllMessages(
                !chatMuted,
                ActionCompletion(
                    content.context,
                    content.context.getString(
                        if (chatMuted) R.string.roomkit_participants_unmute_all_chat_done
                        else R.string.roomkit_participants_mute_all_chat_done,
                    ),
                ),
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
            sheet.dismiss()
            participantStore.disableAllDevices(
                DeviceType.SCREEN_SHARE,
                !shareDisabled,
                ActionCompletion(
                    content.context,
                    content.context.getString(
                        if (shareDisabled) R.string.roomkit_participants_share_everyone_done
                        else R.string.roomkit_participants_share_owner_only_done,
                    ),
                ),
            )
        }
    }
}
