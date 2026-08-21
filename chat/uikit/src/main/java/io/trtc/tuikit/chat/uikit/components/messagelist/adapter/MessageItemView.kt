package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.RtlUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageActionIDs
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.BubbleStyle
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageContentRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderActions
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRendererResolver
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.RecyclableMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.auxiliary.AuxiliaryTextBubbleView
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallMessageDisplayPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.TextMessageWidthAware
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions.MessageReactionBarView
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import java.util.IdentityHashMap

class MessageItemView(
    context: Context,
    private val config: MessageListConfigProtocol,
    private val density: Float
) : LinearLayout(context) {

    private val timeView: TextView
    private val checkBox: ImageView
    private val contentRow: LinearLayout
    private val inlineTimeView: TextView
    private val leftAvatarView: Avatar
    private val rightAvatarView: Avatar
    private val middleColumn: LinearLayout
    private val nicknameView: TextView
    private val quoteBubbleView: MessageQuoteBubbleView
    private val bubbleRow: MaxWidthLinearLayout
    private val bubbleContainer: MaxWidthFrameLayout
    private val bubbleContentContainer: LinearLayout
    private val statusContainer: FrameLayout
    private val callUnreadDotView: View
    private val auxiliaryTextBubbleView: AuxiliaryTextBubbleView
    private val reactionBarView: MessageReactionBarView
    private val violationView: TextView
    private val statusController: MessageStatusController
    private val bubbleBackgroundFactory: MessageBubbleBackgroundFactory
    private val highlightController: MessageItemHighlightController
    private var maxBubbleWidth: Int = 0
    private var maxRowWidth: Int = 0
    private var inlineTimeReserveWidth: Int = 0
    private val customContentRendererAdapters = IdentityHashMap<MessageContentRenderer, CustomContentRendererAdapter>()
    private var cachedContentRenderer: MessageRenderer? = null
    private var cachedContentView: View? = null
    private var cachedContentDesignedWidth: Int = ViewGroup.LayoutParams.WRAP_CONTENT

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        val hierarchy = buildMessageItemViewHierarchy(config, density)
        timeView = hierarchy.timeView
        checkBox = hierarchy.checkBox
        contentRow = hierarchy.contentRow
        inlineTimeView = hierarchy.inlineTimeView
        leftAvatarView = hierarchy.leftAvatarView
        rightAvatarView = hierarchy.rightAvatarView
        middleColumn = hierarchy.middleColumn
        nicknameView = hierarchy.nicknameView
        quoteBubbleView = hierarchy.quoteBubbleView
        bubbleRow = hierarchy.bubbleRow
        bubbleContainer = hierarchy.bubbleContainer
        bubbleContentContainer = hierarchy.bubbleContentContainer
        statusContainer = hierarchy.statusContainer
        callUnreadDotView = hierarchy.callUnreadDotView
        auxiliaryTextBubbleView = hierarchy.auxiliaryTextBubbleView
        reactionBarView = hierarchy.reactionBarView
        violationView = hierarchy.violationView
        maxRowWidth = hierarchy.maxRowWidth
        maxBubbleWidth = hierarchy.maxBubbleWidth
        statusController = MessageStatusController(
            context = context,
            density = density,
            sendingIndicator = hierarchy.sendingIndicator,
            sendFailIcon = hierarchy.sendFailIcon,
            readReceiptIndicatorView = hierarchy.readReceiptIndicatorView,
            statusContainer = statusContainer
        )
        bubbleBackgroundFactory = MessageBubbleBackgroundFactory(
            context = context,
            config = config,
            density = density,
            bubbleContainer = bubbleContainer,
            bubbleContentContainer = bubbleContentContainer
        )
        highlightController = MessageItemHighlightController(bubbleContainer, density)
    }

    internal fun bind(
        message: MessageInfo,
        resolvedRenderer: MessageRendererResolver.ResolvedRenderer,
        colors: ColorTokens,
        viewModel: MessageListViewModel,
        renderActions: MessageRenderActions,
        isMultiSelectMode: Boolean,
        isSelected: Boolean,
        timeString: String?,
        shouldHighlight: Boolean,
        onHighlightConsumed: (String) -> Unit,
        onLongClick: (View) -> Unit,
        onAuxiliaryTextLongClick: (anchorView: View, actions: List<MessageCustomAction>) -> Unit,
        onCheckToggle: () -> Unit,
        onUserClick: (String) -> Unit,
        onUserLongClick: (String) -> Unit,
        onQuoteClick: (MessageQuoteInfo) -> Unit,
        enableMessageInteraction: Boolean = true,
        enableQuoteNavigation: Boolean = true,
        showMessageReadReceipt: Boolean = true,
        inlineTimeString: String? = null
    ) {
        val cellSpacingPx = (config.cellSpacing * density).toInt()
        setPadding(paddingLeft, cellSpacingPx, paddingRight, cellSpacingPx)

        val renderer = when (resolvedRenderer) {
            is MessageRendererResolver.ResolvedRenderer.CustomContent -> {
                customContentRendererAdapters.getOrPut(resolvedRenderer.renderer) {
                    CustomContentRendererAdapter(resolvedRenderer.renderer)
                }
            }
            is MessageRendererResolver.ResolvedRenderer.BuiltInContent -> resolvedRenderer.renderer
            is MessageRendererResolver.ResolvedRenderer.CustomCell -> error("Custom cell renderer must not use MessageItemView")
        }
        val renderConfig = renderer.renderConfig

        val checkBoxVisible = isMultiSelectMode && renderConfig.showMessageMeta
        bindInlineTime(
            inlineTimeString = if (renderConfig.showMessageMeta) inlineTimeString else null,
            colors = colors
        )
        applyMaxWidthForMode(checkBoxVisible, statusReserveWidth = 0)

        visibility = View.VISIBLE

        if (!timeString.isNullOrEmpty() && config.isShowTimeMessage) {
            timeView.visibility = View.VISIBLE
            timeView.text = timeString
            timeView.setTextColor(colors.textColorSecondary)
            val timeLp = timeView.layoutParams as LayoutParams
            timeLp.topMargin = ((20 * density).toInt() - cellSpacingPx).coerceAtLeast(0)
            timeLp.bottomMargin = ((20 * density).toInt() - cellSpacingPx).coerceAtLeast(0)
            timeView.layoutParams = timeLp
        } else {
            timeView.visibility = View.GONE
        }

        if (!renderConfig.showMessageMeta) {
            bindWithoutMessageMeta(
                message = message,
                renderer = renderer,
                viewModel = viewModel,
                colors = colors,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = isSelected,
                renderActions = renderActions,
                shouldHighlight = shouldHighlight,
                onHighlightConsumed = onHighlightConsumed
            )
            return
        }

        val isSelf = message.isSentBySelf
        val isRtl = RtlUtils.isLocaleRtl(context)
        val isLeftAligned = MessageStatusLayoutPolicy.isLeftAligned(config.alignment, isSelf, isRtl)
        val statusLayout = MessageStatusLayoutPolicy.resolve(isLeftAligned)
        val isGroupChat = message.conversationType == ConversationType.GROUP

        checkBox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
        checkBox.setImageResource(
            if (isSelected) {
                R.drawable.message_list_multi_select_checkbox_checked
            } else {
                R.drawable.message_list_multi_select_checkbox_unchecked
            }
        )
        checkBox.setOnClickListener { onCheckToggle() }

        val showLeftAvatar = isLeftAligned
        val showRightAvatar = !isLeftAligned

        bindAvatar(
            avatarView = leftAvatarView,
            visible = showLeftAvatar,
            faceUrl = message.from.avatarURL,
            fallbackName = message.senderDisplayName,
            userId = message.from.userID,
            enableInteraction = enableMessageInteraction,
            onUserClick = onUserClick,
            onUserLongClick = onUserLongClick
        )
        bindAvatar(
            avatarView = rightAvatarView,
            visible = showRightAvatar,
            faceUrl = message.from.avatarURL,
            fallbackName = message.senderDisplayName,
            userId = message.from.userID,
            enableInteraction = enableMessageInteraction,
            onUserClick = onUserClick,
            onUserLongClick = onUserLongClick
        )

        val showNickname = MessageItemDisplayPolicy.shouldShowNickname(
            alignment = config.alignment,
            isSelf = isSelf,
            isGroupChat = isGroupChat
        )
        if (showNickname) {
            nicknameView.visibility = View.VISIBLE
            nicknameView.text = message.senderDisplayName
            nicknameView.setTextColor(colors.textColorSecondary)
        } else {
            nicknameView.visibility = View.GONE
        }

        val horizontalGravity = if (isLeftAligned) Gravity.LEFT else Gravity.RIGHT
        contentRow.gravity = horizontalGravity or Gravity.TOP
        middleColumn.gravity = horizontalGravity
        nicknameView.gravity = horizontalGravity
        (quoteBubbleView.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.gravity = horizontalGravity
            quoteBubbleView.layoutParams = params
        }
        bubbleRow.gravity = horizontalGravity or Gravity.BOTTOM
        violationView.gravity = horizontalGravity

        bubbleRow.removeAllViews()
        val showCallUnreadDot = shouldShowCallUnreadDot(message)
        updateCallUnreadDot(showCallUnreadDot, statusLayout, colors)
        if (statusLayout.statusBeforeBubble) {
            if (showCallUnreadDot) {
                bubbleRow.addView(callUnreadDotView)
            } else {
                bubbleRow.addView(statusContainer)
            }
            bubbleRow.addView(bubbleContainer)
        } else {
            bubbleRow.addView(bubbleContainer)
            if (showCallUnreadDot) {
                bubbleRow.addView(callUnreadDotView)
            } else {
                bubbleRow.addView(statusContainer)
            }
        }

        detachReactionBarFromContainer()
        bubbleContentContainer.gravity = if (isLeftAligned) {
            Gravity.START
        } else {
            Gravity.END
        }
        val contentView = ensureContentView(renderer)

        val lp = bubbleContainer.layoutParams
        if (lp is LinearLayout.LayoutParams) {
            lp.width = LayoutParams.WRAP_CONTENT
        }

        val hasReactions = config.isSupportReaction &&
            message.reactionList.isNotEmpty() &&
            message.status == MessageStatus.SEND_SUCCESS
        val highlightCornerRadii = bubbleBackgroundFactory.apply(
            bubbleStyle = renderConfig.bubbleStyle,
            hasReactions = hasReactions,
            isLeftAligned = isLeftAligned,
            isSelf = isSelf,
            colors = colors
        )
        highlightController.updateCornerRadii(highlightCornerRadii)

        statusController.update(
            message,
            colors,
            viewModel,
            enableMessageInteraction,
            showMessageReadReceipt,
            statusLayout
        )
        val externalReserveWidth = if (showCallUnreadDot) {
            resolveCallUnreadDotReserveWidth()
        } else {
            statusController.resolveStableStatusReserveWidth(
                message = message,
                showMessageReadReceipt = showMessageReadReceipt
            )
        }
        applyMaxWidthForMode(checkBoxVisible, statusReserveWidth = externalReserveWidth)
        applyContentViewWidth()
        updateQuoteBubble(
            message = message,
            colors = colors,
            isMultiSelectMode = isMultiSelectMode,
            enableQuoteNavigation = enableQuoteNavigation,
            onQuoteClick = onQuoteClick
        )
        updateAuxiliaryTextBubble(
            message = message,
            colors = colors,
            viewModel = viewModel,
            isMultiSelectMode = isMultiSelectMode,
            enableMessageInteraction = enableMessageInteraction,
            onAuxiliaryTextLongClick = onAuxiliaryTextLongClick
        )
        updateReactionBar(
            message = message,
            viewModel = viewModel,
            bubbleStyle = renderConfig.bubbleStyle,
            hasReactions = hasReactions,
            isLeftAligned = isLeftAligned
        )

        if (message.status == MessageStatus.VIOLATION) {
            violationView.visibility = View.VISIBLE
            violationView.text = context.getString(R.string.message_list_violation_received)
            violationView.setTextColor(colors.textColorError)
        } else {
            violationView.visibility = View.GONE
        }

        viewModel.requestResourcesForMessage(message)
        bindContentRenderer(
            renderer = renderer,
            contentView = contentView,
            message = message,
            viewModel = viewModel,
            colors = colors,
            isMultiSelectMode = isMultiSelectMode,
            isSelected = isSelected,
            renderActions = renderActions
        )
        applyInteractionState(
            interactionTargetView = contentView,
            isMultiSelectMode = isMultiSelectMode,
            enableMessageInteraction = enableMessageInteraction,
            onLongClick = onLongClick,
            onCheckToggle = onCheckToggle
        )
        highlightController.apply(message, colors, shouldHighlight, onHighlightConsumed)
    }

    private fun shouldShowCallUnreadDot(message: MessageInfo): Boolean {
        val callModel = CallMessageParser.parse(message) ?: return false
        return CallMessageDisplayPolicy.shouldShowOutsideUnreadDot(
            isSelf = message.isSentBySelf,
            isShowUnreadPoint = callModel.isShowUnreadPoint
        )
    }

    private fun updateCallUnreadDot(
        visible: Boolean,
        statusLayout: MessageStatusLayout,
        colors: ColorTokens
    ) {
        val dotSize = (CALL_UNREAD_DOT_SIZE_DP * density).toInt()
        val dotMargin = (CALL_UNREAD_DOT_MARGIN_DP * density).toInt()
        val layoutParams = LayoutParams(dotSize, dotSize).apply {
            gravity = Gravity.CENTER_VERTICAL
            if (statusLayout.statusBeforeBubble) {
                marginEnd = dotMargin
            } else {
                marginStart = dotMargin
            }
        }
        callUnreadDotView.layoutParams = layoutParams
        if (visible) {
            callUnreadDotView.visibility = View.VISIBLE
            callUnreadDotView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colors.textColorError)
            }
        } else {
            callUnreadDotView.visibility = View.GONE
            callUnreadDotView.background = null
        }
    }

    private fun resolveCallUnreadDotReserveWidth(): Int {
        val layoutParams = callUnreadDotView.layoutParams as? LayoutParams
        val margins = (layoutParams?.marginStart ?: 0) + (layoutParams?.marginEnd ?: 0)
        return (layoutParams?.width?.takeIf { it > 0 } ?: callUnreadDotView.measuredWidth).coerceAtLeast(0) + margins
    }

    private fun bindWithoutMessageMeta(
        message: MessageInfo,
        renderer: MessageRenderer,
        viewModel: MessageListViewModel,
        colors: ColorTokens,
        isMultiSelectMode: Boolean,
        isSelected: Boolean,
        renderActions: MessageRenderActions,
        shouldHighlight: Boolean,
        onHighlightConsumed: (String) -> Unit
    ) {
        timeView.visibility = View.GONE
        inlineTimeView.visibility = View.GONE
        inlineTimeReserveWidth = 0
        leftAvatarView.visibility = View.GONE
        rightAvatarView.visibility = View.GONE
        nicknameView.visibility = View.GONE
        statusContainer.visibility = View.GONE
        callUnreadDotView.visibility = View.GONE
        callUnreadDotView.background = null
        auxiliaryTextBubbleView.visibility = View.GONE
        quoteBubbleView.visibility = View.GONE
        quoteBubbleView.setOnClickListener(null)
        reactionBarView.visibility = View.GONE
        violationView.visibility = View.GONE
        checkBox.visibility = View.GONE
        bubbleRow.removeAllViews()
        detachReactionBarFromContainer()
        bubbleContainer.background = null
        bubbleContainer.minimumWidth = 0
        bubbleContainer.minimumHeight = 0
        val bubbleLayoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        bubbleRow.addView(bubbleContainer, bubbleLayoutParams)
        val contentView = ensureContentView(renderer)
        bindContentRenderer(
            renderer = renderer,
            contentView = contentView,
            message = message,
            viewModel = viewModel,
            colors = colors,
            isMultiSelectMode = isMultiSelectMode,
            isSelected = isSelected,
            renderActions = renderActions
        )
        contentRow.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        middleColumn.gravity = Gravity.CENTER_HORIZONTAL
        bubbleRow.gravity = Gravity.CENTER_HORIZONTAL
        bubbleContainer.setOnLongClickListener(null)
        bubbleContainer.setOnClickListener(null)
        this@MessageItemView.setOnClickListener(null)
        this@MessageItemView.isClickable = false
        highlightController.apply(message, colors, shouldHighlight, onHighlightConsumed)
    }

    private fun bindInlineTime(inlineTimeString: String?, colors: ColorTokens) {
        if (!inlineTimeString.isNullOrEmpty()) {
            inlineTimeView.visibility = View.VISIBLE
            inlineTimeView.text = inlineTimeString
            inlineTimeView.setTextColor(colors.textColorSecondary)
            inlineTimeReserveWidth = resolveInlineTimeReserveWidth(inlineTimeString)
        } else {
            inlineTimeView.visibility = View.GONE
            inlineTimeView.text = null
            inlineTimeReserveWidth = 0
        }
    }

    private fun resolveInlineTimeReserveWidth(timeText: String): Int {
        val layoutParams = inlineTimeView.layoutParams as? LayoutParams
        val margins = (layoutParams?.marginStart ?: 0) + (layoutParams?.marginEnd ?: 0)
        val textWidth = inlineTimeView.paint.measureText(timeText).toInt().coerceAtLeast(0)
        return textWidth + margins
    }

    private fun bindContentRenderer(
        renderer: MessageRenderer,
        contentView: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        colors: ColorTokens,
        isMultiSelectMode: Boolean,
        isSelected: Boolean,
        renderActions: MessageRenderActions
    ) {
        contentView.setTag(
            R.id.message_list_render_context_tag,
            MessageRenderContext(
                conversationID = viewModel.conversationID,
                message = message,
                colors = colors,
                config = config,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = isSelected,
                actions = renderActions
            )
        )
        renderer.bindView(contentView, message, viewModel, config, colors)
    }

    private fun bindAvatar(
        avatarView: Avatar,
        visible: Boolean,
        faceUrl: String?,
        fallbackName: String,
        userId: String?,
        enableInteraction: Boolean,
        onUserClick: (String) -> Unit,
        onUserLongClick: (String) -> Unit
    ) {
        if (visible) {
            avatarView.visibility = View.VISIBLE
            avatarView.setContent(
                Avatar.AvatarContent.Image(
                    url = faceUrl ?: "",
                    fallbackName = fallbackName
                )
            )
            if (enableInteraction) {
                avatarView.setOnClickListener { onUserClick(userId.orEmpty()) }
                avatarView.setOnLongClickListener {
                    onUserLongClick(userId.orEmpty())
                    true
                }
            } else {
                avatarView.setOnClickListener(null)
                avatarView.setOnLongClickListener(null)
            }
        } else {
            avatarView.visibility = View.GONE
            avatarView.setOnClickListener(null)
            avatarView.setOnLongClickListener(null)
        }
    }

    private fun updateQuoteBubble(
        message: MessageInfo,
        colors: ColorTokens,
        isMultiSelectMode: Boolean,
        enableQuoteNavigation: Boolean,
        onQuoteClick: (MessageQuoteInfo) -> Unit
    ) {
        val quoteInfo = message.quoteInfo
        if (quoteInfo == null) {
            quoteBubbleView.visibility = View.GONE
            quoteBubbleView.setOnClickListener(null)
            return
        }
        quoteBubbleView.visibility = View.VISIBLE
        quoteBubbleView.bind(
            quoteInfo = quoteInfo,
            colors = colors,
            quoteMaxWidth = bubbleContainer.maxWidth,
            onClick = if (enableQuoteNavigation && !isMultiSelectMode) {
                { onQuoteClick(quoteInfo) }
            } else {
                null
            }
        )
    }

    private fun updateAuxiliaryTextBubble(
        message: MessageInfo,
        colors: ColorTokens,
        viewModel: MessageListViewModel,
        isMultiSelectMode: Boolean,
        enableMessageInteraction: Boolean,
        onAuxiliaryTextLongClick: (anchorView: View, actions: List<MessageCustomAction>) -> Unit
    ) {
        auxiliaryTextBubbleView.visibility = View.GONE
        auxiliaryTextBubbleView.setOnLongClickListener(null)

        val msgId = message.msgID ?: return
        when (message.messageType) {
            MessageType.AUDIO -> {
                val isLoading = viewModel.isMessageProcessingAuxiliaryText(msgId)
                val payload = message.messagePayload as? AudioMessagePayload
                val asrText = payload?.asrText
                val shouldShow = isLoading || (!asrText.isNullOrBlank() && !viewModel.isAsrTextHidden(msgId))
                if (!shouldShow) {
                    return
                }
                auxiliaryTextBubbleView.visibility = View.VISIBLE
                auxiliaryTextBubbleView.bind(
                    isSelf = message.isSentBySelf,
                    isLoading = isLoading,
                    contentText = asrText,
                    footerText = null,
                    colors = colors
                )
                val layoutParams = auxiliaryTextBubbleView.layoutParams as LayoutParams
                layoutParams.topMargin = (10 * density).toInt()
                auxiliaryTextBubbleView.layoutParams = layoutParams
                if (enableMessageInteraction && !isMultiSelectMode) {
                    auxiliaryTextBubbleView.setOnLongClickListener {
                        val actions = buildAuxiliaryTextActions(
                            onCopy = { viewModel.copyAsrText(message, context) },
                            onForward = { viewModel.forwardAsrText(message) }
                        )
                        onAuxiliaryTextLongClick(auxiliaryTextBubbleView, actions)
                        true
                    }
                }
            }

            MessageType.TEXT -> {
                val isLoading = viewModel.isMessageProcessingAuxiliaryText(msgId)
                val translatedText = viewModel.getTranslatedDisplayText(message)
                val shouldShow =
                    isLoading || (!translatedText.isNullOrBlank() && !viewModel.isTranslationHidden(msgId))
                if (!shouldShow) {
                    return
                }
                auxiliaryTextBubbleView.visibility = View.VISIBLE
                auxiliaryTextBubbleView.bind(
                    isSelf = message.isSentBySelf,
                    isLoading = isLoading,
                    contentText = translatedText,
                    footerText = context.getString(R.string.message_list_translate_default_tips),
                    colors = colors
                )
                val layoutParams = auxiliaryTextBubbleView.layoutParams as LayoutParams
                layoutParams.topMargin = (6 * density).toInt()
                auxiliaryTextBubbleView.layoutParams = layoutParams
                if (enableMessageInteraction && !isMultiSelectMode) {
                    auxiliaryTextBubbleView.setOnLongClickListener {
                        val actions = buildAuxiliaryTextActions(
                            onCopy = { viewModel.copyTranslatedText(message, context) },
                            onForward = { viewModel.forwardTranslatedText(message) }
                        )
                        onAuxiliaryTextLongClick(auxiliaryTextBubbleView, actions)
                        true
                    }
                }
            }

            else -> Unit
        }
    }

    private fun buildAuxiliaryTextActions(
        onCopy: () -> Unit,
        onForward: () -> Unit
    ): List<MessageCustomAction> {
        return listOf(
            MessageCustomAction(
                ID = MessageActionIDs.COPY,
                title = context.getString(R.string.message_list_menu_copy),
                iconResID = R.drawable.message_list_menu_copy_icon,
                action = { onCopy() }
            ),
            MessageCustomAction(
                ID = MessageActionIDs.FORWARD,
                title = context.getString(R.string.message_list_menu_forward),
                iconResID = R.drawable.message_list_menu_forward_icon,
                action = { onForward() }
            )
        )
    }

    private fun updateReactionBar(
        message: MessageInfo,
        viewModel: MessageListViewModel,
        bubbleStyle: BubbleStyle,
        hasReactions: Boolean,
        isLeftAligned: Boolean
    ) {
        reactionBarView.visibility = View.GONE
        reactionBarView.setOnClickListener(null)
        (reactionBarView.parent as? ViewGroup)?.removeView(reactionBarView)

        if (!hasReactions) {
            return
        }

        val rowHorizontalGravity = if (isLeftAligned) Gravity.START else Gravity.END
        reactionBarView.visibility = View.VISIBLE
        reactionBarView.bind(message, maxBubbleWidth, rowHorizontalGravity) {
            viewModel.showReactionDetail(it)
        }

        when (bubbleStyle) {
            BubbleStyle.DEFAULT -> {
                bubbleContentContainer.addView(
                    reactionBarView,
                    LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (2 * density).toInt()
                        bottomMargin = (6 * density).toInt()
                        marginStart = (8 * density).toInt()
                        marginEnd = (8 * density).toInt()
                        gravity = rowHorizontalGravity
                    }
                )
            }

            BubbleStyle.CARD -> {
                bubbleContentContainer.addView(
                    reactionBarView,
                    LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (4 * density).toInt()
                        bottomMargin = (10 * density).toInt()
                        marginStart = (12 * density).toInt()
                        marginEnd = (12 * density).toInt()
                        gravity = rowHorizontalGravity
                    }
                )
            }

            BubbleStyle.NONE -> {
                bubbleContentContainer.addView(
                    reactionBarView,
                    LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (MEDIA_BUBBLE_REACTION_TOP_MARGIN_DP * density).toInt()
                        gravity = rowHorizontalGravity
                    }
                )
            }

            else -> {
                bubbleContentContainer.addView(
                    reactionBarView,
                    LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (2 * density).toInt()
                        bottomMargin = (6 * density).toInt()
                        marginStart = (8 * density).toInt()
                        marginEnd = (8 * density).toInt()
                        gravity = rowHorizontalGravity
                    }
                )
            }
        }
    }

    private fun applyInteractionState(
        interactionTargetView: View,
        isMultiSelectMode: Boolean,
        enableMessageInteraction: Boolean,
        onLongClick: (View) -> Unit,
        onCheckToggle: () -> Unit
    ) {
        MessageInteractionBinder.bind(
            containerView = bubbleContainer,
            interactionTargetView = interactionTargetView,
            itemView = this@MessageItemView,
            isMultiSelectMode = isMultiSelectMode,
            enableMessageInteraction = enableMessageInteraction,
            onLongClick = onLongClick,
            onCheckToggle = onCheckToggle
        )
    }

    fun flashHighlight(
        message: MessageInfo,
        colors: ColorTokens,
        onHighlightConsumed: (String) -> Unit
    ) {
        highlightController.apply(message, colors, true, onHighlightConsumed)
    }

    private fun applyMaxWidthForMode(
        checkBoxVisible: Boolean,
        statusReserveWidth: Int
    ) {
        val maxWidths = MessageItemDisplayPolicy.resolveMaxWidthsForMode(
            maxRowWidth = maxRowWidth,
            preferredBubbleMaxWidth = maxBubbleWidth,
            checkBoxVisible = checkBoxVisible,
            statusReserveWidth = statusReserveWidth,
            density = density,
            inlineTimeReserveWidth = inlineTimeReserveWidth
        )
        if (bubbleRow.maxWidth != maxWidths.rowMaxWidth) {
            bubbleRow.maxWidth = maxWidths.rowMaxWidth
        }
        if (bubbleContainer.maxWidth != maxWidths.bubbleMaxWidth) {
            bubbleContainer.maxWidth = maxWidths.bubbleMaxWidth
        }
        val auxiliaryTextMaxWidth = MessageItemDisplayPolicy.resolveAuxiliaryTextMaxWidth(
            maxRowWidth = maxRowWidth,
            preferredBubbleMaxWidth = maxBubbleWidth,
            checkBoxVisible = checkBoxVisible,
            density = density,
            inlineTimeReserveWidth = inlineTimeReserveWidth
        )
        if (auxiliaryTextBubbleView.maxWidth != auxiliaryTextMaxWidth) {
            auxiliaryTextBubbleView.maxWidth = auxiliaryTextMaxWidth
        }
    }

    private fun ensureContentView(renderer: MessageRenderer): View {
        val existing = cachedContentView
        if (existing != null && cachedContentRenderer === renderer && existing.parent === bubbleContentContainer) {
            applyContentViewWidth()
            return existing
        }
        existing?.let { oldView ->
            recycleContentView(cachedContentRenderer, oldView)
            (oldView.parent as? ViewGroup)?.removeView(oldView)
        }
        val newView = renderer.createView(context, bubbleContentContainer)
        bubbleContentContainer.addView(newView, 0)
        cachedContentDesignedWidth = newView.layoutParams?.width
            ?: ViewGroup.LayoutParams.WRAP_CONTENT
        cachedContentView = newView
        cachedContentRenderer = renderer
        applyContentViewWidth()
        return newView
    }

    private fun applyContentViewWidth() {
        val contentView = cachedContentView ?: return
        val innerPad = bubbleContentContainer.paddingLeft + bubbleContentContainer.paddingRight
        val statusReserve = (STATUS_AREA_WORST_CASE_DP * density).toInt()
        val bubbleCap = (bubbleContainer.maxWidth - innerPad).coerceAtLeast(0)
        val rowCap = (bubbleRow.maxWidth - statusReserve - innerPad).coerceAtLeast(0)
        val cap = when {
            bubbleCap > 0 && rowCap > 0 -> minOf(bubbleCap, rowCap)
            else -> maxOf(bubbleCap, rowCap)
        }
        (contentView as? TextMessageWidthAware)?.setMessageMaxWidth(cap)

        val designedWidth = cachedContentDesignedWidth
        if (designedWidth <= 0) return
        val lp = contentView.layoutParams ?: return
        val target = if (cap > 0) minOf(designedWidth, cap) else designedWidth
        if (lp.width != target) {
            lp.width = target
            contentView.layoutParams = lp
        }
    }

    private fun detachReactionBarFromContainer() {
        (reactionBarView.parent as? ViewGroup)?.removeView(reactionBarView)
    }

    fun recycle() {
        statusController.stopSendingRotator()
        highlightController.clear()
        val contentView = cachedContentView ?: return
        recycleContentView(cachedContentRenderer, contentView)
    }

    private fun recycleContentView(renderer: MessageRenderer?, contentView: View) {
        (renderer as? RecyclableMessageRenderer)?.onViewRecycled(contentView)
        contentView.setTag(R.id.message_list_render_context_tag, null)
    }

}
