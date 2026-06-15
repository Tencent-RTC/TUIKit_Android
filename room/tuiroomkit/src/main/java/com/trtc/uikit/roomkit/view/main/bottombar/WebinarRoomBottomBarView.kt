package com.trtc.uikit.roomkit.view.main.bottombar

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.event.RoomEventNotifier
import com.trtc.uikit.roomkit.base.event.RoomObserver
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.operator.DeviceOperator
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import com.trtc.uikit.roomkit.base.ui.RoomPopupDialog
import com.trtc.uikit.roomkit.view.main.HandsUpListView
import com.trtc.uikit.roomkit.view.main.RoomParticipantListView
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.DeviceRequestInfo
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantListener
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import io.trtc.tuikit.atomicxcore.api.room.RoomUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Bottom action bar for Webinar rooms: contains participants, raise hand, hand management,
 * microphone, camera, and screen share buttons. Camera and screen share are mutually exclusive;
 * a confirmation dialog is shown before enabling either.
 */
class WebinarRoomBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr), RoomObserver {

    private val logger = RoomKitLogger.getLogger("WebinarRoomBottomBarView")
    private val errorCodeRequestPending = 100100

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

    private val llHandsUp: LinearLayout by lazy { findViewById(R.id.ll_hands_up) }
    private val ivHandsUp: ImageView by lazy { findViewById(R.id.iv_hands_up) }
    private val tvHandsUp: TextView by lazy { findViewById(R.id.tv_hands_up) }

    private val llHandsUpManage: FrameLayout by lazy { findViewById(R.id.ll_hands_up_manage) }
    private val tvHandsUpManageBadge: TextView by lazy { findViewById(R.id.tv_hands_up_manage_badge) }

    private val llScreenShare: LinearLayout by lazy { findViewById(R.id.ll_screen_share) }
    private val ivScreenShare: ImageView by lazy { findViewById(R.id.iv_screen_share) }

    private var localUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID
    private var participantStore: RoomParticipantStore? = null
    private val roomStore = RoomStore.shared()

    private var roomParticipantListViewDialog: RoomPopupDialog? = null
    private var handsUpListViewDialog: RoomPopupDialog? = null
    private var currentRoomID: String? = null

    private val isHandsUpPending = MutableStateFlow(false)

    private val participantListener = object : RoomParticipantListener() {
        override fun onDeviceRequestApproved(request: DeviceRequestInfo, operator: RoomUser) {
            isHandsUpPending.value = false
        }

        override fun onDeviceRequestRejected(request: DeviceRequestInfo, operator: RoomUser) {
            isHandsUpPending.value = false
        }

        override fun onDeviceRequestTimeout(request: DeviceRequestInfo) {
            isHandsUpPending.value = false
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_bottom_bar_webinar, this)
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
        participantStore.addRoomParticipantListener(participantListener)
        RoomEventNotifier.registerObserver(this)

        subscribeJob?.cancel()
        subscribeJob = scope.launch {
            val isLocalInParticipantList = participantStore.state.participantList
                .map { list -> list.any { it.userID == localUserID } }
                .distinctUntilChanged()
                .shareIn(this, SharingStarted.WhileSubscribed(), replay = 1)

            val localParticipant = participantStore.state.localParticipant

            val localMicrophoneStatus = localParticipant
                .map { it?.microphoneStatus }
                .distinctUntilChanged()
                .shareIn(this, SharingStarted.WhileSubscribed(), replay = 1)

            val localCameraStatus = localParticipant
                .map { it?.cameraStatus }
                .distinctUntilChanged()
                .shareIn(this, SharingStarted.WhileSubscribed(), replay = 1)

            val localScreenShareStatus = localParticipant
                .map { it?.screenShareStatus ?: DeviceStatus.OFF }
                .distinctUntilChanged()
                .shareIn(this, SharingStarted.WhileSubscribed(), replay = 1)

            val localRole = localParticipant
                .map { it?.role }
                .distinctUntilChanged()
                .shareIn(this, SharingStarted.WhileSubscribed(), replay = 1)

            launch {
                roomStore.state.currentRoom
                    .map { room -> (room?.participantCount ?: 0) + (room?.audienceCount ?: 0) }
                    .distinctUntilChanged()
                    .collect { count -> updateParticipantCount(count) }
            }

            launch {
                combine(
                    isLocalInParticipantList,
                    localMicrophoneStatus,
                    localRole,
                    roomStore.state.currentRoom.map { it?.isAllMicrophoneDisabled ?: false }.distinctUntilChanged()
                ) { isLocalInList, micStatus, role, isAllMuted ->
                    updateMicrophoneStatus(isLocalInList, micStatus, role, isAllMuted)
                }.collect {}
            }

            launch {
                combine(
                    isLocalInParticipantList,
                    localCameraStatus
                ) { isLocalInList, camStatus ->
                    updateCameraStatus(isLocalInList, camStatus)
                }.collect {}
            }

            launch {
                combine(
                    isLocalInParticipantList,
                    localScreenShareStatus
                ) { isLocalInList, screenStatus ->
                    updateScreenShareStatus(isLocalInList, screenStatus)
                }.collect {}
            }

            launch {
                combine(
                    isLocalInParticipantList,
                    localRole,
                    isHandsUpPending
                ) { isLocalInList, role, pending ->
                    updateHandsUpButton(isLocalInList, role, pending)
                }.collect {}
            }

            launch {
                combine(
                    localRole,
                    participantStore.state.pendingDeviceApplications
                        .map { list -> list.count { it.device == DeviceType.MICROPHONE } }
                        .distinctUntilChanged()
                ) { role, pendingCount ->
                    updateHandsUpManageButton(role, pendingCount)
                }.collect {}
            }
        }
    }

