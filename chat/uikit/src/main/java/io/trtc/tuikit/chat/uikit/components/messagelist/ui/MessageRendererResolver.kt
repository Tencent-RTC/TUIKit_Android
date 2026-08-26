package io.trtc.tuikit.chat.uikit.components.messagelist.ui
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallingMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallingTipsMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.ChatbotMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CreateGroupMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.DefaultMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.FaceMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.FileMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.ImageMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.MergeMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.SoundMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.SystemMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.TextMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.VideoMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallParticipantType
import io.trtc.tuikit.chat.uikit.components.common.jsonData2Dictionary
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageProtocol
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSource
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType

internal class MessageRendererResolver(
    customRules: List<MessageRenderRule>
) {
    private val rules = customRules.sortedByDescending { it.priority }
    private val visualRules = rules.filter { it.contentRenderer != null || it.cellRenderer != null }
    private val cellRendererByViewType = visualRules.withIndex().mapNotNull { (index, rule) ->
        rule.cellRenderer?.let { renderer ->
            customViewType(index, CELL_VIEW_TYPE_BASE) to renderer
        }
    }.toMap()

    sealed class ResolvedRenderer {
        abstract val viewType: Int

        data class CustomCell(
            val renderer: MessageCellRenderer,
            override val viewType: Int
        ) : ResolvedRenderer()

        data class CustomContent(
            val renderer: MessageContentRenderer,
            override val viewType: Int
        ) : ResolvedRenderer()

        data class BuiltInContent(
            val renderer: MessageRenderer,
            override val viewType: Int
        ) : ResolvedRenderer()
    }

    fun resolve(message: MessageInfo): ResolvedRenderer {
        if (message.status == MessageStatus.REVOKED) {
            return ResolvedRenderer.BuiltInContent(
                renderer = systemMessageRenderer,
                viewType = REVOKED_VIEW_TYPE
            )
        }

        val cellRule = visualRules.withIndex().firstOrNull { (_, rule) ->
            rule.cellRenderer != null && rule.matcher.matches(message)
        }
        val cellRenderer = cellRule?.value?.cellRenderer
        if (cellRenderer != null) {
            return ResolvedRenderer.CustomCell(
                renderer = cellRenderer,
                viewType = customViewType(cellRule.index, CELL_VIEW_TYPE_BASE)
            )
        }

        val contentRule = visualRules.withIndex().firstOrNull { (_, rule) ->
            rule.contentRenderer != null && rule.matcher.matches(message)
        }
        val contentRenderer = contentRule?.value?.contentRenderer
        if (contentRenderer != null) {
            return ResolvedRenderer.CustomContent(
                renderer = contentRenderer,
                viewType = customViewType(contentRule.index, CONTENT_VIEW_TYPE_BASE)
            )
        }

        val builtIn = resolveBuiltIn(message)
        return ResolvedRenderer.BuiltInContent(
            renderer = builtIn.renderer,
            viewType = builtIn.viewType
        )
    }

    fun isDefaultBuiltInRenderer(message: MessageInfo): Boolean {
        val renderer = resolve(message)
        return renderer is ResolvedRenderer.BuiltInContent &&
            renderer.renderer === defaultRenderer
    }

    fun cellRendererForViewType(viewType: Int): MessageCellRenderer? {
        if (!isCustomCellViewType(viewType)) {
            return null
        }
        return cellRendererByViewType[viewType]
    }

    fun isCustomCellViewType(viewType: Int): Boolean {
        return viewType in CELL_VIEW_TYPE_BASE until CELL_VIEW_TYPE_BASE + CUSTOM_VIEW_TYPE_RANGE
    }

    fun isCustomContentViewType(viewType: Int): Boolean {
        return viewType in CONTENT_VIEW_TYPE_BASE until CONTENT_VIEW_TYPE_BASE + CUSTOM_VIEW_TYPE_RANGE
    }

    private fun customViewType(
        ruleIndex: Int,
        base: Int
    ): Int {
        require(ruleIndex < CUSTOM_VIEW_TYPE_RANGE) {
            "Too many custom message renderer rules."
        }
        return base + ruleIndex
    }

    private companion object {
        private val defaultRenderer = DefaultMessageRenderer()
        private val systemMessageRenderer = SystemMessageRenderer()
        private val callingMessageRenderer = CallingMessageRenderer()
        private val callingTipsMessageRenderer = CallingTipsMessageRenderer()
        private val chatbotMessageRenderer = ChatbotMessageRenderer()
        private val renderers = mapOf(
            MessageType.TEXT to TextMessageRenderer(),
            MessageType.IMAGE to ImageMessageRenderer(),
            MessageType.VIDEO to VideoMessageRenderer(),
            MessageType.FILE to FileMessageRenderer(),
            MessageType.AUDIO to SoundMessageRenderer(),
            MessageType.FACE to FaceMessageRenderer(),
            MessageType.TIPS to systemMessageRenderer,
            MessageType.MERGED to MergeMessageRenderer()
        )
        private val customBusinessRenderers = mapOf(
            "group_create" to CreateGroupMessageRenderer()
        )

        private const val CUSTOM_VIEW_TYPE_RANGE = 1_000_000
        private const val CELL_VIEW_TYPE_BASE = Int.MIN_VALUE + CUSTOM_VIEW_TYPE_RANGE
        private const val CONTENT_VIEW_TYPE_BASE = Int.MIN_VALUE + CUSTOM_VIEW_TYPE_RANGE * 2
        private const val UNKNOWN_VIEW_TYPE = -1
        private const val CALLING_C2C_VIEW_TYPE = -2
        private const val CALLING_TIPS_VIEW_TYPE = -3
        private const val REVOKED_VIEW_TYPE = -4
        private const val CHATBOT_VIEW_TYPE = -5
        private const val CUSTOM_VIEW_TYPE_BASE = 1000
        private const val CUSTOM_VIEW_TYPE_HASH_MASK = 0x3FFFFFFF

        private fun resolveBuiltIn(message: MessageInfo): BuiltInResolution {
            if (message.messageType == MessageType.CUSTOM) {
                val chatbotData = ChatbotMessageProtocol.parse(message)
                if (chatbotData?.source == ChatbotMessageSource.FLOW ||
                    chatbotData?.source == ChatbotMessageSource.ERROR
                ) {
                    return BuiltInResolution(chatbotMessageRenderer, CHATBOT_VIEW_TYPE)
                }
                val callModel = CallMessageParser.parse(message)
                if (callModel != null) {
                    return if (callModel.participantType == CallParticipantType.GROUP) {
                        BuiltInResolution(callingTipsMessageRenderer, CALLING_TIPS_VIEW_TYPE)
                    } else {
                        BuiltInResolution(callingMessageRenderer, CALLING_C2C_VIEW_TYPE)
                    }
                }
                val businessID = extractCustomBusinessID(message)
                val viewType = businessID?.let {
                    CUSTOM_VIEW_TYPE_BASE + (it.hashCode() and CUSTOM_VIEW_TYPE_HASH_MASK)
                } ?: UNKNOWN_VIEW_TYPE
                return BuiltInResolution(
                    renderer = customBusinessRenderers[businessID] ?: defaultRenderer,
                    viewType = viewType
                )
            }
            return BuiltInResolution(
                renderer = renderers[message.messageType] ?: defaultRenderer,
                viewType = message.messageType.ordinal
            )
        }

        private fun extractCustomBusinessID(message: MessageInfo): String? {
            val customData = (message.messagePayload as? CustomMessagePayload)?.customData
            return jsonData2Dictionary(customData)?.get("businessID")
        }
    }

    private data class BuiltInResolution(
        val renderer: MessageRenderer,
        val viewType: Int
    )
}
