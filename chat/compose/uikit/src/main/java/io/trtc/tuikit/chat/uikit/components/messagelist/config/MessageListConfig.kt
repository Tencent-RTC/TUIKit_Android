package io.trtc.tuikit.chat.uikit.components.messagelist.config

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.config.MessageAction
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageCellRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderRule
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageMatcher
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageSummaryProvider
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.jsonData2Dictionary
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageType

interface MessageListConfigProtocol {
    val alignment: MessageAlignment
    val background: MessageListBackground?
        get() = null
    val defaultBubbleAppearance: MessageBubbleAppearance?
        get() = null
    val ownBubbleAppearance: MessageBubbleAppearance?
        get() = null
    val incomingBubbleAppearance: MessageBubbleAppearance?
        get() = null
    val leftBubbleAppearance: MessageBubbleAppearance?
        get() = null
    val rightBubbleAppearance: MessageBubbleAppearance?
        get() = null
    val isShowTimeMessage: Boolean
    val isShowLeftAvatar: Boolean
    val isShowLeftNickname: Boolean
    val isShowRightAvatar: Boolean
    val isShowRightNickname: Boolean
    val cellSpacing: Dp
    val isShowSystemMessage: Boolean
    val isShowUnsupportMessage: Boolean
    val horizontalPadding: Dp
    val avatarSpacing: Dp
    val isShowReadReceipt: Boolean

    // actions
    val isSupportCopy: Boolean
    val isSupportDelete: Boolean
    val isSupportRecall: Boolean
    val isSupportMultiSelect: Boolean
    val isSupportForward: Boolean
    val isSupportReaction: Boolean
    val isSupportQuote: Boolean
        get() = true
    val isSupportConvertToText: Boolean
        get() = true
    val isSupportTranslate: Boolean
        get() = true
    val isSupportListenFromHere: Boolean
        get() = true

    val enableTyping: Boolean
        get() = true

    val messageExclusionMatchers: List<MessageMatcher>
        get() = emptyList()

    val actionCustomizer: MessageActionCustomizer?
        get() = null
}

internal interface DelegatingMessageListConfig {
    val delegateConfig: MessageListConfigProtocol
}

internal fun MessageListConfigProtocol.messageRenderRules(): List<MessageRenderRule> {
    return when (this) {
        is ChatMessageListConfig -> customRenderRules
        is DelegatingMessageListConfig -> delegateConfig.messageRenderRules()
        else -> emptyList()
    }
}

sealed class MessageListBackground {
    data class Image(val uri: Any) : MessageListBackground()

    data class Color(val color: Int) : MessageListBackground()

    data class Gradient(
        val colors: List<Int>,
        val direction: GradientDirection = GradientDirection.TOP_BOTTOM
    ) : MessageListBackground() {
        init {
            require(colors.size >= MIN_GRADIENT_COLOR_COUNT) {
                "Gradient background requires at least two colors."
            }
        }
    }

    enum class GradientDirection {
        TOP_BOTTOM,
        BOTTOM_TOP,
        LEFT_RIGHT,
        RIGHT_LEFT,
        TOP_LEFT_BOTTOM_RIGHT,
        BOTTOM_RIGHT_TOP_LEFT
    }

    private companion object {
        const val MIN_GRADIENT_COLOR_COUNT = 2
    }
}

sealed class MessageBubbleBackground {
    data class Color(@ColorInt val color: Int) : MessageBubbleBackground()

    data class Gradient(
        val colors: List<Int>,
        val direction: GradientDirection = GradientDirection.TOP_BOTTOM
    ) : MessageBubbleBackground() {
        init {
            require(colors.size >= MIN_GRADIENT_COLOR_COUNT) {
                "Gradient bubble background requires at least two colors."
            }
        }
    }

    data class DrawableResource(@androidx.annotation.DrawableRes val resId: Int) : MessageBubbleBackground()

    data class DrawableValue(val drawable: Drawable) : MessageBubbleBackground()

