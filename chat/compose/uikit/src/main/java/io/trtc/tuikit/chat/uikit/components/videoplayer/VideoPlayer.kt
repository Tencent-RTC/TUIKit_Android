package io.trtc.tuikit.chat.uikit.components.videoplayer

import android.content.Intent
import android.net.Uri
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.videoplayer.ui.VideoPlayerActivity

data class VideoData(
    val uri: Uri,
    val localPath: String? = null,
    val width: Int,
    val height: Int,
    val duration: Long? = null,
    val snapshotUrl: String? = null,
    val snapshotLocalPath: String? = null,
)

object VideoPlayer {
    private const val TAG = "VideoPlayer"
    internal const val EXTRA_VIDEO_URI = "video_uri"
    internal const val EXTRA_VIDEO_WIDTH = "video_width"
    internal const val EXTRA_VIDEO_HEIGHT = "video_height"
    internal const val EXTRA_PREVIEW_IMAGE = "preview_image"

    fun play(videoData: VideoData) {
        val context = runCatching { ContextProvider.appContext }.getOrNull()
        if (context == null) {
            Log.e(TAG, "play failed, application context is null")
            return
        }
        val previewImage = videoData.snapshotLocalPath?.takeIf { it.isNotBlank() }
            ?: videoData.snapshotUrl?.takeIf { it.isNotBlank() }
        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
            putExtra(EXTRA_VIDEO_URI, videoData.uri)
            putExtra(EXTRA_VIDEO_WIDTH, videoData.width)
            putExtra(EXTRA_VIDEO_HEIGHT, videoData.height)
            if (previewImage != null) {
                putExtra(EXTRA_PREVIEW_IMAGE, previewImage)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
