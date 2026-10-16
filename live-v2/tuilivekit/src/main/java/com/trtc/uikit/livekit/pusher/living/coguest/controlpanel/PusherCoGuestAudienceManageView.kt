package com.trtc.uikit.livekit.pusher.living.coguest.controlpanel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestActionHandling
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestStore
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import java.lang.ref.WeakReference

internal class PusherCoGuestAudienceManageView(context: Context) : BottomSheetView(context) {
    var actionHandler: PusherCoGuestActionHandling?
        get() = actionHandlerReference?.get()
        set(value) {
            actionHandlerReference = value?.let(::WeakReference)
        }

    private val title: AtomicLabel
    private val microphone: View
    private val microphoneIcon: ImageView
    private val microphoneLabel: AtomicLabel
    private val disconnect: View

    private var selectedUserID: String? = null
    private var actionHandlerReference: WeakReference<PusherCoGuestActionHandling>? = null
    private var isAwaitingActionCompletion = false
    private var didObservePendingAction = false
    private var navigationBottom = 0

    override val sheetHeight: Int
        get() = maxOf(dp(MINIMUM_PANEL_HEIGHT_DP), dp(CONTENT_HEIGHT_DP) + navigationBottom)

    init {
        LayoutInflater.from(context).inflate(
            R.layout.livekit_pusher_coguest_audience_manage_view,
            contentView,
            true,
        )
        title = contentView.findViewById(R.id.livekit_pusher_coguest_audience_manage_title)
        microphone = contentView.findViewById(R.id.livekit_pusher_coguest_audience_manage_microphone)
        microphoneIcon = contentView.findViewById(R.id.livekit_pusher_coguest_audience_manage_microphone_icon)
        microphoneLabel = contentView.findViewById(R.id.livekit_pusher_coguest_audience_manage_microphone_label)
        disconnect = contentView.findViewById(R.id.livekit_pusher_coguest_audience_manage_disconnect)

        microphoneLabel.setTextColor(ContextCompat.getColor(context, R.color.livekit_coguest_manage_label))
        contentView.findViewById<AtomicLabel>(R.id.livekit_pusher_coguest_audience_manage_disconnect_label)
            .setTextColor(ContextCompat.getColor(context, R.color.livekit_coguest_manage_destructive))

        microphone.setDebounceClickListener {
            selectedUserID?.let {
                beginWaitingForAction()
                actionHandler?.toggleRemoteMicrophone(it)
                if (!didObservePendingAction) isAwaitingActionCompletion = false
            }
        }
        disconnect.setDebounceClickListener {
            selectedUserID?.let {
                beginWaitingForAction()
                actionHandler?.disconnect(it)
                if (!didObservePendingAction) isAwaitingActionCompletion = false
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            updateSheetHeight(animated = false)
            insets
        }
    }

    fun show(user: PusherCoGuestStore.User, isPending: Boolean) {
        selectedUserID = user.userID
        isAwaitingActionCompletion = false
        didObservePendingAction = false
        render(user, isPending)
        super.show()
    }

    fun bind(state: PusherCoGuestStore.State) {
        val userID = selectedUserID ?: return
        val user = state.connected.firstOrNull { it.userID == userID }
        if (user == null) {
            dismiss()
            return
        }
        val isPending = userID in state.pendingUserIDs
        if (isAwaitingActionCompletion) {
            didObservePendingAction = didObservePendingAction || isPending
            if (didObservePendingAction && !isPending) {
                dismiss()
                return
            }
        }
        render(user, isPending)
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    override fun sheetWillDismiss() {
        selectedUserID = null
        isAwaitingActionCompletion = false
        didObservePendingAction = false
    }

    private fun render(user: PusherCoGuestStore.User, isPending: Boolean) {
        title.text = context.getString(
            R.string.livekit_pusher_coguest_manage_user,
            user.userName,
        )
        val microphoneOn = user.allowOpenMicrophone && user.microphoneStatus == DeviceStatus.ON
        microphoneIcon.setImageResource(
            if (microphoneOn) R.drawable.livekit_pusher_coguest_action_mic
            else R.drawable.livekit_pusher_coguest_action_mic_off,
        )
        microphoneLabel.setText(
            if (microphoneOn) R.string.livekit_pusher_coguest_mute_user
            else R.string.livekit_pusher_coguest_unmute_user,
        )
        listOf(microphone, disconnect).forEach { action ->
            action.isEnabled = !isPending
            action.alpha = if (isPending) PENDING_ALPHA else 1f
        }
    }

    private fun beginWaitingForAction() {
        isAwaitingActionCompletion = true
        didObservePendingAction = false
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val MINIMUM_PANEL_HEIGHT_DP = 162
        const val CONTENT_HEIGHT_DP = 148
        const val PENDING_ALPHA = 0.46f
    }
}
