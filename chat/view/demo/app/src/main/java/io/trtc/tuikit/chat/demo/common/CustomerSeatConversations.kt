package io.trtc.tuikit.chat.demo.common

import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ConversationMatcher

/** 用户和人工客服坐席的 C2C 会话，坐席 userID 固定以 [USER_ID_PREFIX] 开头，不在消息列表里展示。 */
object CustomerSeatConversations {

    const val USER_ID_PREFIX = "@customer_seat#"
    private const val CONVERSATION_ID_PREFIX = "c2c_$USER_ID_PREFIX"

    val exclusion = ConversationMatcher { isCustomerSeat(it.conversationID) }

    fun isCustomerSeat(conversationID: String): Boolean {
        return conversationID.startsWith(CONVERSATION_ID_PREFIX)
    }
}