    enum class GradientDirection {
        TOP_BOTTOM,
        BOTTOM_TOP,
        LEFT_RIGHT,
        RIGHT_LEFT,
        TOP_LEFT_BOTTOM_RIGHT,
        BOTTOM_RIGHT_TOP_LEFT
    }

    private companion object {
        const val MIN_GRADIENT_COLOR_COUNT = 2
    }
}

data class MessageBubbleCornerRadius(
    val topLeft: Int? = null,
    val topRight: Int? = null,
    val bottomRight: Int? = null,
    val bottomLeft: Int? = null
) {
    companion object {
        fun all(radius: Int): MessageBubbleCornerRadius {
            return MessageBubbleCornerRadius(
                topLeft = radius,
                topRight = radius,
                bottomRight = radius,
                bottomLeft = radius
            )
        }
    }
}

data class MessageBubbleStroke(
    val width: Int,
    @ColorInt val color: Int
) {
    init {
        require(width >= 0) {
            "Bubble stroke width must not be negative."
        }
    }
}

data class MessageBubbleInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    constructor(horizontal: Int, vertical: Int) : this(horizontal, vertical, horizontal, vertical)

    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) {
            "Bubble content insets must not be negative."
        }
    }

    companion object {
        fun all(value: Int): MessageBubbleInsets {
            return MessageBubbleInsets(value, value, value, value)
        }

        fun symmetric(horizontal: Int, vertical: Int): MessageBubbleInsets {
            return MessageBubbleInsets(horizontal, vertical, horizontal, vertical)
        }
    }
}

data class MessageBubbleSize(
    val width: Int? = null,
    val height: Int? = null
) {
    init {
        require(width == null || width >= 0) {
            "Bubble minimum width must not be negative."
        }
        require(height == null || height >= 0) {
            "Bubble minimum height must not be negative."
        }
    }
}

data class MessageBubbleAppearance(
    val background: MessageBubbleBackground? = null,
    val cornerRadius: MessageBubbleCornerRadius? = null,
    val stroke: MessageBubbleStroke? = null,
    val contentInsets: MessageBubbleInsets? = null,
    val minimumSize: MessageBubbleSize? = null
) {
    internal fun mergeWith(override: MessageBubbleAppearance?): MessageBubbleAppearance {
        if (override == null) {
            return this
        }
        return MessageBubbleAppearance(
            background = override.background ?: background,
            cornerRadius = cornerRadius.mergeWith(override.cornerRadius),
            stroke = override.stroke ?: stroke,
            contentInsets = override.contentInsets ?: contentInsets,
            minimumSize = minimumSize.mergeWith(override.minimumSize)
        )
    }

    internal val isEmpty: Boolean
        get() = background == null &&
            cornerRadius == null &&
            stroke == null &&
            contentInsets == null &&
            minimumSize == null
}

private fun MessageBubbleCornerRadius?.mergeWith(
    override: MessageBubbleCornerRadius?
): MessageBubbleCornerRadius? {
    if (this == null) {
        return override
    }
    if (override == null) {
        return this
    }
    return MessageBubbleCornerRadius(
        topLeft = override.topLeft ?: topLeft,
        topRight = override.topRight ?: topRight,
        bottomRight = override.bottomRight ?: bottomRight,
        bottomLeft = override.bottomLeft ?: bottomLeft
    )
}

private fun MessageBubbleSize?.mergeWith(override: MessageBubbleSize?): MessageBubbleSize? {
    if (this == null) {
        return override
    }
    if (override == null) {
        return this
    }
    return MessageBubbleSize(
        width = override.width ?: width,
        height = override.height ?: height
    )
}

class ChatMessageListConfig : MessageListConfigProtocol {

