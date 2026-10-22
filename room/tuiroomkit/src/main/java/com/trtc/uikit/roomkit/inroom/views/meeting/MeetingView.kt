package com.trtc.uikit.roomkit.inroom.views.meeting

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.service.RoomForegroundService
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.RoomAudioOutput
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import com.trtc.uikit.roomkit.base.util.dp
import kotlin.math.max
import com.trtc.uikit.roomkit.inroom.features.recording.cloudrecordingstatus.CloudRecordingStatus
import com.trtc.uikit.roomkit.inroom.features.roominfo.currentroominfo.RoomInfoView
import com.trtc.uikit.roomkit.inroom.features.roominfo.currentroominfo.RoomInfoSheet
import com.trtc.uikit.roomkit.inroom.features.roominfo.leaveroombutton.LeaveRoomButton
import com.trtc.uikit.roomkit.inroom.features.roomevents.RoomEventHandler
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomView
import com.trtc.uikit.roomkit.inroom.features.screenshare.RoomScreenShareController
import com.trtc.uikit.roomkit.inroom.features.screenshare.RoomScreenSharingPlaceholderView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.room.CreateRoomOptions
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MeetingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val leaveRoomButton: LeaveRoomButton
    private val roomView: RoomView
    private val roomInfoSheet: RoomPopup
    private val sharingPlaceholder: RoomScreenSharingPlaceholderView
    private val enteringView: RoomEnteringView

    private val roomStore = RoomStore.shared()
    private val deviceStore = DeviceStore.shared()
    private var roomID: String = ""
    private var connectConfig: ConnectConfig? = null
    private var roleJob: Job? = null
    private var localLeftJob: Job? = null
    private var screenShareJob: Job? = null
    private var hasSeenLocalInRoom = false
    private var wasSharing = false
    private lateinit var eventHandler: RoomEventHandler

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_meeting_view, this, true)
        leaveRoomButton = findViewById(R.id.leave_room_button)
        roomView = findViewById(R.id.room_view)
        enteringView = findViewById(R.id.entering_view)
        roomInfoSheet = RoomPopup(context)
        sharingPlaceholder = findViewById(R.id.screen_sharing_placeholder)

        leaveRoomButton.setOnLeaveClickListener { leaveRoom() }
        leaveRoomButton.setOnEndClickListener { endRoom() }
        sharingPlaceholder.onStopClick = { RoomScreenShareController.stop() }
        findViewById<FrameLayout>(R.id.top_bar).applyTopWindowInsets()
    }

    fun bind(roomID: String, behavior: RoomBehavior, config: ConnectConfig) {
        connectConfig = config
        this.roomID = roomID
        roomView.bind(roomID)
        val currentRoomInfo = findViewById<RoomInfoView>(R.id.current_room_info)
        currentRoomInfo.bind(roomID)
        findViewById<CloudRecordingStatus>(R.id.cloud_recording_status).bind(roomID)
        setupRoomInfoSheet(currentRoomInfo, roomID)
        findViewById<MeetingToolBar>(R.id.meeting_tool_bar).apply {
            bind(roomID)
            onScreenShare = { handleScreenShareTap() }
        }
        RoomScreenShareController.bind(roomID)
        observeScreenShare()
        observeRole(roomID)
        observeLocalLeft(roomID)
        setupEventHandler(roomID)
        enteringView.setShown(true)
        when (behavior) {
            is RoomBehavior.Create -> createRoom(roomID, behavior.options)
            RoomBehavior.Join -> joinRoom(roomID)
        }
    }

    private fun setupRoomInfoSheet(currentRoomInfo: RoomInfoView, roomID: String) {
        val infoSheet = RoomInfoSheet(context)
        infoSheet.bind(roomID)
        roomInfoSheet.setContentView(infoSheet, RoomPopup.ContentHeight.AUTO)
        currentRoomInfo.setOnInfoClickListener { roomInfoSheet.show() }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val row = findViewById<View>(R.id.top_bar_row)
        val network = findViewById<View>(R.id.local_network_info)
        val recording = findViewById<View>(R.id.cloud_recording_status)
        val leave = findViewById<View>(R.id.leave_room_button)

        var used = network.width + context.dp(NETWORK_GAP) + leave.width
        if (recording.visibility != GONE) used += recording.width + context.dp(RECORDING_GAP)
        val available = row.width - row.paddingStart - row.paddingEnd - used - context.dp(MIN_TAIL_GAP)
        findViewById<RoomInfoView>(R.id.current_room_info).availableWidth = max(available, 0)
    }

    override fun onDetachedFromWindow() {
        roleJob?.cancel()
        roleJob = null
        localLeftJob?.cancel()
        localLeftJob = null
        screenShareJob?.cancel()
        screenShareJob = null
        RoomScreenShareController.unbind()
        if (::eventHandler.isInitialized) eventHandler.stop()
        RoomForegroundService.stop(context)
        super.onDetachedFromWindow()
    }


    private fun observeScreenShare() {
        screenShareJob?.cancel()
        screenShareJob = CoroutineScope(Dispatchers.Main).launch {
            RoomScreenShareController.isSharing.collect { isSharing ->
                sharingPlaceholder.setSharing(isSharing)
                if (isSharing == wasSharing) return@collect
                wasSharing = isSharing
                val textRes = if (isSharing) {
                    R.string.roomkit_screen_share_started
                } else {
                    R.string.roomkit_screen_share_stopped
                }
                RoomToast.show(context, context.getString(textRes))
            }
        }
    }

    private fun handleScreenShareTap() {
        if (RoomScreenShareController.isSharing.value) {
            showStopScreenShareDialog()
            return
        }
        if (!RoomScreenShareController.sharerName.value.isNullOrBlank()) {
            RoomToast.show(context, context.getString(R.string.roomkit_screen_share_failure_remotemsharing))
            return
        }
        showStartScreenShareDialog()
    }

    private fun showStartScreenShareDialog() {
        RoomDialog.Builder(context)
            .title(context.getString(R.string.roomkit_screen_share_safety_title))
            .message(context.getString(R.string.roomkit_screen_share_safety_message))
            .closable(false)
            .cancelable(false)
            .secondaryAction(context.getString(R.string.roomkit_room_common_cancel)) {}
            .primaryAction(context.getString(R.string.roomkit_screen_share_continue)) {
                RoomScreenShareController.start()
            }
            .show()
    }

    private fun showStopScreenShareDialog() {
        RoomDialog.Builder(context)
            .title(context.getString(R.string.roomkit_screen_share_stop_title))
            .message(context.getString(R.string.roomkit_screen_share_stop_message))
            .secondaryAction(context.getString(R.string.roomkit_room_common_cancel)) {}
            .primaryAction(
                context.getString(R.string.roomkit_screen_share_stop_confirm),
                RoomDialog.Variant.DANGER,
            ) {
                RoomScreenShareController.stop()
            }
            .show()
    }

    private fun observeRole(roomID: String) {
        roleJob?.cancel()
        roleJob = CoroutineScope(Dispatchers.Main).launch {
            val participantState = RoomParticipantStore.create(roomID).state
            combine(participantState.localParticipant, roomStore.state.currentRoom) { local, room ->
                local to room
            }.collect { (local, room) ->
                if (room?.roomID != roomID) return@collect
                val isOwner = local?.role == ParticipantRole.OWNER
                leaveRoomButton.setMemberRole(!isOwner)
            }
        }
    }

    private fun observeLocalLeft(roomID: String) {
        localLeftJob?.cancel()
        localLeftJob = CoroutineScope(Dispatchers.Main).launch {
            RoomParticipantStore.create(roomID).state.localParticipant.collect { local ->
                when {
                    local != null && local.userID.isNotBlank() -> hasSeenLocalInRoom = true
                    hasSeenLocalInRoom -> {
                        hasSeenLocalInRoom = false
                        post { leaveFromMeeting() }
                    }
                }
            }
        }
    }

    private fun setupEventHandler(roomID: String) {
        eventHandler = RoomEventHandler(context, roomID)
        eventHandler.onLeave = { message ->
            message?.let { RoomToast.show(context, it) }
            postDelayed({ leaveFromMeeting() }, LEAVE_AFTER_TOAST_MS)
        }
        eventHandler.start()
    }

    private fun leaveFromMeeting() {
        if (::eventHandler.isInitialized) eventHandler.stop()
        (context as? Activity)?.finish()
    }

    private fun leaveRoom() {
        roomStore.leaveRoom(
            object : CompletionHandler {
                override fun onSuccess() {
                    post {
                        RoomToast.show(context, context.getString(R.string.roomkit_main_left))
                        (context as? Activity)?.finish()
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    post {
                        ErrorLocalized.showError(context, code, desc)
                        (context as? Activity)?.finish()
                    }
                }
            },
        )
    }

    private fun endRoom() {
        roomStore.endRoom(
            object : CompletionHandler {
                override fun onSuccess() {
                    post {
                        RoomToast.show(context, context.getString(R.string.roomkit_main_ended))
                        (context as? Activity)?.finish()
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    post {
                        ErrorLocalized.showError(context, code, desc)
                        (context as? Activity)?.finish()
                    }
                }
            },
        )
    }

    private fun createRoom(roomID: String, options: CreateRoomOptions) {
        roomStore.createAndJoinRoom(
            roomID,
            RoomType.STANDARD,
            options,
            object : CompletionHandler {
                override fun onSuccess() {
                    post {
                        enteringView.setShown(false)
                        applyConnectConfig()
                        roomView.fetchParticipants()
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    enteringView.setShown(false)
                    showError(code, desc)
                }
            },
        )
    }

    private fun joinRoom(roomID: String) {
        joinRoom(roomID, "")
    }

    private fun joinRoom(roomID: String, password: String) {
        roomStore.joinRoom(
            roomID,
            RoomType.STANDARD,
            password,
            object : CompletionHandler {
                override fun onSuccess() {
                    post {
                        enteringView.setShown(false)
                        applyConnectConfig()
                        roomView.fetchParticipants()
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    post {
                        enteringView.setShown(false)
                        when (code) {
                            REQUIRES_PASSWORD_CODE, WRONG_PASSWORD_CODE ->
                                showPasswordDialog(
                                    roomID,
                                    if (code == WRONG_PASSWORD_CODE) PasswordError.WRONG else null,
                                )
                            else -> showError(code, desc)
                        }
                    }
                }
            },
        )
    }

    private fun showPasswordDialog(roomID: String, error: PasswordError?) {
        val passwordInput = RoomPasswordInputView(context).apply {
            error?.let { showError(context.getString(it.messageRes)) }
        }

        var submitted = false
        val dialog = RoomDialog.builder(context)
            .title(context.getString(R.string.roomkit_room_entry_password_title))
            .contentView(passwordInput)
            .closable(false)
            .cancelable(true)
            .keepsOpenOnPrimary(true)
            .primaryAction(context.getString(R.string.roomkit_calendar_join)) {
                if (passwordInput.password.isEmpty()) {
                    passwordInput.showError(context.getString(PasswordError.EMPTY.messageRes))
                    return@primaryAction
                }
                submitted = true
                enteringView.setShown(true)
                joinRoom(roomID, passwordInput.password)
            }
            .secondaryAction(context.getString(R.string.roomkit_room_common_cancel))
            .show()
        dialog.setOnDismissListener {
            if (!submitted) (context as? Activity)?.finish()
        }
        passwordInput.startEditing()
    }

    private fun applyConnectConfig() {
        val config = connectConfig ?: return
        val room = RoomStore.shared().state.currentRoom.value
        val localUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val isOwner = localUserID.isNotBlank() && localUserID == room?.roomOwner?.userID
        val isAdmin = RoomParticipantStore.create(roomID).state.localParticipant.value?.role == ParticipantRole.ADMIN
        if (config.autoEnableMicrophone &&
            canOpenMicrophone(isOwner, isAdmin, room) &&
            deviceStore.deviceState.microphoneStatus.value != DeviceStatus.ON
        ) {
            RoomLocalMediaUtils.openMicrophone(context)
        }
        if (config.autoEnableCamera &&
            canOpenCamera(isOwner, isAdmin, room) &&
            deviceStore.deviceState.cameraStatus.value != DeviceStatus.ON
        ) {
            RoomLocalMediaUtils.openCamera(context)
        }
        RoomLocalMediaUtils.setAudioOutput(
            if (config.autoEnableSpeaker) RoomAudioOutput.SPEAKER else RoomAudioOutput.EARPIECE,
        )
    }

    private fun canOpenMicrophone(isOwner: Boolean, isAdmin: Boolean, room: RoomInfo?): Boolean {
        if (isOwner) return true
        if (isAdmin) return true
        return room?.isAllMicrophoneDisabled != true
    }

    private fun canOpenCamera(isOwner: Boolean, isAdmin: Boolean, room: RoomInfo?): Boolean {
        if (isOwner) return true
        if (room?.roomType == RoomType.STANDARD && isAdmin) return true
        return room?.isAllCameraDisabled != true
    }

    private fun showError(code: Int, desc: String) {
        post {
            ErrorLocalized.showError(context, code, desc)
            (context as? Activity)?.finish()
        }
    }

    private enum class PasswordError(val messageRes: Int) {
        EMPTY(R.string.roomkit_join_password_placeholder),

        WRONG(R.string.roomkit_room_entry_wrong_password),
    }

    private companion object {
        const val LEAVE_AFTER_TOAST_MS = 800L
        const val REQUIRES_PASSWORD_CODE = 100018
        const val WRONG_PASSWORD_CODE = 100019

        const val NETWORK_GAP = 4
        const val RECORDING_GAP = 2
        const val MIN_TAIL_GAP = 4
    }

    sealed class RoomBehavior {
        class Create(val options: CreateRoomOptions) : RoomBehavior()
        object Join : RoomBehavior()
    }

    data class ConnectConfig(
        val autoEnableMicrophone: Boolean,
        val autoEnableCamera: Boolean,
        val autoEnableSpeaker: Boolean,
    )
}
