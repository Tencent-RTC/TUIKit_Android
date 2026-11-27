package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.StreamMessagePayload
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.RecyclableMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel

/**
 * 渲染 TIMStreamElem 流式消息。
 *
 * 消息在生成过程中由后台不断改写同一条，Store 每次改写都会重新下发整条消息，
 * 所以这里不做增量拼接，每次直接展示 markdown 的当前全文，按 Markdown 渲染（表格、加粗、列表）。
 *
 * 这条路径不显示三点加载动画：正文本身就是逐段冒出来的，已经足够表达"还在生成"。
 * 正文到达之前的空消息由 MessageListVisibilityPolicy 直接隐藏，不会留下空气泡。
 * 注意这与 ChatbotMessageRenderer（chatbotPlugin 自定义消息协议）的取舍不同，两者不要互相对齐。
 */
class StreamMessageRenderer : MessageRenderer, RecyclableMessageRenderer {

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
        val payload = message.messagePayload as? StreamMessagePayload
        contentView.bind(
            text = payload?.markdown.orEmpty(),
            isStreaming = payload?.isStreamEnded != true,
            showLoadingDots = false,
            textColor = if (message.isSentBySelf) {
                colors.textColorAntiPrimary
            } else {
                colors.textColorPrimary
            },
            dotColor = if (message.isSentBySelf) {
                colors.textColorAntiSecondary
            } else {
                colors.textColorSecondary
            }
        )
    }

    override fun onViewRecycled(view: View) {
        (view as? StreamingTextContentView)?.recycle()
    }
}