    override fun removeObserver() {
        participantStore?.removeRoomParticipantListener(participantListener)
        RoomEventNotifier.unregisterObserver(this)

        subscribeJob?.cancel()
        subscribeJob = null
        scope.cancel()
        roomParticipantListViewDialog?.dismiss()
        roomParticipantListViewDialog = null
        handsUpListViewDialog?.dismiss()
        handsUpListViewDialog = null
    }

    private fun initView() {
        llParticipants.setOnClickListener { handleParticipantsClick() }
        llMicrophone.setOnClickListener { handleMicrophoneClick() }
        llCamera.setOnClickListener { handleCameraClick() }
        llScreenShare.setOnClickListener { handleScreenShareClick() }
        llHandsUp.setOnClickListener { handleHandsUpClick() }
        llHandsUpManage.setOnClickListener { handleHandsUpManageClick() }
    }

    private fun updateParticipantCount(count: Int) {
        logger.info("updateParticipantCount count:$count")
        if (count > 0) {
            tvParticipants.text = context.getString(R.string.roomkit_member_count, count.toString())
        }
    }

    private fun updateHandsUpManageButton(role: ParticipantRole?, pendingCount: Int) {
        logger.info("updateHandsUpManageButton role:$role pendingCount:$pendingCount")
        if (role == ParticipantRole.GENERAL_USER) {
            llHandsUpManage.visibility = GONE
            return
        }
        llHandsUpManage.visibility = VISIBLE
        if (pendingCount > 0) {
            tvHandsUpManageBadge.visibility = VISIBLE
            tvHandsUpManageBadge.text = if (pendingCount > 99) "99+" else pendingCount.toString()
        } else {
            tvHandsUpManageBadge.visibility = GONE
        }
    }

    private fun updateHandsUpButton(isLocalInParticipantList: Boolean, role: ParticipantRole?, isPending: Boolean) {
        logger.info("updateHandsUpButton isLocalInParticipantList:$isLocalInParticipantList role:$role isPending:$isPending")
        if (role == ParticipantRole.GENERAL_USER && !isLocalInParticipantList) {
            llHandsUp.visibility = VISIBLE
            if (isPending) {
                ivHandsUp.setImageResource(R.drawable.roomkit_ic_hads_down)
                tvHandsUp.text = context.getString(R.string.roomkit_hands_down)
            } else {
                ivHandsUp.setImageResource(R.drawable.roomkit_ic_hands_up)
                tvHandsUp.text = context.getString(R.string.roomkit_hands_up)
            }
        } else {
            llHandsUp.visibility = GONE
            isHandsUpPending.value = false
        }
    }

