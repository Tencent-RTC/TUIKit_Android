package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class DefaultMessageRenderer : MessageRenderer {
    @Composable
    override fun Render(message: MessageInfo) {
        val colors = LocalTheme.current.colors
        Text(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
            fontSize = 14.sp,
            text = stringResource(R.string.message_list_unsupported_message),
            color = if (message.isSentBySelf) {
                colors.textColorAntiPrimary
            } else {
                colors.textColorPrimary
            },
        )
    }
}
