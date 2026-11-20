package com.trtc.uikit.livekit.voicelounge.living.seat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveSwitch
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.clipOval
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestStore
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeErrorText
import com.trtc.uikit.livekit.voicelounge.loadUrl
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.vlString
import com.trtc.uikit.livekit.voicelounge.voiceLoungeAppContext
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.MoveSeatPolicy

internal class VoiceLoungeSeatUserPanel(
    context: Context,
    private val store: VoiceLoungeSeatStore,
) : BottomSheetView(context) {
    var onConfirm: ((title: String, message: String, confirmTitle: String, action: () -> Unit) -> Unit)? = null

    private enum class Mode { ON_MIC, PENDING }

    private val liveID: String get() = store.liveID
    private val coGuest: VoiceLoungeCoGuestStore get() = store.coGuest
    private val muteCache: VoiceLoungeMuteCache get() = store.muteCache

    private val profileCard = LinearLayout(context)
    private val avatarView = ImageView(context)
    private val nameLabel = voiceLoungeText(context, LiveTheme.TextRole.ACTION, LiveTheme.TextTone.PRIMARY)
    private val subtitleLabel = voiceLoungeText(context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.TERTIARY)
    private val badgeRow = LinearLayout(context)
    private val mutedBadge = BadgeLabel(context)
    private val adminBadge = BadgeLabel(context)

    private val cardStack = LinearLayout(context)
    private val togglesCard = LinearLayout(context)
    private val muteRow = ToggleRow(context, PanelGlyph.MIC, vlString(R.string.livekit_voiceroom_comic_disable_mic))
    private val adminRow = ToggleRow(context, PanelGlyph.CROWN, vlString(R.string.livekit_voiceroom_admin_set))
    private val cohostRow = ToggleRow(context, PanelGlyph.STAR, vlString(R.string.livekit_voiceroom_cohost_set))
    private val inviteCard = FrameLayout(context)
    private val inviteRow = ActionRow(context, PanelGlyph.INVITE)
    private val leaveCard = FrameLayout(context)
    private val leaveRow = ActionRow(context, PanelGlyph.LEAVE, PanelStyle.DESTRUCTIVE_TINT)

    private var mode = Mode.ON_MIC
    private var currentUserID = ""
    private var currentSeat: VoiceLoungeSeat? = null
    private var isMuteRequestInFlight = false
    private var pendingAdmin: Boolean? = null
    private var pendingCohost: Boolean? = null
    private val bag = VoiceLoungeBag()

    override val sheetHeight: Int
        get() {
            var content = 0
            if (togglesCard.visibility == View.VISIBLE) content += PanelStyle.CARD_SPACING + PanelStyle.ROW_HEIGHT * toggleRowCount
            if (inviteCard.visibility == View.VISIBLE) content += PanelStyle.CARD_SPACING + PanelStyle.ROW_HEIGHT
            if (leaveCard.visibility == View.VISIBLE) content += PanelStyle.CARD_SPACING + PanelStyle.ROW_HEIGHT
            val total = PanelStyle.PROFILE_TOP + PanelStyle.PROFILE_HEIGHT + content + PanelStyle.BOTTOM_PADDING
            return context.dp(total) + navigationBarHeight()
        }

    private val toggleRowCount: Int get() = if (cohostRow.visibility == View.VISIBLE) 3 else 2

    init {
        setupViews()
        setupStyles()
        setupBindings()
    }

    fun show(seat: VoiceLoungeSeat) {
        if (seat.userID.isEmpty()) return
        mode = when (seat.state) {
            VoiceLoungeSeat.State.OCCUPIED -> Mode.ON_MIC
            VoiceLoungeSeat.State.FADED -> Mode.PENDING
            else -> return
        }
        currentUserID = seat.userID
        isMuteRequestInFlight = false
        pendingAdmin = null
        pendingCohost = null
        if (!render()) return
        avatarView.loadUrl(seat.avatarURL, R.drawable.livekit_livelist_default_avatar)
        show()
    }

    override fun sheetWillPresent() {
        bag.clear()
        store.changes.subscribe { render() }.addTo(bag)
        coGuest.changes.subscribe { render() }.addTo(bag)
    }

    override fun sheetDidDismiss() {
        bag.clear()
        currentSeat = null
        currentUserID = ""
    }

    private fun setupViews() {
        val inset = context.dp(PanelStyle.HORIZONTAL_INSET)
        contentView.addView(
            profileCard,
            frameParams(
                MATCH, context.dp(PanelStyle.PROFILE_HEIGHT), Gravity.TOP,
                start = inset, end = inset, top = context.dp(PanelStyle.PROFILE_TOP),
            ),
        )
        profileCard.orientation = LinearLayout.HORIZONTAL
        profileCard.gravity = Gravity.CENTER_VERTICAL
        profileCard.setPadding(context.dp(16), 0, context.dp(16), 0)
        val avatarSize = context.dp(PanelStyle.AVATAR_SIZE)
        profileCard.addView(avatarView, linearParams(avatarSize, avatarSize))
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(16), 0, 0)
        }
        profileCard.addView(textColumn, linearParams(0, MATCH, 1f, start = context.dp(14)))
        textColumn.addView(nameLabel, linearParams(WRAP, WRAP))
        textColumn.addView(subtitleLabel, linearParams(WRAP, WRAP, top = context.dp(4)))
        badgeRow.orientation = LinearLayout.HORIZONTAL
        badgeRow.gravity = Gravity.CENTER_VERTICAL
        badgeRow.addView(mutedBadge, linearParams(WRAP, WRAP))
        badgeRow.addView(adminBadge, linearParams(WRAP, WRAP, start = context.dp(6)))
        profileCard.addView(badgeRow, linearParams(WRAP, WRAP, start = context.dp(8)))

        cardStack.orientation = LinearLayout.VERTICAL
        contentView.addView(
            cardStack,
            frameParams(
                MATCH, WRAP, Gravity.TOP, start = inset, end = inset,
                top = context.dp(PanelStyle.PROFILE_TOP + PanelStyle.PROFILE_HEIGHT),
            ),
        )
        togglesCard.orientation = LinearLayout.VERTICAL
        val rowHeight = context.dp(PanelStyle.ROW_HEIGHT)
        togglesCard.addView(muteRow, linearParams(MATCH, rowHeight))
        togglesCard.addView(adminRow, linearParams(MATCH, rowHeight))
        togglesCard.addView(cohostRow, linearParams(MATCH, rowHeight))
        adminRow.showsTopDivider = true
        cohostRow.showsTopDivider = true
        inviteCard.addView(inviteRow, frameParams(MATCH, MATCH))
        leaveCard.addView(leaveRow, frameParams(MATCH, MATCH))
        val spacing = context.dp(PanelStyle.CARD_SPACING)
        cardStack.addView(togglesCard, linearParams(MATCH, WRAP, top = spacing))
        cardStack.addView(inviteCard, linearParams(MATCH, rowHeight, top = spacing))
        cardStack.addView(leaveCard, linearParams(MATCH, rowHeight, top = spacing))
    }

    private fun setupStyles() {
        sheetTitle = vlString(R.string.livekit_voiceroom_seat_sheet_title)
        val radius = context.dp(PanelStyle.CARD_RADIUS).toFloat()
        profileCard.background = roundedDrawable(VoiceLoungeColor.overlayHair, radius)
        listOf<View>(togglesCard, inviteCard, leaveCard).forEach { card ->
            card.background = roundedDrawable(VoiceLoungeColor.overlayHair, radius)
            card.clipToOutline = true
        }
        avatarView.scaleType = ImageView.ScaleType.CENTER_CROP
        avatarView.background = ovalDrawable(VoiceLoungeColor.overlaySoft)
        avatarView.clipOval()
        nameLabel.singleLineEnd()
        subtitleLabel.singleLineEnd()
        adminBadge.text = vlString(R.string.livekit_voiceroom_seat_badge_admin)
        mutedBadge.text = vlString(R.string.livekit_voiceroom_seat_badge_muted)
        mutedBadge.apply(PanelStyle.DESTRUCTIVE_TINT)
    }

    private fun setupBindings() {
        muteRow.onToggle = { handleMuteToggle(it) }
        adminRow.onToggle = { handleAdmin(it) }
        cohostRow.onToggle = { handleCohost(it) }
        inviteRow.setOnClickListener { handleInvite() }
        leaveRow.setOnClickListener { handleKick() }
    }

    private fun render(): Boolean {
        val userID = currentUserID
        val expected = if (mode == Mode.ON_MIC) VoiceLoungeSeat.State.OCCUPIED else VoiceLoungeSeat.State.FADED
        val seat = store.seats.firstOrNull { it.userID == userID && it.state == expected }
        if (userID.isEmpty() || seat == null) {
            if (isShowing) dismiss()
            return false
        }
        currentSeat = seat
        nameLabel.text = seat.name
        adminBadge.apply(store.skin.mid)
        when (mode) {
            Mode.ON_MIC -> {
                subtitleLabel.text = seatMeta(seat.index, R.string.livekit_voiceroom_seat_status_on_mic)
                adminBadge.visibility = if (seat.isAdmin) View.VISIBLE else View.GONE
                mutedBadge.visibility = if (seat.isMuted) View.VISIBLE else View.GONE
                if (!isMuteRequestInFlight) muteRow.setOn(seat.isMuted)
                val isCohost = seat.role == VoiceLoungeSeat.Role.COHOST
                if (pendingAdmin == seat.isAdmin) pendingAdmin = null
                if (pendingCohost == isCohost) pendingCohost = null
                adminRow.setOn(pendingAdmin ?: seat.isAdmin)
                cohostRow.setOn(pendingCohost ?: isCohost)
                togglesCard.visibility = View.VISIBLE
                cohostRow.visibility = if (store.layout == VoiceLoungeSeatLayout.TWO_V8) View.VISIBLE else View.GONE
                inviteCard.visibility = View.GONE
                leaveCard.visibility = View.VISIBLE
                leaveRow.title = vlString(R.string.livekit_voiceroom_comic_kick)
            }

            Mode.PENDING -> {
                val applying = isApplicant(seat.userID)
                subtitleLabel.text = seatMeta(seat.index, R.string.livekit_voiceroom_seat_status_pending)
                adminBadge.visibility = View.GONE
                mutedBadge.visibility = View.GONE
                inviteRow.title = vlString(
                    if (applying) R.string.livekit_voiceroom_comic_connect else R.string.livekit_voiceroom_comic_cancel_invite
                )
                togglesCard.visibility = View.GONE
                inviteCard.visibility = View.VISIBLE
                leaveCard.visibility = if (applying) View.VISIBLE else View.GONE
                leaveRow.title = vlString(R.string.livekit_voiceroom_comic_reject_apply)
            }
        }
        (adminBadge.layoutParams as LinearLayout.LayoutParams).marginStart =
            if (mutedBadge.visibility == View.VISIBLE) context.dp(6) else 0
        badgeRow.visibility =
            if (adminBadge.visibility == View.GONE && mutedBadge.visibility == View.GONE) View.GONE else View.VISIBLE
        updateSheetHeight(animated = isShowing)
        return true
    }

    private fun seatMeta(index: Int, statusRes: Int): String =
        vlString(R.string.livekit_voiceroom_seat_seat_meta, index, vlString(statusRes))

    private fun handleMuteToggle(isOn: Boolean) {
        val userID = currentUserID
        if (userID.isEmpty()) return
        val user = LiveSeatStore.create(
            liveID
        ).liveSeatState.seatList.value.firstOrNull { it.userInfo.userID == userID }?.userInfo ?: return
        isMuteRequestInFlight = true
        if (isOn) {
            performSeatAction(revertTo = false, onSuccess = { muteCache.set(true, userID) }) { seatStore, c ->
                seatStore.closeRemoteMicrophone(userID, c)
            }
            return
        }
        val finish = {
            muteCache.set(false, userID)
            coGuest.requestOpenMicrophone(userID)
        }
        if (user.allowOpenMicrophone) {
            isMuteRequestInFlight = false
            finish()
        } else {
            performSeatAction(revertTo = true, onSuccess = finish) { seatStore, c ->
                seatStore.openRemoteMicrophone(userID, DeviceControlPolicy.UNLOCK_ONLY, c)
            }
        }
    }

    private fun handleAdmin(isOn: Boolean) {
        val seat = currentSeat ?: return
        if (mode != Mode.ON_MIC) return
        val userID = seat.userID
        val audienceStore = LiveAudienceStore.create(liveID)
        pendingAdmin = isOn
        val completion = completion { success ->
            if (currentUserID != userID) return@completion
            if (!success) pendingAdmin = null
            render()
        }
        if (isOn) audienceStore.setAdministrator(userID, completion) else audienceStore.revokeAdministrator(
            userID, completion
        )
    }

    private fun handleCohost(isOn: Boolean) {
        val seat = currentSeat ?: return
        if (mode != Mode.ON_MIC || store.layout != VoiceLoungeSeatLayout.TWO_V8) return
        val userID = seat.userID
        val seatList = LiveSeatStore.create(liveID).liveSeatState.seatList.value
        if (!isOn) {
            val freeIndex =
                seatList.filter { it.index >= PanelStyle.FIRST_GUEST_INDEX && it.userInfo.userID.isEmpty() && !it.isLocked }
                    .minOfOrNull { it.index }
            if (freeIndex == null) {
                cohostRow.setOn(true)
                LiveToast.show(voiceLoungeAppContext, vlString(R.string.livekit_voiceroom_cohost_no_free_seat))
                return
            }
            moveUser(userID, freeIndex, MoveSeatPolicy.ABORT_WHEN_OCCUPIED)
            return
        }
        val cohostIndex = PanelStyle.COHOST_INDEX
        val occupantID = seatList.firstOrNull { it.index == cohostIndex }?.userInfo?.userID.orEmpty()
        if (occupantID.isEmpty() || occupantID == userID) {
            moveUser(userID, cohostIndex, MoveSeatPolicy.ABORT_WHEN_OCCUPIED)
            return
        }
        val occupantName = store.seats.firstOrNull { it.index == cohostIndex }?.name ?: occupantID
        dismiss()
        VoiceLoungeMain.postDelayed(ACTION_DELAY_MS) {
            onConfirm?.invoke(
                vlString(R.string.livekit_voiceroom_cohost_replace_title),
                vlString(R.string.livekit_voiceroom_cohost_replace_message, occupantName),
                vlString(R.string.livekit_voiceroom_cohost_replace_confirm),
            ) { moveUser(userID, cohostIndex, MoveSeatPolicy.SWAP_POSITION) }
        }
    }

    private fun moveUser(userID: String, index: Int, policy: MoveSeatPolicy) {
        pendingCohost = index == PanelStyle.COHOST_INDEX
        val completion = completion { success ->
            if (currentUserID != userID) return@completion
            if (!success) pendingCohost = null
            render()
        }
        LiveSeatStore.create(liveID).moveUserToSeat(userID, index, policy, completion)
    }

    private fun isApplicant(userID: String): Boolean = coGuest.applicants.any { it.userID == userID }

    private fun handleInvite() {
        val seat = currentSeat ?: return
        if (mode != Mode.PENDING) return
        if (isApplicant(seat.userID)) coGuest.accept(seat.userID) else coGuest.cancelInvitation(seat.userID)
        dismiss()
    }

    private fun handleKick() {
        val userID = currentUserID
        if (userID.isEmpty()) return
        if (mode == Mode.PENDING) {
            if (!isApplicant(userID)) return
            coGuest.reject(userID)
            dismiss()
            return
        }
        dismiss()
        val seatStore = LiveSeatStore.create(liveID)
        VoiceLoungeMain.postDelayed(ACTION_DELAY_MS) { seatStore.kickUserOutOfSeat(userID, completion()) }
    }

    private fun performSeatAction(
        revertTo: Boolean?,
        onSuccess: (() -> Unit)? = null,
        action: (LiveSeatStore, CompletionHandler) -> Unit,
    ) {
        val completion = completion { success ->
            isMuteRequestInFlight = false
            if (success) onSuccess?.invoke() else revertTo?.let { muteRow.setOn(it) }
        }
        action(LiveSeatStore.create(liveID), completion)
    }

    private fun completion(onFinish: ((Boolean) -> Unit)? = null): CompletionHandler = object : CompletionHandler {
        override fun onSuccess() {
            VoiceLoungeMain.post { onFinish?.invoke(true) }
        }

        override fun onFailure(code: Int, desc: String) {
            VoiceLoungeMain.post {
                LiveToast.show(voiceLoungeAppContext, VoiceLoungeErrorText.coGuest(code))
                onFinish?.invoke(false)
            }
        }
    }

    private fun navigationBarHeight(): Int {
        val insets = (context as? android.app.Activity)?.window?.decorView?.rootWindowInsets ?: return 0
        return androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets)
            .getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
    }

    private companion object {
        const val ACTION_DELAY_MS = 220L
    }
}