    private fun updateMicrophoneStatus(
        isLocalInParticipantList: Boolean,
        microphoneStatus: DeviceStatus?,
        role: ParticipantRole?,
        isAllMicrophoneDisabled: Boolean
    ) {
        logger.info("updateMicrophoneStatus isLocalInParticipantList:$isLocalInParticipantList microphoneStatus:$microphoneStatus role:$role isAllMicrophoneDisabled:$isAllMicrophoneDisabled")
        if (!isLocalInParticipantList) {
            llMicrophone.visibility = GONE
            return
        }
        llMicrophone.visibility = VISIBLE
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
        isLocalInParticipantList: Boolean,
        cameraStatus: DeviceStatus?
    ) {
        logger.info("updateCameraStatus isLocalInParticipantList:$isLocalInParticipantList cameraStatus:$cameraStatus")
        if (!isLocalInParticipantList) {
            llCamera.visibility = GONE
            return
        }
        llCamera.visibility = VISIBLE
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
        llCamera.alpha = 1.0f
    }

    private fun updateScreenShareStatus(
        isLocalInParticipantList: Boolean,
        screenStatus: DeviceStatus
    ) {
        logger.info("updateScreenShareStatus isLocalInParticipantList:$isLocalInParticipantList screenStatus:$screenStatus")
        if (!isLocalInParticipantList) {
            llScreenShare.visibility = GONE
            return
        }
        llScreenShare.visibility = VISIBLE
        when (screenStatus) {
            DeviceStatus.ON -> {
                ivScreenShare.setImageResource(R.drawable.roomkit_ic_sharing)
            }

            else -> {
                ivScreenShare.setImageResource(R.drawable.roomkit_ic_share)
            }
        }
        llScreenShare.alpha = 1.0f
    }

    private fun handleParticipantsClick() {
        logger.info("handleParticipantsClick")
        val roomID = currentRoomID ?: return
        if (roomParticipantListViewDialog == null) {
            val view = RoomParticipantListView(context).apply { init(roomID, RoomType.WEBINAR) }
            roomParticipantListViewDialog = RoomPopupDialog(context).apply { setView(view) }
        }
        roomParticipantListViewDialog?.show()
    }

    private fun handleHandsUpManageClick() {
        logger.info("handleHandsUpManageClick")
        val roomID = currentRoomID ?: return
        if (handsUpListViewDialog == null) {
            val view = HandsUpListView(context).apply { init(roomID) }
            handsUpListViewDialog = RoomPopupDialog(context).apply { setView(view) }
        }
        handsUpListViewDialog?.show()
    }

    private fun handleHandsUpClick() {
        logger.info("handleHandsUpClick")
        val participantStore = participantStore ?: return
        if (isHandsUpPending.value) {
            participantStore.cancelOpenDeviceRequest(DeviceType.MICROPHONE, null)
            isHandsUpPending.value = false
        } else {
            participantStore.requestToOpenDevice(
                device = DeviceType.MICROPHONE,
                timeout = 30,
                completion = object : CompletionHandler {
                    override fun onSuccess() {}

                    override fun onFailure(code: Int, desc: String) {
                        logger.error("hands up requestToOpenDevice failed: code=$code, desc=$desc")
                        if (code == errorCodeRequestPending) {
                            return
                        }
                        ErrorLocalized.showError(context, code)
                        isHandsUpPending.value = false
                    }
                }
            )
            isHandsUpPending.value = true
        }
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
        val participantStore = participantStore ?: return
        val currentStatus = participantStore.state.localParticipant.value?.cameraStatus
        logger.info("handleCameraClick currentStatus:${currentStatus} screenStatus:${participantStore.state.localParticipant.value?.screenShareStatus}")
        if (currentStatus == DeviceStatus.ON) {
            logger.info("handleCameraClick: camera is ON, closing camera")
            deviceOperator.closeCamera()
            return
        }
        if (participantStore.state.localParticipant.value?.screenShareStatus == DeviceStatus.ON) {
            logger.info("handleCameraClick: blocked by screen share")
            AtomicToast.show(context, context.getString(R.string.roomkit_camera_blocked_by_screen_share), Style.WARNING)
            return
        }
        if (participantStore.state.participantListWithVideo.value.none { it.userID != localUserID }) {
            logger.info("handleCameraClick: no other sharing, open camera directly")
            openLocalCamera()
            return
        }
        if (isSharingUserHigherRank()) {
            logger.info("handleCameraClick: sharing user has higher or equal rank, blocked")
            AtomicToast.show(context, context.getString(R.string.roomkit_video_share_occupied), Style.WARNING)
            return
        }
        showStartVideoConfirmDialog(
            titleRes = R.string.roomkit_start_video,
            messageRes = R.string.roomkit_video_start_message,
            onStart = { openLocalCamera() }
        )
    }

