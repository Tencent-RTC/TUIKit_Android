package com.trtc.uikit.livekit.pusher.living.cohost

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView

internal class PusherCoHostActionsView(context: Context) : BottomSheetView(context) {
    internal var onInviteMore: (() -> Unit)? = null
    internal var onEnd: (() -> Unit)? = null
    internal var onDidDismiss: (() -> Unit)? = null
    private val connectionLabel: AppCompatTextView
    private val endButton: AppCompatTextView
    private val inviteButton: View
    override val sheetHeight: Int
        get() = context.resources.getDimensionPixelSize(
            R.dimen.livekit_cohost_actions_height
        ) + bottomInset
    private var bottomInset = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_cohost_actions_view, contentView, true)
        connectionLabel = contentView.findViewById(R.id.livekit_cohost_connection)
        endButton = contentView.findViewById(R.id.livekit_cohost_end)
        inviteButton = contentView.findViewById(R.id.livekit_cohost_more)
    }

    override fun sheetWillPresent() {
        endButton.setOnClickListener { onEnd?.invoke() }
        inviteButton.setOnClickListener { onInviteMore?.invoke() }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            bottomInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
            updateSheetHeight(false)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(contentView)
    }

    fun bind(state: PusherCoHostStore.State) {
        connectionLabel.text = if (state.connected.size == 1) {
            context.getString(R.string.livekit_cohost_connected_with, state.connected.first().userName)
        } else context.getString(R.string.livekit_cohost_connected_count, state.connected.size + 1)
        endButton.setText(if (state.isExiting) R.string.livekit_cohost_processing else R.string.livekit_cohost_end)
        endButton.isEnabled = state.isConnected && !state.isExiting && state.pendingLiveIDs.isEmpty()
        inviteButton.isEnabled = state.isConnected && !state.isExiting
        endButton.alpha = if (endButton.isEnabled) 1f else .46f
        inviteButton.alpha = if (inviteButton.isEnabled) 1f else .46f
    }

    override fun sheetDidDismiss() {
        onDidDismiss?.invoke()
    }
}
