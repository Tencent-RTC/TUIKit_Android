package io.trtc.tuikit.chat.uikit.components.aiassistant

import android.util.Log
import com.google.gson.JsonParser
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload

/**
 * AI real-time voice control protocol.
 *
 * These pings are online-only and must be excluded from visible message lists.
 */
object AIAssistantControlMessages {

    const val VOICE_ENTER_ACTION = "enterVoiceCall"
    const val VOICE_LEAVE_ACTION = "leaveVoiceCall"
    const val VOICE_SESSION_DESCRIPTION = "voice call session"

    fun enterVoiceCall(conversationID: String) {
        sendVoiceSession(conversationID, VOICE_ENTER_ACTION)
    }

    fun leaveVoiceCall(conversationID: String) {
        sendVoiceSession(conversationID, VOICE_LEAVE_ACTION)
    }

    fun isControlMessage(message: MessageInfo): Boolean {
        if (message.messageType != MessageType.CUSTOM) {
            return false
        }
        val payload = message.messagePayload as? CustomMessagePayload ?: return false
        return isControlPayload(payload.customData, payload.description)
    }

    internal fun isControlPayload(customData: String?, description: String?): Boolean {
        if (description?.trim() == VOICE_SESSION_DESCRIPTION) {
            return true
        }
        val action = runCatching {
            JsonParser.parseString(customData.orEmpty()).asJsonObject
                .get("action")
                ?.asString
        }.getOrNull()
        return action == VOICE_ENTER_ACTION || action == VOICE_LEAVE_ACTION
    }

    private fun sendVoiceSession(conversationID: String, action: String) {
        MessageInputStore.create(conversationID).sendMessage(
            payload = SendMessagePayload.CustomSendMessagePayload(
                customData = """{"action":"$action"}""",
                description = VOICE_SESSION_DESCRIPTION,
            ),
            option = SendMessageOption(onlineUserOnly = true),
            completion = object : CompletionHandler {
                override fun onSuccess() = Unit

                override fun onFailure(code: Int, desc: String) {
                    Log.w(TAG, "voice session $action failed: $code $desc")
                }
            },
        )
    }

    private const val TAG = "AIAssistantControl"
}