private object PanelStyle {
    const val HORIZONTAL_INSET = 16
    const val PROFILE_TOP = 62
    const val PROFILE_HEIGHT = 80
    const val CARD_SPACING = 12
    const val CARD_RADIUS = 16
    const val AVATAR_SIZE = 48
    const val ROW_HEIGHT = 56
    const val ROW_INSET = 16
    const val GLYPH_SIZE = 22
    const val BOTTOM_PADDING = 24
    const val COHOST_INDEX = 1
    const val FIRST_GUEST_INDEX = 2
    val DESTRUCTIVE_TINT = rgb(1.0f, 0.36f, 0.42f)
    val TOGGLE_ON_TINT = rgb(0.46f, 0.78f, 1f)
    val ROW_TINT = whiteAlpha(0.85f)
}

private enum class PanelGlyph { MIC, CROWN, LEAVE, STAR, INVITE }

private class GlyphView(context: Context, private val glyph: PanelGlyph) : View(context) {
    var strokeColor: Int = Color.WHITE
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        val scale = minOf(width, height) / 24f
        paint.strokeWidth = 1.6f * scale
        canvas.save()
        canvas.scale(scale, scale)
        paint.strokeWidth = 1.6f
        canvas.drawPath(path(glyph), paint)
        canvas.restore()
    }

    private fun path(glyph: PanelGlyph): Path = Path().apply {
        when (glyph) {
            PanelGlyph.MIC -> {
                addRoundRect(RectF(9f, 3.5f, 15f, 14.5f), 3f, 3f, Path.Direction.CW)
                arcTo(RectF(6.8f, 6.8f, 17.2f, 17.2f), 0f, 180f, true)
                moveTo(12f, 17.2f)
                lineTo(12f, 20.5f)
                moveTo(9f, 20.5f)
                lineTo(15f, 20.5f)
            }

            PanelGlyph.CROWN -> {
                moveTo(4.5f, 17.5f)
                lineTo(19.5f, 17.5f)
                moveTo(4.5f, 17.5f)
                lineTo(3.5f, 7f)
                lineTo(8f, 11f)
                lineTo(12f, 5.5f)
                lineTo(16f, 11f)
                lineTo(20.5f, 7f)
                lineTo(19.5f, 17.5f)
            }

            PanelGlyph.LEAVE -> {
                moveTo(12f, 4f)
                lineTo(12f, 15f)
                moveTo(7.5f, 10.5f)
                lineTo(12f, 15f)
                lineTo(16.5f, 10.5f)
                moveTo(5f, 19.5f)
                lineTo(19f, 19.5f)
            }

            PanelGlyph.STAR -> {
                val cx = 12f
                val cy = 12.6f
                for (point in 0 until 10) {
                    val radius = if (point % 2 == 0) 8.6f else 3.9f
                    val angle = -Math.PI / 2 + point * Math.PI / 5
                    val x = (cx + radius * Math.cos(angle)).toFloat()
                    val y = (cy + radius * Math.sin(angle)).toFloat()
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }

            PanelGlyph.INVITE -> {
                addOval(RectF(6.5f, 4.5f, 13.5f, 11.5f), Path.Direction.CW)
                moveTo(3.5f, 19.5f)
                quadTo(10f, 11f, 16.5f, 19.5f)
                moveTo(19.5f, 8f)
                lineTo(19.5f, 14f)
                moveTo(16.5f, 11f)
                lineTo(22.5f, 11f)
            }
        }
    }
}

