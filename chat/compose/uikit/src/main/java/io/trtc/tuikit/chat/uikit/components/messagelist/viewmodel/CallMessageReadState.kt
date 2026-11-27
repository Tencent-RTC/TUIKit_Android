package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateSetOf
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType

// Local read state for C2C call messages that show an unread dot (missed call).
// The container marks messages read once they become visible; renderers query
// [isRead] to decide whether the dot should still be shown.
// SnapshotStateSet so Compose UI that reads [isRead] during composition
// recomposes immediately when a visible call message is marked read.
// Writes must happen on the main thread (the same thread as composition).
object CallMessageReadState {
    private val readMessageIds = mutableStateSetOf<String>()

    fun markRead(messageId: String) {
        if (messageId.isNotBlank()) {
            readMessageIds.add(messageId)
        }
    }

    fun isRead(messageId: String): Boolean {
        return messageId.isNotBlank() && readMessageIds.contains(messageId)
    }
}

// Minimal container-side parser for call custom messages. Only the fields the
// message list container needs are extracted: history exclusion and the missed
// call unread-dot flag.
internal object CallMessageVisibilityParser {
    private const val TAG = "CallMessageVisibility"
    private const val CALL_BUSINESS_ID_KEY = "businessID"
    private const val CALL_BUSINESS_ID_AV_CALL = "av_call"
    private const val CALL_BUSINESS_ID_RTC_CALL = "rtc_call"
    private const val CALL_BUSINESS_ID_TIMEOUT = 1.0
    private const val SIGNALING_ACTION_TYPE_CANCEL_INVITE = 2
    private const val SIGNALING_ACTION_TYPE_REJECT_INVITE = 4
    private const val SIGNALING_ACTION_TYPE_INVITE_TIMEOUT = 5
    private const val CALL_MESSAGE_UNREAD_LOCAL_CUSTOM_INT = 0

    fun isCallMessage(message: MessageInfo): Boolean {
        return parseSignalDataMap(message) != null
    }

    fun isExcludeFromHistory(message: MessageInfo): Boolean {
        val customDataMap = parseCustomDataMap(message) ?: return false
        parseSignalDataMap(message) ?: return false
        return customDataMap["isExcludedFromLastMessage"].toBooleanValue() &&
            customDataMap["isExcludedFromUnreadCount"].toBooleanValue()
    }

    // Mirrors View's CallMessageParser.isShowUnreadPoint: C2C callee side
    // missed call (cancel / timeout / line busy), locally marked unread, not yet
    // marked read, and not excluded from history.
    fun isShowUnreadPoint(message: MessageInfo): Boolean {
        val customDataMap = parseCustomDataMap(message) ?: return false
        val signalDataMap = parseSignalDataMap(message) ?: return false
        if (customDataMap["groupID"] is String && (customDataMap["groupID"] as String).isNotEmpty()) {
            return false
        }
        val actionType = customDataMap["actionType"].toIntOrNull() ?: return false
        val isMissedCallAction = when (actionType) {
            SIGNALING_ACTION_TYPE_CANCEL_INVITE,
            SIGNALING_ACTION_TYPE_INVITE_TIMEOUT -> true
            SIGNALING_ACTION_TYPE_REJECT_INVITE -> signalDataMap.containsKey("line_busy")
            else -> false
        }
        if (!isMissedCallAction) {
            return false
        }
        val caller = parseCaller(signalDataMap)
        val loginUser = LoginStore.shared.loginState.loginUserInfo.value?.userID
        if (!loginUser.isNullOrEmpty() && caller == loginUser) {
            return false
        }
        if (isExcludeFromHistory(message)) {
            return false
        }
        val localCustomInt = customDataMap["localCustomInt"].toIntOrNull()
        return localCustomInt == CALL_MESSAGE_UNREAD_LOCAL_CUSTOM_INT &&
            !CallMessageReadState.isRead(message.msgID)
    }

    private fun parseCaller(signalDataMap: Map<*, *>): String {
        val data = signalDataMap["data"] as? Map<*, *>
        val inviter = data?.get("inviter") as? String
        if (!inviter.isNullOrEmpty()) {
            return inviter
        }
        return LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
    }

    private fun parseCustomDataMap(message: MessageInfo): Map<*, *>? {
        if (message.messageType != MessageType.CUSTOM) {
            return null
        }
        val customPayload = message.messagePayload as? CustomMessagePayload ?: return null
        return parseJsonMap(customPayload.customData)
    }

    private fun parseSignalDataMap(message: MessageInfo): Map<*, *>? {
        val customDataMap = parseCustomDataMap(message) ?: return null
        val signalDataMap = when (val data = customDataMap["data"]) {
            is String -> parseJsonMap(data)
            is Map<*, *> -> data
            else -> null
        } ?: return null
        val businessIdObj = signalDataMap[CALL_BUSINESS_ID_KEY]
        val isKnownBusinessId = when (businessIdObj) {
            is String -> businessIdObj == CALL_BUSINESS_ID_AV_CALL ||
                businessIdObj == CALL_BUSINESS_ID_RTC_CALL
            is Number -> kotlin.math.abs(businessIdObj.toDouble() - CALL_BUSINESS_ID_TIMEOUT) < 0.000001
            else -> false
        }
        return if (isKnownBusinessId) signalDataMap else null
    }

    private fun parseJsonMap(json: String?): Map<*, *>? {
        if (json.isNullOrBlank()) {
            return null
        }
        return runCatching {
            Gson().fromJson(json, Map::class.java)
        }.onFailure {
            if (it is JsonSyntaxException) {
                Log.e(TAG, "parse call message json error", it)
            }
        }.getOrNull()
    }

    private fun Any?.toIntOrNull(): Int? {
        return when (this) {
            is Number -> toInt()
            is String -> toIntOrNull()
            else -> null
        }
    }

    private fun Any?.toBooleanValue(): Boolean {
        return when (this) {
            is Boolean -> this
            is String -> equals("true", ignoreCase = true)
            is Number -> toInt() != 0
            else -> false
        }
    }
}
