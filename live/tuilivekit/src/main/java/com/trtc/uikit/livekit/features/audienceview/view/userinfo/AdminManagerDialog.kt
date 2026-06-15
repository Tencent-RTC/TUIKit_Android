package com.trtc.uikit.livekit.features.audienceview.view.userinfo

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.ui.setDebounceClickListener
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog.TextColorPreset
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo

class AdminManagerDialog(
    context: Context,
    private val audienceStore: AudienceStore,
) : AtomicPopover(context) {

    private val logger = LiveKitLogger.getLiveStreamLogger("AdminManagerDialog")
    private var seatUserInfo: LiveUserInfo? = null
    private var confirmDialog: AtomicAlertDialog? = null

    private lateinit var imageHeadView: AtomicAvatar
    private lateinit var userIdText: TextView
    private lateinit var userNameText: TextView
    private lateinit var layoutKickOut: View
    private lateinit var layoutDismissMessage: View
    private lateinit var imageDismissMessage: ImageView
    private lateinit var textDismissMessage: TextView
    private var isMessageDisabled = false

    init {
        initView()
    }

    fun init(seatInfo: LiveUserInfo) {
        seatUserInfo = seatInfo
        updateView()
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
        val rootView = View.inflate(context, R.layout.livekit_audience_admin_manager_panel, null)
        setContent(rootView)
        bindViewId(rootView)
    }

    @SuppressLint("CutPasteId")
    private fun bindViewId(rootView: View) {
        userIdText = rootView.findViewById(R.id.user_id)
        userNameText = rootView.findViewById(R.id.user_name)
        imageHeadView = rootView.findViewById(R.id.iv_head)
        layoutKickOut = rootView.findViewById(R.id.layout_kick_out)

        layoutDismissMessage = rootView.findViewById(R.id.layout_dismiss_message)
        imageDismissMessage = rootView.findViewById(R.id.iv_disable_message)
        textDismissMessage = rootView.findViewById(R.id.tv_disable_message)

        layoutDismissMessage.setDebounceClickListener { clickDismissMessage() }
        layoutKickOut.setDebounceClickListener { clickKickOut() }
    }

    private fun clickDismissMessage() {
        seatUserInfo?.let {
            isMessageDisabled = !isMessageDisabled
            audienceStore.getLiveAudienceStore().disableSendMessage(
                it.userID, isMessageDisabled,
                object : CompletionHandler {
                    override fun onSuccess() {
                        imageDismissMessage.setImageResource(if (isMessageDisabled) R.drawable.livekit_ic_disable_message else R.drawable.livekit_ic_enable_message)
                        textDismissMessage.text =
                            if (isMessageDisabled) context.getString(R.string.common_enable_message) else context.getString(
                                R.string.common_disable_message
                            )
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                    }
                })
            dismiss()
        }
    }

    private fun clickKickOut() {
        if (confirmDialog == null) {
            confirmDialog = AtomicAlertDialog(context)
        }

        val name = if (TextUtils.isEmpty(seatUserInfo?.userName)) {
            seatUserInfo?.userID
        } else {
            seatUserInfo?.userName
        }

        confirmDialog?.init {
            title =
                context.getString(R.string.common_kick_user_confirm_message, name)
            confirmButton(context.getString(R.string.common_kick_out_of_room), onClick = {
                audienceStore.getLiveAudienceStore().kickUserOutOfRoom(seatUserInfo?.userID, object : CompletionHandler {
                    override fun onSuccess() {
                        logger.info("AdminManagerDialog kickUserOutOfRoom onSuccess")
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.onError(code)
                        logger.error("AdminManagerDialog kickUserOutOfRoom onFailure: $code, $desc")
                    }
                })
                dismiss()
            }, type = TextColorPreset.RED)
            cancelButton(context.getString(R.string.common_cancel), type = TextColorPreset.PRIMARY)
        }
        confirmDialog?.show()
    }

    private fun updateView() {
        if (seatUserInfo == null) {
            return
        }
        if (TextUtils.isEmpty(seatUserInfo!!.userID)) {
            return
        }
        val avatarUrl = seatUserInfo!!.avatarURL
        imageHeadView.setContent(AvatarContent.URL(avatarUrl, R.drawable.livekit_ic_avatar))
        userNameText.text = seatUserInfo!!.userName
        userIdText.text = context.getString(R.string.common_user_id, seatUserInfo!!.userID)

        isMessageDisabled = audienceStore.getLiveAudienceStore().liveAudienceState.messageBannedUserList.value.any {
            it.userID == seatUserInfo?.userID }
        imageDismissMessage.setImageResource(if (isMessageDisabled) R.drawable.livekit_ic_disable_message else R.drawable.livekit_ic_enable_message)
        textDismissMessage.text =
            if (isMessageDisabled) context.getString(R.string.common_enable_message) else context.getString(
                R.string.common_disable_message
            )
    }

    private fun addObserver() {
        audienceStore.getLiveListStore().addLiveListListener(liveLisListener)
        audienceStore.getLiveAudienceStore().addLiveAudienceListener(liveAudienceListener)
    }

    private fun removeObserver() {
        audienceStore.getLiveListStore().removeLiveListListener(liveLisListener)
        audienceStore.getLiveAudienceStore().removeLiveAudienceListener(liveAudienceListener)
    }

    private val liveLisListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            dismiss()
            confirmDialog?.dismiss()
        }
    }

    private val liveAudienceListener = object : LiveAudienceListener() {
        override fun onAudienceLeft(audience: LiveUserInfo) {
            if (this@AdminManagerDialog.seatUserInfo == null) {
                return
            }
            if (TextUtils.isEmpty(this@AdminManagerDialog.seatUserInfo!!.userID)) {
                return
            }
            if (audience.userID == this@AdminManagerDialog.seatUserInfo!!.userID) {
                dismiss()
                confirmDialog?.dismiss()
            }
        }

        override fun onAudienceMessageDisabled(audience: LiveUserInfo, isDisable: Boolean) {
            if (seatUserInfo?.userID != audience.userID) {
                return
            }
            isMessageDisabled = isDisable
            imageDismissMessage.setImageResource(if (isMessageDisabled) R.drawable.livekit_ic_disable_message else R.drawable.livekit_ic_enable_message)
            textDismissMessage.text =
                if (isMessageDisabled) context.getString(R.string.common_enable_message) else context.getString(
                    R.string.common_disable_message
                )
        }
    }
}
