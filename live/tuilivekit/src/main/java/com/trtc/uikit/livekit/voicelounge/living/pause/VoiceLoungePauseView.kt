package com.trtc.uikit.livekit.voicelounge.living.pause

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLiveClock
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import java.util.Locale

internal class VoiceLoungePauseView(
    context: Context,
    private val role: VoiceLoungeRole,
    private val clock: VoiceLoungeLiveClock,
) : FrameLayout(context) {
    var onResume: (() -> Unit)? = null

    private val titleLabel = TextView(context)
    private val timerLabel = TextView(context)
    private val resumeButton = TextView(context)
    private var renderedSeconds: Int? = null
    private val ticker = object : Runnable {
        override fun run() {
            renderTimer()
            postDelayed(this, TICK_MS)
        }
    }

    var bottomInset: Int = 0
        set(value) {
            field = value
            (resumeButton.layoutParams as LayoutParams).bottomMargin = value + dp(42)
            resumeButton.requestLayout()
        }

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(Color.BLACK.withAlpha(0.9f))
        titleLabel.setTextColor(Color.WHITE)
        titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        titleLabel.gravity = Gravity.CENTER
        titleLabel.includeFontPadding = false
        timerLabel.setTextColor(Color.WHITE)
        timerLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        timerLabel.gravity = Gravity.CENTER
        timerLabel.includeFontPadding = false
        timerLabel.text = "00:00"
        resumeButton.setTextColor(Color.rgb(17, 17, 17))
        resumeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        resumeButton.typeface = Typeface.DEFAULT_BOLD
        resumeButton.gravity = Gravity.CENTER
        resumeButton.includeFontPadding = false
        resumeButton.background = roundedDrawable(Color.WHITE, dp(10).toFloat())
        resumeButton.elevation = dp(8).toFloat()
        resumeButton.setPressAlpha()
        resumeButton.setOnClickListener { onResume?.invoke() }

        val center = FrameLayout(context)
        addView(center, frameParams(MATCH, dp(29 + 14 + 42), Gravity.CENTER, bottom = dp(32)))
        center.addView(titleLabel, frameParams(MATCH, dp(29), Gravity.TOP))
        center.addView(timerLabel, frameParams(MATCH, dp(42), Gravity.TOP, top = dp(29 + 14)))
        addView(
            resumeButton, frameParams(dp(205), dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bottom = dp(42))
        )
        applyRole()
    }

    fun setPaused(paused: Boolean) {
        visibility = if (paused) View.VISIBLE else View.GONE
        renderedSeconds = null
        updateTimer()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTimer()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private fun updateTimer() {
        removeCallbacks(ticker)
        if (visibility != View.VISIBLE || role != VoiceLoungeRole.HOST || !isAttachedToWindow) return
        ticker.run()
    }

    private fun applyRole() {
        val audience = role == VoiceLoungeRole.AUDIENCE
        titleLabel.text = context.getString(
            if (audience) R.string.livekit_voiceroom_living_paused_audience else R.string.livekit_voiceroom_living_paused_title,
        )
        timerLabel.visibility = if (audience) View.GONE else View.VISIBLE
        resumeButton.text = context.getString(
            if (audience) R.string.livekit_voiceroom_living_leave_room else R.string.livekit_voiceroom_living_resume,
        )
    }

    private fun renderTimer() {
        val seconds = clock.currentPauseSeconds
        if (renderedSeconds == seconds) return
        renderedSeconds = seconds
        timerLabel.text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