private class BadgeLabel(context: Context) : TextView(context) {
    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        maxLines = 1
        setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
    }

    fun apply(tint: Int) {
        setTextColor(tint)
        background = roundedDrawable(
            tint.withAlpha(0.16f), context.dp(4).toFloat(), maxOf(1, context.dp(0.5f)), tint.withAlpha(0.5f),
        )
    }
}

private class ActionRow(context: Context, glyph: PanelGlyph, tint: Int = PanelStyle.ROW_TINT) : FrameLayout(context) {
    var title: CharSequence?
        get() = titleLabel.text
        set(value) {
            titleLabel.text = value
        }

    private val titleLabel = TextView(context)

    init {
        val iconView = GlyphView(context, glyph).apply { strokeColor = tint }
        val glyphSize = context.dp(PanelStyle.GLYPH_SIZE)
        val inset = context.dp(PanelStyle.ROW_INSET)
        addView(iconView, frameParams(glyphSize, glyphSize, Gravity.CENTER_VERTICAL or Gravity.START, start = inset))
        titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val isDestructive = tint == PanelStyle.DESTRUCTIVE_TINT
        titleLabel.typeface = Typeface.create(if (isDestructive) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        titleLabel.setTextColor(tint)
        titleLabel.includeFontPadding = false
        titleLabel.maxLines = 1
        addView(
            titleLabel,
            frameParams(
                WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START, start = inset + glyphSize + context.dp(14),
                end = inset
            ),
        )
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> alpha = 0.6f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> alpha = 1f
            }
            false
        }
    }
}

