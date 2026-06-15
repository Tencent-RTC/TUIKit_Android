package com.trtc.uikit.roomkit.view.main.bottombar

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.operator.DeviceOperator
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import com.trtc.uikit.roomkit.base.ui.RoomPopupDialog
import com.trtc.uikit.roomkit.view.main.RoomBottomBarViewListener
import com.trtc.uikit.roomkit.view.main.RoomParticipantListView
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StandardRoomBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("StandardRoomBottomBarView")

    var listener: RoomBottomBarViewListener? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private var subscribeJob: Job? = null
    private val deviceOperator by lazy { DeviceOperator(context) }

    private val llParticipants: LinearLayout by lazy { findViewById(R.id.ll_participants) }
    private val tvParticipants: TextView by lazy { findViewById(R.id.tv_participants) }

    private val llMicrophone: LinearLayout by lazy { findViewById(R.id.ll_microphone) }
    private val ivMicrophone: ImageView by lazy { findViewById(R.id.iv_microphone) }
    private val tvMicrophone: TextView by lazy { findViewById(R.id.tv_microphone) }

    private val llCamera: LinearLayout by lazy { findViewById(R.id.ll_camera) }
    private val ivCamera: ImageView by lazy { findViewById(R.id.iv_camera) }
    private val tvCamera: TextView by lazy { findViewById(R.id.tv_camera) }

    private val llScreenShare: LinearLayout by lazy { findViewById(R.id.ll_screen_share) }
    private val ivScreenShare: ImageView by lazy { findViewById(R.id.iv_screen_share) }

    private val llAiTool: LinearLayout by lazy { findViewById(R.id.ll_ai_tool) }

    private var participantStore: RoomParticipantStore? = null
    private val roomStore = RoomStore.shared()

    private var roomParticipantListViewDialog: RoomPopupDialog? = null
    private var currentRoomID: String? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_bottom_bar_standard, this)
    }

    public override fun init(roomID: String) {
        initView()
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        currentRoomID = roomID
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        val participantStore = participantStore ?: return

        subscribeJob?.cancel()
        subscribeJob = scope.launch {
            launch {
                roomStore.state.currentRoom
                    .map { room -> room?.participantCount ?: 0 }
                    .distinctUntilChanged()
                    .collect { count -> updateParticipantCount(count) }
            }

            launch {
                combine(
                    participantStore.state.localParticipant.map { it?.microphoneStatus to it?.role }
                        .distinctUntilChanged(),
                    roomStore.state.currentRoom.map { it?.isAllMicrophoneDisabled ?: false }.distinctUntilChanged()
                ) { (micStatus, role), isAllMuted ->
                    updateMicrophoneStatus(micStatus, role, isAllMuted)
                }.collect {}
            }

            launch {
                combine(
                    participantStore.state.localParticipant.map { it?.cameraStatus to it?.role }.distinctUntilChanged(),
                    roomStore.state.currentRoom.map { it?.isAllCameraDisabled ?: false }.distinctUntilChanged()
                ) { (camStatus, role), isAllDisabled ->
                    updateCameraStatus(camStatus, role, isAllDisabled)
                }.collect {}
            }

            launch {
                combine(
                    participantStore.state.localParticipant.map { it?.screenShareStatus ?: DeviceStatus.OFF }.distinctUntilChanged(),
                    participantStore.state.localParticipant.map { it?.role ?: ParticipantRole.GENERAL_USER }.distinctUntilChanged(),
                    roomStore.state.currentRoom.map { it?.isAllScreenShareDisabled ?: false }.distinctUntilChanged()
                ) { screenStatus, userRole, isAllScreenShareDisabled ->
                    Triple(screenStatus, userRole, isAllScreenShareDisabled)
                }.collect { (screenStatus, userRole, isAllScreenShareDisabled) ->
                    updateScreenShareStatus(screenStatus, userRole, isAllScreenShareDisabled)
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
        subscribeJob = null
        scope.cancel()
        roomParticipantListViewDialog?.dismiss()
        roomParticipantListViewDialog = null
    }

    private fun initView() {
        llParticipants.setOnClickListener { handleParticipantsClick() }
        llMicrophone.setOnClickListener { handleMicrophoneClick() }
        llCamera.setOnClickListener { handleCameraClick() }
        llScreenShare.setOnClickListener { handleScreenShareClick() }
        llAiTool.setOnClickListener { handleAiToolClick() }
    }

    private fun updateParticipantCount(count: Int) {
        logger.info("updateParticipantCount count:$count")
        if (count > 0) {
            tvParticipants.text = context.getString(R.string.roomkit_member_count, count.toString())
        }
    }

    private fun updateMicrophoneStatus(
        microphoneStatus: DeviceStatus?,
        role: ParticipantRole?,
        isAllMicrophoneDisabled: Boolean
    ) {
        logger.info("updateMicrophoneStatus microphoneStatus:$microphoneStatus role:$role isAllMicrophoneDisabled:$isAllMicrophoneDisabled")
        when (microphoneStatus) {
            DeviceStatus.ON -> {
                ivMicrophone.setImageResource(R.drawable.roomkit_ic_microphone_on)
                tvMicrophone.text = context.getString(R.string.roomkit_mute)
            }

            else -> {
                ivMicrophone.setImageResource(R.drawable.roomkit_ic_microphone_off)
                tvMicrophone.text = context.getString(R.string.roomkit_unmute)
            }
        }
        val isButtonDisabled = microphoneStatus == DeviceStatus.OFF && isAllMicrophoneDisabled &&
                role == ParticipantRole.GENERAL_USER
        llMicrophone.alpha = if (isButtonDisabled) 0.5f else 1.0f
    }

    private fun updateCameraStatus(
        cameraStatus: DeviceStatus?,
        role: ParticipantRole?,
        isAllCameraDisabled: Boolean
    ) {
        logger.info("updateCameraStatus cameraStatus:$cameraStatus role:$role isAllCameraDisabled:$isAllCameraDisabled")
        when (cameraStatus) {
            DeviceStatus.ON -> {
                ivCamera.setImageResource(R.drawable.roomkit_ic_camera_on)
                tvCamera.text = context.getString(R.string.roomkit_stop_video)
            }

            else -> {
                ivCamera.setImageResource(R.drawable.roomkit_ic_camera_off)
                tvCamera.text = context.getString(R.string.roomkit_start_video)
            }
        }
        val isButtonDisabled = cameraStatus == DeviceStatus.OFF && isAllCameraDisabled &&
                role == ParticipantRole.GENERAL_USER
        llCamera.alpha = if (isButtonDisabled) 0.5f else 1.0f
    }

    private fun updateScreenShareStatus(
        screenStatus: DeviceStatus,
        userRole: ParticipantRole,
        isAllScreenShareDisabled: Boolean
    ) {
        logger.info("updateScreenShareStatus screenStatus:$screenStatus, userRole:$userRole, isAllScreenShareDisabled:$isAllScreenShareDisabled")
        llScreenShare.visibility = VISIBLE

        if (isAllScreenShareDisabled && userRole == ParticipantRole.GENERAL_USER) {
            llScreenShare.alpha = 0.5f
            return
        }

        llScreenShare.alpha = 1.0f
        when (screenStatus) {
            DeviceStatus.ON -> {
                ivScreenShare.setImageResource(R.drawable.roomkit_ic_sharing)
            }

            else -> {
                ivScreenShare.setImageResource(R.drawable.roomkit_ic_share)
            }
        }
    }

    private fun handleParticipantsClick() {
        logger.info("handleParticipantsClick")
        val roomID = currentRoomID ?: return
        if (roomParticipantListViewDialog == null) {
            val view = RoomParticipantListView(context).apply { init(roomID, RoomType.STANDARD) }
            roomParticipantListViewDialog = RoomPopupDialog(context).apply { setView(view) }
        }
        roomParticipantListViewDialog?.show()
    }

    private fun handleMicrophoneClick() {
        logger.info("handleMicrophoneClick")
        val participantStore = participantStore ?: return
        val currentStatus = participantStore.state.localParticipant.value?.microphoneStatus
        if (currentStatus == DeviceStatus.ON) {
            deviceOperator.muteMicrophone(participantStore)
        } else {
            val isAllMuted = roomStore.state.currentRoom.value?.isAllMicrophoneDisabled ?: false
            val role = participantStore.state.localParticipant.value?.role
            if (isAllMuted && role == ParticipantRole.GENERAL_USER) {
                logger.info("handleMicrophoneClick: All participants are muted, cannot unmute")
                AtomicToast.show(
                    context,
                    context.getString(R.string.roomkit_tip_all_muted_cannot_unmute),
                    Style.WARNING
                )
                return
            }
            scope.launch {
                try {
                    deviceOperator.unmuteMicrophone(participantStore)
                } catch (e: Exception) {
                    logger.error("Failed to open microphone: ${e.message}")
                }
            }
        }
    }

    private fun handleCameraClick() {
        logger.info("handleCameraClick")
        val participantStore = participantStore ?: return
        val currentStatus = participantStore.state.localParticipant.value?.cameraStatus
        if (currentStatus == DeviceStatus.ON) {
            deviceOperator.closeCamera()
        } else {
            scope.launch {
                try {
                    deviceOperator.openCamera()
                } catch (e: Exception) {
                    logger.error("Failed to open camera: ${e.message}")
                }
            }
        }
    }

    private fun handleScreenShareClick() {
        val participantStore = participantStore ?: return
        val localParticipant = participantStore.state.localParticipant.value ?: return
        val isAllScreenShareDisabled = roomStore.state.currentRoom.value?.isAllScreenShareDisabled ?: false
        if (isAllScreenShareDisabled && localParticipant.role == ParticipantRole.GENERAL_USER) {
            logger.info("handleScreenShareClick: screen share is disabled for general users")
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_not_allowed_to_screen_share),
                Style.WARNING
            )
            return
        }

        val screenStatus = localParticipant.screenShareStatus ?: DeviceStatus.OFF
        logger.info("handleScreenShareClick screenStatus:$screenStatus")
        if (screenStatus == DeviceStatus.ON) {
            showStopScreenShareConfirmDialog()
            return
        }
        val localUserID = participantStore.state.localParticipant.value?.userID
        val sharingUser = participantStore.state.participantWithScreen.value
        if (sharingUser != null && sharingUser.userID != localUserID) {
            logger.info("handleScreenShareClick: another user(${sharingUser.userID}) is sharing the screen")
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_another_is_sharing_the_screen),
                Style.WARNING
            )
            return
        }
        requestScreenShareTip { deviceOperator.startScreenShare() }
    }

    /**
     * Check if screen share is banned and show appropriate dialog.
     * If banned, show forbidden dialog; otherwise, show share tip dialog.
     */
    private fun requestScreenShareTip(onApproved: () -> Unit) {
        val sharedPreferences = context.getSharedPreferences("rtcube_module_permission", Context.MODE_PRIVATE)
        val bannedFeatureIds = sharedPreferences.getStringSet("bannedFeatureIds", emptySet()) ?: emptySet()

        if ("screen_share" in bannedFeatureIds) {
            showScreenShareForbiddenDialog()
        } else {
            showScreenShareTipDialog(onApproved)
        }
    }

    private fun showScreenShareForbiddenDialog() {
        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_tips)
            .setMessage(R.string.roomkit_unable_to_shared_screen)
            .setNegativeButton(R.string.roomkit_cancel)
            .setPositiveButton(R.string.roomkit_contact_us) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, "https://im.cloud.tencent.com/s/cWSPGIIM62CC/cFUPGIIM62CF".toUri())
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .show()
    }

    private fun showScreenShareTipDialog(onApproved: () -> Unit) {
        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_privacy_screen_share_tip_title)
            .setMessage(R.string.roomkit_privacy_screen_share_tip_content)
            .setNegativeButton(R.string.roomkit_cancel)
            .setPositiveButton(R.string.roomkit_privacy_screen_share_tip_continue) {
                onApproved()
            }
            .show()
    }

    private fun showStopScreenShareConfirmDialog() {
        logger.info("showStopScreenShareConfirmDialog")
        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_stop_screen_share)
            .setMessage(R.string.roomkit_stop_screen_share_confirm)
            .setNegativeButton(R.string.roomkit_cancel)
            .setPositiveButton(R.string.roomkit_ok) {
                deviceOperator.stopScreenShare()
            }
            .show()
    }

    private fun handleAiToolClick() {
        logger.info("handleAiToolClick")
        listener?.onAIToolsButtonTapped()
    }
}
