package com.trtc.uikit.livekit.voicelounge.living.seat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveSwitch
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.blackAlpha
import com.trtc.uikit.livekit.voicelounge.clipOval
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestStore
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeErrorText
import com.trtc.uikit.livekit.voicelounge.loadUrl
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.voiceLoungeAppContext
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class VoiceLoungeSeatInvitePanel(
    context: Context,
    private val liveID: String,
    private val coGuest: VoiceLoungeCoGuestStore,
) : BottomSheetView(context) {
    private val profileCard = FrameLayout(context)
    private val profileIconBackground = FrameLayout(context)
    private val profileMicIcon = ImageView(context)
    private val profileLockIcon = SeatLockIconView(context)
    private val profileTitle = voiceLoungeText(context, LiveTheme.TextRole.ACTION)
    private val profileMeta = voiceLoungeText(context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.TERTIARY)
    private val lockCard = FrameLayout(context)
    private val lockLabel = voiceLoungeText(context, LiveTheme.TextRole.BODY, LiveTheme.TextTone.SECONDARY)
    private val lockSwitch = LiveSwitch(context)
    private val card = FrameLayout(context)
    private val cardTitle = voiceLoungeText(context, LiveTheme.TextRole.ACTION)
    private val divider = View(context)
    private val scrollView = ScrollView(context)
    private val listView = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val emptyLabel = voiceLoungeText(
        context, LiveTheme.TextRole.BODY, LiveTheme.TextTone.TERTIARY, Gravity.CENTER_HORIZONTAL,
    )

    private var seatIndex = -1
    private var audienceList: List<LiveUserInfo> = emptyList()
    private var seatList: List<SeatInfo> = emptyList()
    private var pendingLock: Boolean? = null

    private var tappedLocked: Boolean = false
    private var skin = VoiceLoungeSkin.defaultSkin
    private var scope: CoroutineScope? = null
    private val bag = VoiceLoungeBag()
    private val logger = LiveKitLog.getVoiceRoomLogger("VoiceLoungeSeatInvitePanel")

    override val sheetHeight: Int
        get() {
            if (!isLocked) return context.dp(InviteStyle.UNLOCKED_HEIGHT)
            val content =
                InviteStyle.CONTENT_TOP + InviteStyle.PROFILE_HEIGHT + InviteStyle.CARD_GAP + InviteStyle.LOCK_HEIGHT + InviteStyle.LOCKED_BOTTOM_PADDING
            return context.dp(content) + navigationBottom()
        }

    init {
        setupViews()
        setupStyles()
        lockSwitch.onCheckedChange = { toggleLock(it) }
    }

    private fun setupViews() {
        val inset = context.dp(InviteStyle.HORIZONTAL_INSET)
        contentView.addView(
            profileCard,
            frameParams(
                MATCH, context.dp(InviteStyle.PROFILE_HEIGHT), Gravity.TOP,
                start = inset, end = inset, top = context.dp(InviteStyle.CONTENT_TOP),
            ),
        )
        val iconSize = context.dp(InviteStyle.PROFILE_ICON_SIZE)
        profileCard.addView(
            profileIconBackground, frameParams(iconSize, iconSize, Gravity.CENTER_VERTICAL, start = context.dp(16)),
        )
        profileIconBackground.addView(profileMicIcon, frameParams(context.dp(26), context.dp(26), Gravity.CENTER))
        profileIconBackground.addView(profileLockIcon, frameParams(context.dp(26), context.dp(26), Gravity.CENTER))
        val textStart = context.dp(16) + iconSize + context.dp(16)
        val profileHeight = context.dp(InviteStyle.PROFILE_HEIGHT)
        profileCard.addView(
            profileTitle,
            frameParams(
                MATCH, WRAP, Gravity.BOTTOM, start = textStart, end = context.dp(16),
                bottom = profileHeight / 2 + context.dp(2)
            ),
        )
        profileCard.addView(
            profileMeta,
            frameParams(
                MATCH, WRAP, Gravity.TOP, start = textStart, end = context.dp(16),
                top = profileHeight / 2 + context.dp(4)
            ),
        )

        val lockTop = InviteStyle.CONTENT_TOP + InviteStyle.PROFILE_HEIGHT + InviteStyle.CARD_GAP
        contentView.addView(
            lockCard,
            frameParams(
                MATCH, context.dp(InviteStyle.LOCK_HEIGHT), Gravity.TOP,
                start = inset, end = inset, top = context.dp(lockTop),
            ),
        )
        lockCard.addView(lockLabel, frameParams(WRAP, WRAP, Gravity.CENTER_VERTICAL, start = context.dp(16)))
        lockCard.addView(
            lockSwitch,
            frameParams(context.dp(51), context.dp(31), Gravity.CENTER_VERTICAL or Gravity.END, end = context.dp(16)),
        )

        val cardTop = lockTop + InviteStyle.LOCK_HEIGHT + InviteStyle.CARD_GAP
        contentView.addView(
            card,
            frameParams(
                MATCH, MATCH, Gravity.TOP,
                start = inset, end = inset, top = context.dp(cardTop), bottom = context.dp(12),
            ),
        )
        card.addView(cardTitle, frameParams(WRAP, WRAP, Gravity.TOP, start = context.dp(16), top = context.dp(16)))
        card.addView(
            divider,
            frameParams(
                MATCH, context.dp(1), Gravity.TOP, start = context.dp(16), end = context.dp(16), top = context.dp(46),
            ),
        )
        card.addView(scrollView, frameParams(MATCH, MATCH, Gravity.TOP, top = context.dp(51)))
        scrollView.addView(listView, FrameLayout.LayoutParams(MATCH, WRAP))
        card.addView(
            emptyLabel,
            frameParams(MATCH, WRAP, Gravity.TOP, start = context.dp(20), end = context.dp(20), top = context.dp(87)),
        )
    }

    private fun setupStyles() {
        sheetTitle = context.getString(R.string.livekit_voiceroom_seat_sheet_title)
        lockLabel.text = context.getString(R.string.livekit_voiceroom_seat_lock_toggle)
        listOf(profileCard, lockCard, card).forEach {
            it.background = roundedDrawable(
                blackAlpha(0.25f), context.dp(16).toFloat(), context.dp(1), VoiceLoungeColor.overlayHair
            )
        }
        profileIconBackground.background = ovalDrawable(VoiceLoungeColor.overlaySoft)
        profileMicIcon.setImageDrawable(
            ContextCompat.getDrawable(context, R.drawable.livekit_coguest_seat_mic)?.mutate()?.apply {
                setTint(InviteStyle.profileIconTint)
            },
        )
        profileMicIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        profileLockIcon.strokeColor = InviteStyle.profileIconTint
        profileTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        profileTitle.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        profileTitle.singleLineEnd()
        profileMeta.singleLineEnd()
        divider.setBackgroundColor(VoiceLoungeColor.overlaySoft)
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.clipToPadding = false
        scrollView.setPadding(0, 0, 0, context.dp(12))
    }

    fun show(seatIndex: Int, isLocked: Boolean, skin: VoiceLoungeSkin) {
        this.seatIndex = seatIndex
        this.skin = skin
        this.tappedLocked = isLocked
        pendingLock = null
        lockSwitch.onTrackColor = skin.mid
        (card.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.bottomMargin = context.dp(12) + navigationBottom()
            card.layoutParams = it
        }
        renderProfile(isLocked)
        card.visibility = if (isLocked) View.GONE else View.VISIBLE
        show()
    }

    override fun sheetWillPresent() = subscribe()

    override fun sheetDidDismiss() {
        scope?.cancel()
        scope = null
        bag.clear()
        audienceList = emptyList()
        seatList = emptyList()
        pendingLock = null
        tappedLocked = false
    }

    private fun subscribe() {
        scope?.cancel()
        bag.clear()
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        val audienceStore = LiveAudienceStore.create(liveID)
        activeScope.launch {
            audienceStore.liveAudienceState.audienceList.collect {
                audienceList = it
                render()
            }
        }
        val liveID = liveID
        audienceStore.fetchAudienceList(object : CompletionHandler {
            override fun onSuccess() = Unit
            override fun onFailure(code: Int, desc: String) {
                logger.warn("refresh audience list failed liveID=$liveID code=$code message=$desc")
            }
        })
        activeScope.launch { LiveSeatStore.create(liveID).liveSeatState.seatList.collect { applySeatList(it) } }
        coGuest.changes.subscribe { render() }.addTo(bag)
        render()
    }

    private fun applySeatList(list: List<SeatInfo>) {
        seatList = list
        val seat = list.firstOrNull { it.index == seatIndex }
        if (seat != null && seat.userInfo.userID.isNotEmpty()) {
            dismiss()
            return
        }
        if (pendingLock == sdkLocked) pendingLock = null
        render()
    }

    private val sdkLocked: Boolean get() = seatList.firstOrNull { it.index == seatIndex }?.isLocked ?: tappedLocked

    private val isLocked: Boolean get() = pendingLock ?: sdkLocked

    private val candidates: List<LiveUserInfo>
        get() {
            val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
            val onSeatIDs = seatList.map { it.userInfo.userID }.filter { it.isNotEmpty() }.toSet()
            val seen = HashSet<String>()
            return audienceList.filter {
                it.userID.isNotEmpty() && it.userID != selfID && it.userID !in onSeatIDs && seen.add(it.userID)
            }
        }

    private fun toggleLock(locked: Boolean) {
        if (seatIndex < 0) return
        pendingLock = locked
        render()
        val store = LiveSeatStore.create(liveID)
        val completion = object : CompletionHandler {
            override fun onSuccess() = Unit
            override fun onFailure(code: Int, desc: String) {
                VoiceLoungeMain.post {
                    LiveToast.show(voiceLoungeAppContext, VoiceLoungeErrorText.coGuest(code))
                    pendingLock = null
                    render()
                }
            }
        }
        if (locked) store.lockSeat(seatIndex, completion) else store.unlockSeat(seatIndex, completion)
    }

    private fun render() {
        val audience = candidates
        val locked = isLocked
        lockSwitch.setChecked(locked, animate = false)
        lockSwitch.isEnabled = pendingLock == null
        renderProfile(locked)
        cardTitle.text = context.getString(R.string.livekit_voiceroom_seat_invitable, audience.size.toString())
        card.visibility = if (locked) View.GONE else View.VISIBLE
        listView.removeAllViews()
        emptyLabel.text = context.getString(R.string.livekit_voiceroom_comic_invite_empty)
        emptyLabel.visibility = if (locked || audience.isNotEmpty()) View.GONE else View.VISIBLE
        if (!locked) {
            audience.forEach { user ->
                val row = InviteRow(context)
                row.bind(
                    user,
                    isApplicant = isApplicant(user.userID),
                    isInvited = user.userID in coGuest.inviteeIDs,
                    isPending = user.userID in coGuest.pendingUserIDs,
                    skin = skin,
                )
                row.onTap = { toggleInvite(user) }
                listView.addView(row, linearParams(MATCH, context.dp(InviteRow.HEIGHT)))
            }
        }
        if (isShowing) updateSheetHeight(animated = true)
    }

    private fun renderProfile(locked: Boolean) {
        val status = context.getString(
            if (locked) R.string.livekit_voiceroom_seat_locked_seat else R.string.livekit_voiceroom_seat_empty_seat,
        )
        profileMicIcon.visibility = if (locked) View.GONE else View.VISIBLE
        profileLockIcon.visibility = if (locked) View.VISIBLE else View.GONE
        profileTitle.text = status
        profileMeta.text = context.getString(R.string.livekit_voiceroom_seat_seat_meta, seatIndex, status)
    }

    private fun toggleInvite(user: LiveUserInfo) {
        when {
            isApplicant(user.userID) -> {
                coGuest.accept(user.userID)
                dismiss()
            }

            user.userID in coGuest.inviteeIDs -> coGuest.cancelInvitation(user.userID)
            else -> coGuest.invite(user.userID, seatIndex)
        }
    }

    private fun isApplicant(userID: String): Boolean = coGuest.applicants.any { it.userID == userID }

    private fun navigationBottom(): Int {
        val insets =
            (context as? android.app.Activity)?.window?.decorView?.rootWindowInsets ?: contentView.rootWindowInsets
            ?: return 0
        return WindowInsetsCompat.toWindowInsetsCompat(insets)
            .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
    }

    private class InviteRow(context: Context) : FrameLayout(context) {
        var onTap: (() -> Unit)? = null

        private val avatarView = ImageView(context)
        private val nameLabel = voiceLoungeText(context, LiveTheme.TextRole.BODY, LiveTheme.TextTone.SECONDARY)
        private val button = TextView(context)

        init {
            avatarView.scaleType = ImageView.ScaleType.CENTER_CROP
            avatarView.background = ovalDrawable(VoiceLoungeColor.overlayBase)
            avatarView.clipOval()
            addView(avatarView, frameParams(dp(40), dp(40), Gravity.CENTER_VERTICAL, start = dp(16)))
            nameLabel.singleLineEnd()
            addView(
                nameLabel, frameParams(MATCH, WRAP, Gravity.CENTER_VERTICAL, start = dp(68), end = dp(16 + 76 + 12)),
            )
            button.gravity = Gravity.CENTER
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            button.includeFontPadding = false
            button.setOnClickListener { onTap?.invoke() }
            addView(button, frameParams(dp(76), dp(40), Gravity.CENTER_VERTICAL or Gravity.END, end = dp(16)))
        }

        fun bind(
            user: LiveUserInfo, isApplicant: Boolean, isInvited: Boolean, isPending: Boolean, skin: VoiceLoungeSkin
        ) {
            avatarView.loadUrl(user.avatarURL, R.drawable.livekit_livelist_default_avatar)
            nameLabel.text = user.userName.ifEmpty { user.userID }
            val muted = isInvited && !isApplicant
            button.text = context.getString(
                when {
                    isApplicant -> R.string.livekit_voiceroom_comic_connect
                    isInvited -> R.string.livekit_voiceroom_comic_cancel_invite
                    else -> R.string.livekit_voiceroom_comic_invite
                },
            )
            button.setTextColor(whiteAlpha(if (muted) 0.7f else 0.9f))
            button.background = roundedDrawable(
                if (muted) VoiceLoungeColor.overlaySoft else skin.mid.withAlpha(0.35f),
                dp(10).toFloat(),
                dp(1),
                if (muted) whiteAlpha(0.15f) else skin.mid,
            )
            button.isEnabled = !isPending
            button.alpha = if (isPending) 0.5f else 1f
        }

        companion object {
            const val HEIGHT = 64
        }
    }

    private class SeatLockIconView(context: Context) : View(context) {
        var strokeColor: Int = android.graphics.Color.WHITE
            set(value) {
                field = value
                paint.color = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = strokeColor
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val s = width / 24f
            if (s <= 0f) return
            path.reset()
            path.addRoundRect(RectF(6f * s, 11f * s, 18f * s, 20f * s), 2f * s, 2f * s, Path.Direction.CW)
            path.moveTo(8.5f * s, 11f * s)
            path.lineTo(8.5f * s, 8f * s)
            path.arcTo(RectF(8.5f * s, 4.5f * s, 15.5f * s, 11.5f * s), 180f, 180f, false)
            path.lineTo(15.5f * s, 11f * s)
            paint.strokeWidth = 1.8f * s
            canvas.drawPath(path, paint)
        }
    }
}

private object InviteStyle {
    const val HORIZONTAL_INSET = 16
    const val CONTENT_TOP = 66
    const val PROFILE_HEIGHT = 88
    const val PROFILE_ICON_SIZE = 52
    const val CARD_GAP = 12
    const val LOCK_HEIGHT = 56
    const val LOCKED_BOTTOM_PADDING = 28
    const val UNLOCKED_HEIGHT = 520
    val profileIconTint = whiteAlpha(0.8f)
}
