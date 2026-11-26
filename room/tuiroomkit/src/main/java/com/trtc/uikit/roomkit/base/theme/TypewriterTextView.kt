package com.trtc.uikit.roomkit.base.theme

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.roomkit.base.util.dp
import kotlin.math.roundToInt

class TypewriterTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var formatText: ((String) -> CharSequence)? = null
        set(value) {
            field = value
            renderText()
        }
    var showCaret = false
        set(value) {
            field = value
            updateCaretAnimation()
        }
    var showLastLineOnly = false
        set(value) {
            field = value
            if (value) {
                setHorizontallyScrolling(false)
                maxLines = 1
                ellipsize = null
            }
            renderText()
        }

    private val handler = Handler(Looper.getMainLooper())
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val caretAnimator = ValueAnimator.ofFloat(0.7f, 0f).apply {
        duration = 450L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            caretAlpha = it.animatedValue as Float
            invalidate()
        }
    }
    private var runnable: Runnable? = null
    private var contentId = ""
    private var targetText = ""
    private var displayedText = ""
    private var isStreaming = false
    private var caretAlpha = 0.7f

    fun updateText(contentId: String, text: String, isStreaming: Boolean) {
        this.isStreaming = isStreaming
        if (this.contentId != contentId) {
            stop()
            this.contentId = contentId
            displayedText = ""
            renderText()
        }
        targetText = text
        if (!isStreaming || text.isEmpty()) {
            finishTypewriter()
            updateCaretAnimation()
            return
        }
        if (displayedText != text && !text.startsWith(displayedText)) {
            val unchangedLength = text.commonPrefixWith(displayedText).length
            displayedText = displayedText.substring(0, unchangedLength)
            renderText()
        }
        if (displayedText != text) start()
        updateCaretAnimation()
    }

    private fun finishTypewriter() {
        stop()
        displayedText = targetText
        renderText()
    }

    private fun renderText() {
        val value = if (showLastLineOnly) lastLine(displayedText) else displayedText
        text = formatText?.invoke(value) ?: value
    }

    @Suppress("DEPRECATION")
    private fun lastLine(value: String): String {
        val availableWidth = width - compoundPaddingLeft - compoundPaddingRight
        if (value.isEmpty() || availableWidth <= 0) return value
        val textLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(value, 0, value.length, paint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                .setIncludePad(includeFontPadding)
                .build()
        } else {
            StaticLayout(
                value,
                paint,
                availableWidth,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacingMultiplier,
                lineSpacingExtra,
                includeFontPadding,
            )
        }
        if (textLayout.lineCount <= 1) return value
        return value.substring(textLayout.getLineStart(textLayout.lineCount - 1))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth) renderText()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateCaretAnimation()
    }

    override fun onDetachedFromWindow() {
        caretAnimator.cancel()
        finishTypewriter()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isShown) finishTypewriter()
        updateCaretAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val textLayout = layout ?: return
        if (!showCaret || !isStreaming || textLayout.lineCount == 0) return
        val line = textLayout.lineCount - 1
        val lineTop = textLayout.getLineTop(line).toFloat()
        val lineBottom = textLayout.getLineBottom(line).toFloat()
        val caretHeight = textSize * 0.95f
        val caretLeft = compoundPaddingLeft + textLayout.getLineRight(line) + context.dp(3)
        val caretTop = totalPaddingTop + lineTop + (lineBottom - lineTop - caretHeight) / 2
        caretPaint.color = currentTextColor
        caretPaint.alpha = (caretAlpha * 255).roundToInt()
        canvas.drawRect(
            caretLeft,
            caretTop,
            caretLeft + context.dp(2),
            caretTop + caretHeight,
            caretPaint,
        )
    }

    private fun start() {
        if (runnable != null) return
        val task = object : Runnable {
            override fun run() {
                val nextLength = displayedText.length + 1
                if (nextLength > targetText.length) {
                    runnable = null
                    return
                }
                displayedText = targetText.substring(0, nextLength)
                renderText()
                if (displayedText.length < targetText.length) {
                    handler.postDelayed(this, 50L)
                } else {
                    runnable = null
                }
            }
        }
        runnable = task
        handler.post(task)
    }

    private fun stop() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun updateCaretAnimation() {
        val shouldAnimate = showCaret && isStreaming && isAttachedToWindow && isShown
        if (shouldAnimate) {
            if (!caretAnimator.isStarted) caretAnimator.start()
        } else {
            caretAnimator.cancel()
            caretAlpha = 0.7f
        }
    }
}
