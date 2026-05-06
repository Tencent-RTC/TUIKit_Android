package com.trtc.uikit.livekit.features.anchorview.view.usermanage

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ui.setDebounceClickListener
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog.TextColorPreset
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UserManagerDialog(
    private val context: Context,
    private val anchorStore: AnchorStore,
    private val userInfo: LiveUserInfo,
) : AtomicPopover(context) {

    private lateinit var imageHeadView: AtomicAvatar
    private lateinit var userIdText: TextView
    private lateinit var userNameText: TextView
    private lateinit var ivDisableMessage: ImageView
    private lateinit var tvDisableMessage: TextView
    private lateinit var textUnfollow: TextView
    private lateinit var imageFollowIcon: ImageView
    private var isMessageDisabled = false
    private var confirmDialog: AtomicAlertDialog? = null
    private var subscribeStateJob: Job? = null

    private val liveAudienceListener = object : LiveAudienceListener() {
        override fun onAudienceLeft(audience: LiveUserInfo) {
            if (TextUtils.isEmpty(userInfo.userID) || TextUtils.isEmpty(audience.userID)
            ) {
                return
            }
            if (userInfo.userID == audience.userID) {
                dismiss()
                confirmDialog?.dismiss()
            }
        }

        override fun onAudienceMessageDisabled(audience: LiveUserInfo, isDisable: Boolean) {
            if (userInfo.userID != audience.userID) {
                return
            }
            if (isDisable) {
                ivDisableMessage.setImageResource(R.drawable.livekit_ic_disable_message)
                tvDisableMessage.setText(R.string.common_enable_message)
            } else {
                ivDisableMessage.setImageResource(R.drawable.livekit_ic_enable_message)
                tvDisableMessage.setText(R.string.common_disable_message)
            }
        }
    }

    init {
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun initView() {
        val rootView = View.inflate(context, R.layout.livekit_user_manager, null)
        setContent(rootView)
        bindViewId(rootView)
        initFollowButtonView(rootView)

        isMessageDisabled = LiveAudienceStore.create(anchorStore.getState().roomId)
            .liveAudienceState.messageBannedUserList.value.find { it.userID == userInfo.userID } != null
        if (isMessageDisabled) {
            ivDisableMessage.setImageResource(R.drawable.livekit_ic_disable_message)
            tvDisableMessage.setText(R.string.common_enable_message)
        } else {
            ivDisableMessage.setImageResource(R.drawable.livekit_ic_enable_message)
            tvDisableMessage.setText(R.string.common_disable_message)
        }
        userIdText.text = context.getString(R.string.common_user_id, userInfo.userID)

        val name = if (TextUtils.isEmpty(userInfo.userName)) {
            userInfo.userID
        } else {
            userInfo.userName
        }
        userNameText.text = name

        imageHeadView.setContent(
            AvatarContent.URL(
                userInfo.avatarURL,
                R.drawable.livekit_ic_avatar
            )
        )
    }

    private fun bindViewId(rootView: View) {
        userIdText = rootView.findViewById(R.id.user_id)
        userNameText = rootView.findViewById(R.id.user_name)
        imageHeadView = rootView.findViewById(R.id.iv_head)
        ivDisableMessage = rootView.findViewById(R.id.iv_disable_message)
        tvDisableMessage = rootView.findViewById(R.id.tv_disable_message)
        textUnfollow = rootView.findViewById(R.id.tv_unfollow)
        imageFollowIcon = rootView.findViewById(R.id.iv_follow)

        rootView.findViewById<View>(R.id.disable_message_container)
            .setDebounceClickListener { onDisableMessageButtonClicked() }
        rootView.findViewById<View>(R.id.kick_out_room_container)
            .setDebounceClickListener { onKickUserButtonClicked() }
    }

    private fun initFollowButtonView(rootView: View) {
        rootView.findViewById<View>(R.id.fl_follow_panel).setDebounceClickListener {
            val followingUsers = anchorStore.getUserState().followingUserList.value
            if (followingUsers.contains(userInfo.userID)) {
                anchorStore.getUserStore().unfollowUser(userInfo.userID)
            } else {
                anchorStore.getUserStore().followUser(userInfo.userID)
            }
        }
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                onFollowingUserChanged()
            }
        }
        anchorStore.getLiveAudienceStore().addLiveAudienceListener(liveAudienceListener)
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
        anchorStore.getLiveAudienceStore().removeLiveAudienceListener(liveAudienceListener)
    }

    private suspend fun onFollowingUserChanged() {
        anchorStore.getUserState().followingUserList.collect { followUsers ->
            if (followUsers.contains(userInfo.userID)) {
                textUnfollow.visibility = View.GONE
                imageFollowIcon.visibility = View.VISIBLE
            } else {
                imageFollowIcon.visibility = View.GONE
                textUnfollow.visibility = View.VISIBLE
            }
        }
    }

    private fun onDisableMessageButtonClicked() {
        isMessageDisabled = !isMessageDisabled
        anchorStore.getUserStore().disableSendingMessageByAdmin(
            userInfo.userID,
            isMessageDisabled
        )
        if (isMessageDisabled) {
            ivDisableMessage.setImageResource(R.drawable.livekit_ic_disable_message)
            tvDisableMessage.setText(R.string.common_enable_message)
        } else {
            ivDisableMessage.setImageResource(R.drawable.livekit_ic_enable_message)
            tvDisableMessage.setText(R.string.common_disable_message)
        }
    }

    private fun onKickUserButtonClicked() {
        if (confirmDialog == null) {
            confirmDialog = AtomicAlertDialog(context)
        }

        val name = if (TextUtils.isEmpty(userInfo.userName)) {
            userInfo.userID
        } else {
            userInfo.userName
        }

        confirmDialog?.init {
            title =
                context.getString(R.string.common_kick_user_confirm_message, name)
            confirmButton(context.getString(R.string.common_kick_out_of_room), onClick = {
                anchorStore.getUserStore().kickRemoteUserOutOfRoom(userInfo.userID)
                dismiss()
            }, type = TextColorPreset.RED)
            cancelButton(context.getString(R.string.common_cancel), type = TextColorPreset.PRIMARY)
        }
        confirmDialog?.show()
    }
}