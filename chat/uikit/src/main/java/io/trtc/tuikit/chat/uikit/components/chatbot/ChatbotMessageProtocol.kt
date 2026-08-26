package io.trtc.tuikit.chat.uikit.components.chatbot

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType

internal enum class ChatbotMessageSource {
    FLOW,
    INTERRUPT,
    ERROR,
    UNKNOWN
}

internal data class ChatbotMessageData(
    val source: ChatbotMessageSource,
    val chunks: List<String>,
    val isFinished: Boolean,
    val msgKey: String,
    val errorInfo: String,
    val isPlaceholder: Boolean = false
) {
    val displayText: String
        get() = when (source) {
            ChatbotMessageSource.FLOW -> chunks.joinToString(separator = "")
            ChatbotMessageSource.ERROR -> errorInfo
            ChatbotMessageSource.INTERRUPT,
            ChatbotMessageSource.UNKNOWN -> ""
        }
}

internal object ChatbotMessageProtocol {
    const val PLUGIN_VALUE = 2
    const val SOURCE_FLOW = 2
    const val SOURCE_INTERRUPT = 22
    const val SOURCE_ERROR = 23
    const val FINISHED = 1

    fun parse(message: MessageInfo): ChatbotMessageData? {
        if (message.messageType != MessageType.CUSTOM) {
            return null
        }
        val payload = message.messagePayload as? CustomMessagePayload ?: return null
        return parse(payload.customData)
    }

    fun createInterruptPayload(msgKey: String): String {
        return JsonObject().apply {
            addProperty("chatbotPlugin", PLUGIN_VALUE)
            addProperty("src", SOURCE_INTERRUPT)
            addProperty("msgKey", msgKey)
        }.toString()
    }

    fun parse(customData: String?): ChatbotMessageData? {
        if (customData.isNullOrBlank()) {
            return null
        }
        val json = runCatching {
            JsonParser.parseString(customData).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull() ?: return null
        if (json.intValue("chatbotPlugin") != PLUGIN_VALUE) {
            return null
        }

        val source = when (json.intValue("src")) {
            SOURCE_FLOW -> ChatbotMessageSource.FLOW
            SOURCE_INTERRUPT -> ChatbotMessageSource.INTERRUPT
            SOURCE_ERROR -> ChatbotMessageSource.ERROR
            else -> ChatbotMessageSource.UNKNOWN
        }
        val chunks = json.get("chunks")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { it.nullableString() }
            .orEmpty()
        val isFinished = json.intValue("isFinished")?.let { it == FINISHED } ?: true

        return ChatbotMessageData(
            source = source,
            chunks = chunks,
            isFinished = isFinished,
            msgKey = json.stringValue("msgKey"),
            errorInfo = json.stringValue("errorInfo"),
            isPlaceholder = json.booleanValue("localPlaceholder") ?: false
        )
    }

    private fun JsonObject.intValue(key: String): Int? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) {
            return null
        }
        return runCatching { element.asInt }.getOrNull()
    }

    private fun JsonObject.stringValue(key: String): String {
        return get(key)?.nullableString().orEmpty()
    }

    private fun JsonObject.booleanValue(key: String): Boolean? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) {
            return null
        }
        return runCatching { element.asBoolean }.getOrNull()
    }

    private fun JsonElement.nullableString(): String? {
        if (isJsonNull || !isJsonPrimitive) {
            return null
        }
        return runCatching { asString }.getOrNull()
    }
}
