package io.trtc.tuikit.chat.uikit.components.conversationlist.model

import android.content.Context
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomItem
import io.trtc.tuikit.chat.uikit.components.common.uicustom.EditorContext

object ConversationActionIDs {
    const val DELETE = "conversation.delete"
    const val MUTE = "conversation.mute"
    const val PIN = "conversation.pin"
    const val MARK_UNREAD = "conversation.markUnread"
    const val CLEAR_HISTORY = "conversation.clearHistory"
}

data class ConversationCustomAction(
    override val ID: String,
    val titleResID: Int = 0,
    val title: String = "",
    val dangerous: Boolean = false,
    val action: (ConversationInfo) -> Unit = {},
) : CustomItem

data class ConversationCustomActionContext(
    override val androidContext: Context,
    val conversation: ConversationInfo,
) : EditorContext

internal fun resolveConversationActionTitle(
    title: String,
    titleResID: Int,
    getString: (Int) -> String,
): String {
    if (title.isNotEmpty()) {
        return title
    }
    if (titleResID == 0) {
        return ""
    }
    return getString(titleResID)
}
