package io.trtc.tuikit.chat.uikit.components.messagelist.utils
import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType

private const val TAG = "CallMessageParser"

private const val CALL_BUSINESS_ID_KEY = "businessID"
private const val CALL_BUSINESS_ID_AV_CALL = "av_call"
private const val CALL_BUSINESS_ID_RTC_CALL = "rtc_call"
private const val CALL_BUSINESS_ID_TIMEOUT = 1.0
private const val SIGNALING_ACTION_TYPE_INVITE = 1
private const val SIGNALING_ACTION_TYPE_CANCEL_INVITE = 2
private const val SIGNALING_ACTION_TYPE_ACCEPT_INVITE = 3
private const val SIGNALING_ACTION_TYPE_REJECT_INVITE = 4
private const val SIGNALING_ACTION_TYPE_INVITE_TIMEOUT = 5
private const val CALL_MESSAGE_UNREAD_LOCAL_CUSTOM_INT = 0

enum class CallProtocolType {
    UNKNOWN,
    SEND,
    ACCEPT,
    REJECT,
    CANCEL,
    HANGUP,
    TIMEOUT,
    LINE_BUSY,
    SWITCH_TO_AUDIO,
    SWITCH_TO_AUDIO_CONFIRM
}

enum class CallStreamMediaType {
    UNKNOWN,
    VOICE,
    VIDEO
}

enum class CallParticipantType {
    UNKNOWN,
    C2C,
    GROUP
}

enum class CallParticipantRole {
    UNKNOWN,
    CALLER,
    CALLEE
}

data class CallMessageModel(
    val protocolType: CallProtocolType,
    val streamMediaType: CallStreamMediaType,
    val participantType: CallParticipantType,
    val participantRole: CallParticipantRole,
    val caller: String,
    val inviteeList: List<String>,
    val duration: Int,
    val isExcludeFromHistory: Boolean,
    val isShowUnreadPoint: Boolean
) {
    val isCaller: Boolean get() = participantRole == CallParticipantRole.CALLER

    val isGroup: Boolean get() = participantType == CallParticipantType.GROUP
}

object CallMessageReadState {
    private val readMessageIds = mutableSetOf<String>()

    fun markRead(messageId: String) {
        if (messageId.isNotBlank()) {
            readMessageIds.add(messageId)
        }
    }

    fun isRead(messageId: String): Boolean {
        return messageId.isNotBlank() && readMessageIds.contains(messageId)
    }
}

object CallMessageParser {

    fun parse(message: MessageInfo): CallMessageModel? {
        if (message.messageType != MessageType.CUSTOM) {
            return null
        }
        val customPayload = message.messagePayload as? CustomMessagePayload ?: return null
        val customDataMap = parseJsonMap(customPayload.customData) ?: return null
        val signalDataMap = parseSignalDataMap(customDataMap) ?: return null

        val businessIdObj = signalDataMap[CALL_BUSINESS_ID_KEY]
        val isKnownBusinessId = when (businessIdObj) {
            is String -> businessIdObj == CALL_BUSINESS_ID_AV_CALL ||
                businessIdObj == CALL_BUSINESS_ID_RTC_CALL
            is Number -> kotlin.math.abs(businessIdObj.toDouble() - CALL_BUSINESS_ID_TIMEOUT) < 0.000001
            else -> false
        }
        if (!isKnownBusinessId) {
            return null
        }

        val actionType = customDataMap["actionType"].toIntOrNull() ?: return null
        val protocolType = parseProtocolType(actionType, signalDataMap)
        if (protocolType == CallProtocolType.UNKNOWN) {
            return null
        }

        val caller = parseCaller(signalDataMap)
        val streamMediaType = parseStreamMediaType(protocolType, signalDataMap)
        val participantType = parseParticipantType(customDataMap)
        val participantRole = parseParticipantRole(caller)
        val duration = parseDuration(protocolType, signalDataMap)
        val excludeFromHistory = customDataMap["isExcludedFromLastMessage"].toBooleanValue() &&
            customDataMap["isExcludedFromUnreadCount"].toBooleanValue()
        val localCustomInt = customDataMap["localCustomInt"].toIntOrNull()
        val isShowUnreadPoint = localCustomInt == CALL_MESSAGE_UNREAD_LOCAL_CUSTOM_INT &&
            !CallMessageReadState.isRead(message.msgID) &&
            participantRole == CallParticipantRole.CALLEE &&
            participantType == CallParticipantType.C2C &&
            (protocolType == CallProtocolType.CANCEL ||
                protocolType == CallProtocolType.TIMEOUT ||
                protocolType == CallProtocolType.LINE_BUSY) &&
            !excludeFromHistory

        return CallMessageModel(
            protocolType = protocolType,
            streamMediaType = streamMediaType,
            participantType = participantType,
            participantRole = participantRole,
            caller = caller,
            inviteeList = parseInviteeList(customDataMap),
            duration = duration,
            isExcludeFromHistory = excludeFromHistory,
            isShowUnreadPoint = isShowUnreadPoint
        )
    }

