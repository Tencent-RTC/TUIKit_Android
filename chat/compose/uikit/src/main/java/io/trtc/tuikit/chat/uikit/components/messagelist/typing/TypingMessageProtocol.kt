package io.trtc.tuikit.chat.uikit.components.messagelist.typing

import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import org.json.JSONObject

object TypingMessageProtocol {
    const val BUSINESS_ID = "user_typing_status"

    private const val USER_ACTION_TYPING = 14
    private const val ACTION_PARAM_TYPING_START = "EIMAMSG_InputStatus_Ing"
    private const val ACTION_PARAM_TYPING_END = "EIMAMSG_InputStatus_End"

    fun buildTypingMessageData(isTyping: Boolean): String {
        val json = JSONObject()
        json.put("businessID", BUSINESS_ID)
        json.put("typingStatus", if (isTyping) 1 else 0)
        json.put("version", 0)
        json.put("userAction", if (isTyping) USER_ACTION_TYPING else 0)
        json.put("actionParam", if (isTyping) ACTION_PARAM_TYPING_START else ACTION_PARAM_TYPING_END)
        return json.toString()
    }

    fun isTypingMessage(message: MessageInfo): Boolean {
        return parseTypingStatus(message) != null
    }

    fun parseTypingStatus(message: MessageInfo): Boolean? {
        if (message.messageType != MessageType.CUSTOM) return null
        val data = (message.messagePayload as? CustomMessagePayload)?.customData ?: return null
        return parseTypingStatus(data)
    }

    fun parseTypingStatus(data: String): Boolean? {
        return try {
            val json = JSONObject(data)
            if (json.optString("businessID") != BUSINESS_ID) return null
            if (json.has("typingStatus")) {
                json.optInt("typingStatus", 0) == 1
            } else {
                when (json.optString("actionParam")) {
                    ACTION_PARAM_TYPING_START -> true
                    ACTION_PARAM_TYPING_END -> false
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
