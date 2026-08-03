package com.trtc.uikit.roomkit.view

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.RoomMainActivity
import com.trtc.uikit.roomkit.RoomScheduleActivity
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import com.trtc.uikit.roomkit.base.ui.widget.RoomTopBar
import com.trtc.uikit.roomkit.view.schedule.RoomScheduleInfoDialog
import com.trtc.uikit.roomkit.view.schedule.RoomSelectedAttendeeAdapter
import com.trtc.uikit.roomkit.view.schedule.ScheduleDateFormatter
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ListResultCompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomListener
import io.trtc.tuikit.atomicxcore.api.room.RoomStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max

class RoomScheduledDetailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "RoomScheduledDetailView"
        private const val CLIPBOARD_LABEL = "room"
        private const val MILLIS_PER_MINUTE = 60_000L
    }

    private val logger = RoomKitLogger.getLogger(TAG)
    private var roomID: String = ""
    private val localUserID: String?
        get() = LoginStore.shared.loginState.loginUserInfo.value?.userID
    private var roomInfo: RoomInfo? = null
    private var attendees: List<RoomUser> = emptyList()

    private val topBar: RoomTopBar by lazy { findViewById(R.id.top_bar_detail) }
    private val tvModify: TextView by lazy {
        LayoutInflater.from(context)
            .inflate(R.layout.roomkit_view_scheduled_top_bar_right, topBar, false) as TextView
    }
    private val tvRoomName: TextView by lazy { findViewById(R.id.tv_detail_room_name) }
    private val tvRoomId: TextView by lazy { findViewById(R.id.tv_detail_room_id) }
    private val imgCopyRoomId: ImageView by lazy { findViewById(R.id.img_detail_copy_room_id) }
    private val tvStartTime: TextView by lazy { findViewById(R.id.tv_detail_start_time) }
    private val tvDuration: TextView by lazy { findViewById(R.id.tv_detail_duration) }
    private val tvPasswordTitle: TextView by lazy { findViewById(R.id.tv_detail_password_title) }
    private val tvPassword: TextView by lazy { findViewById(R.id.tv_detail_password) }
    private val tvHost: TextView by lazy { findViewById(R.id.tv_detail_host) }
    private val ivHostAvatar: ImageFilterView by lazy { findViewById(R.id.iv_detail_host_avatar) }
    private val llAttendees: LinearLayout by lazy { findViewById(R.id.ll_detail_attendees) }
    private val ivAttendeeAvatars: List<ImageFilterView> by lazy {
        listOf(
            findViewById(R.id.iv_detail_attendee_first),
            findViewById(R.id.iv_detail_attendee_second),
            findViewById(R.id.iv_detail_attendee_third)
        )
    }
    private val tvAttendeesCount: TextView by lazy { findViewById(R.id.tv_detail_attendees_count) }
    private val ivAttendeesArrow: ImageView by lazy { findViewById(R.id.iv_detail_attendees_arrow) }
    private val btnEnterRoom: TextView by lazy { findViewById(R.id.btn_detail_enter_room) }
    private val btnInviteMembers: TextView by lazy { findViewById(R.id.btn_detail_invite_members) }
    private val btnCancelRoom: TextView by lazy { findViewById(R.id.btn_detail_cancel_room) }

    private val roomStore: RoomStore = RoomStore.shared()
    private var subscribeJob: Job? = null
    private var attendeesDialog: BottomSheetDialog? = null
    private val inviteDialog by lazy { RoomScheduleInfoDialog(context) }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_scheduled_detail, this)
        initView()
    }

    private val roomListener = object : RoomListener() {
        override fun onScheduledRoomCancelled(roomInfo: RoomInfo, operator: RoomUser) {
            if (roomInfo.roomID != this@RoomScheduledDetailView.roomID) return
            if (operator.userID == localUserID) return
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_scheduled_room_cancelled_toast),
                AtomicToast.Style.WARNING
            )
            (context as? Activity)?.finish()
        }

        override fun onRemovedFromScheduledRoom(roomInfo: RoomInfo, operator: RoomUser) {
            if (roomInfo.roomID != this@RoomScheduledDetailView.roomID) return
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_scheduled_room_removed_toast),
                AtomicToast.Style.WARNING
            )
            (context as? Activity)?.finish()
        }
    }

    fun init(roomID: String) {
        this.roomID = roomID
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isEmpty()) return
        loadRoomInfo()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        removeObserver()
        super.onDetachedFromWindow()
    }

    private fun addObserver() {
        roomStore.addRoomListener(roomListener)
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            roomStore.state.scheduledRoomList.collect { list ->
                val found = list.find { it.roomID == roomID } ?: return@collect
                roomInfo = found
                updateRoomInfoUI()
                val flowAttendees = found.scheduleAttendees
                if (flowAttendees.isNotEmpty() && flowAttendees != attendees) {
                    attendees = flowAttendees
                    updateAttendeesUI()
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeJob?.cancel()
        subscribeJob = null
        roomStore.removeRoomListener(roomListener)
        attendeesDialog?.takeIf { it.isShowing }?.dismiss()
        attendeesDialog = null
    }

    private fun loadRoomInfo() {
        val list = RoomStore.shared().state.scheduledRoomList.value
        roomInfo = list.find { it.roomID == roomID }
        updateRoomInfoUI()
        updateAttendeesUI()
        if (roomInfo != null) fetchAttendees()
    }

    private fun fetchAttendees() {
        val targetRoomID = roomInfo?.roomID ?: return
        val accumulator = mutableListOf<RoomUser>()
        fun fetchPage(cursor: String?) {
            RoomStore.shared().getScheduledAttendees(
                targetRoomID,
                cursor,
                object : ListResultCompletionHandler<RoomUser> {
                    override fun onSuccess(result: List<RoomUser>, cursor: String) {
                        accumulator.addAll(result)
                        if (cursor.isNotEmpty()) {
                            fetchPage(cursor)
                        } else {
                            attendees = accumulator.toList()
                            updateAttendeesUI()
                        }
                    }

                    override fun onFailure(code: Int, desc: String) {
                        logger.error("getScheduledAttendees failed, code=$code, desc=$desc")
                        ErrorLocalized.showError(context, code)
                    }
                }
            )
        }
        fetchPage(null)
    }

    private fun initView() {
        topBar.setRightView(tvModify)
        topBar.onRightClick = { modifyRoom() }
        btnEnterRoom.setOnClickListener { enterRoom() }
        btnInviteMembers.setOnClickListener { inviteMembers() }
        btnCancelRoom.setOnClickListener { cancelRoom() }
        llAttendees.setOnClickListener { showAttendeesDialog() }
        imgCopyRoomId.setOnClickListener {
            copyToClipboard(roomID)
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_toast_room_id_copied),
                AtomicToast.Style.INFO
            )
        }
    }

    private fun updateRoomInfoUI() {
        val info = roomInfo ?: return
        val currentUserID = localUserID
        val isMyRoom = currentUserID != null && currentUserID == info.roomOwner.userID
        val isRunning = info.roomStatus == RoomStatus.RUNNING
        val canModifyOrCancel = isMyRoom && !isRunning
        tvModify.visibility = if (canModifyOrCancel) VISIBLE else GONE
        btnCancelRoom.visibility = if (canModifyOrCancel) VISIBLE else GONE

        tvRoomName.text = info.roomName
        tvRoomId.text = info.roomID
        tvHost.text = info.roomOwner.userName.ifEmpty { info.roomOwner.userID }
        ImageLoader.load(
            context,
            ivHostAvatar,
            info.roomOwner.avatarURL,
            R.drawable.roomkit_ic_default_avatar
        )

        tvStartTime.text = ScheduleDateFormatter.formatDateTime(context, info.scheduledStartTime)
        tvDuration.text = formatDurationMinutes(info.scheduledStartTime, info.scheduledEndTime)

        val password = info.password
        val hasPassword = !password.isNullOrEmpty()
        tvPasswordTitle.visibility = if (hasPassword) VISIBLE else GONE
        tvPassword.visibility = if (hasPassword) VISIBLE else GONE
        if (hasPassword) tvPassword.text = password
    }

    private fun updateAttendeesUI() {
        val attendeeCount = attendees.size
        ivAttendeeAvatars.forEachIndexed { index, iv ->
            val user = attendees.getOrNull(index)
            if (user == null) {
                iv.visibility = GONE
            } else {
                iv.visibility = VISIBLE
                ImageLoader.load(context, iv, user.avatarURL, R.drawable.roomkit_ic_default_avatar)
            }
        }
        if (attendeeCount <= 0) {
            tvAttendeesCount.text = context.getString(R.string.roomkit_no_participants_yet)
            ivAttendeesArrow.visibility = GONE
            llAttendees.isClickable = false
        } else {
            tvAttendeesCount.text =
                context.getString(R.string.roomkit_format_add_attendee, attendeeCount.toString())
            ivAttendeesArrow.visibility = VISIBLE
            llAttendees.isClickable = true
        }
    }

    private fun formatDurationMinutes(startMillis: Long, endMillis: Long): String {
        val totalMinutes = max(0L, (endMillis - startMillis) / MILLIS_PER_MINUTE).toInt()
        return ScheduleDateFormatter.formatDuration(context, totalMinutes)
    }

    private fun enterRoom() {
        val info = roomInfo ?: return
        val intent = Intent(context, RoomMainActivity::class.java).apply {
            putExtra(RoomMainActivity.EXTRA_ROOM_ID, info.roomID)
            putExtra(RoomMainActivity.EXTRA_ROOM_NAME, info.roomName)
            putExtra(RoomMainActivity.EXTRA_IS_CREATE, false)
            putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_MICROPHONE, true)
            putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_CAMERA, false)
            putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_SPEAKER, true)
        }
        context.startActivity(intent)
    }

    private fun modifyRoom() {
        val intent = Intent(context, RoomScheduleActivity::class.java).apply {
            putExtra(RoomScheduleActivity.EXTRA_EDIT_ROOM_ID, roomID)
        }
        context.startActivity(intent)
    }

    private fun inviteMembers() {
        val info = roomInfo ?: return
        inviteDialog.show(info, context.getString(R.string.roomkit_scheduled_invite_members))
    }

    private fun showAttendeesDialog() {
        val list: List<RoomUser> = attendees
        if (list.isEmpty()) return

        val dialog = attendeesDialog ?: BottomSheetDialog(context, R.style.RoomkitBottomSheetDialog)
            .also { attendeesDialog = it }

        val view = View.inflate(context, R.layout.roomkit_dialog_selected_attendees, null)
        val dragIndicator: View = view.findViewById(R.id.drag_indicator_selected)
        val tvTitle: TextView = view.findViewById(R.id.tv_selected_dialog_title)
        val rv: RecyclerView = view.findViewById(R.id.rv_selected_attendees)

        dragIndicator.setOnClickListener { dialog.dismiss() }

        val adapter = RoomSelectedAttendeeAdapter(context)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter
        adapter.setData(list)
        tvTitle.text = context.getString(R.string.roomkit_selected_participant_format, list.size.toString())

        dialog.setContentView(view)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }

    private fun cancelRoom() {
        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_scheduled_cancel_title)
            .setMessage(R.string.roomkit_scheduled_cancel_message)
            .setWarning(true)
            .setNegativeButton(R.string.roomkit_scheduled_cancel_not)
            .setPositiveButton(R.string.roomkit_scheduled_cancel_confirm) {
                RoomStore.shared().cancelScheduledRoom(roomID, object : CompletionHandler {
                    override fun onSuccess() {
                        logger.info("cancelScheduledRoom success, roomID=$roomID")
                        (context as? Activity)?.finish()
                    }

                    override fun onFailure(code: Int, desc: String) {
                        logger.error("cancelScheduledRoom failed, code=$code, desc=$desc")
                        ErrorLocalized.showError(context, code)
                    }
                })
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_LABEL, text))
    }
}