    fun isCallMessage(message: MessageInfo): Boolean {
        return parse(message) != null
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

    private fun parseSignalDataMap(customDataMap: Map<*, *>): Map<*, *>? {
        return when (val data = customDataMap["data"]) {
            is String -> parseJsonMap(data)
            is Map<*, *> -> data
            else -> null
        }
    }

    private fun parseInviteeList(customDataMap: Map<*, *>): List<String> {
        return (customDataMap["inviteeList"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf { userId -> userId.isNotBlank() } }
            ?: emptyList()
    }

    private fun parseProtocolType(
        actionType: Int,
        signalDataMap: Map<*, *>
    ): CallProtocolType {
        return when (actionType) {
            SIGNALING_ACTION_TYPE_INVITE -> {
                val data = signalDataMap["data"] as? Map<*, *>
                if (data != null) {
                    when (data["cmd"] as? String) {
                        "switchToAudio" -> CallProtocolType.SWITCH_TO_AUDIO
                        "hangup" -> CallProtocolType.HANGUP
                        "videoCall", "audioCall" -> CallProtocolType.SEND
                        else -> CallProtocolType.UNKNOWN
                    }
                } else {
                    if (signalDataMap.containsKey("call_end")) {
                        CallProtocolType.HANGUP
                    } else {
                        CallProtocolType.SEND
                    }
                }
            }
            SIGNALING_ACTION_TYPE_CANCEL_INVITE -> CallProtocolType.CANCEL
            SIGNALING_ACTION_TYPE_ACCEPT_INVITE -> {
                val data = signalDataMap["data"] as? Map<*, *>
                val cmd = data?.get("cmd") as? String
                if (cmd == "switchToAudio") {
                    CallProtocolType.SWITCH_TO_AUDIO_CONFIRM
                } else {
                    CallProtocolType.ACCEPT
                }
            }
            SIGNALING_ACTION_TYPE_REJECT_INVITE -> {
                if (signalDataMap.containsKey("line_busy")) {
                    CallProtocolType.LINE_BUSY
                } else {
                    CallProtocolType.REJECT
                }
            }
            SIGNALING_ACTION_TYPE_INVITE_TIMEOUT -> CallProtocolType.TIMEOUT
            else -> CallProtocolType.UNKNOWN
        }
    }

    private fun parseStreamMediaType(
        protocolType: CallProtocolType,
        signalDataMap: Map<*, *>
    ): CallStreamMediaType {
        var type = CallStreamMediaType.UNKNOWN
        val callType = signalDataMap["call_type"].toIntOrNull()
        if (callType != null) {
            type = when (callType) {
                1 -> CallStreamMediaType.VOICE
                2 -> CallStreamMediaType.VIDEO
                else -> CallStreamMediaType.UNKNOWN
            }
        }

        if (protocolType == CallProtocolType.SEND) {
            val data = signalDataMap["data"] as? Map<*, *>
            when (data?.get("cmd") as? String) {
                "audioCall" -> type = CallStreamMediaType.VOICE
                "videoCall" -> type = CallStreamMediaType.VIDEO
            }
        } else if (protocolType == CallProtocolType.SWITCH_TO_AUDIO ||
            protocolType == CallProtocolType.SWITCH_TO_AUDIO_CONFIRM
        ) {
            type = CallStreamMediaType.VIDEO
        }
        return type
    }

    private fun parseParticipantType(customDataMap: Map<*, *>): CallParticipantType {
        val groupId = customDataMap["groupID"] as? String
        return if (!groupId.isNullOrEmpty()) {
            CallParticipantType.GROUP
        } else {
            CallParticipantType.C2C
        }
    }

    private fun parseCaller(signalDataMap: Map<*, *>): String {
        val data = signalDataMap["data"] as? Map<*, *>
        val inviter = data?.get("inviter") as? String
        if (!inviter.isNullOrEmpty()) {
            return inviter
        }
        return LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
    }

    private fun parseParticipantRole(caller: String): CallParticipantRole {
        val loginUser = LoginStore.shared.loginState.loginUserInfo.value?.userID
        return if (!loginUser.isNullOrEmpty() && TextUtils.equals(caller, loginUser)) {
            CallParticipantRole.CALLER
        } else {
            CallParticipantRole.CALLEE
        }
    }

    private fun parseDuration(
        protocolType: CallProtocolType,
        signalDataMap: Map<*, *>
    ): Int {
        if (protocolType != CallProtocolType.HANGUP) {
            return 0
        }
        val durationObj = signalDataMap["call_end"] ?: return 0
        return durationObj.toIntOrNull() ?: 0
    }
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

fun getCallMessageDisplayString(
    context: Context,
    message: MessageInfo,
    callModel: CallMessageModel
): String {
    val senderShowName = message.senderDisplayName
    return if (callModel.isGroup) {
        getGroupCallDisplayString(context, senderShowName, callModel)
    } else {
        getC2CCallDisplayString(context, callModel)
    }
}

private fun getC2CCallDisplayString(
    context: Context,
    callModel: CallMessageModel
): String {
    val isCaller = callModel.isCaller
    return when (callModel.protocolType) {
        CallProtocolType.REJECT -> {
            if (isCaller) {
                context.getString(R.string.message_list_call_reject_caller)
            } else {
                context.getString(R.string.message_list_call_reject_callee)
            }
        }

        CallProtocolType.CANCEL -> {
            if (isCaller) {
                context.getString(R.string.message_list_call_cancel_caller)
            } else {
                context.getString(R.string.message_list_call_cancel_callee)
            }
        }

        CallProtocolType.HANGUP -> {
            context.getString(
                R.string.message_list_call_duration_format,
                ChatDateTimeUtils.formatCallDuration(callModel.duration)
            )
        }

        CallProtocolType.TIMEOUT -> {
            if (isCaller) {
                context.getString(R.string.message_list_call_timeout_caller)
            } else {
                context.getString(R.string.message_list_call_timeout_callee)
            }
        }

        CallProtocolType.LINE_BUSY -> {
            if (isCaller) {
                context.getString(R.string.message_list_call_line_busy_caller)
            } else {
                context.getString(R.string.message_list_call_line_busy_callee)
            }
        }

        CallProtocolType.SEND -> {
            context.getString(R.string.message_list_call_start)
        }

        CallProtocolType.ACCEPT -> {
            context.getString(R.string.message_list_call_accept)
        }

        CallProtocolType.SWITCH_TO_AUDIO -> {
            context.getString(R.string.message_list_call_switch_to_audio)
        }

        CallProtocolType.SWITCH_TO_AUDIO_CONFIRM -> {
            context.getString(R.string.message_list_call_switch_to_audio_accept)
        }

        else -> context.getString(R.string.message_list_call_invalid_command)
    }
}

private fun getGroupCallDisplayString(
    context: Context,
    senderShowName: String,
    callModel: CallMessageModel
): String {
    return when (callModel.protocolType) {
        CallProtocolType.SEND -> {
            context.getString(
                R.string.message_list_call_group_send_format,
                senderShowName
            )
        }

        CallProtocolType.CANCEL,
        CallProtocolType.HANGUP -> {
            context.getString(R.string.message_list_call_group_end)
        }

        CallProtocolType.TIMEOUT,
        CallProtocolType.LINE_BUSY -> {
            val names = callModel.inviteeList
                .joinToString(separator = "、") { "\"$it\"" }
            val suffix = if (callModel.protocolType == CallProtocolType.LINE_BUSY) {
                context.getString(R.string.message_list_call_line_busy_callee)
            } else {
                context.getString(R.string.message_list_call_group_no_answer)
            }
            if (names.isBlank()) suffix else "$names $suffix"
        }

        CallProtocolType.REJECT -> {
            context.getString(
                R.string.message_list_call_group_reject_format,
                senderShowName
            )
        }

        CallProtocolType.ACCEPT -> {
            context.getString(
                R.string.message_list_call_group_accept_format,
                senderShowName
            )
        }

        CallProtocolType.SWITCH_TO_AUDIO -> {
            context.getString(
                R.string.message_list_call_group_switch_to_audio_format,
                senderShowName
            )
        }

        CallProtocolType.SWITCH_TO_AUDIO_CONFIRM -> {
            context.getString(
                R.string.message_list_call_group_confirm_switch_to_audio_format,
                senderShowName
            )
        }

        else -> context.getString(R.string.message_list_call_invalid_command)
    }
}
