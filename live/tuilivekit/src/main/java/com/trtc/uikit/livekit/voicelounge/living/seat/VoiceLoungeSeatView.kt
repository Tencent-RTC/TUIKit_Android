package com.trtc.uikit.livekit.voicelounge.living.seat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.CenteredImageSpan
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.clearImage
import com.trtc.uikit.livekit.voicelounge.clipOval
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.loadUrl
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.placeholderColor
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha

internal class VoiceLoungeSeatView(
    context: Context,
    private val avatarSize: Float,
    private val emptyTitle: String,
    private val showsName: Boolean = true,
    private val nameGap: Float = DEFAULT_NAME_GAP,
    private val isCompact: Boolean = false,
    private val showsWave: Boolean = true,
) : FrameLayout(context) {
    var onTap: ((Int) -> Unit)? = null

    val avatarView: View get() = avatarContainer
    val avatarDiameterPx: Int get() = dp(avatarSize)

    private val waveView = WaveRingsView(context, avatarSize)
    private val avatarContainer = FrameLayout(context)
    private val avatarImageView = ImageView(context)
    private val initialLabel = voiceLoungeText(context, LiveTheme.TextRole.BODY, gravity = Gravity.CENTER)
    private val emptyRing = EmptyRingView(context)
    private val nameLabel = voiceLoungeText(
        context, LiveTheme.TextRole.CONTROL, LiveTheme.TextTone.SECONDARY, Gravity.CENTER,
    )
    private val pillLabel = TextView(context)
    private val adminBadge = TextView(context)

    private var index = 0
    private var appliedAvatarURL: String? = null
    private var failedAvatarURL: String? = null
    private var ringState = RingState.IDLE

    init {
        val avatarPx = dp(avatarSize)
        val ringPx = dp(avatarSize + 8)
        val inset = (ringPx - avatarPx) / 2
        val waveSide = waveView.sidePx
        addView(
            waveView,
            frameParams(waveSide, waveSide, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = (ringPx - waveSide) / 2),
        )
        addView(emptyRing, frameParams(avatarPx, avatarPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = inset))
        addView(
            avatarContainer,
            frameParams(avatarPx, avatarPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = inset),
        )
        avatarContainer.addView(avatarImageView, frameParams(MATCH, MATCH))
        avatarContainer.addView(initialLabel, frameParams(MATCH, MATCH))
        val nameTop = ringPx + dp(nameGap)
        if (showsName) {
            addView(nameLabel, frameParams(MATCH, dp(NAME_HEIGHT), Gravity.TOP, top = nameTop))
        }
        val badgeHeight = dp(if (showsName) 16 else 12)
        addView(
            adminBadge,
            frameParams(
                WRAP, badgeHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                top = inset + avatarPx - dp(1) - badgeHeight / 2,
            ),
        )
        val pillHeight = dp(if (isCompact) 16 else 18)
        val pillCenter = if (showsName) nameTop + dp(NAME_HEIGHT) / 2 else ringPx
        addView(
            pillLabel,
            frameParams(WRAP, pillHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = pillCenter - pillHeight / 2),
        )
        setupStyles()
        setOnClickListener { onTap?.invoke(index) }
    }

    private fun setupStyles() {
        clipChildren = false
        clipToPadding = false
        avatarContainer.background = ovalDrawable(VoiceLoungeSeatStyle.seatBackground)
        avatarContainer.clipOval()
        avatarImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        initialLabel.visibility = View.GONE
        nameLabel.maxLines = 1
        nameLabel.isSingleLine = true
        nameLabel.ellipsize = TextUtils.TruncateAt.MIDDLE
        nameLabel.visibility = if (showsName) View.VISIBLE else View.GONE

        adminBadge.text = context.getString(R.string.livekit_voicelounge_seat_admin_badge)
        adminBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (showsName) 7f else 5f)
        adminBadge.typeface = Typeface.DEFAULT_BOLD
        adminBadge.setTextColor(Color.WHITE)
        adminBadge.gravity = Gravity.CENTER
        adminBadge.includeFontPadding = false
        adminBadge.setPadding(dp(6), 0, dp(6), 0)
        adminBadge.minWidth = dp(if (showsName) 30 else 24)
        adminBadge.visibility = View.GONE
        applyBadgeColor(VoiceLoungeSeatStyle.adminBadge)

        pillLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCompact) 7f else 10f)
        pillLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        pillLabel.gravity = Gravity.CENTER
        pillLabel.includeFontPadding = false
        val pillPaddingPx = dp(if (isCompact) 5 else 8)
        pillLabel.setPadding(pillPaddingPx, 0, pillPaddingPx, 0)
        pillLabel.maxLines = 1
        pillLabel.visibility = View.GONE
    }

    private fun applyBadgeColor(color: Int) {
        adminBadge.background = roundedDrawable(
            color, dp(if (showsName) 7 else 6).toFloat(), dp(1), blackAlphaColor(0.25f),
        )
    }

    fun configure(seat: VoiceLoungeSeat) {
        index = seat.index
        val faded = seat.state == VoiceLoungeSeat.State.FADED
        val occupied = seat.state == VoiceLoungeSeat.State.OCCUPIED || faded
        val locked = seat.state == VoiceLoungeSeat.State.LOCKED

        avatarContainer.visibility = if (occupied) View.VISIBLE else View.GONE
        avatarContainer.alpha = if (faded) 0.5f else 1f
        emptyRing.visibility = if (occupied) View.GONE else View.VISIBLE
        avatarImageView.visibility = if (occupied) View.VISIBLE else View.GONE
        val showsInitial = occupied && (seat.avatarURL.isEmpty() || failedAvatarURL == seat.avatarURL)
        initialLabel.visibility = if (showsInitial) View.VISIBLE else View.GONE
        adminBadge.visibility =
            if (seat.state == VoiceLoungeSeat.State.OCCUPIED && seat.isAdmin) View.VISIBLE else View.GONE
        seat.accentColor?.let { applyBadgeColor(it) }
        emptyRing.isLocked = locked

        val pill = if (faded) seat.pillText else ""
        pillLabel.visibility = if (pill.isEmpty()) View.GONE else View.VISIBLE
        if (pill.isNotEmpty()) {
            pillLabel.text = pill
            pillLabel.setTextColor(if (seat.isPillHighlighted) Color.WHITE else whiteAlpha(0.8f))
            val accent = seat.accentColor
            val background = if (accent != null) {
                accent.withAlpha(if (seat.isPillHighlighted) 0.9f else 0.45f)
            } else if (seat.isPillHighlighted) {
                VoiceLoungeSeatStyle.pillHighlighted
            } else {
                VoiceLoungeSeatStyle.pillNormal
            }
            pillLabel.background = roundedDrawable(background, dp(9).toFloat())
        }

        if (occupied) {
            val showsMuteIcon = seat.state == VoiceLoungeSeat.State.OCCUPIED && seat.isMuted
            if (pill.isEmpty()) setName(seat.name, showsMuteIcon) else nameLabel.text = ""
            bindAvatar(seat)
        } else {
            nameLabel.text = if (locked) "" else emptyTitle
            avatarImageView.clearImage()
            appliedAvatarURL = null
        }

        val onSeat = seat.state == VoiceLoungeSeat.State.OCCUPIED
        updateRing(if (showsWave && onSeat && !seat.isMuted && seat.isSpeaking) RingState.SPEAKING else RingState.IDLE)
    }

    private fun bindAvatar(seat: VoiceLoungeSeat) {
        val name = seat.name
        if (seat.avatarURL.isEmpty()) {
            avatarContainer.background = ovalDrawable(placeholderColor(name, VoiceLoungeColor.avatarPlaceholders))
            initialLabel.text = name.take(1).uppercase()
            avatarImageView.clearImage()
            appliedAvatarURL = null
            return
        }
        if (appliedAvatarURL == seat.avatarURL) return
        val url = seat.avatarURL
        appliedAvatarURL = url
        failedAvatarURL = null
        avatarContainer.background = ovalDrawable(VoiceLoungeSeatStyle.seatBackground)
        avatarImageView.setImageDrawable(null)
        avatarImageView.loadUrl(url) {
            post {
                if (appliedAvatarURL != url) return@post
                failedAvatarURL = url
                avatarContainer.background = ovalDrawable(placeholderColor(name, VoiceLoungeColor.avatarPlaceholders))
                initialLabel.text = name.take(1).uppercase()
                initialLabel.visibility = View.VISIBLE
            }
        }
    }

    private fun setName(name: String, muted: Boolean) {
        val icon =
            if (muted) ContextCompat.getDrawable(context, R.drawable.livekit_coguest_seat_muted)?.mutate() else null
        if (icon == null) {
            nameLabel.text = name
            return
        }
        val side = nameLabel.textSize.toInt()
        icon.setBounds(0, 0, side, side)
        icon.setTint(whiteAlpha(0.7f))
        val text = SpannableStringBuilder("  $name")
        text.setSpan(CenteredImageSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        nameLabel.text = text
    }

    private fun updateRing(state: RingState) {
        if (state == ringState) return
        ringState = state
        waveView.setSpeaking(state == RingState.SPEAKING)
    }

    private enum class RingState { IDLE, SPEAKING }

    class WaveRingsView(context: Context, avatarSize: Float) : View(context) {
        private val strokeWidth = context.dp(VoiceLoungeSeatStyle.WAVE_RING_WIDTH).toFloat()
        private var baseRadius = 0f
        var sidePx = 0
            private set

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = this@WaveRingsView.strokeWidth
            color = VoiceLoungeSeatStyle.waveRing
        }
        private val easing = PathInterpolator(0.16f, 0.5f, 0.4f, 1f)
        private var speaking = false
        private var isStatic = false
        private var progress = 0f
        private var animator: ValueAnimator? = null

        init {
            visibility = GONE
            setAvatarSize(avatarSize)
        }

        fun setAvatarSize(avatarSize: Float) {
            baseRadius = context.dp(avatarSize / 2 + VoiceLoungeSeatStyle.WAVE_RING_OFFSET).toFloat()
            sidePx = kotlin.math.ceil(baseRadius * 2 * VoiceLoungeSeatStyle.WAVE_MAX_SCALE + strokeWidth * 2).toInt()
            invalidate()
        }

        fun setSpeaking(value: Boolean) {
            if (speaking == value) return
            speaking = value
            visibility = if (value) VISIBLE else GONE
            if (value) start() else stop()
        }

        private fun start() {
            stop()
            if (!isAttachedToWindow || !speaking) return
            isStatic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()
            if (isStatic) {
                invalidate()
                return
            }
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = VoiceLoungeSeatStyle.WAVE_DURATION_MS
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun stop() {
            animator?.cancel()
            animator = null
            progress = 0f
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (speaking) start()
        }

        override fun onDetachedFromWindow() {
            stop()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!speaking) return
            val cx = width / 2f
            val cy = height / 2f
            if (isStatic) {
                ringPaint.alpha = (VoiceLoungeSeatStyle.WAVE_STATIC_OPACITY * WAVE_RING_ALPHA).toInt()
                canvas.drawCircle(cx, cy, baseRadius, ringPaint)
                return
            }
            for (i in 0 until RING_COUNT) {
                val phase = (progress + i.toFloat() / RING_COUNT) % 1f
                val t = easing.getInterpolation(phase)
                val scale = 1f + (VoiceLoungeSeatStyle.WAVE_MAX_SCALE - 1f) * t
                val opacity = waveOpacity(t)
                if (opacity <= 0f) continue
                ringPaint.alpha = (opacity * WAVE_RING_ALPHA).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, baseRadius * scale, ringPaint)
            }
        }

        private fun waveOpacity(t: Float): Float {
            for (k in 1 until OPACITY_TIMES.size) {
                if (t <= OPACITY_TIMES[k]) {
                    val t0 = OPACITY_TIMES[k - 1]
                    val f = (t - t0) / (OPACITY_TIMES[k] - t0)
                    return OPACITY_VALUES[k - 1] + (OPACITY_VALUES[k] - OPACITY_VALUES[k - 1]) * f
                }
            }
            return 0f
        }

        companion object {
            private const val RING_COUNT = 2
            private const val WAVE_RING_ALPHA = 255f * 0.8f
            private val OPACITY_VALUES = floatArrayOf(0f, 0.62f, 0.22f, 0f)
            private val OPACITY_TIMES = floatArrayOf(0f, 0.14f, 0.7f, 1f)
        }
    }

    private class EmptyRingView(context: Context) : View(context) {
        var isLocked = false
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = VoiceLoungeSeatStyle.emptyFill
        }
        private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = VoiceLoungeSeatStyle.emptyStroke
            strokeWidth = context.dp(1).toFloat()
            val dash = context.dp(3).toFloat()
            pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
        }
        private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = VoiceLoungeSeatStyle.emptyStroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val micIcon = ContextCompat.getDrawable(context, R.drawable.livekit_coguest_seat_mic)?.mutate()?.apply {
            setTint(VoiceLoungeSeatStyle.emptyStroke)
        }
        private val oval = RectF()
        private val lockPath = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val half = dashPaint.strokeWidth / 2
            oval.set(half, half, w - half, h - half)
            canvas.drawOval(oval, fillPaint)
            canvas.drawOval(oval, dashPaint)
            if (isLocked) {
                drawLock(canvas, w / 24f)
            } else {
                val side = (w * 0.5f).toInt()
                val left = ((w - side) / 2).toInt()
                val top = ((h - side) / 2).toInt()
                micIcon?.setBounds(left, top, left + side, top + side)
                micIcon?.draw(canvas)
            }
        }

        private fun drawLock(canvas: Canvas, s: Float) {
            lockPath.reset()
            lockPath.addRoundRect(RectF(8f * s, 11f * s, 16f * s, 18f * s), 1.5f * s, 1.5f * s, Path.Direction.CW)
            lockPath.moveTo(9.8f * s, 11f * s)
            lockPath.lineTo(9.8f * s, 9f * s)
            lockPath.arcTo(RectF(9.8f * s, 6.8f * s, 14.2f * s, 11.2f * s), 180f, 180f, false)
            lockPath.lineTo(14.2f * s, 11f * s)
            lockPaint.strokeWidth = context.dp(1.6f).toFloat()
            canvas.drawPath(lockPath, lockPaint)
        }
    }

    companion object {
        const val DEFAULT_NAME_GAP = 2f
        private const val NAME_HEIGHT = 17

        fun height(context: Context, avatarSize: Float, nameGap: Float = DEFAULT_NAME_GAP): Int =
            context.dp(avatarSize + 8 + nameGap + NAME_HEIGHT)

        private fun blackAlphaColor(alpha: Float): Int = Color.BLACK.withAlpha(alpha)
    }
}

internal object VoiceLoungeSeatStyle {
    val seatBackground = VoiceLoungeColor.overlaySoft
    val emptyStroke = whiteAlpha(0.4f)
    val emptyFill = whiteAlpha(0.04f)
    val waveRing = Color.WHITE
    const val WAVE_RING_WIDTH = 2f
    const val WAVE_RING_OFFSET = 2f
    const val WAVE_MAX_SCALE = 1.38f
    const val WAVE_DURATION_MS = 1400L
    const val WAVE_STATIC_OPACITY = 0.58f
    const val FOCUS_AVATAR = 64f
    const val GUEST_AVATAR = 52f
    val adminBadge = rgb(0.27f, 0.55f, 1.0f, 0.95f)
    val pillNormal = rgb(0.42f, 0.40f, 0.75f, 0.45f)
    val pillHighlighted = rgb(0.45f, 0.42f, 0.88f, 0.9f)
}
