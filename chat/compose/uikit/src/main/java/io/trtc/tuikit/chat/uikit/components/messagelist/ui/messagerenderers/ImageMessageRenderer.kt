package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalInteractionHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageContentGestures
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.ImageUtils
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class ImageMessageRenderer : MessageRenderer {

    override val renderConfig: MessageRenderConfig =
        MessageRenderConfig(showMessageMeta = true, useDefaultBubble = false)

    @Composable
    override fun Render(message: MessageInfo) {
        val messageInteraction = LocalInteractionHandler.current
        val payload = message.messagePayload as? ImageMessagePayload
        val imageSize = MediaBubbleSizePolicy.calculateSize(
            originalWidth = payload?.originalImageWidth ?: 0,
            originalHeight = payload?.originalImageHeight ?: 0
        )
        val imageModel = ImageMessageRenderPolicy.resolveDisplayImagePath(
            originalImagePath = payload?.originalImagePath,
            largeImagePath = payload?.largeImagePath,
            thumbImagePath = payload?.thumbImagePath,
            originalImageURL = payload?.originalImageURL,
            largeImageURL = payload?.largeImageURL,
            thumbImageURL = payload?.thumbImageURL
        )
        LaunchedEffect(message.msgID) {
            messageInteraction.onRendered()
        }

        AsyncImage(
            modifier = Modifier
                .size(imageSize)
                .clip(RoundedCornerShape(MediaBubbleSizePolicy.CORNER_RADIUS_DP.dp))
                .messageContentGestures(message, messageInteraction),
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.message_list_image_error_image),
            placeholder = painterResource(R.drawable.message_list_image_error_image),
            imageLoader = ImageUtils.getImageLoader()
        )
    }
}
