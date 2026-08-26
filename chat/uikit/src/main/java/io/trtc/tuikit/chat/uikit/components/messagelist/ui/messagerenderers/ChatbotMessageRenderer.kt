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
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageProtocol
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSource
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.RecyclableMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import kotlin.math.PI
import kotlin.math.sin

internal class ChatbotMessageRenderer : MessageRenderer, RecyclableMessageRenderer {
    override fun createView(context: Context, parent: ViewGroup): View {
        return ChatbotMessageContentView(context)
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val contentView = view as ChatbotMessageContentView
        val data = ChatbotMessageProtocol.parse(message)
        if (data == null) {
            contentView.bind("", false, colors)
            return
        }
        contentView.bind(
            text = data.displayText,
            isLoading = data.isPlaceholder ||
                (data.source == ChatbotMessageSource.FLOW && !data.isFinished),
            colors = colors
        )
    }

    override fun onViewRecycled(view: View) {
        (view as? ChatbotMessageContentView)?.recycle()
    }
}

private class ChatbotMessageContentView(
    context: Context
) : LinearLayout(context), TextMessageWidthAware {
    private val density = resources.displayMetrics.density
    private val textView = AppCompatTextView(context)
    private val loadingView = ChatbotLoadingView(context)
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

    fun bind(
        text: String,
        isLoading: Boolean,
        colors: ColorTokens
    ) {
        textView.setTextColor(colors.textColorPrimary)
        textView.visibility = if (text.isEmpty()) GONE else VISIBLE
        loadingView.setDotColor(colors.textColorSecondary)
        loadingView.setLoading(isLoading)
        updateTextMaxWidth(isLoading)

        val bindToken = "$text|${textView.textSize}"
        textView.setTag(R.id.message_list_text_bind_token_tag, bindToken)
        textView.text = text
        if (text.isNotEmpty()) {
            EmojiSpanHelper.setEmojiSpanText(
                context = context,
                text = text,
                textSizePx = textView.textSize,
                requestView = textView
            ) { spanned ->
                if (textView.getTag(R.id.message_list_text_bind_token_tag) == bindToken) {
                    textView.text = spanned
                }
            }
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

private class ChatbotLoadingView(context: Context) : View(context) {
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
