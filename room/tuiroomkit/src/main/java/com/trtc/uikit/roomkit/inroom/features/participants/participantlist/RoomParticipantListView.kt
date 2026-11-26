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
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.canManage
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
import kotlinx.coroutines.launch

class RoomParticipantListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val searchInput: EditText
    private val titleText: TextView
    private val joinedHeader: RoomParticipantSectionView
    private val recyclerView: RecyclerView
    private val emptyView: View
    private val emptyDesc: TextView
    private lateinit var participantAdapter: ParticipantAdapter
    private val footer: View
    private val muteAllMicrophoneButton: TextView
    private val muteAllCameraButton: TextView

    private val roomStore = RoomStore.shared()
    private var roomID: String = ""
    private var participants: List<RoomParticipant> = emptyList()
    private var keyword: String = ""
    private var isCollapsed = false
    private var displayedParticipants: List<RoomParticipant> = emptyList()
    private var subscribeJob: Job? = null
    private val actionPopup by lazy { RoomParticipantActionPopup(context, roomID) }
    private val morePopup by lazy { RoomParticipantMorePopup(context, roomID) }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_participant_list, this, true)
        searchInput = findViewById(R.id.participant_search_input)
        titleText = findViewById(R.id.participant_title)
        joinedHeader = findViewById(R.id.participant_joined_header)
        recyclerView = findViewById(R.id.participant_recycler)
        emptyView = findViewById(R.id.participant_search_empty)
        emptyDesc = findViewById(R.id.search_empty_desc)
        findViewById<View>(R.id.search_empty_clear_button).setOnClickListener { searchInput.setText("") }
        footer = findViewById(R.id.participant_footer)
        muteAllMicrophoneButton = findViewById(R.id.footer_mute_button)
        muteAllCameraButton = findViewById(R.id.footer_camera_button)
        recyclerView.layoutManager = LinearLayoutManager(context)
        joinedHeader.setOnClickListener {
            isCollapsed = !isCollapsed
            joinedHeader.updateCollapsed(isCollapsed)
            updateList()
        }
        findViewById<View>(R.id.participant_invite_button).setOnClickListener {
            RoomToast.show(context, context.getString(R.string.roomkit_common_developing))
        }
        findViewById<View>(R.id.footer_more_button).setOnClickListener {
            roomStore.state.currentRoom.value?.let { room ->
                morePopup.show(room)
            }
        }
        muteAllMicrophoneButton.setOnClickListener { toggleAllMicrophones() }
        muteAllCameraButton.setOnClickListener { toggleAllCameras() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                keyword = s?.toString()?.trim().orEmpty()
                updateList()
            }
        })
    }

    fun init(roomID: String) {
        this.roomID = roomID
        participantAdapter = ParticipantAdapter(roomID) { participant ->
            actionPopup.show(participant)
        }
        recyclerView.adapter = participantAdapter
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
        val participantStore = RoomParticipantStore.create(roomID)
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                participantStore.state.participantList.collect { list ->
                    participants = list
                    updateList()
                }
            }
            launch {
                roomStore.state.currentRoom.collect { room ->
                    if (room != null && room.roomID == roomID) {
                        updateTitle(room.participantCount)
                        joinedHeader.updateCount(room.participantCount)
                        updateFooter(room)
                    }
                }
            }
            launch {
                participantStore.state.localParticipant.collect {
                    val room = roomStore.state.currentRoom.value
                    if (room != null && room.roomID == roomID) updateFooter(room)
                }
            }
        }
    }

    private fun updateTitle(count: Int) {
        titleText.text = if (count == 0) {
            context.getString(R.string.roomkit_participants_panel)
        } else {
            context.getString(R.string.roomkit_participants_panel_count, count.toString())
        }
    }

    private fun updateList() {
        val filtered = filteredParticipants()
        updateSearchEmptyState(filtered)
        displayedParticipants = if (isCollapsed) emptyList() else filtered
        participantAdapter.updateData(displayedParticipants)
    }

    private fun filteredParticipants(): List<RoomParticipant> {
        if (keyword.isEmpty()) return participants
        return participants.filter { it.getDisplayName().contains(keyword, ignoreCase = true) }
    }

    private fun updateSearchEmptyState(filtered: List<RoomParticipant>) {
        val showEmpty = filtered.isEmpty() && keyword.isNotEmpty()
        emptyView.visibility = if (showEmpty) View.VISIBLE else View.GONE
        joinedHeader.visibility = if (showEmpty) View.GONE else View.VISIBLE
        recyclerView.visibility = if (showEmpty) View.GONE else View.VISIBLE
        if (showEmpty) updateSearchEmptyDescription()
    }

    private fun updateSearchEmptyDescription() {
        val text = context.getString(R.string.roomkit_participants_search_empty_desc, keyword)
        val spannable = SpannableStringBuilder(text)
        text.indexOf(keyword).takeIf { it >= 0 }?.let { start ->
            spannable.setSpan(
                StyleSpan(Typeface.BOLD), start, start + keyword.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        emptyDesc.text = spannable
    }

    private fun updateFooter(room: RoomInfo) {
        val localRole = RoomParticipantStore.create(roomID).state.localParticipant.value?.role
        val isLocalOwnerOrAdmin = localRole == ParticipantRole.OWNER || localRole == ParticipantRole.ADMIN
        footer.visibility = if (isLocalOwnerOrAdmin) VISIBLE else GONE
        muteAllMicrophoneButton.text = context.getString(
            if (room.isAllMicrophoneDisabled) R.string.roomkit_participants_unmute_all else R.string.roomkit_participants_mute_all,
        )
        muteAllMicrophoneButton.isSelected = room.isAllMicrophoneDisabled
        muteAllCameraButton.text = context.getString(
            if (room.isAllCameraDisabled) R.string.roomkit_participants_cam_on_all else R.string.roomkit_participants_cam_off_all,
        )
        muteAllCameraButton.isSelected = room.isAllCameraDisabled
    }

    private fun toggleAllMicrophones() {
        val room = roomStore.state.currentRoom.value?.takeIf { it.roomID == roomID } ?: return
        val disable = !room.isAllMicrophoneDisabled
        val doneRes = if (disable) R.string.roomkit_participants_mute_all_done else R.string.roomkit_participants_unmute_all_done
        RoomParticipantStore.create(roomID).disableAllDevices(
            DeviceType.MICROPHONE,
            disable,
            object : CompletionHandler {
                override fun onSuccess() {
                    RoomToast.show(context, context.getString(doneRes))
                }

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            },
        )
    }

    private fun toggleAllCameras() {
        val room = roomStore.state.currentRoom.value?.takeIf { it.roomID == roomID } ?: return
        val disable = !room.isAllCameraDisabled
        val doneRes = if (disable) R.string.roomkit_participants_cam_off_all_done else R.string.roomkit_participants_cam_on_all_done
        RoomParticipantStore.create(roomID).disableAllDevices(
            DeviceType.CAMERA,
            disable,
            object : CompletionHandler {
                override fun onSuccess() {
                    RoomToast.show(context, context.getString(doneRes))
                }

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            },
        )
    }
}

class ParticipantAdapter(
    private val roomID: String,
    private val onParticipantTap: (participant: RoomParticipant) -> Unit,
) : RecyclerView.Adapter<RoomParticipantListCell>() {

    private var participants: List<RoomParticipant> = emptyList()

    fun updateData(list: List<RoomParticipant>) {
        participants = list
        notifyDataSetChanged()
    }

    private fun isSelf(participant: RoomParticipant): Boolean {
        return participant.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
    }

    private fun canAction(participant: RoomParticipant): Boolean {
        if (isSelf(participant)) return true
        val localRole = RoomParticipantStore.create(roomID).state.localParticipant.value?.role
        return localRole?.canManage(participant.role) == true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomParticipantListCell {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.roomkit_participant_item, parent, false)
        return RoomParticipantListCell(view)
    }

    override fun getItemCount(): Int = participants.size

    override fun onBindViewHolder(holder: RoomParticipantListCell, position: Int) {
        val participant = participants[position]
        holder.bind(participant, canAction(participant)) {
            onParticipantTap(participant)
        }
    }
}

class RoomParticipantSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val chevron: AppCompatImageView
    private val titleText: AppCompatTextView
    private val countText: AppCompatTextView

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_participant_section_header, this, true)
        chevron = findViewById(R.id.section_chevron)
        titleText = findViewById(R.id.section_title)
        countText = findViewById(R.id.section_count)
    }

    fun setTitle(title: String) {
        titleText.text = title
    }

    fun updateCollapsed(collapsed: Boolean) {
        chevron.rotation = if (collapsed) -90f else 0f
    }

    fun updateCount(count: Int) {
        countText.text = "($count)"
        countText.visibility = if (count == 0) View.GONE else View.VISIBLE
    }
}
