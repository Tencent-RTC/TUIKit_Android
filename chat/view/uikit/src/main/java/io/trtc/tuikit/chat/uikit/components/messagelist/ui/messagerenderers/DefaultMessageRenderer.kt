package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class DefaultMessageRenderer : MessageRenderer {

    override fun createView(context: Context, parent: ViewGroup): View {
        return TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val density = context.resources.displayMetrics.density
            val horizontalPad = (16 * density).toInt()
            val verticalPad = (8 * density).toInt()
            setPadding(horizontalPad, verticalPad, horizontalPad, verticalPad)
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
        textView.text = view.context.getString(R.string.message_list_unsupported_message)
        textView.setTextColor(
            if (message.isSentBySelf) colors.textColorAntiPrimary else colors.textColorPrimary
        )
    }
}
