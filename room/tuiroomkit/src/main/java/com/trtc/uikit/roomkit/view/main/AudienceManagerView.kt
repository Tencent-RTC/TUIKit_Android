package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudienceManagerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("AudienceManagerView")

    private var subscribeJob: Job? = null

    private val tvUsername: TextView by lazy { findViewById(R.id.tv_username) }
    private val ivUserAvatar: ImageFilterView by lazy { findViewById(R.id.iv_avatar) }
    private val llRemove: LinearLayout by lazy { findViewById(R.id.ll_remove) }
    private val llSetParticipant: LinearLayout by lazy { findViewById(R.id.ll_set_participant) }
    private val llSetManager: LinearLayout by lazy { findViewById(R.id.ll_set_manager) }
    private val tvSetManager: TextView by lazy { findViewById(R.id.tv_set_manager) }

    private var audience: RoomUser? = null
    private var localParticipant: RoomParticipant? = null
    private var participantStore: RoomParticipantStore? = null
    private var onActionListener: OnAudienceActionListener? = null
    private val adminList = HashSet<String>()

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_audience_action, this)
    }

    fun init(roomID: String, listener: OnAudienceActionListener) {
        super.init(roomID)
        this.onActionListener = listener
        setupListeners()
    }

    fun setAudience(audience: RoomUser) {
        this.audience = audience
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
                store.state.adminList.collect { admins ->
                    val adminIds = admins.map { it.userID }.toSet()
                    adminList.clear()
                    adminList.addAll(adminIds)
                    logger.info("admins changed: $adminList")
                    updateSetManagerText()
                    updateActionVisibility()
                }
            }

            launch {
                store.state.audienceList.collect { audiences ->
                    val currentAudience = audience ?: return@collect
                    val updatedAudience = audiences.find { it.userID == currentAudience.userID }
                    if (updatedAudience == null) {
                        onActionListener?.onDismiss()
                        return@collect
                    }
                    if (updatedAudience != currentAudience) {
                        logger.info("audience data updated for ${updatedAudience.userID}")
                        audience = updatedAudience
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
        val audience = audience ?: return
        tvUsername.text = audience.getDisplayName()
        if (audience.avatarURL.isEmpty()) {
            ivUserAvatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
        } else {
            ImageLoader.load(context, ivUserAvatar, audience.avatarURL, R.drawable.roomkit_ic_default_avatar)
        }
        updateSetManagerText()
    }

    private fun updateSetManagerText() {
        val audience = audience ?: return
        tvSetManager.text = if (adminList.contains(audience.userID)) {
            context.getString(R.string.roomkit_revoke_admin)
        } else {
            context.getString(R.string.roomkit_set_admin)
        }
    }

    private fun updateActionVisibility() {
        val local = localParticipant ?: return
        val target = audience ?: return
        val isLocalOwner = local.role == ParticipantRole.OWNER
        val isLocalAdmin = adminList.contains(local.userID)
        val isTargetAdmin = adminList.contains(target.userID)
        llSetManager.visibility = if (isLocalOwner) VISIBLE else GONE
        val canManage = isLocalOwner || (isLocalAdmin && !isTargetAdmin)
        llRemove.visibility = if (canManage) VISIBLE else GONE
    }

    private fun setupListeners() {
        val listener = onActionListener ?: return

        llRemove.setOnClickListener {
            audience?.let { audience ->
                logger.info("Remove action clicked for ${audience.userID}")
                showKickParticipantConfirmDialog(audience)
                listener.onDismiss()
            }
        }

        llSetParticipant.setOnClickListener {
            audience?.let { audience ->
                logger.info("setParticipant action clicked for ${audience.userID}")
                handlePromoteAudienceToParticipant(audience)
                listener.onDismiss()
            }
        }

        llSetManager.setOnClickListener {
            audience?.let { audience ->
                logger.info("setManager action clicked for ${audience.userID}")
                handleSetManagerAction(audience)
                listener.onDismiss()
            }
        }
    }

    private fun showKickParticipantConfirmDialog(audience: RoomUser) {
        logger.info("Show kick audience confirm dialog for ${audience.userID}")

        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_confirm_remove_member, audience.getDisplayName())
            .setNegativeButton(android.R.string.cancel)
            .setPositiveButton(android.R.string.ok) {
                handleKickAudience(audience)
            }
            .show()
    }

    private fun handleKickAudience(audience: RoomUser) {
        val store = participantStore ?: return
        logger.info("Kick audience ${audience.userID}")
        store.kickUser(audience.userID, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Kick audience success: ${audience.userID}")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Kick audience failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleSetManagerAction(audience: RoomUser) {
        val store = participantStore ?: return
        if (adminList.contains(audience.userID)) {
            store.revokeAdmin(audience.userID, object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("Revoke admin success: ${audience.userID}")
                    val message = context.getString(R.string.roomkit_toast_admin_revoked, audience.getDisplayName())
                    AtomicToast.show(context, message, Style.SUCCESS)
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("Revoke admin failed: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                }
            })
        } else {
            store.setAdmin(audience.userID, object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("Set admin success: ${audience.userID}")
                    val message = context.getString(R.string.roomkit_toast_admin_set, audience.getDisplayName())
                    AtomicToast.show(context, message, Style.SUCCESS)
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("Set admin failed: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                }
            })
        }
    }

    private fun handlePromoteAudienceToParticipant(audience: RoomUser) {
        val store = participantStore ?: return
        logger.info("promoteAudienceToParticipant audience ${audience.userID}")
        store.promoteAudienceToParticipant(audience.userID, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("promoteAudienceToParticipant success: ${audience.userID}")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("promoteAudienceToParticipant failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    interface OnAudienceActionListener {
        fun onDismiss()
    }
}