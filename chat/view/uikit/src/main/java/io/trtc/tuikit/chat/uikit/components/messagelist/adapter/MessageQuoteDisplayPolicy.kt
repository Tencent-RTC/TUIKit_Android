package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
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
        when (quoteInfo.status) {
            MessageStatus.DELETED -> {
                return MessageQuoteDisplayData(
                    senderName = senderName,
                    contentText = labels.deleted,
                    isStatusText = true
                )
            }
            else -> Unit
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
