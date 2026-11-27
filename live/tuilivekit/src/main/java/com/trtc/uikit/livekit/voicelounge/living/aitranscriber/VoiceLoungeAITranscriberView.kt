package com.trtc.uikit.livekit.voicelounge.living.aitranscriber

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungeAITranscriberView(
    context: Context,
    private val store: VoiceLoungeAITranscriberStore,
) : FrameLayout(context) {
    var onTap: (() -> Unit)? = null

    var fitsContent = false
        set(value) {
            if (field == value) return
            field = value
            updateCardHeight()
        }

    private val cardView = FrameLayout(context)
    private val linesContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val hintLabel = TextView(context)
    private val dotsView = DotsView(context)
    private val bag = VoiceLoungeBag()

    init {
        cardView.background = roundedDrawable(VoiceLoungeColor.transcriberCard, dp(16).toFloat())
        cardView.isClickable = false
        cardView.addView(
            linesContainer,
            frameParams(
                MATCH, WRAP, Gravity.TOP, start = dp(PADDING_H), end = dp(PADDING_H), top = dp(PADDING_V),
                bottom = dp(PADDING_V)
            ),
        )
        hintLabel.gravity = Gravity.CENTER
        hintLabel.setTextColor(LiveTheme.Color.text(LiveTheme.TextTone.SECONDARY))
        hintLabel.text = context.getString(R.string.livekit_voicelounge_caption_hint_tap)
        cardView.addView(hintLabel, frameParams(MATCH, WRAP, Gravity.CENTER))
        cardView.addView(dotsView, frameParams(WRAP, WRAP, Gravity.CENTER))
        addView(cardView, frameParams(MATCH, MATCH, Gravity.CENTER_VERTICAL))
        setPressAlpha()
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap?.invoke() }
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bag.isEmpty) {
            store.changes.subscribe {
                render()
                updateCardHeight()
            }.addTo(bag)
        }
        render()
        updateCardHeight()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCardHeight()
    }

    private fun updateCardHeight() {
        val params = cardView.layoutParams as? FrameLayout.LayoutParams ?: return
        val targetHeight: Int
        val targetGravity: Int
        if (fitsContent) {
            targetHeight = measuredContentHeight()
            targetGravity = Gravity.TOP
        } else {
            targetHeight = MATCH
            targetGravity = Gravity.CENTER_VERTICAL
        }
        if (params.height == targetHeight && params.gravity == targetGravity) return
        params.height = targetHeight
        params.gravity = targetGravity
        cardView.layoutParams = params
    }

    private fun measuredContentHeight(): Int {
        val maxHeight = dp(HEIGHT)
        if (!store.isEnabled) return maxHeight
        if (store.lines.isEmpty()) return minOf(maxHeight, dp(COLLAPSED_HEIGHT))
        val contentWidth = (if (width > 0) width else resources.displayMetrics.widthPixels) - 2 * dp(PADDING_H)
        if (contentWidth <= 0) return maxHeight
        linesContainer.measure(
            MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        return minOf(maxHeight, linesContainer.measuredHeight + 2 * dp(PADDING_V))
    }

    override fun onDetachedFromWindow() {
        bag.clear()
        super.onDetachedFromWindow()
    }

    private fun render() {
        val lines = store.lines
        visibility = if (store.isEnabled) View.VISIBLE else View.GONE
        if (!store.isEnabled) return

        val textSp = (store.scale.sizeSp - 2f).coerceAtLeast(1f)
        val nameSp = (textSp - 1f).coerceAtLeast(1f)

        if (lines.isEmpty()) {
            linesContainer.visibility = View.GONE
            linesContainer.removeAllViews()
            if (store.isHintActive) {
                hintLabel.visibility = View.VISIBLE
                dotsView.visibility = View.GONE
            } else {
                hintLabel.visibility = View.GONE
                dotsView.visibility = View.VISIBLE
            }
            return
        }
        hintLabel.visibility = View.GONE
        dotsView.visibility = View.GONE
        linesContainer.visibility = View.VISIBLE
        rebuildBlocks(lines.size)
        lines.forEachIndexed { index, line ->
            val block = linesContainer.getChildAt(index) as? SpeakerBlock ?: return@forEachIndexed
            val primary = if (line.translated.isNotEmpty()) line.translated else line.source
            val original = if (store.showOriginal && line.translated.isNotEmpty() && line.source.isNotEmpty()) {
                line.source
            } else {
                null
            }
            block.bind(line.speaker, primary, original, nameSp, textSp)
        }
    }

    private fun rebuildBlocks(count: Int) {
        while (linesContainer.childCount > count) {
            linesContainer.removeViewAt(linesContainer.childCount - 1)
        }
        while (linesContainer.childCount < count) {
            val block = SpeakerBlock(context)
            linesContainer.addView(
                block, linearParams(MATCH, WRAP, top = if (linesContainer.childCount == 0) 0 else dp(6))
            )
        }
    }

    private class SpeakerBlock(context: Context) : LinearLayout(context) {
        private val nameView = GradientTextView(context)
        private val textView = TextView(context)

        init {
            orientation = VERTICAL
            nameView.maxLines = 1
            nameView.setGradientColors(VoiceLoungeColor.transcriberNameGradient)
            textView.setTextColor(LiveTheme.Color.text(LiveTheme.TextTone.PRIMARY))
            addView(nameView, linearParams(WRAP, WRAP))
            addView(textView, linearParams(MATCH, WRAP, top = dp(2)))
        }

        fun bind(name: String, text: String, original: String?, nameSp: Float, textSp: Float) {
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, nameSp)
            nameView.text = name
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            if (original != null) {
                val builder = android.text.SpannableStringBuilder()
                builder.append(text)
                builder.append("\n")
                val start = builder.length
                builder.append(original)
                builder.setSpan(
                    android.text.style.ForegroundColorSpan(LiveTheme.Color.text(LiveTheme.TextTone.TERTIARY)), start,
                    builder.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    android.text.style.RelativeSizeSpan(0.9f), start, builder.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                textView.text = builder
            } else {
                textView.text = text
            }
        }
    }

    private class GradientTextView(context: Context) : androidx.appcompat.widget.AppCompatTextView(context) {
        private var gradientColors: IntArray? = null

        fun setGradientColors(colors: IntArray) {
            gradientColors = colors
            applyShader()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            applyShader()
        }

        override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
            super.onTextChanged(text, start, lengthBefore, lengthAfter)
            applyShader()
        }

        private fun applyShader() {
            val colors = gradientColors
            val textWidth = paint.measureText(text?.toString().orEmpty())
            if (colors == null || colors.size < 2 || textWidth <= 0f) {
                paint.shader = null
                return
            }
            paint.shader = LinearGradient(0f, 0f, textWidth, 0f, colors, null, Shader.TileMode.CLAMP)
            invalidate()
        }
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
        const val COLLAPSED_HEIGHT = 48
        private const val PADDING_V = 12
        private const val PADDING_H = 14
    }
}
