package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import android.content.Context
import android.net.Uri
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.videoplayer.VideoData
import io.trtc.tuikit.chat.uikit.components.videoplayer.VideoPlayer
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MediaQuality
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun interface MessageVideoPlaybackLauncher {
    fun downloadOrShowVideo(context: Context, messageInfo: MessageInfo)
}

internal data class MessageVideoPlaybackSource(
    val uriString: String,
    val localPath: String,
    val width: Int,
    val height: Int,
    val duration: Long?,
    val snapshotLocalPath: String?
) {
    fun toVideoData(): VideoData {
        return VideoData(
            uri = MessageVideoPlaybackController.uriForPath(localPath),
            localPath = localPath,
            width = width,
            height = height,
            duration = duration,
            snapshotLocalPath = snapshotLocalPath,
        )
    }
}

internal class MessageVideoPlaybackController(
    private val scope: CoroutineScope,
    private val downloadResource: (MessageInfo, MessageMediaFileType, CompletionHandler) -> Unit = ::defaultDownloadResource,
    private val playVideo: (VideoData) -> Unit = VideoPlayer::play,
    private val showWarning: (Context, Int) -> Unit = { context, resId ->
        AtomicToast.show(context, context.getString(resId), style = AtomicToast.Style.WARNING)
    },
    private val playVideoSource: ((MessageVideoPlaybackSource) -> Unit)? = null
) : MessageVideoPlaybackLauncher {

    override fun downloadOrShowVideo(context: Context, messageInfo: MessageInfo) {
        downloadOrShowVideo(messageInfo) { resId ->
            showWarning(context, resId)
        }
    }

    internal fun downloadOrShowVideo(messageInfo: MessageInfo, showWarningRes: (Int) -> Unit) {
        val directVideoSource = buildVideoDataSource(messageInfo)
        if (directVideoSource != null && canPlayDirectlyByPath(directVideoSource.localPath)) {
            playSource(directVideoSource)
            return
        }
        downloadResource(
            messageInfo,
            MessageMediaFileType.VIDEO,
            object : CompletionHandler {
                override fun onSuccess() {
                    scope.launch {
                        val downloadedVideoSource = buildVideoDataSource(messageInfo)
                        if (downloadedVideoSource == null) {
                            showWarningRes(R.string.video_player_invalid_source)
                            return@launch
                        }
                        playSource(downloadedVideoSource)
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    scope.launch {
                        showWarningRes(R.string.video_player_download_failed)
                    }
                }
            }
        )
    }

    internal fun buildVideoData(messageInfo: MessageInfo): VideoData? {
        return buildVideoDataSource(messageInfo)?.toVideoData()
    }

    internal fun buildVideoDataSource(messageInfo: MessageInfo): MessageVideoPlaybackSource? {
        val payload = messageInfo.messagePayload as? VideoMessagePayload ?: return null
        val videoPath = payload.videoPath?.takeIf { it.isNotBlank() } ?: return null
        val width = payload.videoSnapshotWidth.takeIf { it > 0 } ?: 0
        val height = payload.videoSnapshotHeight.takeIf { it > 0 } ?: 0
        val duration = payload.videoDuration.toLong()
        return MessageVideoPlaybackSource(
            uriString = uriStringForPath(videoPath),
            localPath = videoPath,
            width = width,
            height = height,
            duration = duration,
            snapshotLocalPath = payload.videoSnapshotPath,
        )
    }

    internal fun canPlayDirectly(videoData: VideoData): Boolean {
        return canPlayDirectlyByPath(videoData.localPath.orEmpty())
    }

    private fun playSource(source: MessageVideoPlaybackSource) {
        val sourcePlayer = playVideoSource
        if (sourcePlayer != null) {
            sourcePlayer(source)
        } else {
            playVideo(source.toVideoData())
        }
    }

    internal companion object {
        private const val FILE_URI_PREFIX = "file://"
        private const val CONTENT_URI_PREFIX = "content://"
        private const val HTTP_URI_PREFIX = "http://"
        private const val HTTPS_URI_PREFIX = "https://"

        fun canPlayDirectlyByPath(path: String): Boolean {
            return when {
                path.isBlank() -> false
                isUriPath(path) -> true
                else -> File(path).exists()
            }
        }

        fun uriForPath(path: String): Uri {
            return if (isUriPath(path)) {
                Uri.parse(path)
            } else {
                Uri.fromFile(File(path))
            }
        }

        fun uriStringForPath(path: String): String {
            return if (isUriPath(path)) {
                path
            } else {
                File(path).toURI().toString()
            }
        }

        private fun isUriPath(path: String): Boolean {
            return path.startsWith(FILE_URI_PREFIX) ||
                path.startsWith(CONTENT_URI_PREFIX) ||
                path.startsWith(HTTP_URI_PREFIX) ||
                path.startsWith(HTTPS_URI_PREFIX)
        }

        private fun defaultDownloadResource(
            message: MessageInfo,
            resourceType: MessageMediaFileType,
            completion: CompletionHandler
        ) {
            val quality = when (resourceType) {
                MessageMediaFileType.THUMB_IMAGE -> MediaQuality.THUMBNAIL
                MessageMediaFileType.LARGE_IMAGE -> MediaQuality.STANDARD
                MessageMediaFileType.ORIGINAL_IMAGE -> MediaQuality.ORIGINAL
                MessageMediaFileType.VIDEO_SNAPSHOT -> MediaQuality.THUMBNAIL
                MessageMediaFileType.VIDEO -> MediaQuality.ORIGINAL
                else -> null
            }
            MessageActionStore.create(message).downloadMedia(quality, completion)
        }
    }
}
