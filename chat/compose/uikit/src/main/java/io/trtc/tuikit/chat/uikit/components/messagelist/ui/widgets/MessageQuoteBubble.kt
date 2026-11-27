package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.emojiSizeForTextSize
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiText
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalCurrentMessage
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalQuoteClickHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.ImageUtils
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.FaceMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MergedMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload

@Composable
fun MessageQuoteBubble(
    quoteInfo: MessageQuoteInfo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val sourceMessage = LocalCurrentMessage.current
    val quoteHandler = LocalQuoteClickHandler.current
    val resolvedOnClick = onClick ?: sourceMessage?.let { source ->
        { quoteHandler(source, quoteInfo) }
    }
    val labels = MessageQuoteLabels(
        deleted = context.getString(R.string.message_list_quote_deleted),
        revoked = context.getString(R.string.message_list_quote_revoked),
        image = context.getString(R.string.message_list_message_type_image),
        video = context.getString(R.string.message_list_message_type_video),
        voice = context.getString(R.string.message_list_message_type_voice),
        file = context.getString(R.string.message_list_message_type_file),
        face = context.getString(R.string.message_list_message_type_animate_emoji),
        custom = context.getString(R.string.message_list_message_tips_unsupport_custom_message),
        merged = context.getString(R.string.message_list_message_type_merged),
        unknown = context.getString(R.string.message_list_unsupported_message)
    )
    val displayData = MessageQuoteDisplayPolicy.resolve(quoteInfo, labels)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgColorBubbleReciprocal)
            .then(if (resolvedOnClick != null) Modifier.clickable { resolvedOnClick() } else Modifier)
            .padding(8.dp)
    ) {
        if (displayData.senderName.isNotBlank()) {
            Text(
                text = stringResource(R.string.message_list_quote_sender_format, displayData.senderName),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.textColorSecondary
            )
        }
        if (displayData.thumbnail == null) {
            if (displayData.shouldRenderEmoji) {
                val (annotatedString, inlineContent) = rememberEmojiText(displayData.contentText, emojiSize = emojiSizeForTextSize(12.sp))
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = annotatedString,
                    inlineContent = inlineContent,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = if (displayData.isStatusText) FontStyle.Italic else FontStyle.Normal,
                    color = colors.textColorSecondary
                )
            } else {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = displayData.contentText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = if (displayData.isStatusText) FontStyle.Italic else FontStyle.Normal,
                    color = colors.textColorSecondary
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.bgColorBubbleReciprocal)
            ) {
                AsyncImage(
                    modifier = Modifier.size(36.dp),
                    model = displayData.thumbnail.path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.message_list_image_error_image),
                    error = painterResource(R.drawable.message_list_image_error_image),
                    imageLoader = ImageUtils.getImageLoader()
                )
                if (displayData.thumbnail.isVideo) {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = "▶",
                        fontSize = 16.sp,
                        color = colors.textColorAntiPrimary
                    )
                }
            }
        }
    }
}

internal object MessageQuoteDisplayPolicy {
    fun resolve(
        quoteInfo: MessageQuoteInfo,
        labels: MessageQuoteLabels
    ): MessageQuoteDisplayData {
        val senderName = resolveSenderName(quoteInfo)
        val payload = quoteInfo.messagePayload
        if (quoteInfo.status == MessageStatus.REVOKED) {
            return MessageQuoteDisplayData(
                senderName = senderName,
                contentText = labels.revoked,
                isStatusText = true
            )
        }
        if (payload == null) {
            return MessageQuoteDisplayData(
                senderName = senderName,
                contentText = PARTIAL_CONTENT,
                isPartial = true
            )
        }
        if (quoteInfo.status == MessageStatus.DELETED) {
            return MessageQuoteDisplayData(
                senderName = senderName,
                contentText = labels.deleted,
                isStatusText = true
            )
        }
        return MessageQuoteDisplayData(
            senderName = senderName,
            contentText = resolveContentText(payload, labels),
            thumbnail = resolveThumbnail(payload),
            shouldRenderEmoji = payload is TextMessagePayload
        )
    }

    private fun resolveSenderName(quoteInfo: MessageQuoteInfo): String {
        val sender = quoteInfo.sender
        return sender.friendRemark?.takeIf { it.isNotBlank() }
            ?: sender.nameCard?.takeIf { it.isNotBlank() }
            ?: sender.nickname?.takeIf { it.isNotBlank() }
            ?: sender.userID
    }

    private fun resolveContentText(payload: MessagePayload, labels: MessageQuoteLabels): String {
        return when (payload) {
            is TextMessagePayload -> payload.text
            is ImageMessagePayload -> labels.image
            is VideoMessagePayload -> labels.video
            is AudioMessagePayload -> resolveAudioText(payload, labels)
            is FileMessagePayload -> payload.fileName?.takeIf { it.isNotBlank() } ?: labels.file
            is FaceMessagePayload -> labels.face
            is CustomMessagePayload -> payload.description?.takeIf { it.isNotBlank() } ?: labels.custom
            is MergedMessagePayload -> payload.title.takeIf { it.isNotBlank() } ?: labels.merged
            else -> labels.unknown
        }
    }

    private fun resolveAudioText(payload: AudioMessagePayload, labels: MessageQuoteLabels): String {
        val duration = payload.audioDuration
        if (duration <= 0) {
            return labels.voice
        }
        val minutes = duration / SECONDS_PER_MINUTE
        val seconds = duration % SECONDS_PER_MINUTE
        val durationText = if (minutes > 0) {
            "$minutes:${seconds.toString().padStart(2, '0')}\""
        } else {
            "$seconds\""
        }
        return "${labels.voice} $durationText"
    }

    private fun resolveThumbnail(payload: MessagePayload): MessageQuoteThumbnail? {
        if (payload is ImageMessagePayload) {
            val path = payload.thumbImagePath.takeUnlessBlank()
                ?: payload.thumbImageURL.takeUnlessBlank()
                ?: payload.originalImagePath.takeUnlessBlank()
                ?: payload.originalImageURL.takeUnlessBlank()
            return path?.let { MessageQuoteThumbnail(path = it, isVideo = false) }
        }
        if (payload is VideoMessagePayload) {
            val path = payload.videoSnapshotPath.takeUnlessBlank()
                ?: payload.videoSnapshotURL.takeUnlessBlank()
            return path?.let { MessageQuoteThumbnail(path = it, isVideo = true) }
        }
        return null
    }

    private fun String?.takeUnlessBlank(): String? = this?.takeIf { it.isNotBlank() }

    private const val PARTIAL_CONTENT = "..."
    private const val SECONDS_PER_MINUTE = 60
}

internal data class MessageQuoteLabels(
    val deleted: String,
    val revoked: String,
    val image: String,
    val video: String,
    val voice: String,
    val file: String,
    val face: String,
    val custom: String,
    val merged: String,
    val unknown: String
)

internal data class MessageQuoteDisplayData(
    val senderName: String,
    val contentText: String,
    val thumbnail: MessageQuoteThumbnail? = null,
    val isPartial: Boolean = false,
    val isStatusText: Boolean = false,
    val shouldRenderEmoji: Boolean = false
)

internal data class MessageQuoteThumbnail(
    val path: String,
    val isVideo: Boolean
)
