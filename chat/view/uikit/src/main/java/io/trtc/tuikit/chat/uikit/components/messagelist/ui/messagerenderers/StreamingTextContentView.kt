package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import io.trtc.tuikit.chat.uikit.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * 「文本 + 三点加载动画」气泡，供逐步生成的消息共用。
 *
 * 由 ChatbotMessageRenderer 内部的 ChatbotMessageContentView 原样抽出，
 * 现在 ChatbotMessageRenderer（CUSTOM 里的 chatbot 协议消息）和
 * StreamMessageRenderer（TIMStreamElem 流式消息）都用它。
 * 颜色由调用方传入，因为两者对自发/收到消息的取色规则不同。
 */
internal class StreamingTextContentView(
    context: Context
) : LinearLayout(context), TextMessageWidthAware {
    private val density = resources.displayMetrics.density
    private val textView = AppCompatTextView(context)
    private val loadingView = StreamingLoadingDotsView(context)
    private var messageMaxWidth = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM
        val horizontalPadding = (12 * density).toInt()
        val verticalPadding = (8 * density).toInt()
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        textView.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.3f)
            includeFontPadding = false
        }
        MarkdownLinkTouchListener.attach(textView)
        addView(
            textView,
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            loadingView,
            LayoutParams(
                (24 * density).toInt(),
                (20 * density).toInt()
            ).apply {
                marginStart = (4 * density).toInt()
                gravity = Gravity.BOTTOM
            }
        )
    }

    /**
     * [isStreaming] 只影响 Markdown 渲染：正文还在生成时语法可能是断的（未闭合的代码块、
     * 表格），需要先做补全处理。[showLoadingDots] 单独控制文末的三点动画，因为
     * TIMStreamElem 这条路径要的是"边出字边渲染、但不显示加载点"。
     */
    fun bind(
        text: String,
        isStreaming: Boolean,
        textColor: Int,
        dotColor: Int,
        showLoadingDots: Boolean = isStreaming
    ) {
        textView.setTextColor(textColor)
        textView.visibility = if (text.isEmpty()) GONE else VISIBLE
        loadingView.setDotColor(dotColor)
        loadingView.setLoading(showLoadingDots)
        updateTextMaxWidth(showLoadingDots)

        val bindToken = "$text|${textView.textSize}|$isStreaming|$showLoadingDots"
        textView.setTag(R.id.message_list_text_bind_token_tag, bindToken)
        if (text.isEmpty()) {
            textView.text = null
        } else {
            MarkdownMessageHelper.render(
                textView = textView,
                markdown = text,
                isStreaming = isStreaming,
                textColor = textColor
            )
        }
    }

    override fun setMessageMaxWidth(maxWidth: Int) {
        messageMaxWidth = maxWidth.coerceAtLeast(0)
        updateTextMaxWidth(loadingView.visibility == VISIBLE)
    }

    fun recycle() {
        textView.setTag(R.id.message_list_text_bind_token_tag, null)
        textView.text = null
        loadingView.setLoading(false)
    }

    private fun updateTextMaxWidth(isLoading: Boolean) {
        if (messageMaxWidth <= 0) {
            textView.maxWidth = Int.MAX_VALUE
            return
        }
        val loadingWidth = if (isLoading) {
            val params = loadingView.layoutParams as LayoutParams
            params.width + params.marginStart + params.marginEnd
        } else {
            0
        }
        textView.maxWidth = (
            messageMaxWidth -
                paddingLeft -
                paddingRight -
                loadingWidth
            ).coerceAtLeast(0)
    }
}

private class StreamingLoadingDotsView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null

    fun setDotColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setLoading(loading: Boolean) {
        visibility = if (loading) VISIBLE else GONE
        if (loading && isAttachedToWindow) {
            startAnimator()
        } else if (!loading) {
            stopAnimator()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) {
            startAnimator()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimator()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 2f * density
        val spacing = 7f * density
        val totalWidth = spacing * 2f
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        repeat(3) { index ->
            val wave = (sin((phase - index / 3f) * 2f * PI).toFloat() + 1f) / 2f
            paint.alpha = (DOT_MIN_ALPHA + wave * (255 - DOT_MIN_ALPHA)).toInt()
            canvas.drawCircle(startX + spacing * index, centerY, radius, paint)
        }
        paint.alpha = 255
    }

    private fun startAnimator() {
        if (animator?.isStarted == true) {
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimator() {
        animator?.cancel()
        animator = null
        phase = 0f
    }

    private companion object {
        const val DOT_MIN_ALPHA = 64
    }
}
