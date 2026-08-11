package com.trtc.uikit.roomkit.view.main.bottombar

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.operator.DeviceOperator
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.ExpandableBarAnimator
import com.trtc.uikit.roomkit.base.ui.RoomActionSheetDialog
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import com.trtc.uikit.roomkit.base.ui.RoomPopupDialog
import com.trtc.uikit.roomkit.base.ui.contactpicker.ContactPickerDialog
import com.trtc.uikit.roomkit.view.main.RoomBottomBarViewListener
import com.trtc.uikit.roomkit.view.main.RoomParticipantListView
import com.trtc.uikit.roomkit.view.main.ShareRoomView
import com.trtc.uikit.roomkit.view.chat.ChatButton
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.CallUserToRoomCompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RecordingStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomCallResult
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

    private val llInvite: LinearLayout by lazy { findViewById(R.id.ll_invite) }

    private val chatButton: ChatButton by lazy { findViewById(R.id.chat_button) }

    private val llAiTool: LinearLayout by lazy { findViewById(R.id.ll_ai_tool) }

    private val llRecording: LinearLayout by lazy { findViewById(R.id.ll_recording) }
    private val ivRecording: ImageView by lazy { findViewById(R.id.iv_recording) }
    private val tvRecording: TextView by lazy { findViewById(R.id.tv_recording) }

    private val llRoot: View by lazy { findViewById(R.id.ll_root) }
    private val llMainBar: LinearLayout by lazy { findViewById(R.id.ll_main_bar) }
    private val llExtensionBar: LinearLayout by lazy { findViewById(R.id.ll_extension_bar) }
    private val llExpand: LinearLayout by lazy { findViewById(R.id.ll_expand) }
    private val ivExpand: ImageView by lazy { findViewById(R.id.iv_expand) }
    private val tvExpand: TextView by lazy { findViewById(R.id.tv_expand) }
    private var barAnimator: ExpandableBarAnimator? = null

    private var isRecording = false
    private var recordingConfirmDialog: RoomAlertDialog? = null

    private var participantStore: RoomParticipantStore? = null
    private val roomStore = RoomStore.shared()

    private var roomParticipantListViewDialog: RoomPopupDialog? = null
    private var shareRoomDialog: RoomPopupDialog? = null
    private var inviteDialog: RoomActionSheetDialog? = null
    private var currentRoomID: String? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_bottom_bar_standard, this)
    }

    public override fun init(roomID: String) {
        initView()
        super.init(roomID)
        chatButton.init(roomID)
        chatButton.onClick = { handleChatClick() }
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
                    participantStore.state.localParticipant.map { it?.screenShareStatus ?: DeviceStatus.OFF }
                        .distinctUntilChanged(),
                    participantStore.state.localParticipant.map { it?.role ?: ParticipantRole.GENERAL_USER }
                        .distinctUntilChanged(),
                    roomStore.state.currentRoom.map { it?.isAllScreenShareDisabled ?: false }.distinctUntilChanged()
                ) { screenStatus, userRole, isAllScreenShareDisabled ->
                    Triple(screenStatus, userRole, isAllScreenShareDisabled)
                }.collect { (screenStatus, userRole, isAllScreenShareDisabled) ->
                    updateScreenShareStatus(screenStatus, userRole, isAllScreenShareDisabled)
                }
            }
            launch {
                combine(
                    participantStore.state.localParticipant.map { it?.role ?: ParticipantRole.GENERAL_USER }
                        .distinctUntilChanged(),
                    roomStore.state.currentRoom.map { it?.recordingInfo?.status ?: RecordingStatus.NONE }
                        .distinctUntilChanged()
                ) { role, status ->
                    role to status
                }.collect { (role, status) ->
                    updateRecordingStatus(role, status)
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
        shareRoomDialog?.dismiss()
        shareRoomDialog = null
        inviteDialog?.dismiss()
        inviteDialog = null
        barAnimator?.cancel()
        barAnimator = null
        dismissRecordingConfirmDialog()
    }

    private fun initView() {
        llParticipants.setOnClickListener { handleParticipantsClick() }
        llMicrophone.setOnClickListener { handleMicrophoneClick() }
        llCamera.setOnClickListener { handleCameraClick() }
        llScreenShare.setOnClickListener { handleScreenShareClick() }
        llInvite.setOnClickListener { handleInviteClick() }
        llAiTool.setOnClickListener { handleAiToolClick() }
        llRecording.setOnClickListener { handleRecordingClick() }
        llExpand.setOnClickListener { handleExpandClick() }
        llRoot.background?.alpha = 0
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

    private fun handleInviteClick() {
        logger.info("handleInviteClick")
        val roomID = currentRoomID ?: return
        val textColor = ContextCompat.getColor(context, R.color.roomkit_color_text_light)
        if (inviteDialog == null) {
            inviteDialog = RoomActionSheetDialog.Builder(context)
                .addAction(
                    R.string.roomkit_add_user,
                    iconRes = R.drawable.roomkit_ic_add_user,
                    iconSizeDp = 18f,
                    textColor = textColor,
                    textSizeSp = 14f
                ) { showAddUserDialog(roomID) }
                .addAction(
                    R.string.roomkit_share_room,
                    iconRes = R.drawable.roomkit_ic_share_room,
                    iconSizeDp = 18f,
                    textColor = textColor,
                    textSizeSp = 14f
                ) { showShareRoomDialog(roomID) }.build()
        }
        inviteDialog?.show()
    }

    private fun showAddUserDialog(roomID: String) {
        ContactPickerDialog(
            context = context,
            initialSelectedIds = emptyList()
        ) { selectedIds ->
            if (selectedIds.isEmpty()) return@ContactPickerDialog
            logger.info("Inviting users: count=${selectedIds.size}")
            RoomStore.shared().callUserToRoom(
                roomID,
                selectedIds,
                60,
                "",
                object : CallUserToRoomCompletionHandler {
                    override fun onSuccess(result: Map<String, RoomCallResult>) {
                        logger.info("callUserToRoom success: $result")
                        AtomicToast.show(context, context.getString(R.string.roomkit_invite_success), Style.SUCCESS)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        logger.error("callUserToRoom failed: code=$code, desc=$desc")
                        ErrorLocalized.showError(context, code)
                    }
                }
            )
        }.show()
    }

    private fun showShareRoomDialog(roomID: String) {
        if (shareRoomDialog == null) {
            val view = ShareRoomView(context).apply { init(roomID) }
            shareRoomDialog = RoomPopupDialog(context).apply { setView(view) }
        }
        shareRoomDialog?.show()
    }

    private fun handleChatClick() {
        logger.info("handleChatClick")
        chatButton.clearUnreadCount()
        listener?.onChatButtonTapped()
    }

    private fun handleExpandClick() {
        getOrCreateBarAnimator().toggle()
    }

    private fun getOrCreateBarAnimator(): ExpandableBarAnimator {
        return barAnimator ?: ExpandableBarAnimator(
            rootView = llRoot,
            mainBar = llMainBar,
            extensionBar = llExtensionBar,
        ).apply {
            onToggleStart = { expand ->
                llExpand.isClickable = false
                ivExpand.setImageResource(if (expand) R.drawable.roomkit_ic_close else R.drawable.roomkit_ic_expand)
                tvExpand.text =
                    context.getString(if (expand) R.string.roomkit_item_close else R.string.roomkit_item_expand)
            }
            onToggleEnd = { expand ->
                llExpand.isClickable = true
            }
            barAnimator = this
        }
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
                    val intent =
                        Intent(Intent.ACTION_VIEW, "https://im.cloud.tencent.com/s/cWSPGIIM62CC/cFUPGIIM62CF".toUri())
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

    private fun updateRecordingStatus(role: ParticipantRole, status: RecordingStatus) {
        val wasRecording = isRecording
        isRecording = status == RecordingStatus.RECORDING
        val canManage = role == ParticipantRole.OWNER || role == ParticipantRole.ADMIN
        llRecording.visibility = if (canManage) VISIBLE else GONE
        if (isRecording) {
            ivRecording.setImageResource(R.drawable.roomkit_ic_recording_on)
            tvRecording.text = context.getString(R.string.roomkit_cloud_record_recording)
        } else {
            ivRecording.setImageResource(R.drawable.roomkit_ic_recording)
            tvRecording.text = context.getString(R.string.roomkit_cloud_record)
        }
        if (!canManage || wasRecording != isRecording) {
            dismissRecordingConfirmDialog()
        }
    }

    private fun dismissRecordingConfirmDialog() {
        recordingConfirmDialog?.takeIf { it.isShowing }?.dismiss()
        recordingConfirmDialog = null
    }

    private fun handleRecordingClick() {
        if (isRecording) showStopRecordingConfirmDialog() else showStartRecordingConfirmDialog()
    }

    private fun showStartRecordingConfirmDialog() {
        dismissRecordingConfirmDialog()
        recordingConfirmDialog = RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_cloud_record_start_title)
            .setMessage(R.string.roomkit_cloud_record_start_tips)
            .setNegativeButton(R.string.roomkit_cancel)
            .setPositiveButton(R.string.roomkit_cloud_record_start_confirm) { startRecording() }
            .show()
    }

    private fun showStopRecordingConfirmDialog() {
        dismissRecordingConfirmDialog()
        recordingConfirmDialog = RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_cloud_record_stop_title)
            .setMessage(R.string.roomkit_cloud_record_stop_tips)
            .setWarning(true)
            .setNegativeButton(R.string.roomkit_cancel)
            .setPositiveButton(R.string.roomkit_cloud_record_stop) { stopRecording() }
            .show()
    }

    private fun startRecording() {
        roomStore.startRecording(completion = object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                logger.error("startRecording failed:code=$code,desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun stopRecording() {
        roomStore.stopRecording(object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                logger.error("stopRecording failed:code=$code,desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }
}
