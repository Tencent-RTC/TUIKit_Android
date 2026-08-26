package io.trtc.tuikit.chat.uikit.components.messagelist.utils
import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.jsonData2Dictionary
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSummaryPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.config.messageRenderRules
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageMatcher
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderRule
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageSummaryContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageSummaryProvider
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.RecalledMessageDisplayPolicy
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.FaceMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MergedMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.TipsMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload

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
        return jsonData2Dictionary(data)?.get("businessID")
    }
}

class MessageListMessageSummaryFormatter(
    config: MessageListConfigProtocol? = null
) {
    private val configRules: List<MessageSummaryRule> =
        config
            ?.messageRenderRules()
            .orEmpty()
            .mapNotNull { it.toSummaryRule() }

    fun format(
        context: Context,
        message: MessageInfo,
        conversationID: String? = null
    ): String {
        RecalledMessageDisplayPolicy.format(context, message)?.let { return it }
        if (message.status == MessageStatus.VIOLATION) {
            return context.getString(R.string.message_list_violation_message)
        }
        resolveCustomSummary(context, message, conversationID)?.let { return it }
        return formatBuiltInMessage(context, message)
    }

    private fun resolveCustomSummary(
        context: Context,
        message: MessageInfo,
        conversationID: String?
    ): String? {
        val rule = (configRules + MessageListMessageSummaryRegistry.snapshotRules())
            .sortedByDescending { it.priority }
            .firstOrNull { it.matcher.matches(message) }
            ?: return null
        return rule.summaryProvider
            .getSummary(MessageSummaryContext(context, conversationID, message))
            ?.takeIf { it.isNotEmpty() }
    }

    private fun MessageRenderRule.toSummaryRule(): MessageSummaryRule? {
        val provider = summaryProvider ?: return null
        return MessageSummaryRule(
            matcher = matcher,
            summaryProvider = provider,
            priority = priority
        )
    }

    private fun formatBuiltInMessage(context: Context, message: MessageInfo): String {
        return when (val payload = message.messagePayload) {
            is TextMessagePayload -> payload.text
            is ImageMessagePayload -> context.getString(R.string.message_list_message_type_image)
            is AudioMessagePayload -> {
                val voiceLabel = context.getString(R.string.message_list_message_type_voice)
                val duration = payload.audioDuration
                if (duration > 0) "$voiceLabel $duration\"" else voiceLabel
            }
            is FileMessagePayload -> {
                val filePrefix = context.getString(R.string.message_list_message_type_file)
                val fileName = payload.fileName.orEmpty()
                if (fileName.isEmpty()) filePrefix else "$filePrefix $fileName"
            }
            is VideoMessagePayload -> context.getString(R.string.message_list_message_type_video)
            is FaceMessagePayload -> context.getString(R.string.message_list_message_type_animate_emoji)
            is CustomMessagePayload -> formatCustomMessage(context, message, payload)
            is TipsMessagePayload -> getSystemInfoDisplayString(context, payload.groupTips)
            is MergedMessagePayload -> context.getString(R.string.message_list_message_type_merged)
            else -> ""
        }
    }

}

internal data class MessageSummaryRule(
    val matcher: MessageMatcher,
    val summaryProvider: MessageSummaryProvider,
    val priority: Int = 0
)

private fun formatCustomMessage(
    context: Context,
    message: MessageInfo,
    payload: CustomMessagePayload
): String {
    ChatbotMessageSummaryPolicy.getSummary(message)?.let { return it }
    val callModel = CallMessageParser.parse(message)
    if (callModel != null) {
        return getCallMessageDisplayString(
            context = context,
            message = message,
            callModel = callModel
        )
    }
    val customInfo = jsonData2Dictionary(payload.customData)
    return if (customInfo?.get("businessID") == "group_create") {
        getCreateGroupDisplayString(context, message)
    } else {
        context.getString(R.string.message_list_message_tips_unsupport_custom_message)
    }
}
