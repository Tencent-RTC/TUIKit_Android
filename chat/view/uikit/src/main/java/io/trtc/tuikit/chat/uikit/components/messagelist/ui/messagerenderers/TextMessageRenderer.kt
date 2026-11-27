package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload

class TextMessageRenderer : MessageRenderer {

    override fun createView(context: Context, parent: ViewGroup): View {
        return CompactTextMessageView(context).apply {
            val density = context.resources.displayMetrics.density
            val horizontalPadding = (12 * density).toInt()
            val verticalPadding = (8 * density).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.3f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val textView = view as TextView
        textView.setTextColor(
            if (message.isSentBySelf) colors.textColorAntiPrimary else colors.textColorPrimary
        )
        val rawText = (message.messagePayload as? TextMessagePayload)?.text.orEmpty()
        val bindToken = "${message.msgID.orEmpty()}|$rawText|${textView.textSize}"
        textView.setTag(R.id.message_list_text_bind_token_tag, bindToken)
        textView.text = rawText
        EmojiSpanHelper.setEmojiSpanText(
            context = textView.context,
            text = rawText,
            textSizePx = textView.textSize,
            requestView = textView
        ) { spanned ->
            if (textView.getTag(R.id.message_list_text_bind_token_tag) == bindToken) {
                textView.text = spanned
            }
        }
    }
}

internal interface TextMessageWidthAware {
    fun setMessageMaxWidth(maxWidth: Int)
}

private class CompactTextMessageView(context: Context) : AppCompatTextView(context), TextMessageWidthAware {
    private var messageMaxWidth: Int = 0

    override fun setMessageMaxWidth(maxWidthPx: Int) {
        val nextMaxWidth = maxWidthPx.coerceAtLeast(0)
        if (messageMaxWidth == nextMaxWidth) {
            return
        }
        messageMaxWidth = nextMaxWidth
        maxWidth = nextMaxWidth.takeIf { it > 0 } ?: Int.MAX_VALUE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val textLayout = layout ?: return
        if (textLayout.lineCount <= 1) {
            return
        }
        val maxLineWidth = (0 until textLayout.lineCount)
            .maxOfOrNull { line -> kotlin.math.ceil(textLayout.getLineWidth(line).toDouble()).toInt() }
            ?: return
        val compactWidth = TextMessageMeasurePolicy.resolveCompactWidth(
            measuredWidth = measuredWidth,
            maxLineWidth = maxLineWidth,
            horizontalPadding = compoundPaddingLeft + compoundPaddingRight,
            maxWidth = messageMaxWidth
        )
        if (compactWidth in 1 until measuredWidth) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(compactWidth, MeasureSpec.EXACTLY),
                heightMeasureSpec
            )
        }
    }
}
