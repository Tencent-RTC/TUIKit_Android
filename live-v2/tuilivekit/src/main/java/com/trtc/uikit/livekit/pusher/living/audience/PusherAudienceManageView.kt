package com.trtc.uikit.livekit.pusher.living.audience

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsItemView

internal class PusherAudienceManageView(context: Context) : BottomSheetView(context) {
    var onToggleFollow: ((String) -> Unit)? = null
    var onToggleMessagePermission: ((String) -> Unit)? = null
    var onRemoveFromRoom: ((String) -> Unit)? = null

    private val followItem: PusherSettingsItemView
    private val muteItem: PusherSettingsItemView
    private val removeItem: PusherSettingsItemView
    private var selectedUserID: String? = null
    private var navigationBottom = 0

    override val sheetHeight: Int
        get() = dp(CONTENT_HEIGHT_DP) + navigationBottom

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_pusher_audience_manage_view, contentView, true)
        followItem = contentView.findViewById(R.id.livekit_pusher_audience_manage_follow)
        muteItem = contentView.findViewById(R.id.livekit_pusher_audience_manage_mute)
        removeItem = contentView.findViewById(R.id.livekit_pusher_audience_manage_remove)

        followItem.configure(
            R.string.livekit_pusher_audience_follow,
            R.drawable.livekit_pusher_audience_follow,
        )
        muteItem.configure(
            R.string.livekit_pusher_audience_mute,
            R.drawable.livekit_pusher_audience_mute,
        )
        removeItem.configure(
            R.string.livekit_pusher_audience_remove,
            R.drawable.livekit_pusher_audience_remove,
            iconWidthDp = 21f,
            iconHeightDp = 21f,
        )

        followItem.setDebounceClickListener { perform(onToggleFollow) }
        muteItem.setDebounceClickListener { perform(onToggleMessagePermission) }
        removeItem.setDebounceClickListener { perform(onRemoveFromRoom) }

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            updateSheetHeight(animated = false)
            insets
        }
    }

    fun show(user: PusherAudienceStore.User, state: PusherAudienceStore.State) {
        selectedUserID = user.userID
        render(user, state)
        super.show()
    }

    fun bind(state: PusherAudienceStore.State) {
        val userID = selectedUserID ?: return
        val user = state.users.firstOrNull { it.userID == userID }
        if (user == null) {
            if (isShowing) dismiss()
            return
        }
        render(user, state)
    }

    fun completeAction(result: PusherAudienceStore.ActionResult) {
        if (!result.succeeded || result.userID != selectedUserID) return
        when (result.action) {
            PusherAudienceStore.Action.MUTE,
            PusherAudienceStore.Action.UNMUTE,
            PusherAudienceStore.Action.REMOVE,
            -> dismiss()
            PusherAudienceStore.Action.FOLLOW,
            PusherAudienceStore.Action.UNFOLLOW,
            -> Unit
        }
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    override fun sheetWillDismiss() {
        selectedUserID = null
    }

    private fun render(user: PusherAudienceStore.User, state: PusherAudienceStore.State) {
        sheetTitle = user.userName.ifEmpty { user.userID }

        val followStatus = state.followStatus(user.userID)
        followItem.setIcon(
            if (followStatus == PusherAudienceStore.FollowStatus.FOLLOWING) {
                R.drawable.livekit_pusher_audience_followed
            } else {
                R.drawable.livekit_pusher_audience_follow
            },
        )
        followItem.setTitle(
            if (followStatus == PusherAudienceStore.FollowStatus.FOLLOWING) {
                R.string.livekit_pusher_audience_following
            } else {
                R.string.livekit_pusher_audience_follow
            },
        )

        val isMuted = user.userID in state.mutedUserIDs
        muteItem.setTitle(
            if (isMuted) {
                R.string.livekit_pusher_audience_unmute
            } else {
                R.string.livekit_pusher_audience_mute
            },
        )

        val pendingAction = state.pendingActionByUserID[user.userID]
        val isPending = pendingAction != null
        followItem.isEnabled = !isPending && followStatus != PusherAudienceStore.FollowStatus.UNKNOWN
        muteItem.isEnabled = !isPending
        removeItem.isEnabled = !isPending
        followItem.alpha = if (
            pendingAction == PusherAudienceStore.Action.FOLLOW ||
            pendingAction == PusherAudienceStore.Action.UNFOLLOW
        ) PENDING_ALPHA else 1f
        muteItem.alpha = if (
            pendingAction == PusherAudienceStore.Action.MUTE ||
            pendingAction == PusherAudienceStore.Action.UNMUTE
        ) PENDING_ALPHA else 1f
        removeItem.alpha = if (pendingAction == PusherAudienceStore.Action.REMOVE) PENDING_ALPHA else 1f
    }

    private fun perform(action: ((String) -> Unit)?) {
        selectedUserID?.let { action?.invoke(it) }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val CONTENT_HEIGHT_DP = 191
        const val PENDING_ALPHA = 0.46f
    }
}
