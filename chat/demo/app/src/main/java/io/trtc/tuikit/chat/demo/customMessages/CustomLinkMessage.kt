import com.google.gson.Gson
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageContentRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderContext
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryRegistry

data class CustomLinkMessage(
    val businessID: String? = null,
    val text: String? = null,
    val link: String? = null,
) {
    companion object {
        const val BUSINESS_ID = "text_link"

        fun from(customData: String?): CustomLinkMessage? {
            if (customData.isNullOrBlank()) {
                return null
            }
            return runCatching {
                Gson().fromJson(customData, CustomLinkMessage::class.java)
            }.getOrNull()
        }
    }
}


class CustomLinkMessageRenderer : MessageContentRenderer {

    override val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig(showMessageMeta = true, useDefaultBubble = true)

    override fun createView(context: Context, parent: ViewGroup): View {
        return CustomLinkMessageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun bindView(view: View, context: MessageRenderContext) {
        val messageView = view as CustomLinkMessageView
        val colors = context.colors
        val isSelf = context.message.isSentBySelf

        val payload = context.message.messagePayload as? CustomMessagePayload
        val linkMessage = CustomLinkMessage.from(payload?.customData)
        val text = linkMessage?.text.orEmpty()
        val link = linkMessage?.link.orEmpty().trim()
        val hasLink = link.isNotEmpty()
        val canOpenLink = hasLink && !context.isMultiSelectMode

        messageView.textView.text = text
        messageView.linkView.text = view.context.getString(R.string.demo_chat_custom_message_view_details)
        messageView.linkView.visibility = if (hasLink) View.VISIBLE else View.GONE
        messageView.textView.setTextColor(
            if (isSelf) colors.textColorAntiPrimary else colors.textColorPrimary
        )
        messageView.linkView.setTextColor(colors.textColorLink)

        messageView.isClickable = canOpenLink
        messageView.setOnClickListener(
            if (canOpenLink) View.OnClickListener {
                openLink(view.context, link)
            } else null
        )
    }

    private fun openLink(context: Context, link: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

private class CustomLinkMessageView(context: Context) : LinearLayout(context) {
    val textView: TextView
    val linkView: TextView

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val horizontalPadding = (16 * density).toInt()
        val verticalPadding = (12 * density).toInt()
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        textView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.3f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        }
        linkView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.2f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
        }
        addView(textView)
        addView(linkView)
    }
}

class CustomLinkMessageManager() {

    companion object {

        fun registerMessageSummary() {
            MessageListMessageSummaryRegistry.setCustomMessageSummary(
                businessID = CustomLinkMessage.BUSINESS_ID,
                summaryProvider = { summaryContext ->
                    val payload = summaryContext.message.messagePayload as? CustomMessagePayload
                    CustomLinkMessage.from(payload?.customData)?.text
                }
            )
        }
    }
}