private class ToggleRow(context: Context, glyph: PanelGlyph, title: String) : FrameLayout(context) {
    var onToggle: ((Boolean) -> Unit)? = null
    var showsTopDivider: Boolean = false
        set(value) {
            field = value
            divider.visibility = if (value) View.VISIBLE else View.GONE
        }

    private val toggleSwitch = LiveSwitch(context)
    private val divider = View(context)

    init {
        val inset = context.dp(PanelStyle.ROW_INSET)
        val glyphSize = context.dp(PanelStyle.GLYPH_SIZE)
        val iconView = GlyphView(context, glyph).apply { strokeColor = PanelStyle.ROW_TINT }
        addView(iconView, frameParams(glyphSize, glyphSize, Gravity.CENTER_VERTICAL or Gravity.START, start = inset))
        val titleLabel = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(PanelStyle.ROW_TINT)
            includeFontPadding = false
            maxLines = 1
        }
        toggleSwitch.onTrackColor = PanelStyle.TOGGLE_ON_TINT
        addView(toggleSwitch, frameParams(WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.END, end = inset))
        addView(
            titleLabel,
            frameParams(
                WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START,
                start = inset + glyphSize + context.dp(14), end = inset + context.dp(51 + 10),
            ),
        )
        divider.setBackgroundColor(whiteAlpha(0.08f))
        divider.visibility = View.GONE
        addView(divider, frameParams(MATCH, maxOf(1, context.dp(0.5f)), Gravity.TOP, start = inset, end = inset))
        toggleSwitch.onCheckedChange = { onToggle?.invoke(it) }
    }

    fun setOn(isOn: Boolean) = toggleSwitch.setChecked(isOn, animate = false)
}
