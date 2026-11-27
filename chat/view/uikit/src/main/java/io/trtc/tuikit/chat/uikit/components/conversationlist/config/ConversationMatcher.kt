package io.trtc.tuikit.chat.uikit.components.conversationlist.config

import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo

fun interface ConversationMatcher {
    fun matches(conversation: ConversationInfo): Boolean
}
