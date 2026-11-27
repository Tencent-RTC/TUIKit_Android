package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageProtocol
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageRenderer
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSource
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.config.messageRenderRules
import io.trtc.tuikit.chat.uikit.components.messagelist.model.CallParticipantType
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallingMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallingTipsMessageRenderer
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
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageMatcher
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageSummaryProvider
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.jsonData2Dictionary
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class MessageRenderRule(
    val matcher: MessageMatcher,
    val contentRenderer: MessageRenderer? = null,
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

private data class MessageRendererRule(
    val id: Long,
    val matcher: MessageRendererMatcher,
    val renderer: MessageRenderer? = null,
    val cellRenderer: MessageCellRenderer? = null,
    val priority: Int = 0
)

class MessageRendererRegistration internal constructor(
    private val unregisterAction: () -> Unit
) {
    private val unregistered = AtomicBoolean(false)

    fun unregister() {
        if (unregistered.compareAndSet(false, true)) {
            unregisterAction()
        }
    }
}

private class RendererLayer(
    val renderer: MessageRenderer,
    var unregistered: Boolean = false
)

object MessageRendererRegistry {
    private val lock = Any()
    private val nextRuleId = AtomicLong(0)
    private val renderers = mutableMapOf<MessageType, MessageRenderer>()
    private val customRenderers = mutableMapOf<String, MessageRenderer>()
    private val rendererLayers = mutableMapOf<MessageType, MutableList<RendererLayer>>()
    private val customRendererLayers = mutableMapOf<String, MutableList<RendererLayer>>()
    private val rendererRules = mutableListOf<MessageRendererRule>()
    private val defaultRenderer = DefaultMessageRenderer()
    private val callingMessageRenderer = CallingMessageRenderer()
    private val callingTipsMessageRenderer = CallingTipsMessageRenderer()
    private val chatbotMessageRenderer = ChatbotMessageRenderer()
    private val systemMessageRenderer = SystemMessageRenderer()

    init {
        registerRenderer(MessageType.TEXT, TextMessageRenderer())
        registerRenderer(MessageType.FILE, FileMessageRenderer())
        registerRenderer(MessageType.IMAGE, ImageMessageRenderer())
        registerRenderer(MessageType.VIDEO, VideoMessageRenderer())
        registerRenderer(MessageType.AUDIO, SoundMessageRenderer())
        registerRenderer(MessageType.FACE, FaceMessageRenderer())
        registerRenderer(MessageType.TIPS, systemMessageRenderer)
        registerRenderer(MessageType.MERGED, MergeMessageRenderer())
        registerCustomMessageRenderer(
            "group_create",
            CreateGroupMessageRenderer()
        )
    }

    fun registerRenderer(type: MessageType, renderer: MessageRenderer): MessageRendererRegistration {
        val layer = RendererLayer(renderer)
        synchronized(lock) {
            pushLayer(rendererLayers, renderers, type, layer)
        }
        return MessageRendererRegistration {
            synchronized(lock) {
                removeLayer(rendererLayers, renderers, type, layer)
            }
        }
    }

    fun unregisterRenderer(type: MessageType) {
        synchronized(lock) {
            popActiveLayer(rendererLayers, renderers, type)
        }
    }

    fun registerCustomMessageRenderer(businessID: String, renderer: MessageRenderer): MessageRendererRegistration {
        val layer = RendererLayer(renderer)
        synchronized(lock) {
            pushLayer(customRendererLayers, customRenderers, businessID, layer)
        }
        return MessageRendererRegistration {
            synchronized(lock) {
                removeLayer(customRendererLayers, customRenderers, businessID, layer)
            }
        }
    }

    fun unregisterCustomMessageRenderer(businessID: String) {
        synchronized(lock) {
            popActiveLayer(customRendererLayers, customRenderers, businessID)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun registerMessageRenderer(
        matcher: MessageRendererMatcher,
        renderer: MessageRenderer,
        priority: Int = 0
    ): MessageRendererRegistration {
        return addRendererRule(
            MessageRendererRule(
                id = nextRuleId.getAndIncrement(),
                matcher = matcher,
                renderer = renderer,
                priority = priority
            )
        )
    }

    @JvmStatic
    fun unregisterMessageRenderer(registration: MessageRendererRegistration) {
        registration.unregister()
    }

    @JvmStatic
    @JvmOverloads
    fun registerMessageCellRenderer(
        matcher: MessageRendererMatcher,
        renderer: MessageCellRenderer,
        priority: Int = 0
    ): MessageRendererRegistration {
        return addRendererRule(
            MessageRendererRule(
                id = nextRuleId.getAndIncrement(),
                matcher = matcher,
                cellRenderer = renderer,
                priority = priority
            )
        )
    }

    @JvmStatic
    fun unregisterMessageCellRenderer(registration: MessageRendererRegistration) {
        registration.unregister()
    }

    fun getCellRenderer(
        message: MessageInfo,
        config: MessageListConfigProtocol? = null
    ): MessageCellRenderer? {
        if (message.status == MessageStatus.REVOKED) {
            return null
        }
        val configRules = sortedConfigRules(config)
        configRules.firstOrNull { rule ->
            rule.cellRenderer != null && ruleMatches(rule.matcher, message)
        }?.cellRenderer?.let { return it }
        val hasConfigContent = configRules.any { rule ->
            rule.contentRenderer != null && ruleMatches(rule.matcher, message)
        }
        if (hasConfigContent) {
            return null
        }
        return snapshotRendererRules()
            .sortedByDescending { it.priority }
            .firstOrNull { rule ->
                rule.cellRenderer != null && ruleMatches(rule.matcher, message)
            }
            ?.cellRenderer
    }

    fun getRenderer(
        message: MessageInfo,
        config: MessageListConfigProtocol? = null
    ): MessageRenderer {
        if (message.status == MessageStatus.REVOKED) {
            return synchronized(lock) { renderers[MessageType.TIPS] } ?: defaultRenderer
        }
        val configRules = sortedConfigRules(config)
        configRules.firstOrNull { rule ->
            rule.contentRenderer != null && ruleMatches(rule.matcher, message)
        }?.contentRenderer?.let { return it }
        snapshotRendererRules()
            .sortedByDescending { it.priority }
            .firstOrNull { rule ->
                rule.renderer != null && ruleMatches(rule.matcher, message)
            }
            ?.renderer
            ?.let { return it }
        return resolveBuiltIn(message)
    }

    fun isDefaultBuiltInRenderer(
        message: MessageInfo,
        config: MessageListConfigProtocol? = null
    ): Boolean {
        if (getCellRenderer(message, config) != null) {
            return false
        }
        return getRenderer(message, config) === defaultRenderer
    }

    private fun resolveBuiltIn(message: MessageInfo): MessageRenderer {
        if (message.messageType != MessageType.CUSTOM) {
            return synchronized(lock) { renderers[message.messageType] } ?: defaultRenderer
        }
        val chatbotData = ChatbotMessageProtocol.parse(message)
        if (chatbotData?.source == ChatbotMessageSource.FLOW ||
            chatbotData?.source == ChatbotMessageSource.ERROR
        ) {
            return chatbotMessageRenderer
        }
        val callModel = CallMessageParser.parse(message)
        if (callModel != null) {
            return if (callModel.participantType == CallParticipantType.GROUP) {
                callingTipsMessageRenderer
            } else {
                callingMessageRenderer
            }
        }
        val customInfo = (message.messagePayload as? CustomMessagePayload)?.customData
        val dict = jsonData2Dictionary(customInfo)
        val businessID = dict?.get("businessID")
        return synchronized(lock) { customRenderers[businessID] } ?: defaultRenderer
    }

    private fun addRendererRule(rule: MessageRendererRule): MessageRendererRegistration {
        synchronized(lock) {
            rendererRules.add(rule)
        }
        return MessageRendererRegistration {
            synchronized(lock) {
                rendererRules.removeAll { it.id == rule.id }
            }
        }
    }

    private fun snapshotRendererRules(): List<MessageRendererRule> {
        return synchronized(lock) { rendererRules.toList() }
    }

    private fun sortedConfigRules(config: MessageListConfigProtocol?): List<MessageRenderRule> {
        return config?.messageRenderRules().orEmpty().sortedByDescending { it.priority }
    }

    private fun ruleMatches(matcher: MessageMatcher, message: MessageInfo): Boolean {
        return runCatching { matcher.matches(message) }.getOrDefault(false)
    }

    private fun ruleMatches(matcher: MessageRendererMatcher, message: MessageInfo): Boolean {
        return runCatching { matcher.matches(message) }.getOrDefault(false)
    }

    private fun <K> pushLayer(
        layers: MutableMap<K, MutableList<RendererLayer>>,
        active: MutableMap<K, MessageRenderer>,
        key: K,
        layer: RendererLayer
    ) {
        layers.getOrPut(key) { mutableListOf() }.add(layer)
        active[key] = layer.renderer
    }

    private fun <K> removeLayer(
        layers: MutableMap<K, MutableList<RendererLayer>>,
        active: MutableMap<K, MessageRenderer>,
        key: K,
        layer: RendererLayer
    ) {
        layer.unregistered = true
        restoreActiveLayer(layers, active, key)
    }

    private fun <K> popActiveLayer(
        layers: MutableMap<K, MutableList<RendererLayer>>,
        active: MutableMap<K, MessageRenderer>,
        key: K
    ) {
        val stack = layers[key] ?: return
        val current = stack.lastOrNull { !it.unregistered } ?: return
        current.unregistered = true
        restoreActiveLayer(layers, active, key)
    }

    private fun <K> restoreActiveLayer(
        layers: MutableMap<K, MutableList<RendererLayer>>,
        active: MutableMap<K, MessageRenderer>,
        key: K
    ) {
        val stack = layers[key]
        val current = stack?.lastOrNull { !it.unregistered }
        if (current != null) {
            active[key] = current.renderer
        } else {
            active.remove(key)
        }
        stack?.removeAll { it.unregistered }
        if (stack != null && stack.isEmpty()) {
            layers.remove(key)
        }
    }
}
