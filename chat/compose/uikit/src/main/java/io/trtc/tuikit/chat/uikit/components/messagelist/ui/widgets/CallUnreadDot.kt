package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallMessageDisplayPolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.CallMessageReadState
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

@Composable
fun CallUnreadDot(
    message: MessageInfo,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    val callModel = CallMessageParser.parse(message)
    val visible = callModel != null &&
        CallMessageDisplayPolicy.shouldShowOutsideUnreadDot(
            isSelf = message.isSentBySelf,
            isShowUnreadPoint = callModel.isShowUnreadPoint
        )
    if (!visible) {
        return
    }
    Box(
        modifier = modifier
            .size(6.dp)
            .background(color = colors.textColorError, shape = CircleShape)
    )
}

fun MessageInfo.shouldShowCallUnreadDot(): Boolean {
    val callModel = CallMessageParser.parse(this) ?: return false
    return CallMessageDisplayPolicy.shouldShowOutsideUnreadDot(
        isSelf = isSentBySelf,
        isShowUnreadPoint = callModel.isShowUnreadPoint
    )
}
