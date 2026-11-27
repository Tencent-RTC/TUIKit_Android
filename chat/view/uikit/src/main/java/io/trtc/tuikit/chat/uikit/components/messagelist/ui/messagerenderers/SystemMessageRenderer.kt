package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.imsdk.v2.V2TIMManager
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.getSystemInfoDisplayString
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.TipsMessagePayload

class SystemMessageRenderer : MessageRenderer {

    override val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig(showMessageMeta = false, useDefaultBubble = false)

    override fun createView(context: Context, parent: ViewGroup): View {
        return TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            val pad = (10 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
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
        val displayText = RecalledMessageDisplayPolicy.format(textView.context, message) ?: run {
            val payload = message.messagePayload as? TipsMessagePayload
            getSystemInfoDisplayString(
                textView.context,
                payload?.groupTips
            )
        }
        textView.text = displayText
        textView.setTextColor(colors.textColorSecondary)
    }
}

internal data class RecalledMessageDisplaySpec(
    val textResId: Int,
    val formatArg: String? = null,
)

internal object RecalledMessageDisplayPolicy {
    fun format(context: Context, message: MessageInfo): String? {
        val spec = createSpec(message) ?: return null
        return spec.formatArg?.let { context.getString(spec.textResId, it) }
            ?: context.getString(spec.textResId)
    }

    fun createSpec(
        message: MessageInfo,
        currentUserID: String? = V2TIMManager.getInstance().loginUser,
    ): RecalledMessageDisplaySpec? {
        if (message.status != MessageStatus.REVOKED) {
            return null
        }
        val revokerUserID = message.revokerInfo?.userID.takeUnless { it.isNullOrBlank() }
        val selfUserID = currentUserID.takeUnless { it.isNullOrBlank() }
        if (!revokerUserID.isNullOrBlank() && revokerUserID == selfUserID) {
            return RecalledMessageDisplaySpec(R.string.message_list_message_tips_you_recall_message)
        }
        val revokerName = message.revokerInfo?.nickname.takeUnless { it.isNullOrBlank() }
            ?: revokerUserID
        if (!revokerName.isNullOrBlank()) {
            return RecalledMessageDisplaySpec(
                textResId = R.string.message_list_message_tips_recall_message_format,
                formatArg = revokerName,
            )
        }
        val textResId = if (message.conversationType == ConversationType.C2C) {
            R.string.message_list_message_tips_others_recall_message
        } else {
            R.string.message_list_message_tips_normal_recall_message
        }
        return RecalledMessageDisplaySpec(textResId)
    }
}
