package com.trtc.uikit.roomkit.inroom.views

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.service.RoomForegroundService
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.RoomDevicePermission
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import com.trtc.uikit.roomkit.inroom.features.recording.cloudrecordingstatus.CloudRecordingStatus
import com.trtc.uikit.roomkit.inroom.features.roominfo.currentroominfo.RoomInfoView
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomView
import com.trtc.uikit.roomkit.inroom.features.screenshare.RoomScreenSharingPlaceholderView
import com.trtc.uikit.roomkit.inroom.views.roomevent.RoomEventHandler
import com.trtc.uikit.roomkit.inroom.views.widgets.RoomLoadingView
import com.trtc.uikit.roomkit.inroom.views.widgets.RoomPasswordDialog
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ListResultCompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.AudioRoute
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.CreateRoomOptions
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeetingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val topBar: RoomTopBar
    private val roomView: RoomView
    private val sharingPlaceholder: RoomScreenSharingPlaceholderView
    private val loadingView: RoomLoadingView

    private val roomStore = RoomStore.shared()
    private var roomID: String = ""
    private var connectConfig: ConnectConfig? = null
    private lateinit var eventHandler: RoomEventHandler

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_meeting_view, this, true)
        topBar = findViewById(R.id.top_bar)
        roomView = findViewById(R.id.room_view)
        loadingView = findViewById(R.id.entering_view)
        sharingPlaceholder = findViewById(R.id.screen_sharing_placeholder)

        findViewById<FrameLayout>(R.id.top_bar).applyTopWindowInsets()
    }

    fun init(roomID: String, behavior: RoomBehavior, config: ConnectConfig) {
        connectConfig = config
        this.roomID = roomID
        topBar.init(roomID)
        roomView.init(roomID)
        val currentRoomInfo = findViewById<RoomInfoView>(R.id.current_room_info)
        currentRoomInfo.init(roomID)
        findViewById<CloudRecordingStatus>(R.id.cloud_recording_status).init(roomID)
        sharingPlaceholder.init(roomID)
        findViewById<MeetingToolBar>(R.id.meeting_tool_bar).init(roomID)
        setupEventHandler(roomID)
        enterRoom(behavior)
    }

    override fun onDetachedFromWindow() {
        if (::eventHandler.isInitialized) eventHandler.stop()
        RoomForegroundService.stop(context)
        super.onDetachedFromWindow()
    }

    private fun setupEventHandler(roomID: String) {
        eventHandler = RoomEventHandler(context, roomID)
        eventHandler.start()
    }

    private fun enterRoom(behavior: RoomBehavior) {
        loadingView.setShown(true)
        when (behavior) {
            is RoomBehavior.CreateAndJoinRoom -> createRoom(behavior.options)
            RoomBehavior.Join -> joinRoom()
        }
    }

    private fun createRoom(options: CreateRoomOptions) {
        roomStore.createAndJoinRoom(
            roomID,
            RoomType.STANDARD,
            options,
            object : CompletionHandler {
                override fun onSuccess() {
                    handleEnterResult(success = true)
                }

                override fun onFailure(code: Int, desc: String) {
                    handleEnterResult(success = false, code, desc)
                }
            },
        )
    }

    private fun joinRoom(password: String = "") {
        roomStore.joinRoom(
            roomID,
            RoomType.STANDARD,
            password,
            object : CompletionHandler {
                override fun onSuccess() {
                    handleEnterResult(success = true)
                }

                override fun onFailure(code: Int, desc: String) {
                    when (val rule = ErrorLocalized.rule(code)) {
                        ErrorLocalized.Rule.PASSWORD_REQUIRED, ErrorLocalized.Rule.WRONG_PASSWORD -> {
                            loadingView.setShown(false)
                            showPasswordDialog(wrongPassword = rule == ErrorLocalized.Rule.WRONG_PASSWORD)
                        }

                        else -> handleEnterResult(success = false, code, desc)
                    }
                }
            },
        )
    }

    private fun showPasswordDialog(wrongPassword: Boolean) {
        RoomPasswordDialog(
            context = context,
            wrongPassword = wrongPassword,
            onSubmit = { password ->
                loadingView.setShown(true)
                joinRoom(password)
            },
            onCancel = { (context as? Activity)?.finish() },
        ).show()
    }

    private fun handleEnterResult(success: Boolean, code: Int = 0, desc: String = "") {
        if (success) {
            finishEnter()
        } else {
            loadingView.setShown(false)
            ErrorLocalized.showError(context, code, desc)
            leaveFromMeeting()
        }
    }

    private fun leaveFromMeeting() {
        if (::eventHandler.isInitialized) eventHandler.stop()
        (context as? Activity)?.finish()
    }

    private fun finishEnter() {
        loadingView.setShown(false)
        applyConnectConfig()
        RoomParticipantStore.create(roomID).getParticipantList(
            null,
            object : ListResultCompletionHandler<RoomParticipant> {
                override fun onSuccess(list: List<RoomParticipant>, nextCursor: String) {}

                override fun onFailure(code: Int, desc: String) {}
            },
        )
    }

    private fun applyConnectConfig() {
        val config = connectConfig ?: return
        DeviceStore.shared().setAudioRoute(
            if (config.autoEnableSpeaker) AudioRoute.SPEAKERPHONE else AudioRoute.EARPIECE,
        )
        CoroutineScope(Dispatchers.Main).launch {
            val deviceStore = DeviceStore.shared()
            if (config.autoEnableMicrophone && canOpenMicrophone()) {
                val granted = RoomDevicePermission.requestMicrophonePermission(context as Activity)
                if (granted) {
                    deviceStore.openLocalMicrophone(object : CompletionHandler {
                        override fun onSuccess() {
                            RoomForegroundService.start(context)
                            RoomParticipantStore.create(roomID).unmuteMicrophone(object : CompletionHandler {
                                override fun onSuccess() {}
                                override fun onFailure(code: Int, desc: String) {
                                    ErrorLocalized.showError(context, code, desc)
                                }
                            })
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    })
                } else {
                    RoomToast.show(context, context.getString(R.string.roomkit_device_mic_no_permission))
                }
            }
            if (config.autoEnableCamera && canOpenCamera()) {
                val granted = RoomDevicePermission.requestCameraPermission(context as Activity)
                if (granted) {
                    val isFront = deviceStore.deviceState.isFrontCamera.value
                    deviceStore.openLocalCamera(isFront, object : CompletionHandler {
                        override fun onSuccess() {
                            RoomForegroundService.start(context)
                        }

                        override fun onFailure(code: Int, desc: String) {
                            ErrorLocalized.showError(context, code, desc)
                        }
                    })
                } else {
                    RoomToast.show(context, context.getString(R.string.roomkit_device_camera_no_permission))
                }
            }
        }
    }

    private fun isLocalOwnerOrAdmin(): Boolean {
        val room = roomStore.state.currentRoom.value
        val localUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val isOwner = localUserID.isNotBlank() && localUserID == room?.roomOwner?.userID
        val isAdmin = RoomParticipantStore.create(roomID).state.localParticipant.value?.role == ParticipantRole.ADMIN
        return isOwner || isAdmin
    }

    private fun canOpenMicrophone(): Boolean =
        isLocalOwnerOrAdmin() || roomStore.state.currentRoom.value?.isAllMicrophoneDisabled != true

    private fun canOpenCamera(): Boolean =
        isLocalOwnerOrAdmin() || roomStore.state.currentRoom.value?.isAllCameraDisabled != true

    sealed class RoomBehavior {
        class CreateAndJoinRoom(val options: CreateRoomOptions) : RoomBehavior()
        object Join : RoomBehavior()
    }

    data class ConnectConfig(
        val autoEnableMicrophone: Boolean,
        val autoEnableCamera: Boolean,
        val autoEnableSpeaker: Boolean,
    )
}