    private fun handleScreenShareClick() {
        val screenStatus = participantStore?.state?.localParticipant?.value?.screenShareStatus ?: DeviceStatus.OFF
        logger.info("handleScreenShareClick screenStatus:$screenStatus")
        if (screenStatus == DeviceStatus.ON) {
            logger.info("handleScreenShareClick: screen share is ON, show stop dialog")
            showStopScreenShareConfirmDialog()
            return
        }
        if (participantStore?.state?.localParticipant?.value?.cameraStatus == DeviceStatus.ON) {
            logger.info("handleScreenShareClick: blocked by camera")
            AtomicToast.show(context, context.getString(R.string.roomkit_screen_share_blocked_by_camera), Style.WARNING)
            return
        }
        if (participantStore?.state?.participantListWithVideo?.value?.none { it.userID != localUserID } != false) {
            logger.info("handleScreenShareClick: no other sharing, start screen share directly")
            requestScreenShareTip { deviceOperator.startScreenShare() }
            return
        }
        if (isSharingUserHigherRank()) {
            logger.info("handleScreenShareClick: sharing user has higher or equal rank, blocked")
            AtomicToast.show(context, context.getString(R.string.roomkit_screen_share_occupied), Style.WARNING)
            return
        }
        showStartVideoConfirmDialog(
            titleRes = R.string.roomkit_start_screen_share,
            messageRes = R.string.roomkit_screen_share_start_message,
            onStart = { requestScreenShareTip { deviceOperator.startScreenShare() } }
        )
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

    /**
     * Returns true if another user is sharing video and their role rank is higher than or equal to the local user.
     * Lower role value means higher rank: OWNER(0) > ADMIN(1) > GENERAL_USER(2)
     */
    private fun isSharingUserHigherRank(): Boolean {
        val store = participantStore ?: return false
        val localRole = store.state.localParticipant.value?.role ?: return false
        val sharingUser =
            store.state.participantListWithVideo.value.firstOrNull { it.userID != localUserID } ?: return false
        return sharingUser.role.value <= localRole.value
    }

    private fun showStartVideoConfirmDialog(titleRes: Int, messageRes: Int, onStart: () -> Unit) {
        logger.info("showStartVideoConfirmDialog titleRes:$titleRes")
        RoomAlertDialog.Builder(context)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setNegativeButton(R.string.roomkit_cancel)
            .setPositiveButton(R.string.roomkit_ok) { onStart() }
            .build()
            .show()
    }

    private fun openLocalCamera() {
        scope.launch {
            try {
                deviceOperator.openCamera()
            } catch (e: Exception) {
                logger.error("Failed to open camera: ${e.message}")
            }
        }
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

    override fun willLeaveRoom() {
        logger.info("willLeaveRoom: isHandsUpPending=${isHandsUpPending.value}")
        if (isHandsUpPending.value) {
            val participantStore = participantStore ?: return
            participantStore.cancelOpenDeviceRequest(DeviceType.MICROPHONE, null)
            isHandsUpPending.value = false
        }
    }
}