    private var _alignment: MessageAlignment? = null
    private var _isShowTimeMessage: Boolean? = null
    private var _isShowLeftAvatar: Boolean? = null
    private var _isShowLeftNickname: Boolean? = null
    private var _isShowRightAvatar: Boolean? = null
    private var _isShowRightNickname: Boolean? = null
    private var _cellSpacing: Dp? = null
    private var _isShowSystemMessage: Boolean? = null
    private var _isShowUnsupportMessage: Boolean? = null
    private var _horizontalPadding: Dp? = null
    private var _avatarSpacing: Dp? = null
    private var _isShowReadReceipt: Boolean? = null
    private var _isSupportCopy: Boolean? = null
    private var _isSupportDelete: Boolean? = null
    private var _isSupportRecall: Boolean? = null
    private var _isSupportMultiSelect: Boolean? = null
    private var _isSupportForward: Boolean? = null
    private var _isSupportReaction: Boolean? = null
    private var _isSupportQuote: Boolean? = null
    private var _isSupportConvertToText: Boolean? = null
    private var _isSupportTranslate: Boolean? = null
    private var _isSupportListenFromHere: Boolean? = null
    private var _enableTyping: Boolean? = null
    private var _background: MessageListBackground? = null
    private var _defaultBubbleAppearance: MessageBubbleAppearance? = null
    private var _ownBubbleAppearance: MessageBubbleAppearance? = null
    private var _incomingBubbleAppearance: MessageBubbleAppearance? = null
    private var _leftBubbleAppearance: MessageBubbleAppearance? = null
    private var _rightBubbleAppearance: MessageBubbleAppearance? = null
    private var _actionCustomizer: MessageActionCustomizer? = null
    private val _customRenderRules = mutableListOf<MessageRenderRule>()

    internal val customRenderRules: List<MessageRenderRule>
        get() = _customRenderRules.toList()

    constructor(
        alignment: MessageAlignment? = null,
        isShowTimeMessage: Boolean? = null,
        isShowLeftAvatar: Boolean? = null,
        isShowLeftNickname: Boolean? = null,
        isShowRightAvatar: Boolean? = null,
        isShowRightNickname: Boolean? = null,
        cellSpacing: Dp? = null,
        isShowSystemMessage: Boolean? = null,
        isShowUnsupportMessage: Boolean? = null,
        horizontalPadding: Dp? = null,
        avatarSpacing: Dp? = null,
        isShowReadReceipt: Boolean? = null,
        isSupportCopy: Boolean? = null,
        isSupportDelete: Boolean? = null,
        isSupportRecall: Boolean? = null,
        isSupportMultiSelect: Boolean? = null,
        isSupportForward: Boolean? = null,
        isSupportReaction: Boolean? = null,
        isSupportQuote: Boolean? = null,
        isSupportConvertToText: Boolean? = null,
        isSupportTranslate: Boolean? = null,
        isSupportListenFromHere: Boolean? = null,
        enableTyping: Boolean? = null,
        background: MessageListBackground? = null,
        defaultBubbleAppearance: MessageBubbleAppearance? = null,
        ownBubbleAppearance: MessageBubbleAppearance? = null,
        incomingBubbleAppearance: MessageBubbleAppearance? = null,
        leftBubbleAppearance: MessageBubbleAppearance? = null,
        rightBubbleAppearance: MessageBubbleAppearance? = null,
    ) {
        _alignment = alignment
        _isShowTimeMessage = isShowTimeMessage
        _isShowLeftAvatar = isShowLeftAvatar
        _isShowLeftNickname = isShowLeftNickname
        _isShowRightAvatar = isShowRightAvatar
        _isShowRightNickname = isShowRightNickname
        _cellSpacing = cellSpacing
        _isShowSystemMessage = isShowSystemMessage
        _isShowUnsupportMessage = isShowUnsupportMessage
        _horizontalPadding = horizontalPadding
        _avatarSpacing = avatarSpacing
        _isShowReadReceipt = isShowReadReceipt
        _isSupportCopy = isSupportCopy
        _isSupportDelete = isSupportDelete
        _isSupportRecall = isSupportRecall
        _isSupportMultiSelect = isSupportMultiSelect
        _isSupportForward = isSupportForward
        _isSupportReaction = isSupportReaction
        _isSupportQuote = isSupportQuote
        _isSupportConvertToText = isSupportConvertToText
        _isSupportTranslate = isSupportTranslate
        _isSupportListenFromHere = isSupportListenFromHere
        _enableTyping = enableTyping
        _background = background
        _defaultBubbleAppearance = defaultBubbleAppearance
        _ownBubbleAppearance = ownBubbleAppearance
        _incomingBubbleAppearance = incomingBubbleAppearance
        _leftBubbleAppearance = leftBubbleAppearance
        _rightBubbleAppearance = rightBubbleAppearance
    }

