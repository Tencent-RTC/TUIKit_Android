package com.trtc.uikit.livekit.pusher.living.cohost

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import kotlin.math.ceil
import kotlin.math.min

internal class PusherCoHostIncomingInviteView(context: Context) : BottomSheetView(context) {
    internal var onAccept: (() -> Unit)? = null
    internal var onReject: (() -> Unit)? = null
    private var state = PusherCoHostStore.State()
    private val title: AppCompatTextView
    private val name: AppCompatTextView
    private val avatar: AppCompatImageView
    private val accept: AppCompatTextView
    private val reject: AppCompatTextView
    private var avatarURL: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var bottomInset = 0
    private val tick = object : Runnable {
        override fun run() {
            if (isShowing) {
                updateCountdown()
                handler.postDelayed(this, 1000)
            }
        }
    }
    override val sheetHeight: Int
        get() = min(
            dp(286) + bottomInset, (context.resources.displayMetrics.heightPixels - dp(40)).coerceAtLeast(0)
        )

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_cohost_incoming_view, contentView, true)
        title = contentView.findViewById(R.id.livekit_cohost_incoming_title)
        name = contentView.findViewById(R.id.livekit_cohost_incoming_name)
        avatar = contentView.findViewById(R.id.livekit_cohost_incoming_avatar)
        accept = contentView.findViewById(R.id.livekit_cohost_accept)
        reject = contentView.findViewById(R.id.livekit_cohost_reject)
        accept.setText(R.string.livekit_cohost_accept)
        isDismissibleByTappingOutside = false
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    fun bind(state: PusherCoHostStore.State) {
        this.state = state
        val user = state.applicant ?: return
        title.setText(if (user.isMutual) R.string.livekit_cohost_incoming_mutual else R.string.livekit_cohost_incoming)
        name.text = user.userName
        if (avatarURL != user.avatarURL) {
            avatarURL = user.avatarURL
            ImageLoader.loadAvatar(avatar, user.avatarURL.trim(), R.drawable.livekit_livelist_default_avatar)
        }
        updateCountdown()
    }

    private fun updateCountdown() {
        val remaining =
            ceil(((state.applicantDeadline ?: 0) - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(0)
        val busy = state.applicant?.let { state.isBusy(it.liveID) } ?: true
        reject.text = if (busy) context.getString(R.string.livekit_cohost_processing)
        else context.getString(R.string.livekit_cohost_reject_countdown, remaining)
        listOf(reject, accept).forEach {
            it.isEnabled = !busy && remaining > 0
            it.alpha = if (it.isEnabled) 1f else .46f
        }
    }

    override fun sheetWillPresent() {
        accept.setOnClickListener { onAccept?.invoke() }
        reject.setOnClickListener { onReject?.invoke() }
        handler.removeCallbacks(tick)
        handler.post(tick)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            contentView.setPadding(0, 0, 0, bottomInset)
            updateSheetHeight(false)
            insets
        }
        ViewCompat.requestApplyInsets(contentView)
    }

    override fun sheetWillDismiss() {
        handler.removeCallbacks(tick)
        Glide.with(context).clear(avatar)
        avatarURL = null
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
