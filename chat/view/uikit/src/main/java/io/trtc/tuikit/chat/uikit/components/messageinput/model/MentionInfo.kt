package io.trtc.tuikit.chat.uikit.components.messageinput.model
data class MentionInfo(
    val userID: String,
    val displayName: String,
) {
    companion object {
        const val AT_ALL_USER_ID = "__kImSDK_MesssageAtALL__"
    }

    val isAtAll: Boolean
        get() = userID == AT_ALL_USER_ID

    val mentionText: String
        get() = "@$displayName "
}
