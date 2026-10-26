package com.trtc.uikit.roomkit.view.main.participantlist

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import com.trtc.uikit.roomkit.base.ui.RoomPopupDialog
import com.trtc.uikit.roomkit.base.utils.KeyboardUtils
import com.trtc.uikit.roomkit.view.main.ParticipantManagerView
import com.trtc.uikit.roomkit.view.main.ParticipantManagerView.OnParticipantActionListener
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.room.CallUserToRoomCompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomCallResult
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StandardRoomParticipantListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomParticipantListView")

    companion object {
        private const val TAB_INDEX_JOINED = 0
        private const val TAB_INDEX_PENDING = 1
    }

    private var subscribeStateJob: Job? = null

    private val tabLayout: TabLayout by lazy { findViewById(R.id.tab_layout) }
    private val etSearch: EditText by lazy { findViewById(R.id.et_search) }
    private val rvParticipants: RecyclerView by lazy { findViewById(R.id.rv_participants) }
    private val rvPending: RecyclerView by lazy { findViewById(R.id.rv_pending) }
    private val llBottomActions: LinearLayout by lazy { findViewById(R.id.ll_bottom_actions) }
    private val btnMuteAll: AppCompatButton by lazy { findViewById(R.id.btn_mute_all) }
    private val btnDisableAllVideo: AppCompatButton by lazy { findViewById(R.id.btn_disable_all_video) }
    private val btnCallAll: AppCompatButton by lazy { findViewById(R.id.btn_call_all) }
    private lateinit var joinedTab: TabLayout.Tab
    private lateinit var pendingTab: TabLayout.Tab

    private val joinedAdapter = ParticipantListAdapter(RoomType.STANDARD)
    private val pendingAdapter = PendingParticipantListAdapter()

    private var participantStore: RoomParticipantStore? = null
    private var roomStore: RoomStore? = null
    private var participantManagerDialog: RoomPopupDialog? = null
    private var participantManagerView: ParticipantManagerView? = null

    // Full lists kept locally so that search only filters the display, not the source or tab counts.
    private var allJoined: List<RoomParticipant> = emptyList()
    private var allPending: List<RoomParticipant> = emptyList()
    private var searchKeyword: String = ""
    private var currentTab: Int = TAB_INDEX_JOINED
    private var localRole: ParticipantRole = ParticipantRole.GENERAL_USER

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_standard_room_participant_list_view, this)
        initView()
    }

    public override fun init(roomID: String) {
        super.init(roomID)
        pendingAdapter.setRoomID(roomID)
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
        roomStore = RoomStore.shared()
    }

    override fun addObserver() {
        val participantStore = participantStore ?: return
        val roomStore = roomStore ?: return
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                participantStore.state.participantList.collect { participants ->
                    allJoined = participants
                    applyJoinedFilter()
                    updateTabView()
                }
            }

            launch {
                participantStore.state.pendingParticipantList.collect { participants ->
                    allPending = participants
                    applyPendingFilter()
                    updateTabView()
                    updateControlButtonsVisibility()
                }
            }

            launch {
                participantStore.state.localParticipant.collect { localParticipant ->
                    localParticipant?.let {
                        localRole = it.role
                        updateControlButtonsVisibility()
                    }
                }
            }

            launch {
                roomStore.state.currentRoom.collect { roomInfo ->
                    roomInfo?.let {
                        updateMuteAllButton(it.isAllMicrophoneDisabled)
                        updateDisableAllVideoButton(it.isAllCameraDisabled)
                    }
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
        subscribeStateJob = null
        participantManagerDialog?.dismiss()
        participantManagerDialog = null
    }

    private fun initView() {
        joinedTab = tabLayout.newTab()
        pendingTab = tabLayout.newTab()
        tabLayout.addTab(joinedTab)
        tabLayout.addTab(pendingTab)
        updateTabView()

        rvParticipants.layoutManager = LinearLayoutManager(context)
        rvParticipants.adapter = joinedAdapter

        rvPending.layoutManager = LinearLayoutManager(context)
        rvPending.adapter = pendingAdapter

        rvParticipants.visibility = VISIBLE
        rvPending.visibility = GONE

        val hideKeyboardOnScrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    KeyboardUtils.hideKeyboard(etSearch)
                }
            }
        }
        rvParticipants.addOnScrollListener(hideKeyboardOnScrollListener)
        rvPending.addOnScrollListener(hideKeyboardOnScrollListener)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                when (tab.position) {
                    TAB_INDEX_JOINED -> {
                        rvParticipants.visibility = VISIBLE
                        rvPending.visibility = GONE
                    }

                    TAB_INDEX_PENDING -> {
                        rvParticipants.visibility = GONE
                        rvPending.visibility = VISIBLE
                    }
                }
                updateControlButtonsVisibility()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        joinedAdapter.setOnItemClickListener { participant ->
            showParticipantActionDialog(participant)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchKeyword = s?.toString()?.trim().orEmpty()
                applyJoinedFilter()
                applyPendingFilter()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                KeyboardUtils.hideKeyboard(etSearch)
                true
            } else {
                false
            }
        }

        btnMuteAll.setOnClickListener {
            handleMuteAllClick()
        }

        btnDisableAllVideo.setOnClickListener {
            handleDisableAllVideoClick()
        }

        btnCallAll.setOnClickListener {
            handleCallAllClick()
        }
    }

    private fun updateTabView() {
        joinedTab.text = context.getString(R.string.roomkit_tab_joined, allJoined.size.toString())
        pendingTab.text = context.getString(R.string.roomkit_tab_pending, allPending.size.toString())
    }

    private fun applyJoinedFilter() {
        joinedAdapter.updateData(filterByKeyword(allJoined))
    }

    private fun applyPendingFilter() {
        pendingAdapter.updateData(filterByKeyword(allPending))
    }

    private fun filterByKeyword(source: List<RoomParticipant>): List<RoomParticipant> {
        if (searchKeyword.isEmpty()) {
            return source
        }
        val kw = searchKeyword.lowercase()
        return source.filter {
            it.userName.lowercase().contains(kw) || it.userID.lowercase().contains(kw)
        }
    }

    private fun updateControlButtonsVisibility() {
        val shouldShowControls = (localRole == ParticipantRole.OWNER || localRole == ParticipantRole.ADMIN)
        val isJoinedTab = currentTab == TAB_INDEX_JOINED
        llBottomActions.visibility = if (shouldShowControls && isJoinedTab) VISIBLE else GONE
        // Call-all is available to all roles on the pending tab, but only when there is someone to call.
        val isPendingTab = !isJoinedTab
        btnCallAll.visibility = if (isPendingTab && allPending.isNotEmpty()) VISIBLE else GONE
    }

    private fun showParticipantActionDialog(participant: RoomParticipant) {
        logger.info("Show action dialog for participant: ${participant.userID}")
        val localParticipant = participantStore?.state?.localParticipant?.value ?: return
        val canOperate = localParticipant.role.value < participant.role.value
        if (!canOperate) {
            return
        }

        if (participantManagerDialog == null) {
            participantManagerView = ParticipantManagerView(context).apply {
                init(
                    roomID,
                    RoomType.STANDARD,
                    object : OnParticipantActionListener {
                        override fun onDismiss() {
                            participantManagerDialog?.dismiss()
                        }
                    }
                )
            }
            participantManagerDialog = RoomPopupDialog(context).apply {
                participantManagerView?.let {
                    setView(it)
                }
            }
        }
        participantManagerView?.setRoomParticipant(participant)
        participantManagerDialog?.show()
    }

    private fun updateMuteAllButton(isAllMuted: Boolean) {
        if (isAllMuted) {
            btnMuteAll.text = context.getString(R.string.roomkit_unmute_all_audio)
            btnMuteAll.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_text_red))
        } else {
            btnMuteAll.text = context.getString(R.string.roomkit_mute_all_audio)
            btnMuteAll.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_text_grey))
        }
    }

    private fun updateDisableAllVideoButton(isAllVideoDisabled: Boolean) {
        if (isAllVideoDisabled) {
            btnDisableAllVideo.text = context.getString(R.string.roomkit_enable_all_video)
            btnDisableAllVideo.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_end_room))
        } else {
            btnDisableAllVideo.text = context.getString(R.string.roomkit_disable_all_video)
            btnDisableAllVideo.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_text_grey))
        }
    }

    private fun handleMuteAllClick() {
        val roomInfo = roomStore?.state?.currentRoom?.value
        val isAllMuted = roomInfo?.isAllMicrophoneDisabled ?: false

        if (isAllMuted) {
            logger.info("Unmute all participants clicked")
            RoomAlertDialog.Builder(context)
                .setTitle(R.string.roomkit_msg_all_members_will_be_unmuted)
                .setMessage(R.string.roomkit_msg_members_can_unmute)
                .setNegativeButton(android.R.string.cancel)
                .setPositiveButton(R.string.roomkit_confirm_release) {
                    handleUnmuteAll()
                }
                .show()
        } else {
            logger.info("Mute all participants clicked")
            RoomAlertDialog.Builder(context)
                .setTitle(R.string.roomkit_msg_all_members_will_be_muted)
                .setMessage(R.string.roomkit_msg_members_cannot_unmute)
                .setNegativeButton(android.R.string.cancel)
                .setPositiveButton(R.string.roomkit_mute_all_audio) {
                    handleMuteAll()
                }
                .show()
        }
    }

    private fun handleMuteAll() {
        val store = participantStore ?: return
        logger.info("Execute mute all")
        store.disableAllDevices(DeviceType.MICROPHONE, true, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Mute all success")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Mute all failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleUnmuteAll() {
        val store = participantStore ?: return
        logger.info("Execute unmute all")
        store.disableAllDevices(DeviceType.MICROPHONE, false, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Unmute all success")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Unmute all failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleDisableAllVideoClick() {
        val roomInfo = roomStore?.state?.currentRoom?.value
        val isAllVideoDisabled = roomInfo?.isAllCameraDisabled ?: false

        if (isAllVideoDisabled) {
            logger.info("Enable all video clicked")
            RoomAlertDialog.Builder(context)
                .setTitle(R.string.roomkit_msg_all_members_video_enabled)
                .setMessage(R.string.roomkit_msg_members_can_start_video)
                .setNegativeButton(android.R.string.cancel)
                .setPositiveButton(R.string.roomkit_confirm_release) {
                    handleEnableAllVideo()
                }
                .show()
        } else {
            logger.info("Disable all video clicked")
            RoomAlertDialog.Builder(context)
                .setTitle(R.string.roomkit_msg_all_members_video_disabled)
                .setMessage(R.string.roomkit_msg_members_cannot_start_video)
                .setNegativeButton(android.R.string.cancel)
                .setPositiveButton(R.string.roomkit_disable_all_video) {
                    handleDisableAllVideo()
                }
                .show()
        }
    }

    private fun handleDisableAllVideo() {
        val store = participantStore ?: return
        logger.info("Execute disable all video")
        store.disableAllDevices(DeviceType.CAMERA, true, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Disable all video success")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Disable all video failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleEnableAllVideo() {
        val store = participantStore ?: return
        logger.info("Execute enable all video")
        store.disableAllDevices(DeviceType.CAMERA, false, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Enable all video success")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Enable all video failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleCallAllClick() {
        if (roomID.isEmpty()) {
            logger.warn("handleCallAllClick skipped: roomID empty")
            return
        }
        val inviteeIds = allPending.map { it.userID }.filter { it.isNotEmpty() }
        if (inviteeIds.isEmpty()) {
            logger.info("handleCallAllClick skipped: no pending participants to call")
            return
        }
        logger.info("Call all pending participants: count=${inviteeIds.size}")
        RoomStore.shared().callUserToRoom(
            roomID,
            inviteeIds,
            60,
            "",
            object : CallUserToRoomCompletionHandler {
                override fun onSuccess(result: Map<String, RoomCallResult>) {
                    logger.info("Call all success: $result")
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("Call all failed: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                }
            }
        )
    }
}