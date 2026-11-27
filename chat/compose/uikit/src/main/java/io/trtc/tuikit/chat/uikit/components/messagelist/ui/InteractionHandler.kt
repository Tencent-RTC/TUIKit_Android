package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

val LocalInteractionHandler = compositionLocalOf<InteractionHandler> {
    error("no MessageInteraction")
}

val LocalQuoteClickHandler = compositionLocalOf<(MessageInfo, MessageQuoteInfo) -> Unit> { { _, _ -> } }

val LocalCurrentMessage = compositionLocalOf<MessageInfo?> { null }

class InteractionHandler(
    val onRendered: () -> Unit = {},

    val onTap: () -> Unit = {},
    val onLongPress: () -> Unit = {}
)

fun Modifier.messageContentGestures(
    message: MessageInfo,
    handler: InteractionHandler,
    onTap: (() -> Unit)? = null,
): Modifier {
    if (message.status == MessageStatus.VIOLATION) {
        return this
    }
    return pointerInput(message.msgID, handler) {
        detectTapGestures(
            onTap = { (onTap ?: handler.onTap).invoke() },
            onLongPress = { handler.onLongPress() }
        )
    }
}