package com.trtc.uikit.roomkit.inroom.features.screenshare.screensharebutton

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.inroom.views.TitleDisplaying
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScreenShareButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), TitleDisplaying {

    var onRequestDismiss: (() -> Unit)? = null

    override var showsTitle: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            labelView.isVisible = value
        }

    private var roomID = ""
    private var isSharing = false
    private var isShareRestricted = false
    private var isAllShareDisabled = false
    private var isPrivileged = false
    private var subscribeJob: Job? = null

    private val iconView: AppCompatImageView
    private val labelView: AppCompatTextView

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_screen_share_button, this, true)
        iconView = findViewById(R.id.icon)
        labelView = findViewById(R.id.label)
        setOnClickListener { handleTap() }
    }

    fun bind(roomID: String) {
        this.roomID = roomID
        subscribe()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotEmpty() && subscribeJob == null) subscribe()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) PRESSED_SCALE else 1f
        animate().setDuration(PRESSED_DURATION.toLong()).scaleX(scale).scaleY(scale)
    }

    private fun subscribe() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                RoomParticipantStore.create(roomID).state.localParticipant.collect { local ->
                    val sharing = local?.screenShareStatus == DeviceStatus.ON
                    if (sharing != isSharing) {
                        isSharing = sharing
                        render(sharing)
                    }
                    val role = local?.role
                    isPrivileged = role == ParticipantRole.OWNER || role == ParticipantRole.ADMIN
                    updateRestriction()
                }
            }
            launch {
                RoomStore.shared().state.currentRoom.collect { room ->
                    isAllShareDisabled = room?.isAllScreenShareDisabled == true
                    updateRestriction()
                }
            }
        }
    }

    private fun updateRestriction() {
        val restricted = isAllShareDisabled && !isPrivileged
        if (restricted == isShareRestricted) return
        isShareRestricted = restricted
        alpha = if (restricted) RESTRICTED_ALPHA else 1f
    }

    private fun render(sharing: Boolean) {
        iconView.setImageResource(
            if (sharing) R.drawable.roomkit_icon_screen_share_stop else R.drawable.roomkit_icon_screen_share_start,
        )
        labelView.setText(
            if (sharing) R.string.roomkit_screen_share_stop else R.string.roomkit_more_screen_share
        )
    }

    private fun handleTap() {
        if (isSharing) {
            confirmStopSharing()
            return
        }
        if (isShareRestricted) {
            RoomToast.show(context, context.getString(R.string.roomkit_screen_share_owner_admin_only))
            return
        }
        val sharer = RoomParticipantStore.create(roomID).state.participantWithScreen.value
            ?.takeIf { it.userID.isNotBlank() }
            ?.getDisplayName()
        if (!sharer.isNullOrBlank()) {
            RoomToast.show(context, context.getString(R.string.roomkit_screen_share_failure_remotemsharing))
            return
        }
        requestStartSharing()
    }

    private fun requestStartSharing() {
        RoomDialog.Builder(context)
            .title(context.getString(R.string.roomkit_screen_share_safety_title))
            .message(context.getString(R.string.roomkit_screen_share_safety_message))
            .secondaryAction(context.getString(R.string.roomkit_room_common_cancel)) {}
            .primaryAction(context.getString(R.string.roomkit_screen_share_continue)) {
                onRequestDismiss?.invoke()
                DeviceStore.shared().startScreenShare()
            }
            .show()
    }

    private fun confirmStopSharing() {
        onRequestDismiss?.invoke()
        RoomDialog.Builder(context)
            .title(context.getString(R.string.roomkit_screen_share_stop_title))
            .message(context.getString(R.string.roomkit_screen_share_stop_message))
            .secondaryAction(context.getString(R.string.roomkit_room_common_cancel)) {}
            .primaryAction(
                context.getString(R.string.roomkit_screen_share_stop_confirm),
                RoomDialog.Variant.DANGER,
            ) {
                DeviceStore.shared().stopScreenShare()
            }
            .show()
    }

    private companion object {
        const val PRESSED_SCALE = 0.92f
        const val PRESSED_DURATION = 80
        const val RESTRICTED_ALPHA = 0.45f
    }
}
