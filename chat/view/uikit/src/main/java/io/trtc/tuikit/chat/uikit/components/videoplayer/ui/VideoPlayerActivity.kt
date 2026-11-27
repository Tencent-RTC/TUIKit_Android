package io.trtc.tuikit.chat.uikit.components.videoplayer.ui
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import io.trtc.tuikit.chat.uikit.components.videoplayer.VideoPlayer

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var videoPlayerView: VideoPlayerView
    private var shouldResumePlayback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeDisplay()

        val videoUri = intent?.getParcelableExtra<Uri>(VideoPlayer.EXTRA_VIDEO_URI)
        if (videoUri == null) {
            Log.e(TAG, "onCreate failed, video uri is null")
            finish()
            return
        }

        val videoWidth = intent.getIntExtra(VideoPlayer.EXTRA_VIDEO_WIDTH, 0)
        val videoHeight = intent.getIntExtra(VideoPlayer.EXTRA_VIDEO_HEIGHT, 0)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        videoPlayerView = VideoPlayerView(this).apply {
            setOnCloseClickListener { finish() }
            setVideoUri(videoUri, videoWidth, videoHeight)
        }
        setContentView(videoPlayerView)
    }

    override fun onStop() {
        shouldResumePlayback = this::videoPlayerView.isInitialized && videoPlayerView.isPlaying()
        if (this::videoPlayerView.isInitialized) {
            videoPlayerView.pausePlayback()
        }
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (this::videoPlayerView.isInitialized && shouldResumePlayback) {
            videoPlayerView.resumePlayback()
        }
    }

    override fun onDestroy() {
        if (this::videoPlayerView.isInitialized) {
            videoPlayerView.release()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VideoPlayerActivity"
    }

    private fun enableEdgeToEdgeDisplay() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
