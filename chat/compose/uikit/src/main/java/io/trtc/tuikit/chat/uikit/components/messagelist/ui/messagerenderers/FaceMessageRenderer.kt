package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalInteractionHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageContentGestures
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.ImageUtils
import io.trtc.tuikit.atomicxcore.api.message.FaceMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class FaceMessageRenderer : MessageRenderer {

    override val renderConfig: MessageRenderConfig =
        MessageRenderConfig(showMessageMeta = true, useDefaultBubble = false)

    @Composable
    override fun Render(message: MessageInfo) {
        val context = LocalContext.current
        val messageInteraction = LocalInteractionHandler.current
        EmojiManager.initialize(context)
        val payload = message.messagePayload as? FaceMessagePayload
        val emoji = FaceMessageResourceResolver.resolve(
            faceName = payload?.faceData,
            faceIndex = payload?.faceIndex ?: -1,
            emojis = EmojiManager.emojiGroupList.flatMap { it.emojis }
        )
        val cachedDrawable = remember(emoji?.key) {
            emoji?.key?.let { EmojiManager.getCachedEmojiDrawable(it) }
        }
        val cachedBitmap = remember(cachedDrawable) {
            when (cachedDrawable) {
                is BitmapDrawable -> cachedDrawable.bitmap
                null -> null
                else -> runCatching { cachedDrawable.toBitmap() }.getOrNull()
            }
        }
        val gestureModifier = Modifier
            .size(80.dp)
            .messageContentGestures(message, messageInteraction)

        if (cachedBitmap != null) {
            Image(
                bitmap = cachedBitmap.asImageBitmap(),
                contentDescription = "FaceMessage",
                contentScale = ContentScale.Fit,
                modifier = gestureModifier
            )
        } else {
            AsyncImage(
                modifier = gestureModifier,
                model = emoji?.emojiUrl ?: R.drawable.message_list_image_error_image,
                contentDescription = "FaceMessage",
                contentScale = ContentScale.Fit,
                error = painterResource(R.drawable.message_list_image_error_image),
                placeholder = painterResource(R.drawable.message_list_image_error_image),
                imageLoader = ImageUtils.getImageLoader()
            )
        }
    }
}
