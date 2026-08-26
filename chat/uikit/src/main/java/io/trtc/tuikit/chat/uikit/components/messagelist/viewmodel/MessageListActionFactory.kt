package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageActionCustomizer
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageActionIDs
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomActionContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups.withDeleteConfirmation
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.AuxiliaryTextVisibilityStore
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryFormatter
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageProtocol
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageSource

internal data class MessageListActionCallbacks(
    val onEnterMultiSelectMode: (MessageInfo) -> Unit = {},
    val onForwardSingleMessage: (MessageInfo) -> Unit = {},
    val onConvertVoiceToText: (MessageInfo) -> Unit = {},
    val onTranslateText: (MessageInfo) -> Unit = {},
    val onQuoteMessage: (MessageInfo, String) -> Unit = { _, _ -> },
    val onListenFromHere: (MessageInfo) -> Unit = {},
    val onCopyText: (MessageInfo) -> Unit = {},
    val onRecall: (MessageInfo) -> Unit = {},
    val onDelete: (MessageInfo) -> Unit = {},
)

internal data class MessageListActionLabels(
    val multiSelect: String,
    val forward: String,
    val quote: String,
    val copy: String,
    val recall: String,
    val delete: String,
    val convertToText: String,
    val translate: String,
    val listenFromHere: String,
) {
    companion object {
        fun from(context: Context): MessageListActionLabels {
            return MessageListActionLabels(
                multiSelect = context.getString(R.string.message_list_menu_multi_select),
                forward = context.getString(R.string.message_list_menu_forward),
                quote = context.getString(R.string.message_list_menu_quote),
                copy = context.getString(R.string.message_list_menu_copy),
                recall = context.getString(R.string.message_list_menu_recall),
                delete = context.getString(R.string.message_list_menu_delete),
                convertToText = context.getString(R.string.message_list_menu_convert_to_text),
                translate = context.getString(R.string.message_list_menu_translate),
                listenFromHere = context.getString(R.string.voice_message_listen_from_here),
            )
        }
    }
}

internal data class MessageListActionIcons(
    val multiSelect: Int,
    val forward: Int,
    val quote: Int,
    val copy: Int,
    val recall: Int,
    val delete: Int,
    val convertToText: Int,
    val translate: Int,
    val listenFromHere: Int,
) {
    companion object {
        fun defaults(): MessageListActionIcons {
            return MessageListActionIcons(
                multiSelect = R.drawable.message_list_menu_multi_select_icon,
                forward = R.drawable.message_list_menu_forward_icon,
                quote = R.drawable.message_list_menu_quote_icon,
                copy = R.drawable.message_list_menu_copy_icon,
                recall = R.drawable.message_list_menu_recall_icon,
                delete = R.drawable.message_list_menu_delete_icon,
                convertToText = R.drawable.message_list_menu_convert_icon,
                translate = R.drawable.message_list_menu_translate_icon,
                listenFromHere = R.drawable.message_list_menu_listen_from_here_icon,
            )
        }
    }
}

internal data class MessageListActionCapability(
    val isSupportMultiSelect: Boolean = true,
    val isSupportForward: Boolean = true,
    val isSupportQuote: Boolean = true,
    val isSupportCopy: Boolean = true,
    val isSupportRecall: Boolean = true,
    val isSupportDelete: Boolean = true,
    val showConvertToText: Boolean = true,
    val showTranslate: Boolean = true,
    val showListenFromHere: Boolean = true,
)

