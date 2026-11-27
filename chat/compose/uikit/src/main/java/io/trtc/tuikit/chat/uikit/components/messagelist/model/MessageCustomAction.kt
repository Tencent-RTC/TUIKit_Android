package io.trtc.tuikit.chat.uikit.components.messagelist.model

import android.content.Context
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

object MessageActionIDs {
    const val MULTI_SELECT = "message.multiSelect"
    const val FORWARD = "message.forward"
    const val QUOTE = "message.quote"
    const val COPY = "message.copy"
    const val RECALL = "message.recall"
    const val DELETE = "message.delete"
    const val CONVERT_TO_TEXT = "message.convertToText"
    const val TRANSLATE = "message.translate"
    const val LISTEN_FROM_HERE = "message.listenFromHere"
}

data class MessageCustomAction(
    val ID: String,
    val title: String = "",
    val iconResID: Int = 0,
    val action: (MessageInfo) -> Unit = {},
    val dangerous: Boolean = false,
)

data class MessageCustomActionContext(
    val androidContext: Context,
    val conversationID: String,
    val message: MessageInfo,
)
