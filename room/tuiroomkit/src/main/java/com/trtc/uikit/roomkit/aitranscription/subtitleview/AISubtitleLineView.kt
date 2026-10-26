package com.trtc.uikit.roomkit.aitranscription.subtitleview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.aitranscription.config.TextStyle

class AISubtitleLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val textView: TextView

    private var fullText: String = ""
    private var currentCharIndex: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private var streamRunnable: Runnable? = null
    private var streamAnimationIntervalMs: Long = 30L

    private var maxLinesValue: Int = 0

    var onTextUpdateCompleted: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_ai_subtitle_line, this, true)
        textView = findViewById(R.id.tv_subtitle_line)
    }

    // MARK: - Configuration

    fun updateMaxLines(maxLines: Int) {
        this.maxLinesValue = maxLines
        textView.maxLines = if (maxLines > 0) maxLines else Int.MAX_VALUE
        applyTextToView(textView.text?.toString().orEmpty())
    }

    fun configure(style: TextStyle, animationIntervalMs: Long = 30L) {
        this.streamAnimationIntervalMs = animationIntervalMs

        textView.setTextColor(style.textColor)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize)
        textView.typeface = style.typeface
        textView.setShadowLayer(style.shadowRadius, style.shadowDx, style.shadowDy, style.shadowColor)
        applyTextToView(textView.text?.toString().orEmpty())
    }

    private fun applyTextToView(text: String) {
        val maxLines = maxLinesValue
        if (maxLines <= 0 || text.isEmpty()) {
            textView.text = text
            return
        }

        val innerWidth = textView.width - textView.paddingLeft - textView.paddingRight
        if (innerWidth <= 0) {
            textView.text = text
            textView.post { if (textView.width > 0) applyTextToView(fullText.ifEmpty { text }) }
            return
        }

        val layout = buildStaticLayout(text, TextPaint(textView.paint), innerWidth)
        if (layout.lineCount <= maxLines) {
            textView.text = text
            return
        }

        val firstKeptLine = layout.lineCount - maxLines
        val startChar = layout.getLineStart(firstKeptLine)
        textView.text = text.substring(startChar)
    }

    @Suppress("DEPRECATION")
    private fun buildStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(textView.lineSpacingExtra, textView.lineSpacingMultiplier)
                .setIncludePad(textView.includeFontPadding)
                .build()
        } else {
            StaticLayout(
                text, paint, width, Layout.Alignment.ALIGN_NORMAL,
                textView.lineSpacingMultiplier, textView.lineSpacingExtra,
                textView.includeFontPadding
            )
        }
    }

    // MARK: - Text Update

    fun updateText(text: String, animated: Boolean = true) {
        cancelStreamAnimation()

        val previousText = fullText
        fullText = text

        if (animated && text.isNotEmpty()) {
            if (text.startsWith(previousText) && text.length > previousText.length) {
                currentCharIndex = previousText.length
                startStreamAnimation()
            } else {
                currentCharIndex = 0
                applyTextToView("")
                startStreamAnimation()
            }
        } else {
            applyTextToView(text)
            onTextUpdateCompleted?.invoke()
        }
    }

    fun appendText(text: String, animated: Boolean = true) {
        cancelStreamAnimation()

        fullText += text
        currentCharIndex = fullText.length

        if (animated) {
            textView.animate().alpha(0.95f).setDuration(50).withEndAction {
                applyTextToView(fullText)
                textView.alpha = 1f
            }.start()
        } else {
            applyTextToView(fullText)
        }
    }

    fun clearText() {
        cancelStreamAnimation()
        fullText = ""
        currentCharIndex = 0
        textView.text = ""
    }

    val currentText: String get() = fullText

    // MARK: - Stream Animation

    private fun startStreamAnimation() {
        streamRunnable = object : Runnable {
            override fun run() {
                if (currentCharIndex < fullText.length) {
                    currentCharIndex++
                    val displayText = fullText.substring(0, currentCharIndex)
                    applyTextToView(displayText)
                    handler.postDelayed(this, streamAnimationIntervalMs)
                } else {
                    streamRunnable = null
                    onTextUpdateCompleted?.invoke()
                }
            }
        }
        handler.post(streamRunnable!!)
    }

    private fun cancelStreamAnimation() {
        streamRunnable?.let { handler.removeCallbacks(it) }
        streamRunnable = null
    }

    // MARK: - Fade Animation

    fun fadeIn(durationMs: Long = 300L, completion: (() -> Unit)? = null) {
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(durationMs).withEndAction { completion?.invoke() }.start()
    }

    fun fadeOut(durationMs: Long = 500L, completion: (() -> Unit)? = null) {
        animate().alpha(0f).setDuration(durationMs).withEndAction {
            visibility = View.GONE
            completion?.invoke()
        }.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelStreamAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw && fullText.isNotEmpty()) {
            val shown = if (streamRunnable != null) {
                fullText.substring(0, currentCharIndex.coerceAtMost(fullText.length))
            } else {
                fullText
            }
            applyTextToView(shown)
        }
    }
}
