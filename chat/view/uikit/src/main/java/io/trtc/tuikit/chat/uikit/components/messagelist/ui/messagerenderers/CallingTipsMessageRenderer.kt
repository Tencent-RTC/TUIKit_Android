package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.getCallMessageDisplayString
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class CallingTipsMessageRenderer : MessageRenderer {

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
        val callModel = CallMessageParser.parse(message)
        if (callModel == null) {
            textView.text = ""
            return
        }
        textView.text = getCallMessageDisplayString(
            context = textView.context,
            message = message,
            callModel = callModel
        )
        textView.setTextColor(colors.textColorSecondary)
    }
}
