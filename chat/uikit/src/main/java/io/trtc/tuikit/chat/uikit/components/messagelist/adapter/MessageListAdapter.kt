package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageCellRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderActions
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRendererResolver
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageListTouchTargetTags
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.NoOpMessageRenderActions
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo

class MessageListAdapter internal constructor(
    private val context: Context,
    private val viewModel: MessageListViewModel,
    private val config: MessageListConfigProtocol,
    private val onItemLongClick: (MessageInfo, View) -> Unit = { _, _ -> },
    private val onAuxiliaryTextLongClick:
    (message: MessageInfo, anchorView: View, actions: List<MessageCustomAction>) -> Unit =
        { _, _, _ -> },
    private val onUserClick: (String) -> Unit = {},
    private val onUserLongClick: (MessageInfo, String) -> Unit = { _, _ -> },
    private val onQuoteClick: (MessageInfo, MessageQuoteInfo) -> Unit = { _, _ -> },
    private val enableMessageInteraction: Boolean = true,
    private val enableQuoteNavigation: Boolean = true,
    private val showMessageReadReceipt: Boolean = true,
    private val showInlineMessageTime: Boolean = false,
    private val resolver: MessageRendererResolver = MessageRendererResolver(emptyList()),
    private val renderActions: MessageRenderActions = NoOpMessageRenderActions
) : ListAdapter<MessageInfo, RecyclerView.ViewHolder>(DIFF) {

    private val themeStore = ThemeStore.shared(context)
    private val colors: ColorTokens
        get() = themeStore.themeState.value.currentTheme.tokens.color
    private val density = context.resources.displayMetrics.density
    private val yesterdayLabel by lazy {
        context.getString(R.string.message_list_time_yesterday)
    }
    private var highlightedMessageId: String? = null
    private var attachedRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecyclerView === recyclerView) {
            attachedRecyclerView = null
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return resolver.resolve(message).viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        resolver.cellRendererForViewType(viewType)?.let { renderer ->
            val view = renderer.createView(parent.context, parent)
            MessageListTouchTargetTags.mark(view)
            view.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            return CustomCellViewHolder(view, renderer)
        }
        val itemRoot = FrameLayout(parent.context)
        val messageItemView = MessageItemView(parent.context, config, density)
        itemRoot.addView(
            messageItemView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val multiSelectOverlay = View(parent.context).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            MessageListTouchTargetTags.mark(this)
        }
        itemRoot.addView(
            multiSelectOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        itemRoot.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        return MessageViewHolder(itemRoot, messageItemView, multiSelectOverlay)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is MessageViewHolder -> holder.bind(message, position)
            is CustomCellViewHolder -> holder.bind(message)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        recycleHolder(holder)
        super.onViewRecycled(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is MessageViewHolder -> holder.recycle()
            is CustomCellViewHolder -> holder.detachFromWindow()
        }
        super.onViewDetachedFromWindow(holder)
    }

    private fun recycleHolder(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is MessageViewHolder -> holder.recycle()
            is CustomCellViewHolder -> holder.recycle()
        }
    }

    fun clearHighlightedMessage() {
        val previousMessageId = highlightedMessageId ?: return
        highlightedMessageId = null
        notifyMessageChanged(previousMessageId)
    }

    fun highlightMessage(messageId: String) {
        val previousMessageId = highlightedMessageId
        highlightedMessageId = messageId
        if (previousMessageId != null && previousMessageId != messageId) {
            notifyMessageChanged(previousMessageId)
        }

        val rv = attachedRecyclerView
        val index = currentList.indexOfFirst { it.msgID == messageId }
        if (rv != null && index >= 0) {
            val viewHolder = rv.findViewHolderForAdapterPosition(index) as? MessageViewHolder
            if (viewHolder != null) {
                viewHolder.triggerHighlightAnimation(getItem(index))
                return
            }
        }

        notifyMessageChanged(messageId)
    }

    private fun notifyMessageChanged(messageId: String) {
        val index = currentList.indexOfFirst { it.msgID == messageId }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    private fun onHighlightConsumedByViewHolder(messageId: String) {
        if (highlightedMessageId == messageId) {
            highlightedMessageId = null
        }
    }

    inner class MessageViewHolder(
        rootView: View,
        private val messageItemView: MessageItemView,
        private val multiSelectOverlay: View
    ) : RecyclerView.ViewHolder(rootView) {

        fun triggerHighlightAnimation(message: MessageInfo) {
            messageItemView.flashHighlight(
                message = message,
                colors = this@MessageListAdapter.colors,
                onHighlightConsumed = { consumedMessageId ->
                    onHighlightConsumedByViewHolder(consumedMessageId)
                }
            )
        }

        fun bind(message: MessageInfo, position: Int) {
            val colors = this@MessageListAdapter.colors
            val isMultiSelectMode = viewModel.isMultiSelectMode.value
            val selectedMessages = viewModel.selectedMessages.value
            val timeString = viewModel.getMessageTimeString(position)
            val inlineTimeString = if (showInlineMessageTime) {
                ChatDateTimeUtils.formatMessageListTime(
                    timestampMs = message.timestamp?.times(1000),
                    yesterdayLabel = yesterdayLabel
                )
            } else {
                null
            }
            val resolvedRenderer = resolver.resolve(message)

            messageItemView.bind(
                message = message,
                resolvedRenderer = resolvedRenderer,
                colors = colors,
                viewModel = viewModel,
                renderActions = renderActions,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = selectedMessages.contains(message),
                timeString = timeString,
                shouldHighlight = message.msgID == highlightedMessageId,
                onHighlightConsumed = { messageId ->
                    onHighlightConsumedByViewHolder(messageId)
                },
                onLongClick = { anchorView ->
                    if (!isMultiSelectMode) {
                        onItemLongClick(message, anchorView)
                    }
                },
                onAuxiliaryTextLongClick = { anchorView, actions ->
                    if (!isMultiSelectMode) {
                        onAuxiliaryTextLongClick(message, anchorView, actions)
                    }
                },
                onCheckToggle = {
                    viewModel.toggleMessageSelection(message)
                },
                onUserClick = { userID ->
                    onUserClick(userID)
                },
                onUserLongClick = { userID ->
                    if (!isMultiSelectMode) {
                        onUserLongClick(message, userID)
                    }
                },
                onQuoteClick = { quoteInfo ->
                    onQuoteClick(message, quoteInfo)
                },
                enableMessageInteraction = enableMessageInteraction,
                enableQuoteNavigation = enableQuoteNavigation,
                showMessageReadReceipt = showMessageReadReceipt,
                inlineTimeString = inlineTimeString
            )

            if (isMultiSelectMode && enableMessageInteraction) {
                multiSelectOverlay.visibility = View.VISIBLE
                multiSelectOverlay.setOnClickListener {
                    viewModel.toggleMessageSelection(message)
                }
            } else {
                multiSelectOverlay.visibility = View.GONE
                multiSelectOverlay.setOnClickListener(null)
            }
        }

        fun recycle() {
            messageItemView.recycle()
        }
    }

    inner class CustomCellViewHolder(
        rootView: View,
        private val renderer: MessageCellRenderer
    ) : RecyclerView.ViewHolder(rootView) {
        fun bind(message: MessageInfo) {
            renderer.bindView(
                itemView,
                MessageRenderContext(
                    conversationID = viewModel.conversationID,
                    message = message,
                    colors = this@MessageListAdapter.colors,
                    config = config,
                    isMultiSelectMode = viewModel.isMultiSelectMode.value,
                    isSelected = viewModel.selectedMessages.value.contains(message),
                    actions = renderActions
                )
            )
        }

        fun recycle() {
            renderer.onViewRecycled(itemView)
        }

        fun detachFromWindow() {
            renderer.onViewDetachedFromWindow(itemView)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<MessageInfo>() {
            override fun areItemsTheSame(oldItem: MessageInfo, newItem: MessageInfo): Boolean {
                return oldItem.msgID == newItem.msgID
            }

            override fun areContentsTheSame(oldItem: MessageInfo, newItem: MessageInfo): Boolean {
                return oldItem == newItem
            }
        }
    }
}
