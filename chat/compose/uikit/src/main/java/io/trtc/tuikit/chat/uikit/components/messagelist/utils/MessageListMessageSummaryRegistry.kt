package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import android.content.Context
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.config.messageRenderRules
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType

fun interface MessageMatcher {
    fun matches(message: MessageInfo): Boolean
}

fun interface MessageSummaryProvider {
    fun getSummary(context: MessageSummaryContext): String?
}

class MessageSummaryContext(
    val context: Context,
    val conversationID: String?,
    val message: MessageInfo
)

internal data class MessageSummaryRule(
    val matcher: MessageMatcher,
    val summaryProvider: MessageSummaryProvider,
    val priority: Int = 0
)

object MessageListMessageSummaryRegistry {
    private val lock = Any()
    private val rules = mutableListOf<MessageSummaryRule>()

    @JvmStatic
    @JvmOverloads
    fun setCustomMessageSummary(
        businessID: String,
        summaryProvider: MessageSummaryProvider,
        priority: Int = 0
    ) {
        addCustomMessageSummary(
            matcher = businessIDMatcher(businessID),
            summaryProvider = summaryProvider,
            priority = priority
        )
    }

    @JvmStatic
    @JvmOverloads
    fun addCustomMessageSummary(
        matcher: MessageMatcher,
        summaryProvider: MessageSummaryProvider,
        priority: Int = 0
    ) {
        synchronized(lock) {
            rules.add(
                MessageSummaryRule(
                    matcher = matcher,
                    summaryProvider = summaryProvider,
                    priority = priority
                )
            )
        }
    }

    internal fun resolveSummary(
        message: MessageInfo,
        conversationID: String?,
        context: Context,
        config: MessageListConfigProtocol? = null
    ): String? {
        val configRules = config?.messageRenderRules().orEmpty().mapNotNull { rule ->
            val provider = rule.summaryProvider ?: return@mapNotNull null
            MessageSummaryRule(
                matcher = rule.matcher,
                summaryProvider = provider,
                priority = rule.priority
            )
        }
        val rule = (configRules + snapshotRules())
            .sortedByDescending { it.priority }
            .firstOrNull { it.matcher.matches(message) }
            ?: return null
        return rule.summaryProvider
            .getSummary(MessageSummaryContext(context, conversationID, message))
            ?.takeIf { it.isNotEmpty() }
    }

    internal fun snapshotRules(): List<MessageSummaryRule> {
        return synchronized(lock) {
            rules.toList()
        }
    }

    internal fun clear() {
        synchronized(lock) {
            rules.clear()
        }
    }

    private fun businessIDMatcher(businessID: String): MessageMatcher {
        return MessageMatcher { message ->
            message.messageType == MessageType.CUSTOM &&
                extractCustomBusinessID(message) == businessID
        }
    }

    private fun extractCustomBusinessID(message: MessageInfo): String? {
        val data = (message.messagePayload as? CustomMessagePayload)?.customData ?: return null
        return jsonData2Dictionary(data)?.get("businessID") as? String
    }
}
