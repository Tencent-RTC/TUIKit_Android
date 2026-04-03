package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Participant manager view for managing individual participant actions.
 * Provides controls for managing participant audio, video, role, and other properties.
 */
class ParticipantManagerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("ParticipantManagerView")

    private var subscribeJob: Job? = null

    private val tvUsername: TextView by lazy { findViewById(R.id.tv_username) }
    private val ivUserAvatar: ImageFilterView by lazy { findViewById(R.id.iv_avatar) }
    private val llMicrophone: LinearLayout by lazy { findViewById(R.id.ll_microphone) }
    private val ivMicrophoneIcon: ImageView by lazy { findViewById(R.id.iv_microphone_icon) }
    private val tvMicrophone: TextView by lazy { findViewById(R.id.tv_microphone) }
    private val llCamera: LinearLayout by lazy { findViewById(R.id.ll_camera) }
    private val ivCameraIcon: ImageView by lazy { findViewById(R.id.iv_camera_icon) }
    private val tvCamera: TextView by lazy { findViewById(R.id.tv_camera) }
    private val llTransferMaster: LinearLayout by lazy { findViewById(R.id.ll_transfer_master) }
    private val llSetManager: LinearLayout by lazy { findViewById(R.id.ll_set_manager) }
    private val tvSetManager: TextView by lazy { findViewById(R.id.tv_set_manager) }
    private val llBanChat: LinearLayout by lazy { findViewById(R.id.ll_ban_chat) }
    private val tvBanChat: TextView by lazy { findViewById(R.id.tv_ban_chat) }
    private val llRemove: LinearLayout by lazy { findViewById(R.id.ll_remove) }
    private val llSetAudience: LinearLayout by lazy { findViewById(R.id.ll_set_audience) }

    private var roomType = RoomType.STANDARD
    private var participant: RoomParticipant? = null
    private var localParticipant: RoomParticipant? = null
    private var participantStore: RoomParticipantStore? = null
    private var onActionListener: OnParticipantActionListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_participant_action, this)
    }

    fun init(roomID: String, roomType: RoomType, listener: OnParticipantActionListener) {
        this.roomType = roomType
        super.init(roomID)
        this.onActionListener = listener
        setupListeners()
    }

    fun setRoomParticipant(participant: RoomParticipant) {
        this.participant = participant
        bindData()
        updateActionVisibility()
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        val store = participantStore ?: return
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                store.state.localParticipant.collect { local ->
                    localParticipant = local
                    updateActionVisibility()
                }
            }

            launch {
                store.state.participantList.collect { participants ->
                    val currentParticipant = participant ?: return@collect
                    val updatedParticipant = participants.find { it.userID == currentParticipant.userID }
                    if (updatedParticipant == null) {
                        onActionListener?.onDismiss()
                        return@collect
                    }
                    if (updatedParticipant != currentParticipant) {
                        logger.info("Participant data updated for ${updatedParticipant.userID}")
                        participant = updatedParticipant
                        bindData()
                        updateActionVisibility()
                    }
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
    }

    private fun bindData() {
        val participant = participant ?: return

        tvUsername.text = participant.getDisplayName()

        if (participant.avatarURL.isEmpty()) {
            ivUserAvatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
        } else {
            ImageLoader.load(context, ivUserAvatar, participant.avatarURL, R.drawable.roomkit_ic_default_avatar)
        }

        when (participant.microphoneStatus) {
            DeviceStatus.ON -> {
                ivMicrophoneIcon.setImageResource(R.drawable.roomkit_ic_microphone_on)
                tvMicrophone.text = context.getString(R.string.roomkit_mute)
            }

            else -> {
                ivMicrophoneIcon.setImageResource(R.drawable.roomkit_ic_microphone_off)
                tvMicrophone.text = context.getString(R.string.roomkit_request_unmute_audio)
            }
        }

        when (participant.cameraStatus) {
            DeviceStatus.ON -> {
                ivCameraIcon.setImageResource(R.drawable.roomkit_ic_camera_on)
                tvCamera.text = context.getString(R.string.roomkit_stop_video)
            }

            else -> {
                ivCameraIcon.setImageResource(R.drawable.roomkit_ic_camera_off)
                tvCamera.text = context.getString(R.string.roomkit_request_start_video)
                if (roomType == RoomType.WEBINAR) {
                    llCamera.visibility = GONE
                } else {
                    llCamera.visibility = VISIBLE
                }
            }
        }

        when (participant.role) {
            ParticipantRole.ADMIN -> {
                tvSetManager.text = context.getString(R.string.roomkit_revoke_admin)
            }

            else -> {
                tvSetManager.text = context.getString(R.string.roomkit_set_admin)
            }
        }

        tvBanChat.text = if (participant.isMessageDisabled) {
            context.getString(R.string.roomkit_unmute_text_chat)
        } else {
            context.getString(R.string.roomkit_mute_text_chat)
        }
    }

    private fun updateActionVisibility() {
        val local = localParticipant ?: return
        val target = participant ?: return
        logger.info("Update action visibility - LocalRole: ${local.role}, TargetRole: ${target.role}")

        if (local.userID == target.userID) {
            showSelfActions()
            return
        }

        when (local.role) {
            ParticipantRole.OWNER -> showOwnerActions(target)
            ParticipantRole.ADMIN -> showAdminActions(target)
            else -> Unit
        }
    }

    private fun showSelfActions() {
        llMicrophone.visibility = GONE
        llCamera.visibility = GONE
        llTransferMaster.visibility = GONE
        llSetManager.visibility = GONE
        llRemove.visibility = GONE
    }

    private fun showOwnerActions(target: RoomParticipant) {
        llMicrophone.visibility = VISIBLE
        llTransferMaster.visibility = if (roomType == RoomType.WEBINAR) GONE else VISIBLE
        llSetManager.visibility = if (target.role != ParticipantRole.OWNER) VISIBLE else GONE
        llRemove.visibility = if (target.role != ParticipantRole.OWNER) VISIBLE else GONE
        llSetAudience.visibility = VISIBLE
        if (roomType == RoomType.WEBINAR && target.cameraStatus == DeviceStatus.OFF) {
            llCamera.visibility = GONE
        } else {
            llCamera.visibility = VISIBLE
        }
    }

    private fun showAdminActions(target: RoomParticipant) {
        val canManage = target.role == ParticipantRole.GENERAL_USER
        if (roomType == RoomType.WEBINAR) {
            llSetAudience.visibility = if (canManage) VISIBLE else GONE
            if (canManage && target.cameraStatus == DeviceStatus.ON) {
                llCamera.visibility = VISIBLE
            } else {
                llCamera.visibility = GONE
            }
        } else {
            llCamera.visibility = if (canManage) VISIBLE else GONE
            llSetAudience.visibility = GONE
        }
        llMicrophone.visibility = if (canManage) VISIBLE else GONE
        llTransferMaster.visibility = GONE
        llSetManager.visibility = GONE
        llRemove.visibility = if (canManage) VISIBLE else GONE
        llSetAudience.visibility = VISIBLE
    }

    private fun setupListeners() {
        val listener = onActionListener ?: return

        llMicrophone.setOnClickListener {
            participant?.let { participant ->
                logger.info("Microphone action clicked for ${participant.userID}")
                handleMuteAction(participant)
                listener.onDismiss()
            }
        }

        llCamera.setOnClickListener {
            participant?.let { participant ->
                logger.info("Camera action clicked for ${participant.userID}")
                handleCameraAction(participant)
                listener.onDismiss()
            }
        }

        llTransferMaster.setOnClickListener {
            participant?.let { participant ->
                logger.info("Transfer master action clicked for ${participant.userID}")
                showTransferOwnerConfirmDialog(participant)
                listener.onDismiss()
            }
        }

        llSetManager.setOnClickListener {
            participant?.let { participant ->
                logger.info("Set manager action clicked for ${participant.userID}")
                handleSetManagerAction(participant)
                listener.onDismiss()
            }
        }

        llBanChat.setOnClickListener {
            participant?.let { participant ->
                logger.info("Ban chat action clicked for ${participant.userID}")
                handleBanChatAction(participant)
                listener.onDismiss()
            }
        }

        llRemove.setOnClickListener {
            participant?.let { participant ->
                logger.info("Remove action clicked for ${participant.userID}")
                showKickParticipantConfirmDialog(participant)
                listener.onDismiss()
            }
        }

        llSetAudience.setOnClickListener {
            participant?.let { participant ->
                logger.info("setAudience action clicked for ${participant.userID}")
                handleDemoteParticipantToAudience(participant)
                listener.onDismiss()
            }
        }
    }

    private fun handleMuteAction(participant: RoomParticipant) {
        val store = participantStore ?: return
        when (participant.microphoneStatus) {
            DeviceStatus.ON -> {
                store.closeParticipantDevice(
                    participant.userID,
                    DeviceType.MICROPHONE,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.info("Close participant microphone success: ${participant.userID}")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("Close participant microphone failed: code=$code, desc=$desc")
                            ErrorLocalized.showError(context, code)
                        }
                    }
                )
            }

            else -> {
                store.inviteToOpenDevice(
                    participant.userID,
                    DeviceType.MICROPHONE,
                    30,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.info("Invite to open microphone success: ${participant.userID}")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("Invite to open microphone failed: code=$code, desc=$desc")
                            ErrorLocalized.showError(context, code)
                        }
                    }
                )
            }
        }
    }

    private fun handleCameraAction(participant: RoomParticipant) {
        val store = participantStore ?: return
        when (participant.cameraStatus) {
            DeviceStatus.ON -> {
                store.closeParticipantDevice(
                    participant.userID,
                    DeviceType.CAMERA,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.info("Close participant camera success: ${participant.userID}")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("Close participant camera failed: code=$code, desc=$desc")
                            ErrorLocalized.showError(context, code)
                        }
                    }
                )
            }

            else -> {
                store.inviteToOpenDevice(
                    participant.userID,
                    DeviceType.CAMERA,
                    30,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.info("Invite to open camera success: ${participant.userID}")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("Invite to open camera failed: code=$code, desc=$desc")
                            ErrorLocalized.showError(context, code)
                        }
                    }
                )
            }
        }
    }

    private fun handleSetManagerAction(participant: RoomParticipant) {
        val store = participantStore ?: return
        when (participant.role) {
            ParticipantRole.ADMIN -> {
                store.revokeAdmin(participant.userID, object : CompletionHandler {
                    override fun onSuccess() {
                        logger.info("Revoke admin success: ${participant.userID}")
                        val message =
                            context.getString(R.string.roomkit_toast_admin_revoked, participant.getDisplayName())
                        AtomicToast.show(context, message, Style.SUCCESS)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        logger.error("Revoke admin failed: code=$code, desc=$desc")
                        ErrorLocalized.showError(context, code)
                    }
                })
            }

            else -> {
                store.setAdmin(participant.userID, object : CompletionHandler {
                    override fun onSuccess() {
                        logger.info("Set admin success: ${participant.userID}")
                        val message = context.getString(R.string.roomkit_toast_admin_set, participant.getDisplayName())
                        AtomicToast.show(context, message, Style.SUCCESS)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        logger.error("Set admin failed: code=$code, desc=$desc")
                        ErrorLocalized.showError(context, code)
                    }
                })
            }
        }
    }

    private fun handleBanChatAction(participant: RoomParticipant) {
        val store = participantStore ?: return
        val disable = !participant.isMessageDisabled
        store.disableUserMessage(participant.userID, disable, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("disable participant message success: ${participant.userID}, disable=$disable")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("disable participant message failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun showTransferOwnerConfirmDialog(participant: RoomParticipant) {
        logger.info("Show transfer owner confirm dialog for ${participant.userID}")

        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_msg_transfer_owner_to, participant.getDisplayName())
            .setMessage(R.string.roomkit_msg_transfer_owner_tip)
            .setNegativeButton(android.R.string.cancel)
            .setPositiveButton(R.string.roomkit_confirm_transfer) {
                handleTransferOwner(participant)
            }
            .show()
    }

    private fun handleTransferOwner(participant: RoomParticipant) {
        val store = participantStore ?: return
        logger.info("Transfer owner to ${participant.userID}")
        store.transferOwner(participant.userID, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Transfer owner success: ${participant.userID}")
                val message = context.getString(R.string.roomkit_toast_owner_transferred, participant.getDisplayName())
                AtomicToast.show(context, message, Style.SUCCESS)
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Transfer owner failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun showKickParticipantConfirmDialog(participant: RoomParticipant) {
        logger.info("Show kick participant confirm dialog for ${participant.userID}")

        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_confirm_remove_member, participant.getDisplayName())
            .setNegativeButton(android.R.string.cancel)
            .setPositiveButton(android.R.string.ok) {
                handleKickParticipant(participant)
            }
            .show()
    }

    private fun handleKickParticipant(participant: RoomParticipant) {
        val store = participantStore ?: return
        logger.info("Kick participant ${participant.userID}")
        store.kickUser(participant.userID, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Kick participant success: ${participant.userID}")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Kick participant failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleDemoteParticipantToAudience(participant: RoomParticipant) {
        val store = participantStore ?: return
        logger.info("demoteParticipantToAudience audience ${participant.userID}")
        store.demoteParticipantToAudience(participant.userID, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("demoteParticipantToAudience success: ${participant.userID}")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("demoteParticipantToAudience failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    interface OnParticipantActionListener {
        fun onDismiss()
    }
}