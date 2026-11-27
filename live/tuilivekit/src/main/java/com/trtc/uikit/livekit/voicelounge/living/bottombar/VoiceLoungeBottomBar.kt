package com.trtc.uikit.livekit.voicelounge.living.bottombar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.blackAlpha
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.liveText
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLivingActions
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestManagePanel
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestStore
import com.trtc.uikit.livekit.voicelounge.living.seat.VoiceLoungeSeatStore
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText

internal class VoiceLoungeBottomBar(
    context: Context,
    private val liveID: String,
    private val seatStore: VoiceLoungeSeatStore,
    private val livingActions: VoiceLoungeLivingActions,
) : FrameLayout(context) {
    var onComment: (() -> Unit)? = null
    var onLike: (() -> Unit)? = null
    var onGift: (() -> Unit)? = null
    var onAudioEffects: (() -> Unit)? = null
    var onBackgroundMusic: (() -> Unit)? = null

    val role: VoiceLoungeRole = seatStore.role
    private val bag = VoiceLoungeBag()
    private val coGuestManagePanel by lazy { VoiceLoungeCoGuestManagePanel(context, seatStore) }
    private val morePanel by lazy {
        VoiceLoungeLivingMorePanel(context, seatStore.settings, livingActions).apply {
            onComment = { this@VoiceLoungeBottomBar.onComment?.invoke() }
        }
    }

    private var hostCoMicButton: CoMicButton? = null
    private var hostMuteButton: ActionButton? = null
    private var audienceMuteButton: AudienceCircleButton? = null
    private var audienceCoMicButton: AudienceCircleButton? = null

    init {
        clipChildren = false
        when (role) {
            VoiceLoungeRole.HOST -> setupHost()
            VoiceLoungeRole.AUDIENCE -> setupAudience()
        }
    }

    fun dismissPanels() {
        if (role == VoiceLoungeRole.HOST) {
            listOf(coGuestManagePanel, morePanel).forEach { if (it.isShowing) it.dismiss() }
            morePanel.dismissChildren()
        }
    }

    private fun setupHost() {
        val coMic = CoMicButton(context)
        hostCoMicButton = coMic
        addView(coMic, frameParams(dp(46), dp(46), Gravity.CENTER_VERTICAL or Gravity.START, start = dp(12)))
        coMic.setOnClickListener { showCoMic() }

        val titles = listOf(
            R.string.livekit_voicelounge_living_mute, R.string.livekit_voicelounge_living_unmute,
            R.string.livekit_voicelounge_living_bgm, R.string.livekit_voicelounge_prepare_more_audio,
            R.string.livekit_voicelounge_room_more,
        ).map { context.getString(it) }
        val slotWidth = titles.maxOf { ActionButton.fittingWidth(context, it) }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        addView(row, frameParams(WRAP, dp(46), Gravity.CENTER_VERTICAL or Gravity.END, end = dp(12)))
        val mute = ActionButton(context, R.drawable.livekit_coguest_seat_mic, 26f, -0.5f, titles[0])
        val music = ActionButton(context, R.drawable.livekit_living_setting_music, 22f, 0f, titles[2])
        val audio = ActionButton(context, R.drawable.livekit_living_setting_audio, 20.5f, 0f, titles[3])
        val more = ActionButton(context, R.drawable.livekit_living_more, 27f, 0f, titles[4])
        hostMuteButton = mute
        listOf(mute, music, audio, more).forEachIndexed { index, button ->
            row.addView(button, linearParams(slotWidth, dp(46), start = if (index == 0) 0 else dp(12)))
        }
        mute.setOnClickListener {
            livingActions.toggleMute()
            syncMuteIcon()
        }
        music.setOnClickListener { onBackgroundMusic?.invoke() }
        audio.setOnClickListener { onAudioEffects?.invoke() }
        more.setOnClickListener { morePanel.show() }
        syncMuteIcon()
    }

    private fun setupAudience() {
        val size = dp(36)
        val gap = dp(8)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        addView(row, frameParams(MATCH, size, Gravity.CENTER_VERTICAL, start = dp(12), end = dp(12)))

        val inputPill = FrameLayout(context)
        inputPill.background = roundedDrawable(blackAlpha(0.3f), dp(18).toFloat())
        val placeholder = voiceLoungeText(
            context, LiveTheme.TextRole.BODY, LiveTheme.TextTone.TERTIARY,
            text = context.getString(R.string.livekit_voicelounge_room_input_placeholder),
        )
        placeholder.maxLines = 1
        placeholder.ellipsize = android.text.TextUtils.TruncateAt.END
        inputPill.addView(placeholder, frameParams(MATCH, WRAP, Gravity.CENTER_VERTICAL, start = dp(14), end = dp(14)))
        row.addView(inputPill, linearParams(0, size, 1f, end = dp(10)))

        val mute = AudienceCircleButton(context, AudienceCircleButton.Icon.MUTE)
        val audio = AudienceCircleButton(context, AudienceCircleButton.Icon.AUDIO_EFFECTS)
        val coMic = AudienceCircleButton(context, AudienceCircleButton.Icon.CO_MIC)
        val gift = AudienceCircleButton(context, AudienceCircleButton.Icon.GIFT)
        row.addView(mute, linearParams(size, size, end = gap))
        row.addView(audio, linearParams(size, size))
        row.addView(coMic, linearParams(size, size, start = gap))
        row.addView(gift, linearParams(size, size, start = gap))
        audienceMuteButton = mute
        audienceCoMicButton = coMic

        inputPill.setOnClickListener { onComment?.invoke() }
        mute.setOnClickListener { toggleAudienceMute() }
        audio.setOnClickListener { onAudioEffects?.invoke() }
        coMic.setOnClickListener { showCoMic() }
        gift.setOnClickListener { onGift?.invoke() }

        syncAudienceCoMic()
    }

    private fun toggleAudienceMute() {
        if (!seatStore.toggleSelfMicrophone()) {
            com.trtc.uikit.livekit.common.theme.LiveToast.showScoped(
                context,
                context.getString(R.string.livekit_player_coguest_microphone_locked),
            )
        }
    }

    private fun syncAudienceCoMic() {
        if (role != VoiceLoungeRole.AUDIENCE) return
        val onSeat = seatStore.coGuest.guestStatus == VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT
        audienceMuteButton?.let { button ->
            button.visibility = if (onSeat) View.VISIBLE else View.GONE
            button.setMuted(seatStore.isSelfMicOff)
        }
        audienceCoMicButton?.setLeaveBadgeVisible(onSeat)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!bag.isEmpty) return
        when (role) {
            VoiceLoungeRole.HOST -> {
                seatStore.coGuest.changes.subscribe {
                    hostCoMicButton?.setBadgeVisible(seatStore.coGuest.applicants.isNotEmpty())
                }.addTo(bag)
                seatStore.changes.subscribe { syncMuteIcon() }.addTo(bag)
                hostCoMicButton?.setBadgeVisible(seatStore.coGuest.applicants.isNotEmpty())
                syncMuteIcon()
            }

            VoiceLoungeRole.AUDIENCE -> {
                seatStore.changes.subscribe { syncAudienceCoMic() }.addTo(bag)
                seatStore.coGuest.changes.subscribe { syncAudienceCoMic() }.addTo(bag)
                syncAudienceCoMic()
            }
        }
    }

    override fun onDetachedFromWindow() {
        bag.clear()
        super.onDetachedFromWindow()
    }

    private fun syncMuteIcon() {
        val button = hostMuteButton ?: return
        val muted = livingActions.isMuted()
        button.setImage(if (muted) R.drawable.livekit_coguest_seat_muted else R.drawable.livekit_coguest_seat_mic)
        button.setTitle(
            context.getString(
                if (muted) R.string.livekit_voicelounge_living_unmute else R.string.livekit_voicelounge_living_mute
            ),
        )
    }

    private fun showCoMic() {
        when (role) {
            VoiceLoungeRole.HOST -> coGuestManagePanel.show(seatStore.skin)
            VoiceLoungeRole.AUDIENCE -> tapAudienceCoMic()
        }
    }

    private fun tapAudienceCoMic() {
        val coGuest = seatStore.coGuest
        when (coGuest.guestStatus) {
            VoiceLoungeCoGuestStore.GuestStatus.IDLE -> coGuest.apply(seatStore.audienceFadedSeatIndex ?: -1)
            VoiceLoungeCoGuestStore.GuestStatus.APPLYING -> seatStore.promptCancelApplication()
            VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT -> seatStore.promptLeave()
        }
    }

    private class AudienceCircleButton(context: Context, private val icon: Icon) : FrameLayout(context) {
        enum class Icon { MUTE, AUDIO_EFFECTS, CO_MIC, GIFT }

        private val micGlyph = GradientGlyphView(context, GradientGlyphView.Glyph.MIC, lineWidth = 1.6f)
        private val leaveBadge = LeaveBadgeView(context, radius = 3.6f * BADGE_SCALE)

        init {
            clipChildren = false
            clipToPadding = false
            background = ovalDrawable(BottomBarStyle.CIRCLE_BACKGROUND, dp(1), BottomBarStyle.CIRCLE_BORDER)
            when (icon) {
                Icon.MUTE -> {
                    addView(micGlyph, frameParams(dp(MIC_BOX_SIZE), dp(MIC_BOX_SIZE), Gravity.CENTER))
                    contentDescription = context.getString(R.string.livekit_voicelounge_living_mute)
                }

                Icon.AUDIO_EFFECTS -> {
                    val glyph = GradientImageView(context, R.drawable.livekit_living_setting_audio)
                    glyph.translationX = -dp(1).toFloat()
                    glyph.translationY = -dp(1).toFloat()
                    addView(glyph, frameParams(dp(AUDIO_ICON_SIZE), dp(AUDIO_ICON_SIZE), Gravity.CENTER))
                    contentDescription = context.getString(R.string.livekit_voicelounge_prepare_more_audio)
                }

                Icon.CO_MIC -> {
                    addView(
                        imageView(R.drawable.livekit_voicelounge_comic),
                        frameParams(dp(MIC_BOX_SIZE), dp(MIC_BOX_SIZE), Gravity.CENTER)
                    )
                    leaveBadge.visibility = View.GONE
                    leaveBadge.translationX = dp(7f * BADGE_SCALE).toFloat()
                    leaveBadge.translationY = dp(7f * BADGE_SCALE).toFloat()
                    addView(leaveBadge, frameParams(dp(leaveBadge.side), dp(leaveBadge.side), Gravity.CENTER))
                }

                Icon.GIFT -> addView(
                    imageView(R.drawable.livekit_voicelounge_gift), frameParams(dp(30), dp(30), Gravity.CENTER)
                )
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80)
                        .start()

                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate()
                        .scaleX(1f).scaleY(1f).setDuration(80).start()
                }
                false
            }
        }

        fun setMuted(muted: Boolean) {
            micGlyph.glyph = if (muted) GradientGlyphView.Glyph.MIC_SLASH else GradientGlyphView.Glyph.MIC
            contentDescription = context.getString(
                if (muted) R.string.livekit_voicelounge_living_unmute else R.string.livekit_voicelounge_living_mute,
            )
        }

        fun setLeaveBadgeVisible(visible: Boolean) {
            leaveBadge.visibility = if (visible) View.VISIBLE else View.GONE
        }

        private fun imageView(@DrawableRes res: Int): ImageView = ImageView(context).apply {
            setImageResource(res)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        companion object {
            const val MIC_BOX_SIZE = 28
            const val AUDIO_ICON_SIZE = 20
            const val BADGE_SCALE = MIC_BOX_SIZE / 24f
        }
    }

    private class GradientGlyphView(context: Context, glyph: Glyph, private val lineWidth: Float) : View(context) {
        enum class Glyph { MIC, MIC_SLASH }

        var glyph: Glyph = glyph
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            paint.shader = BottomBarStyle.glyphShader(w.toFloat(), h.toFloat())
        }

        override fun onDraw(canvas: Canvas) {
            val scale = width / 24f
            paint.strokeWidth = lineWidth * scale
            path.reset()
            path.addRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, Path.Direction.CW)
            path.moveTo(6f, 11f)
            path.arcTo(RectF(6f, 5f, 18f, 17f), 180f, -180f, false)
            path.moveTo(12f, 17f)
            path.lineTo(12f, 21f)
            path.moveTo(8.5f, 21f)
            path.lineTo(15.5f, 21f)
            if (glyph == Glyph.MIC_SLASH) {
                path.moveTo(3.5f, 3.5f)
                path.lineTo(20.5f, 20.5f)
            }
            val matrix = android.graphics.Matrix().apply { setScale(scale, scale) }
            path.transform(matrix)
            canvas.drawPath(path, paint)
        }
    }

    private class GradientImageView(context: Context, @DrawableRes res: Int) : View(context) {
        private val drawable = androidx.core.content.ContextCompat.getDrawable(context, res)?.mutate()
        private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            gradientPaint.shader = BottomBarStyle.glyphShader(w.toFloat(), h.toFloat())
        }

        override fun onDraw(canvas: Canvas) {
            val image = drawable ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            val saved = canvas.saveLayer(0f, 0f, w, h, null)
            val iw = image.intrinsicWidth.takeIf { it > 0 }?.toFloat() ?: w
            val ih = image.intrinsicHeight.takeIf { it > 0 }?.toFloat() ?: h
            val scale = minOf(w / iw, h / ih)
            val dw = iw * scale
            val dh = ih * scale
            val left = ((w - dw) / 2f).toInt()
            val top = ((h - dh) / 2f).toInt()
            image.setBounds(left, top, left + dw.toInt(), top + dh.toInt())
            image.draw(canvas)
            canvas.drawRect(0f, 0f, w, h, gradientPaint)
            canvas.restoreToCount(saved)
        }
    }

    private class LeaveBadgeView(context: Context, private val radius: Float) : View(context) {
        val side: Int = kotlin.math.ceil(radius * 2 + 2).toInt()

        private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BottomBarStyle.BADGE_CUTOUT }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            strokePaint.shader = BottomBarStyle.glyphShader(w.toFloat(), h.toFloat())
        }

        override fun onDraw(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f
            val r = radius * density
            canvas.drawCircle(cx, cy, r + 0.6f * density, cutoutPaint)
            strokePaint.strokeWidth = 1.3f * r / 3.6f
            canvas.drawCircle(cx, cy, r, strokePaint)
            val arm = 1.4f * r / 3.6f
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, strokePaint)
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, strokePaint)
        }
    }

    private object BottomBarStyle {
        val CIRCLE_BACKGROUND = Color.argb((0.34f * 255).toInt(), 10, 10, 15)
        val CIRCLE_BORDER = Color.argb((0.08f * 255).toInt(), 255, 255, 255)
        val BADGE_CUTOUT = Color.rgb(12, 13, 18)
        private val GLYPH_COLORS =
            intArrayOf(Color.rgb(255, 122, 198), Color.rgb(192, 101, 240), Color.rgb(77, 212, 243))
        private val GLYPH_LOCATIONS = floatArrayOf(0f, 0.48f, 1f)

        fun glyphShader(w: Float, h: Float): Shader =
            LinearGradient(0f, 0f, w, h, GLYPH_COLORS, GLYPH_LOCATIONS, Shader.TileMode.CLAMP)
    }

    private class CoMicButton(context: Context) : FrameLayout(context) {
        private val badgeView = View(context)

        init {
            clipChildren = false
            val iconSize = dp(28)
            val icon = ImageView(context).apply {
                setImageResource(R.drawable.livekit_voicelounge_comic)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(icon, frameParams(iconSize, iconSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            icon.translationY = dp(13.5f) - iconSize / 2f
            val title = liveText(context, LiveTheme.TextRole.CONTROL, LiveTheme.TextTone.SECONDARY, Gravity.CENTER)
            title.text = context.getString(R.string.livekit_voicelounge_room_comic)
            title.applyBottomBarShadow()
            addView(title, frameParams(MATCH, dp(16), Gravity.TOP, top = dp(30)))
            badgeView.background = ovalDrawable(VoiceLoungeColor.rose)
            badgeView.visibility = View.GONE
            addView(badgeView, frameParams(dp(8), dp(8), Gravity.TOP or Gravity.START))
            badgeView.translationX = dp(46) / 2f + iconSize / 2f - dp(2) - dp(4)
            badgeView.translationY = dp(13.5f) - iconSize / 2f + dp(5) - dp(4)
            setPressAlpha()
        }

        fun setBadgeVisible(visible: Boolean) {
            badgeView.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private class ActionButton(
        context: Context,
        @DrawableRes image: Int,
        iconSize: Float,
        iconCenterOffset: Float,
        title: String,
    ) : FrameLayout(context) {
        private val iconView = ImageView(context)
        private val titleLabel =
            liveText(context, LiveTheme.TextRole.CONTROL, LiveTheme.TextTone.SECONDARY, Gravity.CENTER)

        init {
            clipChildren = false
            iconView.scaleType = ImageView.ScaleType.FIT_CENTER
            val size = dp(iconSize)
            addView(iconView, frameParams(size, size, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            iconView.translationY = dp(ICON_CENTER_Y + iconCenterOffset) - size / 2f
            titleLabel.maxLines = 1
            titleLabel.applyBottomBarShadow()
            addView(titleLabel, frameParams(MATCH, dp(16), Gravity.TOP, top = dp(30)))
            minimumWidth = dp(34)
            contentDescription = title
            setImage(image)
            setTitle(title)
            setPressAlpha()
        }

        fun setImage(@DrawableRes image: Int) = iconView.setImageResource(image)

        fun setTitle(title: String) {
            titleLabel.text = title
            contentDescription = title
        }

        companion object {
            private const val ICON_CENTER_Y = 13.5f

            fun fittingWidth(context: Context, title: String): Int {
                val label = liveText(context, LiveTheme.TextRole.CONTROL, LiveTheme.TextTone.SECONDARY)
                val width = kotlin.math.ceil(label.paint.measureText(title)).toInt()
                return maxOf(context.dp(34), width)
            }
        }
    }
}

private fun TextView.applyBottomBarShadow() {
    setShadowLayer(context.dp(2).toFloat(), 0f, context.dp(1).toFloat(), blackAlpha(0.35f))
}
