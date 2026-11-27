package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageProtocol
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSource
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.RecyclableMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel

internal class ChatbotMessageRenderer : MessageRenderer, RecyclableMessageRenderer {
    override fun createView(context: Context, parent: ViewGroup): View {
        return StreamingTextContentView(context)
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val contentView = view as StreamingTextContentView
        val data = ChatbotMessageProtocol.parse(message)
        if (data == null) {
            contentView.bind(
                text = "",
                isStreaming = false,
                textColor = colors.textColorPrimary,
                dotColor = colors.textColorSecondary
            )
            return
        }
        contentView.bind(
            text = data.displayText,
            isStreaming = data.isPlaceholder ||
                (data.source == ChatbotMessageSource.FLOW && !data.isFinished),
            textColor = colors.textColorPrimary,
            dotColor = colors.textColorSecondary
        )
    }

    override fun onViewRecycled(view: View) {
        (view as? StreamingTextContentView)?.recycle()
    }
}
