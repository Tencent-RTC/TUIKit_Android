package com.trtc.uikit.livekit.voicelounge.living.aitranscriber

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungeAITranscriberView(
    context: Context,
    private val store: VoiceLoungeAITranscriberStore,
) : FrameLayout(context) {
    private val textLabel = voiceLoungeText(context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.SECONDARY)
    private val dotsView = DotsView(context)
    private val bag = VoiceLoungeBag()

    init {
        addView(textLabel, frameParams(MATCH, WRAP, Gravity.TOP, start = dp(14), top = dp(14), end = dp(14)))
        addView(dotsView, frameParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bottom = dp(14)))
        background = roundedDrawable(VoiceLoungeColor.card, dp(16).toFloat())
        isClickable = false
        textLabel.ellipsize = TextUtils.TruncateAt.END
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bag.isEmpty) store.changes.subscribe { render() }.addTo(bag)
        render()
    }

    override fun onDetachedFromWindow() {
        bag.clear()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val lineHeight = textLabel.lineHeight.coerceAtLeast(1)
        val available = h - dp(14) - dp(14) - dp(5) - dp(8)
        textLabel.maxLines = maxOf(1, available / lineHeight)
    }

    private fun render() {
        visibility = if (store.isEnabled) View.VISIBLE else View.GONE
        if (!store.isEnabled) return
        val lines = store.lines
        if (lines.isEmpty()) {
            textLabel.text = context.getString(R.string.livekit_voiceroom_caption_waiting)
            return
        }
        val text = SpannableStringBuilder()
        lines.forEachIndexed { index, line ->
            if (index > 0) text.append("\n")
            appendColored(text, "${line.speaker}：", VoiceLoungeColor.nickname)
            appendColored(text, line.text, LiveTheme.Color.text(LiveTheme.TextTone.PRIMARY))
        }
        textLabel.text = text
    }

    private fun appendColored(builder: SpannableStringBuilder, value: String, color: Int) {
        val start = builder.length
        builder.append(value)
        builder.setSpan(ForegroundColorSpan(color), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private class DotsView(context: Context) : LinearLayout(context) {
        private val sizes = listOf(3, 4, 5, 5, 4, 3)
        private val dots = mutableListOf<View>()
        private val animators = mutableListOf<ObjectAnimator>()

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            sizes.forEachIndexed { index, size ->
                val dot = View(context)
                dot.background = ovalDrawable(whiteAlpha(0.55f))
                addView(dot, linearParams(dp(size), dp(size), start = if (index == 0) 0 else dp(6)))
                dots.add(dot)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animators.forEach { it.cancel() }
            animators.clear()
            dots.forEachIndexed { index, dot ->
                animators += ObjectAnimator.ofFloat(dot, View.ALPHA, 0.3f, 1f).apply {
                    duration = 600
                    startDelay = index * 120L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        }

        override fun onDetachedFromWindow() {
            animators.forEach { it.cancel() }
            animators.clear()
            super.onDetachedFromWindow()
        }
    }

    companion object {
        const val HEIGHT = 146
    }
}
