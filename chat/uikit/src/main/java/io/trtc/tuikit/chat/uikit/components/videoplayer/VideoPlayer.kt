package io.trtc.tuikit.chat.uikit.components.videoplayer
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.trtc.tuikit.atomicx.common.util.ActivityLauncher
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
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

    @JvmStatic
    fun play(videoData: VideoData) {
        val context = ContextProvider.getApplicationContext()
        if (context == null) {
            Log.e(TAG, "play failed, application context is null")
            return
        }
        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
            putExtra(EXTRA_VIDEO_URI, videoData.uri)
            putExtra(EXTRA_VIDEO_WIDTH, videoData.width)
            putExtra(EXTRA_VIDEO_HEIGHT, videoData.height)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityLauncher.startActivity(context, intent)
    }
}