    private val _messageExclusionMatchers = mutableListOf<MessageMatcher>()

    override val messageExclusionMatchers: List<MessageMatcher>
        get() = _messageExclusionMatchers.toList()

    override var alignment: MessageAlignment
        get() = _alignment ?: AppBuilderConfig.messageAlignment
        set(value) {
            _alignment = value
        }

    override var background: MessageListBackground?
        get() = _background
        set(value) {
            _background = value
        }

    override var defaultBubbleAppearance: MessageBubbleAppearance?
        get() = _defaultBubbleAppearance
        set(value) {
            _defaultBubbleAppearance = value
        }

    override var ownBubbleAppearance: MessageBubbleAppearance?
        get() = _ownBubbleAppearance
        set(value) {
            _ownBubbleAppearance = value
        }

    override var incomingBubbleAppearance: MessageBubbleAppearance?
        get() = _incomingBubbleAppearance
        set(value) {
            _incomingBubbleAppearance = value
        }

    override var leftBubbleAppearance: MessageBubbleAppearance?
        get() = _leftBubbleAppearance
        set(value) {
            _leftBubbleAppearance = value
        }

    override var rightBubbleAppearance: MessageBubbleAppearance?
        get() = _rightBubbleAppearance
        set(value) {
            _rightBubbleAppearance = value
        }

    override var isShowTimeMessage: Boolean
        get() = _isShowTimeMessage ?: true
        set(value) {
            _isShowTimeMessage = value
        }

    override var isShowLeftAvatar: Boolean
        get() = _isShowLeftAvatar ?: true
        set(value) {
            _isShowLeftAvatar = value
        }

    override var isShowLeftNickname: Boolean
        get() = _isShowLeftNickname ?: true
        set(value) {
            _isShowLeftNickname = value
        }

    override var isShowRightAvatar: Boolean
        get() = _isShowRightAvatar ?: true
        set(value) {
            _isShowRightAvatar = value
        }

    override var isShowRightNickname: Boolean
        get() = _isShowRightNickname ?: true
        set(value) {
            _isShowRightNickname = value
        }

    override var cellSpacing: Dp
        get() = _cellSpacing ?: 10.0.dp
        set(value) {
            _cellSpacing = value
        }

    override var isShowSystemMessage: Boolean
        get() = _isShowSystemMessage ?: true
        set(value) {
            _isShowSystemMessage = value
        }

    override var isShowUnsupportMessage: Boolean
        get() = _isShowUnsupportMessage ?: true
        set(value) {
            _isShowUnsupportMessage = value
        }

    override var isSupportCopy: Boolean
        get() = _isSupportCopy
            ?: AppBuilderConfig.messageActionList.contains(MessageAction.COPY)
        set(value) {
            _isSupportCopy = value
        }

    override var isSupportDelete: Boolean
        get() = _isSupportDelete
            ?: AppBuilderConfig.messageActionList.contains(MessageAction.DELETE)
        set(value) {
            _isSupportDelete = value
        }

    override var isSupportRecall: Boolean
        get() = _isSupportRecall
            ?: AppBuilderConfig.messageActionList.contains(MessageAction.RECALL)
        set(value) {
            _isSupportRecall = value
        }

    override var horizontalPadding: Dp
        get() = _horizontalPadding ?: 16.0.dp
        set(value) {
            _horizontalPadding = value
        }

