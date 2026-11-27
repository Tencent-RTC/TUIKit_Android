package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalInteractionHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageContentGestures
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.DateTimeUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.ImageUtils
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload

class VideoMessageRenderer : MessageRenderer {

    override val renderConfig: MessageRenderConfig =
        MessageRenderConfig(showMessageMeta = true, useDefaultBubble = false)

    @Composable
    override fun Render(message: MessageInfo) {
        val payload = message.messagePayload as? VideoMessagePayload
        val imageSize = MediaBubbleSizePolicy.calculateSize(
            originalWidth = payload?.videoSnapshotWidth ?: 0,
            originalHeight = payload?.videoSnapshotHeight ?: 0
        )
        val snapshotModel = payload?.videoSnapshotPath?.takeIf { it.isNotBlank() }
            ?: payload?.videoSnapshotURL?.takeIf { it.isNotBlank() }
        val messageInteraction = LocalInteractionHandler.current
        var snapshotLoaded by remember(message.msgID, snapshotModel) { mutableStateOf(false) }
        LaunchedEffect(message.msgID) {
            messageInteraction.onRendered()
        }

        Box(
            modifier = Modifier
                .size(imageSize)
                .clip(RoundedCornerShape(MediaBubbleSizePolicy.CORNER_RADIUS_DP.dp))
                .background(Color.Black)
                .messageContentGestures(message, messageInteraction),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                modifier = Modifier.size(imageSize),
                model = snapshotModel,
                contentDescription = null,
                contentScale = if (snapshotLoaded) ContentScale.Fit else ContentScale.Crop,
                placeholder = painterResource(R.drawable.message_list_image_error_image),
                error = painterResource(R.drawable.message_list_image_error_image),
                onSuccess = { snapshotLoaded = true },
                onError = { snapshotLoaded = false },
                imageLoader = ImageUtils.getImageLoader()
            )

            Icon(
                modifier = Modifier.size(44.dp),
                painter = painterResource(R.drawable.message_list_video_play_icon),
                contentDescription = "",
                tint = Color.Unspecified
            )

            Text(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        color = Color(DURATION_CHIP_BG_COLOR),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                text = DateTimeUtils.formatSmartTime(payload?.videoDuration),
                fontSize = 12.sp,
                color = Color.White
            )
        }
    }

    private companion object {
        const val DURATION_CHIP_BG_COLOR = 0x66000000
    }
}
