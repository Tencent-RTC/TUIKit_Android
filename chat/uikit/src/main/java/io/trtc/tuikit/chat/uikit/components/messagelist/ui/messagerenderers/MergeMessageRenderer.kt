package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.BubbleStyle
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.merged.MergedMessageDetailActivity
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MergedMessagePayload

class MergeMessageRenderer : MessageRenderer {

    private companion object {
        const val BUBBLE_WIDTH_DP = 238f

        const val PADDING_HORIZONTAL_DP = 12f
        const val PADDING_TOP_DP = 10f
        const val PADDING_BOTTOM_DP = 10f

        const val TITLE_FONT_SIZE_SP = 16f
        const val ABSTRACT_FONT_SIZE_SP = 12f
        const val FOOTER_FONT_SIZE_SP = 9f

        const val TITLE_LINE_HEIGHT_DP = 21f
        const val ABSTRACT_LINE_HEIGHT_DP = 18f
        const val FOOTER_LINE_HEIGHT_DP = 14f

        const val TITLE_TO_ABSTRACT_MARGIN_DP = 5.5f
        const val ABSTRACT_LINE_MARGIN_DP = 4f

        const val DIVIDER_MARGIN_TOP_DP = 9.25f
        const val DIVIDER_MARGIN_BOTTOM_DP = 5.75f
        const val DIVIDER_HEIGHT_DP = 0.5f

        const val ABSTRACT_MAX_COUNT = 4

        const val TAG_TITLE = "merge_title"
        const val TAG_DIVIDER = "merge_divider"
        const val TAG_FOOTER = "merge_footer"
        const val TAG_ABSTRACT_PREFIX = "merge_abstract_"
    }

    private fun TextView.applyLocaleTextAlignment() {
        textDirection = View.TEXT_DIRECTION_LOCALE
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        gravity = Gravity.START
    }

    override val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig(
            useDefaultBubble = false,
            bubbleStyle = BubbleStyle.CARD
        )

    override fun createView(context: Context, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setPadding(
                (PADDING_HORIZONTAL_DP * density).toInt(),
                (PADDING_TOP_DP * density).toInt(),
                (PADDING_HORIZONTAL_DP * density).toInt(),
                (PADDING_BOTTOM_DP * density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                (BUBBLE_WIDTH_DP * density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_FONT_SIZE_SP)
            typeface = Typeface.DEFAULT
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            TextViewCompat.setLineHeight(this, (TITLE_LINE_HEIGHT_DP * density).toInt())
            applyLocaleTextAlignment()
            tag = TAG_TITLE
        }
        container.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        for (index in 0 until ABSTRACT_MAX_COUNT) {
            val abstractView = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, ABSTRACT_FONT_SIZE_SP)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                TextViewCompat.setLineHeight(this, (ABSTRACT_LINE_HEIGHT_DP * density).toInt())
                applyLocaleTextAlignment()
                tag = "$TAG_ABSTRACT_PREFIX$index"
                visibility = View.GONE
            }
            val topMarginDp = if (index == 0) TITLE_TO_ABSTRACT_MARGIN_DP else ABSTRACT_LINE_MARGIN_DP
            container.addView(
                abstractView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (topMarginDp * density).toInt()
                }
            )
        }

        val divider = View(context).apply {
            tag = TAG_DIVIDER
        }
        container.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (DIVIDER_HEIGHT_DP * density).toInt().coerceAtLeast(1)
            ).apply {
                topMargin = (DIVIDER_MARGIN_TOP_DP * density).toInt()
                bottomMargin = (DIVIDER_MARGIN_BOTTOM_DP * density).toInt()
            }
        )

        val footerView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FOOTER_FONT_SIZE_SP)
            TextViewCompat.setLineHeight(this, (FOOTER_LINE_HEIGHT_DP * density).toInt())
            applyLocaleTextAlignment()
            tag = TAG_FOOTER
        }
        container.addView(
            footerView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        return container
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val titleView = view.findViewWithTag<TextView>(TAG_TITLE)
        val divider = view.findViewWithTag<View>(TAG_DIVIDER)
        val footerView = view.findViewWithTag<TextView>(TAG_FOOTER)

        val payload = message.messagePayload as? MergedMessagePayload
        titleView.text = payload?.title.orEmpty()
        titleView.setTextColor(colors.textColorPrimary)

        val abstractList = payload?.abstractList.orEmpty()
        for (index in 0 until ABSTRACT_MAX_COUNT) {
            val abstractView = view.findViewWithTag<TextView>("$TAG_ABSTRACT_PREFIX$index")
            if (index < abstractList.size) {
                abstractView.visibility = View.VISIBLE
                val rawAbstract = abstractList[index]
                val bindToken = "${message.msgID.orEmpty()}|abstract|$index|$rawAbstract"
                abstractView.setTag(R.id.emoji_span_bind_token_tag, bindToken)
                abstractView.text = EmojiSpanHelper.replaceEmojiKeysWithNames(rawAbstract)
                EmojiSpanHelper.applyEmojiSpans(view.context, rawAbstract, abstractView.textSize, abstractView, matchNames = true) { spanned ->
                    if (abstractView.getTag(R.id.emoji_span_bind_token_tag) == bindToken) {
                        abstractView.text = spanned
                    }
                }
                abstractView.setTextColor(colors.textColorTertiary)
            } else {
                abstractView.visibility = View.GONE
            }
        }

        divider.setBackgroundColor(colors.strokeColorPrimary)
        footerView.text = view.context.getString(R.string.message_list_forward_chat_record)
        footerView.setTextColor(colors.textColorTertiary)

        if (message.status == MessageStatus.VIOLATION) {
            view.setOnClickListener(null)
            view.isClickable = false
        } else {
            view.isClickable = true
            view.setOnClickListener {
                MergedMessageDetailActivity.start(view.context, message)
            }
        }
    }
}