    override var avatarSpacing: Dp
        get() = _avatarSpacing ?: 8.0.dp
        set(value) {
            _avatarSpacing = value
        }

    override var isShowReadReceipt: Boolean
        get() = _isShowReadReceipt ?: AppBuilderConfig.enableReadReceipt
        set(value) {
            _isShowReadReceipt = value
        }

    override var isSupportMultiSelect: Boolean
        get() = _isSupportMultiSelect
            ?: true
        set(value) {
            _isSupportMultiSelect = value
        }

    override var isSupportForward: Boolean
        get() = _isSupportForward
            ?: true
        set(value) {
            _isSupportForward = value
        }

    override var isSupportReaction: Boolean
        get() = _isSupportReaction ?: true
        set(value) {
            _isSupportReaction = value
        }

    override var isSupportQuote: Boolean
        get() = _isSupportQuote
            ?: AppBuilderConfig.messageActionList.contains(MessageAction.QUOTE)
        set(value) {
            _isSupportQuote = value
        }

    override var isSupportConvertToText: Boolean
        get() = _isSupportConvertToText ?: true
        set(value) {
            _isSupportConvertToText = value
        }

    override var isSupportTranslate: Boolean
        get() = _isSupportTranslate ?: true
        set(value) {
            _isSupportTranslate = value
        }

    override var isSupportListenFromHere: Boolean
        get() = _isSupportListenFromHere ?: true
        set(value) {
            _isSupportListenFromHere = value
        }

    override var enableTyping: Boolean
        get() = _enableTyping ?: true
        set(value) {
            _enableTyping = value
        }

    override val actionCustomizer: MessageActionCustomizer?
        get() = _actionCustomizer

    fun setEnableTyping(enable: Boolean): ChatMessageListConfig {
        _enableTyping = enable
        return this
    }

    fun customizeActions(block: MessageActionEditor.() -> Unit): ChatMessageListConfig = apply {
        _actionCustomizer = MessageActionCustomizer { editor ->
            editor.block()
        }
    }

    fun setBackgroundImage(uri: Any): ChatMessageListConfig {
        _background = MessageListBackground.Image(uri)
        return this
    }

    fun setBackgroundColor(@ColorInt color: Int): ChatMessageListConfig {
        _background = MessageListBackground.Color(color)
        return this
    }

    fun setBackgroundGradient(
        colors: IntArray,
        direction: MessageListBackground.GradientDirection = MessageListBackground.GradientDirection.TOP_BOTTOM
    ): ChatMessageListConfig {
        _background = MessageListBackground.Gradient(
            colors = colors.toList(),
            direction = direction
        )
        return this
    }

    fun clearBackground(): ChatMessageListConfig {
        _background = null
        return this
    }

    fun setDefaultBubbleAppearance(appearance: MessageBubbleAppearance?): ChatMessageListConfig {
        _defaultBubbleAppearance = appearance
        return this
    }

    fun clearDefaultBubbleAppearance(): ChatMessageListConfig {
        _defaultBubbleAppearance = null
        return this
    }

    fun setOwnBubbleAppearance(appearance: MessageBubbleAppearance?): ChatMessageListConfig {
        _ownBubbleAppearance = appearance
        return this
    }

    fun clearOwnBubbleAppearance(): ChatMessageListConfig {
        _ownBubbleAppearance = null
        return this
    }

    fun setIncomingBubbleAppearance(appearance: MessageBubbleAppearance?): ChatMessageListConfig {
        _incomingBubbleAppearance = appearance
        return this
    }

    fun clearIncomingBubbleAppearance(): ChatMessageListConfig {
        _incomingBubbleAppearance = null
        return this
    }

    fun setLeftBubbleAppearance(appearance: MessageBubbleAppearance?): ChatMessageListConfig {
        _leftBubbleAppearance = appearance
        return this
    }

    fun clearLeftBubbleAppearance(): ChatMessageListConfig {
        _leftBubbleAppearance = null
        return this
    }