internal fun buildDefaultMessageActions(
    capability: MessageListActionCapability,
    labels: MessageListActionLabels,
    icons: MessageListActionIcons,
    callbacks: MessageListActionCallbacks,
): List<MessageCustomAction> {
    val actions = mutableListOf<MessageCustomAction>()
    if (capability.isSupportCopy) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.COPY,
            title = labels.copy,
            iconResID = icons.copy,
            action = callbacks.onCopyText,
        )
    }
    if (capability.isSupportForward) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.FORWARD,
            title = labels.forward,
            iconResID = icons.forward,
            action = callbacks.onForwardSingleMessage,
        )
    }
    if (capability.isSupportQuote) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.QUOTE,
            title = labels.quote,
            iconResID = icons.quote,
            action = { message -> callbacks.onQuoteMessage(message, "") },
        )
    }
    if (capability.isSupportMultiSelect) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.MULTI_SELECT,
            title = labels.multiSelect,
            iconResID = icons.multiSelect,
            action = callbacks.onEnterMultiSelectMode,
        )
    }
    if (capability.isSupportDelete) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.DELETE,
            title = labels.delete,
            iconResID = icons.delete,
            dangerous = true,
            action = callbacks.onDelete,
        )
    }
    if (capability.isSupportRecall) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.RECALL,
            title = labels.recall,
            iconResID = icons.recall,
            action = callbacks.onRecall,
        )
    }
    if (capability.showConvertToText) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.CONVERT_TO_TEXT,
            title = labels.convertToText,
            iconResID = icons.convertToText,
            action = callbacks.onConvertVoiceToText,
        )
    }
    if (capability.showTranslate) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.TRANSLATE,
            title = labels.translate,
            iconResID = icons.translate,
            action = callbacks.onTranslateText,
        )
    }
    if (capability.showListenFromHere) {
        actions += MessageCustomAction(
            ID = MessageActionIDs.LISTEN_FROM_HERE,
            title = labels.listenFromHere,
            iconResID = icons.listenFromHere,
            action = callbacks.onListenFromHere,
        )
    }
    return actions
}

internal fun applyMessageActionCustomizer(
    actionContext: MessageCustomActionContext,
    defaults: List<MessageCustomAction>,
    customizer: MessageActionCustomizer?,
): List<MessageCustomAction> {
    if (customizer == null) {
        return defaults
    }
    val editor = CustomEditor(actionContext, defaults)
    customizer.customize(editor)
    return editor.build()
}

internal fun composeMessageLongPressActions(
    actionContext: MessageCustomActionContext,
    defaults: List<MessageCustomAction>,
    customizer: MessageActionCustomizer?,
    onDeleteRequested: (MessageInfo, onConfirm: () -> Unit) -> Unit,
): List<MessageCustomAction> {
    val customized = applyMessageActionCustomizer(
        actionContext = actionContext,
        defaults = defaults,
        customizer = customizer,
    )
    return customized.withDeleteConfirmation(onDeleteRequested)
}

internal class MessageListActionFactory(
    private val config: MessageListConfigProtocol,
    private val latestMessageProvider: (MessageInfo) -> MessageInfo,
    private val auxiliaryTextVisibilityStore: AuxiliaryTextVisibilityStore,
    private val callbacks: MessageListActionCallbacks,
) {
    fun createDefaults(context: Context, messageInfo: MessageInfo): List<MessageCustomAction> {
        val latestMessage = latestMessageProvider(messageInfo)
        val messageActionStore = MessageActionStore.create(latestMessage)
        val labels = MessageListActionLabels.from(context)
        val icons = MessageListActionIcons.defaults()
        val capability = resolveMessageListActionCapability(
            config = config,
            message = latestMessage,
            auxiliaryTextVisibilityStore = auxiliaryTextVisibilityStore,
        )
        val wiredCallbacks = MessageListActionCallbacks(
            onEnterMultiSelectMode = { callbacks.onEnterMultiSelectMode(messageInfo) },
            onForwardSingleMessage = callbacks.onForwardSingleMessage,
            onConvertVoiceToText = callbacks.onConvertVoiceToText,
            onTranslateText = callbacks.onTranslateText,
            onQuoteMessage = { message, _ ->
                val summary = MessageListMessageSummaryFormatter(config).format(
                    context = context,
                    message = message,
                )
                callbacks.onQuoteMessage(message, summary)
            },
            onListenFromHere = { callbacks.onListenFromHere(messageInfo) },
            onCopyText = { msg ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = (msg.messagePayload as? TextMessagePayload)?.text
                    ?: ChatbotMessageProtocol.parse(msg)?.displayText
                val clip = ClipData.newPlainText("Copied Text", text.orEmpty())
                clipboard.setPrimaryClip(clip)
            },
            onRecall = { messageActionStore.revoke() },
            onDelete = { messageActionStore.delete() },
        )
        return buildDefaultMessageActions(
            capability = capability,
            labels = labels,
            icons = icons,
            callbacks = wiredCallbacks,
        )
    }
}

