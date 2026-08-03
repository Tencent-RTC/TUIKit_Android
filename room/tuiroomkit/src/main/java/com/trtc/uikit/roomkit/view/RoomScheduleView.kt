package com.trtc.uikit.roomkit.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.widget.RoomTopBar
import com.trtc.uikit.roomkit.base.utils.generateRoomID
import com.trtc.uikit.roomkit.view.schedule.RoomDurationPickerDialog
import com.trtc.uikit.roomkit.view.schedule.RoomSelectAttendeeDialog
import com.trtc.uikit.roomkit.view.schedule.RoomStartTimePickerDialog
import com.trtc.uikit.roomkit.view.schedule.RoomTimeZoneDialog
import com.trtc.uikit.roomkit.view.schedule.ScheduleDateFormatter
import com.trtc.uikit.roomkit.view.schedule.TimeZoneFormatter
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomListener
import io.trtc.tuikit.atomicxcore.api.room.RoomStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomUser
import io.trtc.tuikit.atomicxcore.api.room.ScheduleRoomOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Random
import java.util.TimeZone

class RoomScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomScheduleView")
    private var editRoomID: String = ""

    companion object {
        private const val DEFAULT_DURATION_MINUTES = 30
        private const val MINUTE_STEP = 5
        private const val PASSWORD_MIN = 100000
        private const val PASSWORD_MAX = 999999
        private const val MILLIS_PER_MINUTE = 60 * 1000L

        /** Extras key passed back to the host via [Activity.setResult] after scheduling succeeds. */
        const val EXTRA_SCHEDULED_ROOM_ID = "scheduled_room_id"
        const val EXTRA_SCHEDULED_ROOM_NAME = "scheduled_room_name"
        const val EXTRA_SCHEDULED_START_TIME = "scheduled_start_time"
        const val EXTRA_SCHEDULED_END_TIME = "scheduled_end_time"
        const val EXTRA_SCHEDULED_PASSWORD = "scheduled_password"
    }

    private val topBar: RoomTopBar by lazy { findViewById(R.id.top_bar_schedule) }
    private val etRoomName: EditText by lazy { findViewById(R.id.et_room_name) }
    private val tvStartTime: TextView by lazy { findViewById(R.id.tv_start_time) }
    private val tvDuration: TextView by lazy { findViewById(R.id.tv_duration) }
    private val tvTimeZone: TextView by lazy { findViewById(R.id.tv_time_zone) }
    private val tvAttendees: TextView by lazy { findViewById(R.id.tv_attendees) }
    private val ivAttendeeAvatars: List<ImageFilterView> by lazy {
        listOf(
            findViewById(R.id.iv_attendee_first),
            findViewById(R.id.iv_attendee_second),
            findViewById(R.id.iv_attendee_third)
        )
    }
    private val switchAllMute: ImageView by lazy { findViewById(R.id.switch_all_mute) }
    private val switchAllVideo: ImageView by lazy { findViewById(R.id.switch_all_video) }
    private val switchEncrypt: ImageView by lazy { findViewById(R.id.switch_encrypt) }
    private val clRoomPassword: View by lazy { findViewById(R.id.cl_room_password) }
    private val etPassword: EditText by lazy { findViewById(R.id.et_password) }
    private val cardEncrypt: View by lazy { findViewById(R.id.card_encrypt) }
    private val cardDevice: View by lazy { findViewById(R.id.card_device) }
    private var isAllMuteEnabled = false
    private var isAllVideoEnabled = false
    private var isEncryptEnabled = false
    private val btnSchedule: TextView by lazy { findViewById(R.id.btn_schedule) }
    private val llStartTime: View by lazy { findViewById(R.id.ll_start_time) }
    private val llDuration: View by lazy { findViewById(R.id.ll_duration) }
    private val llTimeZone: View by lazy { findViewById(R.id.ll_time_zone) }
    private val llAttendees: View by lazy { findViewById(R.id.ll_attendees) }

    private val roomStore: RoomStore = RoomStore.shared()
    private val calendar = Calendar.getInstance()
    private var selectedDuration = DEFAULT_DURATION_MINUTES
    private var selectedTimeZoneId: String = TimeZone.getDefault().id
    private val selectedAttendeeIds = mutableListOf<String>()
    private var timeZoneDialog: RoomTimeZoneDialog? = null
    private var startTimePickerDialog: RoomStartTimePickerDialog? = null
    private var durationPickerDialog: RoomDurationPickerDialog? = null
    private var selectAttendeeDialog: RoomSelectAttendeeDialog? = null

    private val isEditMode: Boolean get() = editRoomID.isNotEmpty()
    private var editingRoomInfo: RoomInfo? = null

    private var contactMap: Map<String, ContactInfo> = emptyMap()
    private var friendSubscribeJob: Job? = null

    private var scheduledRoomJob: Job? = null

    private val roomListener = object : RoomListener() {
        override fun onScheduledRoomCancelled(roomInfo: RoomInfo, operator: RoomUser) {
            if (!isEditMode || roomInfo.roomID != editRoomID) return
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_scheduled_room_cancelled_toast),
                AtomicToast.Style.WARNING
            )
            (context as? Activity)?.finish()
        }

        override fun onRemovedFromScheduledRoom(roomInfo: RoomInfo, operator: RoomUser) {
            if (!isEditMode || roomInfo.roomID != editRoomID) return
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_scheduled_room_removed_toast),
                AtomicToast.Style.WARNING
            )
            (context as? Activity)?.finish()
        }
    }

    init {
        // Round the initial start time up to the next MINUTE_STEP boundary.
        calendar.add(Calendar.MINUTE, MINUTE_STEP)
        val minute = calendar.get(Calendar.MINUTE)
        calendar.set(Calendar.MINUTE, minute - minute % MINUTE_STEP)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        LayoutInflater.from(context).inflate(R.layout.roomkit_view_schedule, this)
        initView()
    }

    fun init(editRoomID: String) {
        this.editRoomID = editRoomID
    }

    private fun initView() {
        llStartTime.setOnClickListener { showDateTimePicker() }
        llDuration.setOnClickListener { showDurationPicker() }
        llTimeZone.setOnClickListener { showTimeZonePicker() }
        llAttendees.setOnClickListener { showAttendeePicker() }
        btnSchedule.setOnClickListener { onPrimaryButtonClick() }

        switchAllMute.setOnClickListener {
            isAllMuteEnabled = !isAllMuteEnabled
            switchAllMute.setImageResource(
                if (isAllMuteEnabled) R.drawable.roomkit_ic_switch_on else R.drawable.roomkit_ic_switch_off
            )
        }
        switchAllVideo.setOnClickListener {
            isAllVideoEnabled = !isAllVideoEnabled
            switchAllVideo.setImageResource(
                if (isAllVideoEnabled) R.drawable.roomkit_ic_switch_on else R.drawable.roomkit_ic_switch_off
            )
        }

        switchEncrypt.setOnClickListener {
            isEncryptEnabled = !isEncryptEnabled
            switchEncrypt.setImageResource(
                if (isEncryptEnabled) R.drawable.roomkit_ic_switch_on else R.drawable.roomkit_ic_switch_off
            )
            clRoomPassword.visibility = if (isEncryptEnabled) VISIBLE else GONE
            if (isEncryptEnabled && etPassword.text.isEmpty()) {
                etPassword.setText(generateRandomPassword())
            }
        }

        val loginUserInfo = LoginStore.shared.loginState.loginUserInfo.value
        val nickName = loginUserInfo?.nickname ?: loginUserInfo?.userID ?: ""
        etRoomName.setText(context.getString(R.string.roomkit_temporary_room_name, nickName))

        updateStartTimeDisplay()
        updateDurationDisplay()
        updateTimeZoneDisplay()
        updateAttendeesDisplay()
    }

    private fun applyEditMode() {
        topBar.title = context.getString(R.string.roomkit_amend_scheduled_room)
        btnSchedule.text = context.getString(R.string.roomkit_save_scheduled_room)

        cardEncrypt.visibility = View.GONE
        cardDevice.visibility = View.GONE

        val info = roomStore.state.scheduledRoomList.value.find { it.roomID == editRoomID }
        if (info != null) {
            fillFromRoomInfo(info)
        }
    }

    private fun fillFromRoomInfo(info: RoomInfo) {
        editingRoomInfo = info
        etRoomName.setText(info.roomName)
        etRoomName.setSelection(info.roomName.length)
        calendar.timeInMillis = info.scheduledStartTime
        val durationMinutes =
            ((info.scheduledEndTime - info.scheduledStartTime) / MILLIS_PER_MINUTE)
                .toInt()
                .coerceAtLeast(MINUTE_STEP)
        selectedDuration = durationMinutes
        selectedAttendeeIds.clear()
        selectedAttendeeIds.addAll(info.scheduleAttendees.map { it.userID })
        updateStartTimeDisplay()
        updateDurationDisplay()
        updateAttendeesDisplay()
    }

    private fun updateAttendeesDisplay() {
        val count = selectedAttendeeIds.size
        tvAttendees.text = if (count == 0) {
            context.getString(R.string.roomkit_add_member)
        } else {
            context.getString(R.string.roomkit_format_add_attendee, count.toString())
        }
        ivAttendeeAvatars.forEachIndexed { index, iv ->
            val userId = selectedAttendeeIds.getOrNull(index)
            if (userId == null) {
                iv.visibility = View.GONE
                return@forEachIndexed
            }
            iv.visibility = View.VISIBLE
            val avatarUrl = contactMap[userId]?.avatarURL
            ImageLoader.load(context, iv, avatarUrl, R.drawable.roomkit_ic_default_avatar)
        }
    }

    private fun updateStartTimeDisplay() {
        tvStartTime.text = ScheduleDateFormatter.formatDateTime(context, calendar.timeInMillis)
    }

    private fun updateDurationDisplay() {
        tvDuration.text = ScheduleDateFormatter.formatDuration(context, selectedDuration)
    }

    private fun updateTimeZoneDisplay() {
        tvTimeZone.text = TimeZoneFormatter.formatWithDisplayName(selectedTimeZoneId)
    }

    private fun showDateTimePicker() {
        dismissStartTimePickerDialog()
        startTimePickerDialog = RoomStartTimePickerDialog(
            context = context,
            initialTimeMillis = calendar.timeInMillis
        ) { pickedMillis ->
            calendar.timeInMillis = pickedMillis
            updateStartTimeDisplay()
        }.also { it.show() }
    }

    private fun showDurationPicker() {
        dismissDurationPickerDialog()
        durationPickerDialog = RoomDurationPickerDialog(
            context = context,
            initialDurationMinutes = selectedDuration
        ) { pickedMinutes ->
            selectedDuration = pickedMinutes
            updateDurationDisplay()
        }.also { it.show() }
    }

    private fun showTimeZonePicker() {
        dismissTimeZoneDialog()
        timeZoneDialog = RoomTimeZoneDialog(context, selectedTimeZoneId) { id ->
            selectedTimeZoneId = id
            updateTimeZoneDisplay()
        }.also { it.show() }
    }

    private fun dismissTimeZoneDialog() {
        timeZoneDialog?.takeIf { it.isShowing }?.dismiss()
        timeZoneDialog = null
    }

    private fun dismissStartTimePickerDialog() {
        startTimePickerDialog?.takeIf { it.isShowing }?.dismiss()
        startTimePickerDialog = null
    }

    private fun dismissDurationPickerDialog() {
        durationPickerDialog?.takeIf { it.isShowing }?.dismiss()
        durationPickerDialog = null
    }

    private fun dismissSelectAttendeeDialog() {
        selectAttendeeDialog?.takeIf { it.isShowing }?.dismiss()
        selectAttendeeDialog = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        friendSubscribeJob = CoroutineScope(Dispatchers.Main).launch {
            ContactStore.shared.state.friendList.collect { list ->
                contactMap = list.associateBy { it.userID }
                updateAttendeesDisplay()
            }
        }
        ContactStore.shared.loadFriends()
        if (isEditMode) {
            applyEditMode()
            observeScheduledRoomList()
            roomStore.addRoomListener(roomListener)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        friendSubscribeJob?.cancel()
        friendSubscribeJob = null
        scheduledRoomJob?.cancel()
        scheduledRoomJob = null
        if (isEditMode) {
            roomStore.removeRoomListener(roomListener)
        }
        dismissTimeZoneDialog()
        dismissStartTimePickerDialog()
        dismissDurationPickerDialog()
        dismissSelectAttendeeDialog()
    }

    private fun observeScheduledRoomList() {
        scheduledRoomJob = CoroutineScope(Dispatchers.Main).launch {
            roomStore.state.scheduledRoomList.collect { list ->
                val found = list.find { it.roomID == editRoomID }
                if (found != null && editingRoomInfo == null) {
                    fillFromRoomInfo(found)
                }
            }
        }
    }

    private fun showAttendeePicker() {
        dismissSelectAttendeeDialog()
        selectAttendeeDialog = RoomSelectAttendeeDialog(
            context = context,
            initialSelectedIds = selectedAttendeeIds
        ) { ids ->
            selectedAttendeeIds.clear()
            selectedAttendeeIds.addAll(ids)
            updateAttendeesDisplay()
        }.also { it.show() }
    }

    private fun onPrimaryButtonClick() {
        if (isEditMode) {
            saveScheduledRoom()
        } else {
            scheduleRoom()
        }
    }

    private fun scheduleRoom() {
        val roomName = etRoomName.text.toString().trim()
        if (roomName.isEmpty()) {
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_conference_name_empty),
                AtomicToast.Style.WARNING
            )
            return
        }
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_start_time_earlier_than_current_time),
                AtomicToast.Style.WARNING
            )
            return
        }

        val roomID = generateRoomID()
        val options = ScheduleRoomOptions().apply {
            this.roomName = roomName
            scheduleStartTime = calendar.timeInMillis
            scheduleEndTime = calendar.timeInMillis + selectedDuration * MILLIS_PER_MINUTE
            isAllMicrophoneDisabled = isAllMuteEnabled
            isAllCameraDisabled = isAllVideoEnabled
            if (isEncryptEnabled) {
                password = etPassword.text.toString()
            }
            if (selectedAttendeeIds.isNotEmpty()) {
                scheduleAttendees = ArrayList(selectedAttendeeIds)
            }
        }

        roomStore.scheduleRoom(roomID, options, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("scheduleRoom success: roomID=$roomID")
                notifyScheduleSuccessAndFinish(roomID, options)
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("scheduleRoom failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun saveScheduledRoom() {
        val info = editingRoomInfo
        if (info == null) {
            return
        }
        if (info.roomStatus == RoomStatus.RUNNING) {
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_scheduled_room_already_started_cannot_modify),
                AtomicToast.Style.WARNING
            )
            return
        }
        val roomName = etRoomName.text.toString().trim()
        if (roomName.isEmpty()) {
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_conference_name_empty),
                AtomicToast.Style.WARNING
            )
            return
        }
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_start_time_earlier_than_current_time),
                AtomicToast.Style.WARNING
            )
            return
        }

        val newStart = calendar.timeInMillis
        val newEnd = newStart + selectedDuration * MILLIS_PER_MINUTE
        val flags = mutableListOf<ScheduleRoomOptions.ModifyFlag>()
        if (roomName != info.roomName) flags.add(ScheduleRoomOptions.ModifyFlag.ROOM_NAME)
        if (newStart != info.scheduledStartTime) flags.add(ScheduleRoomOptions.ModifyFlag.SCHEDULE_START_TIME)
        if (newEnd != info.scheduledEndTime) flags.add(ScheduleRoomOptions.ModifyFlag.SCHEDULE_END_TIME)

        // Attendee diff
        val originalAttendeeIds = info.scheduleAttendees.map { it.userID }.toSet()
        val newAttendeeIds = selectedAttendeeIds.toSet()
        val toAdd = (newAttendeeIds - originalAttendeeIds).toList()
        val toRemove = (originalAttendeeIds - newAttendeeIds).toList()

        if (flags.isEmpty() && toAdd.isEmpty() && toRemove.isEmpty()) {
            (context as? Activity)?.finish()
            return
        }

        submitBasicUpdate(flags, roomName, newStart, newEnd) {
            submitAddAttendees(toAdd) {
                submitRemoveAttendees(toRemove) {
                    AtomicToast.show(
                        context,
                        context.getString(R.string.roomkit_scheduled_room_modify_success),
                        AtomicToast.Style.INFO
                    )
                    (context as? Activity)?.finish()
                }
            }
        }
    }

    private fun submitBasicUpdate(
        flags: List<ScheduleRoomOptions.ModifyFlag>,
        roomName: String,
        newStart: Long,
        newEnd: Long,
        onNext: () -> Unit
    ) {
        if (flags.isEmpty()) {
            onNext()
            return
        }
        val options = ScheduleRoomOptions().apply {
            this.roomName = roomName
            scheduleStartTime = newStart
            scheduleEndTime = newEnd
        }
        roomStore.updateScheduledRoom(editRoomID, options, flags, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("updateScheduledRoom success: roomID=$editRoomID, flags=$flags")
                onNext()
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("updateScheduledRoom failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun submitAddAttendees(userIDs: List<String>, onNext: () -> Unit) {
        if (userIDs.isEmpty()) {
            onNext()
            return
        }
        roomStore.addScheduledAttendees(editRoomID, userIDs, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("addScheduledAttendees success: roomID=$editRoomID, size=${userIDs.size}")
                onNext()
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("addScheduledAttendees failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun submitRemoveAttendees(userIDs: List<String>, onNext: () -> Unit) {
        if (userIDs.isEmpty()) {
            onNext()
            return
        }
        roomStore.removeScheduledAttendees(editRoomID, userIDs, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("removeScheduledAttendees success: roomID=$editRoomID, size=${userIDs.size}")
                onNext()
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("removeScheduledAttendees failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun notifyScheduleSuccessAndFinish(roomID: String, options: ScheduleRoomOptions) {
        val activity = context as? Activity ?: return
        val data = Intent().apply {
            putExtra(EXTRA_SCHEDULED_ROOM_ID, roomID)
            putExtra(EXTRA_SCHEDULED_ROOM_NAME, options.roomName)
            putExtra(EXTRA_SCHEDULED_START_TIME, options.scheduleStartTime)
            putExtra(EXTRA_SCHEDULED_END_TIME, options.scheduleEndTime)
            putExtra(EXTRA_SCHEDULED_PASSWORD, options.password)
        }
        activity.setResult(Activity.RESULT_OK, data)
        activity.finish()
    }

    private fun generateRandomPassword(): String =
        (Random().nextInt(PASSWORD_MAX - PASSWORD_MIN + 1) + PASSWORD_MIN).toString()
}