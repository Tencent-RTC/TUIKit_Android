package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.getCreateGroupDisplayString
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class CreateGroupMessageRenderer : MessageRenderer {

    override val renderConfig =
        MessageRenderConfig(showMessageMeta = false, useDefaultBubble = false)

    @Composable
    override fun Render(message: MessageInfo) {
        val colors = LocalTheme.current.colors
        val text = getCreateGroupDisplayString(LocalContext.current, message)
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            textAlign = TextAlign.Center,
            text = text,
            fontSize = 14.sp,
            color = colors.textColorSecondary
        )
    }
}