internal fun resolveMessageListActionCapability(
    config: MessageListConfigProtocol,
    message: MessageInfo,
    auxiliaryTextVisibilityStore: AuxiliaryTextVisibilityStore = AuxiliaryTextVisibilityStore(),
    currentTimeMs: Long = System.currentTimeMillis(),
): MessageListActionCapability {
    val chatbotData = ChatbotMessageProtocol.parse(message)
    if (chatbotData?.source == ChatbotMessageSource.FLOW ||
        chatbotData?.source == ChatbotMessageSource.ERROR
    ) {
        val isComplete = !chatbotData.isPlaceholder &&
            (chatbotData.source == ChatbotMessageSource.ERROR || chatbotData.isFinished) &&
            message.status != MessageStatus.VIOLATION
        return MessageListActionCapability(
            isSupportMultiSelect = false,
            isSupportForward = isComplete &&
                config.isSupportForward &&
                message.status == MessageStatus.SEND_SUCCESS,
            isSupportQuote = false,
            isSupportCopy = isComplete && config.isSupportCopy,
            isSupportRecall = false,
            isSupportDelete = isComplete && config.isSupportDelete,
            showConvertToText = false,
            showTranslate = false,
            showListenFromHere = false
        )
    }
    val showConvertToText = config.isSupportConvertToText &&
        message.messageType == MessageType.AUDIO &&
        message.status == MessageStatus.SEND_SUCCESS &&
        run {
            val messageID = message.msgID.orEmpty()
            val asrText = (message.messagePayload as? AudioMessagePayload)?.asrText
            val isHidden = auxiliaryTextVisibilityStore.isHidden(messageID)
            asrText.isNullOrEmpty() || isHidden
        }
    val showTranslate = config.isSupportTranslate &&
        message.messageType == MessageType.TEXT &&
        message.status == MessageStatus.SEND_SUCCESS &&
        run {
            val messageID = message.msgID.orEmpty()
            val translatedText = (message.messagePayload as? TextMessagePayload)?.translatedText
            val isHidden = auxiliaryTextVisibilityStore.isHidden(messageID)
            translatedText.isNullOrEmpty() || isHidden
        }
    return MessageListActionCapability(
        isSupportMultiSelect = config.isSupportMultiSelect,
        isSupportForward = config.isSupportForward &&
            message.status == MessageStatus.SEND_SUCCESS,
        isSupportQuote = config.isSupportQuote &&
            message.status == MessageStatus.SEND_SUCCESS &&
            message.status != MessageStatus.VIOLATION,
        isSupportCopy = message.messageType == MessageType.TEXT && config.isSupportCopy,
        isSupportRecall = MessageRecallActionPolicy.shouldShowRecall(
            isSentBySelf = message.isSentBySelf,
            status = message.status,
            timestamp = message.timestamp,
            isSupportRecall = config.isSupportRecall,
            currentTimeMs = currentTimeMs,
        ),
        isSupportDelete = config.isSupportDelete,
        showConvertToText = showConvertToText,
        showTranslate = showTranslate,
        showListenFromHere = config.isSupportListenFromHere,
    )
}

internal object MessageRecallActionPolicy {
    private const val RECALL_WINDOW_SECONDS = 120L

    fun shouldShowRecall(
        isSentBySelf: Boolean,
        status: MessageStatus,
        timestamp: Long?,
        isSupportRecall: Boolean,
        currentTimeMs: Long,
    ): Boolean {
        if (!isSupportRecall || !isSentBySelf || status != MessageStatus.SEND_SUCCESS) {
            return false
        }
        val messageTime = timestamp ?: return true
        if (messageTime <= 0L) {
            return true
        }
        val timeDifferenceSeconds = (currentTimeMs - messageTime * 1000L) / 1000L
        return timeDifferenceSeconds <= RECALL_WINDOW_SECONDS
    }
}