    fun setRightBubbleAppearance(appearance: MessageBubbleAppearance?): ChatMessageListConfig {
        _rightBubbleAppearance = appearance
        return this
    }

    fun clearRightBubbleAppearance(): ChatMessageListConfig {
        _rightBubbleAppearance = null
        return this
    }

    @JvmOverloads
    fun setCustomMessageRenderer(
        businessID: String,
        renderer: MessageRenderer,
        priority: Int = 0,
        summaryProvider: MessageSummaryProvider? = null
    ): ChatMessageListConfig {
        return addCustomMessageRenderer(
            matcher = businessIDMatcher(businessID),
            renderer = renderer,
            priority = priority,
            summaryProvider = summaryProvider
        )
    }

    @JvmOverloads
    fun addCustomMessageRenderer(
        matcher: MessageMatcher,
        renderer: MessageRenderer,
        priority: Int = 0,
        summaryProvider: MessageSummaryProvider? = null
    ): ChatMessageListConfig {
        _customRenderRules.add(
            MessageRenderRule(
                matcher = matcher,
                contentRenderer = renderer,
                summaryProvider = summaryProvider,
                priority = priority
            )
        )
        return this
    }

    @JvmOverloads
    fun setCustomMessageCellRenderer(
        businessID: String,
        renderer: MessageCellRenderer,
        priority: Int = 0,
        summaryProvider: MessageSummaryProvider? = null
    ): ChatMessageListConfig {
        return addCustomMessageCellRenderer(
            matcher = businessIDMatcher(businessID),
            renderer = renderer,
            priority = priority,
            summaryProvider = summaryProvider
        )
    }

    @JvmOverloads
    fun addCustomMessageCellRenderer(
        matcher: MessageMatcher,
        renderer: MessageCellRenderer,
        priority: Int = 0,
        summaryProvider: MessageSummaryProvider? = null
    ): ChatMessageListConfig {
        _customRenderRules.add(
            MessageRenderRule(
                matcher = matcher,
                cellRenderer = renderer,
                summaryProvider = summaryProvider,
                priority = priority
            )
        )
        return this
    }

    @JvmOverloads
    fun setCustomMessageSummary(
        businessID: String,
        summaryProvider: MessageSummaryProvider,
        priority: Int = 0
    ): ChatMessageListConfig {
        return addCustomMessageSummary(
            matcher = businessIDMatcher(businessID),
            summaryProvider = summaryProvider,
            priority = priority
        )
    }

    @JvmOverloads
    fun addCustomMessageSummary(
        matcher: MessageMatcher,
        summaryProvider: MessageSummaryProvider,
        priority: Int = 0
    ): ChatMessageListConfig {
        _customRenderRules.add(
            MessageRenderRule(
                matcher = matcher,
                summaryProvider = summaryProvider,
                priority = priority
            )
        )
        return this
    }

    fun addMessageExclusion(matcher: MessageMatcher): ChatMessageListConfig {
        _messageExclusionMatchers.add(matcher)
        return this
    }

    fun excludeMessagesByType(vararg types: MessageType): ChatMessageListConfig {
        val excludedTypes = types.toSet()
        return addMessageExclusion(MessageMatcher { message -> message.messageType in excludedTypes })
    }

    fun excludeCustomMessagesByBusinessID(businessID: String): ChatMessageListConfig {
        return addMessageExclusion(businessIDMatcher(businessID))
    }

    fun clearMessageExclusions(): ChatMessageListConfig {
        _messageExclusionMatchers.clear()
        return this
    }

    private fun businessIDMatcher(businessID: String): MessageMatcher {
        return MessageMatcher { message ->
            message.messageType == MessageType.CUSTOM &&
                extractCustomBusinessID(message) == businessID
        }
    }

    private fun extractCustomBusinessID(message: io.trtc.tuikit.atomicxcore.api.message.MessageInfo): String? {
        val data = (message.messagePayload as? CustomMessagePayload)?.customData ?: return null
        return jsonData2Dictionary(data)?.get("businessID") as? String
    }
}
