package io.trtc.tuikit.chat.uikit.components.messagelist.ui
import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

enum class BubbleStyle {
    DEFAULT,
    NONE,
    CARD
}

data class MessageRenderConfig(
    val showMessageMeta: Boolean = true,
    val useDefaultBubble: Boolean = true,
    val bubbleStyle: BubbleStyle = if (useDefaultBubble) BubbleStyle.DEFAULT else BubbleStyle.NONE
)

interface MessageRenderer {
    fun createView(context: Context, parent: ViewGroup): View
    fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    )

    val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig()
}

interface RecyclableMessageRenderer {
    fun onViewRecycled(view: View)
}

fun interface MessageMatcher {
    fun matches(message: MessageInfo): Boolean
}

fun interface MessageSummaryProvider {
    fun getSummary(context: MessageSummaryContext): String?
}

interface MessageRenderActions {
    fun openImageViewer(message: MessageInfo)
    fun openVideoPlayer(message: MessageInfo)
    fun playSound(message: MessageInfo)
    fun toggleSelection(message: MessageInfo)
    fun showLongPressMenu(message: MessageInfo, anchorView: View)
}

internal object NoOpMessageRenderActions : MessageRenderActions {
    override fun openImageViewer(message: MessageInfo) = Unit
    override fun openVideoPlayer(message: MessageInfo) = Unit
    override fun playSound(message: MessageInfo) = Unit
    override fun toggleSelection(message: MessageInfo) = Unit
    override fun showLongPressMenu(message: MessageInfo, anchorView: View) = Unit
}

class MessageRenderContext(
    val conversationID: String,
    val message: MessageInfo,
    val colors: ColorTokens,
    val config: MessageListConfigProtocol,
    val isMultiSelectMode: Boolean,
    val isSelected: Boolean,
    val actions: MessageRenderActions
)

class MessageSummaryContext(
    val context: Context,
    val conversationID: String?,
    val message: MessageInfo
)

interface MessageContentRenderer {
    val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig()

    fun createView(context: Context, parent: ViewGroup): View
    fun bindView(view: View, context: MessageRenderContext)
    fun onViewRecycled(view: View) = Unit
}

interface MessageCellRenderer {
    fun createView(context: Context, parent: ViewGroup): View
    fun bindView(view: View, context: MessageRenderContext)
    fun onViewDetachedFromWindow(view: View) = Unit
    fun onViewRecycled(view: View) = Unit
}

internal data class MessageRenderRule(
    val matcher: MessageMatcher,
    val contentRenderer: MessageContentRenderer? = null,
    val cellRenderer: MessageCellRenderer? = null,
    val summaryProvider: MessageSummaryProvider? = null,
    val priority: Int = 0
) {
    init {
        require(contentRenderer == null || cellRenderer == null) {
            "Only one visual message renderer can be provided."
        }
        require(contentRenderer != null || cellRenderer != null || summaryProvider != null) {
            "A message render rule must provide a renderer or summary provider."
        }
    }
}
