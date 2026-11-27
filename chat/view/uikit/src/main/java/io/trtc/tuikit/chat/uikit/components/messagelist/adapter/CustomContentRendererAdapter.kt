package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageContentRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.RecyclableMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal class CustomContentRendererAdapter(
    private val renderer: MessageContentRenderer
) : MessageRenderer, RecyclableMessageRenderer {
    override val renderConfig: MessageRenderConfig
        get() = renderer.renderConfig

    override fun createView(context: Context, parent: ViewGroup): View {
        return renderer.createView(context, parent)
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val context = view.getTag(R.id.message_list_render_context_tag) as? MessageRenderContext
            ?: return
        renderer.bindView(view, context)
    }

    override fun onViewRecycled(view: View) {
        renderer.onViewRecycled(view)
    }
}
