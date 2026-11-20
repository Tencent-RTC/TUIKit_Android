package com.trtc.uikit.livekit.voicelounge.living.coguest

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.living.seat.VoiceLoungeSeatStore
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.tint
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText

internal class VoiceLoungeCoGuestBannerView(
    context: Context,
    private val kind: Kind,
    private val store: VoiceLoungeSeatStore,
) : FrameLayout(context) {
    enum class Kind {
        INVITATION, REQUESTING, LEAVING,
    }

    private val hasCloseButton: Boolean = kind != Kind.REQUESTING

    private val barView = FrameLayout(context)
    private val iconView = ImageView(context)
    private val titleLabel = voiceLoungeText(context, LiveTheme.TextRole.BODY)
    private val actionButton = TextView(context)
    private val closeButton = ImageView(context)
    private val bag = VoiceLoungeBag()
    private val autoHideRunnable = Runnable { hide() }

    private val coGuest: VoiceLoungeCoGuestStore get() = store.coGuest

    init {
        addView(barView, frameParams(MATCH, MATCH, end = dp(CLOSE_SIZE + CLOSE_GAP)))
        addView(closeButton, frameParams(dp(CLOSE_SIZE), dp(CLOSE_SIZE), Gravity.CENTER_VERTICAL or Gravity.END))
        val iconSize = dp(if (kind == Kind.REQUESTING) 20 else 22)
        val actionWidth = dp(if (hasCloseButton) 84 else 68)
        val actionHeight = dp(if (hasCloseButton) 40 else 36)
        barView.addView(iconView, frameParams(iconSize, iconSize, Gravity.CENTER_VERTICAL, start = dp(18)))
        barView.addView(
            actionButton, frameParams(actionWidth, actionHeight, Gravity.CENTER_VERTICAL or Gravity.END, end = dp(8))
        )
        barView.addView(
            titleLabel,
            frameParams(
                WRAP, WRAP, Gravity.CENTER_VERTICAL, start = dp(18) + iconSize + dp(12),
                end = dp(8) + actionWidth + dp(10)
            ),
        )
        visibility = View.GONE
        barView.background = roundedDrawable(VoiceLoungeColor.floatingBar, dp(22).toFloat())
        barView.isClickable = true
        iconView.setImageResource(R.drawable.livekit_coguest_invitation)
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        titleLabel.singleLineEnd()
        titleLabel.text = context.getString(
            when (kind) {
                Kind.INVITATION -> R.string.livekit_voiceroom_comic_invite_bar_title
                Kind.REQUESTING -> R.string.livekit_voiceroom_comic_retract_title
                Kind.LEAVING -> R.string.livekit_voiceroom_comic_leave_title
            },
        )
        actionButton.text = context.getString(
            when (kind) {
                Kind.INVITATION -> R.string.livekit_voiceroom_comic_connect
                Kind.REQUESTING -> R.string.livekit_voiceroom_comic_retract
                Kind.LEAVING -> R.string.livekit_voiceroom_comic_leave_confirm
            },
        )
        actionButton.setTextColor(Color.WHITE)
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        actionButton.typeface = Typeface.DEFAULT_BOLD
        actionButton.gravity = Gravity.CENTER
        actionButton.includeFontPadding = false
        closeButton.setImageResource(R.drawable.livekit_living_close)
        closeButton.tint(VoiceLoungeColor.floatingBarIcon)
        closeButton.scaleType = ImageView.ScaleType.CENTER_INSIDE
        closeButton.background = ovalDrawable(VoiceLoungeColor.floatingBar)
        val pad = dp(9)
        closeButton.setPadding(pad, pad, pad, pad)
        closeButton.visibility = if (hasCloseButton) View.VISIBLE else View.GONE
        actionButton.setOnClickListener { tapAction() }
        closeButton.setOnClickListener { tapClose() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!bag.isEmpty) return
        when (kind) {
            Kind.INVITATION -> coGuest.invitationEvents.subscribe { handle(it) }.addTo(bag)
            Kind.REQUESTING -> store.cancelApplicationPrompts.subscribe { show() }.addTo(bag)
            Kind.LEAVING -> store.leavePrompts.subscribe { show() }.addTo(bag)
        }
        coGuest.changes.subscribe { syncWithStatus() }.addTo(bag)
    }

    private fun syncWithStatus() {
        val status = coGuest.guestStatus
        if (kind == Kind.REQUESTING && status == VoiceLoungeCoGuestStore.GuestStatus.APPLYING) {
            if (visibility != View.VISIBLE) show()
            return
        }
        if (shouldHide(status)) hide()
    }

    override fun onDetachedFromWindow() {
        bag.clear()
        removeCallbacks(autoHideRunnable)
        super.onDetachedFromWindow()
    }

    private fun tapAction() {
        hide()
        when (kind) {
            Kind.INVITATION -> coGuest.acceptInvitation()
            Kind.REQUESTING -> coGuest.cancelApplication()
            Kind.LEAVING -> coGuest.leaveSeat()
        }
    }

    private fun tapClose() {
        hide()
        if (kind == Kind.INVITATION) coGuest.rejectInvitation()
    }

    private fun shouldHide(status: VoiceLoungeCoGuestStore.GuestStatus): Boolean = when (kind) {
        Kind.INVITATION -> status == VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT
        Kind.REQUESTING -> status != VoiceLoungeCoGuestStore.GuestStatus.APPLYING
        Kind.LEAVING -> status != VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT
    }

    private fun handle(event: VoiceLoungeCoGuestStore.InvitationEvent) {
        when (event) {
            is VoiceLoungeCoGuestStore.InvitationEvent.Received -> show()
            is VoiceLoungeCoGuestStore.InvitationEvent.Cancelled -> {
                if (visibility != View.VISIBLE) return
                hide()
            }
        }
    }

    private fun show() {
        val skin = store.skin
        actionButton.background = roundedDrawable(skin.mid, (if (hasCloseButton) dp(20) else dp(18)).toFloat())
        iconView.tint(skin.light)
        closeButton.tint(skin.light)
        animate().cancel()
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(ANIMATION_MS).start()
        if (kind == Kind.REQUESTING) {
            removeCallbacks(autoHideRunnable)
            postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
        }
    }

    private fun hide() {
        removeCallbacks(autoHideRunnable)
        if (visibility != View.VISIBLE) return
        animate().cancel()
        animate().alpha(0f).setDuration(ANIMATION_MS).withEndAction { visibility = View.GONE }.start()
    }

    companion object {
        const val HEIGHT = 56
        private const val CLOSE_SIZE = 36
        private const val CLOSE_GAP = 8
        private const val ANIMATION_MS = 200L
        private const val AUTO_HIDE_DELAY_MS = 6_000L
    }
}
