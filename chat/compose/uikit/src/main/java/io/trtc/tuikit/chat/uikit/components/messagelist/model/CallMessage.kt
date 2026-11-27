package io.trtc.tuikit.chat.uikit.components.messagelist.model

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

enum class MessageReadReceiptDisplayState {
    UNREAD,
    READ,
    ALL_READ,
}
