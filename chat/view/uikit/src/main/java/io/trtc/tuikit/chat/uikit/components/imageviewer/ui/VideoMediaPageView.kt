package io.trtc.tuikit.chat.uikit.components.imageviewer.ui
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoContentMode
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoCloseAction
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoPlaybackPolicy
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoTapAction
import io.trtc.tuikit.chat.uikit.components.imageviewer.ui.photoview.PhotoView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.videoplayer.ui.VideoPlayerView
import java.io.File

class VideoMediaPageView(context: Context) : FrameLayout(context) {

    private val photoView = PhotoView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setAllowParentInterceptOnEdge(true)
        setScaleLevels(MIN_SCALE, MID_SCALE, MAX_SCALE)
    }

    private val overlayButton = ImageView(context).apply {
        layoutParams = LayoutParams(dpToPx(60), dpToPx(60), Gravity.CENTER)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private val loadingView = ProgressBar(context).apply {
        layoutParams = LayoutParams(dpToPx(48), dpToPx(48), Gravity.CENTER)
        isIndeterminate = true
        visibility = GONE
    }

    private var currentElement: ImageElement? = null
    private var effectiveVideoData: Any? = null
    private var isDownloading: Boolean = false
    private var onImageTap: (() -> Unit)? = null
    private var onCloseRequested: (() -> Unit)? = null
    private var onDownloadRequested: ((ImageElement) -> Unit)? = null
    private var inlinePlayerView: VideoPlayerView? = null
    private var currentPlayerVideoData: Any? = null

    init {
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        addView(photoView)
        addView(overlayButton)
        addView(loadingView)
        photoView.setOnPhotoTapListener { _, _, _ -> onImageTap?.invoke() }
        overlayButton.setOnClickListener { handleCenterTap() }
    }

    fun bind(
        item: ImageElement,
        effectiveVideoData: Any?,
        isDownloading: Boolean,
        onImageTap: () -> Unit,
        onCloseRequested: () -> Unit,
        onDownloadRequested: (ImageElement) -> Unit
    ) {
        if (currentElement != item) {
            releaseInlinePlayer()
        }
        currentElement = item
        this.effectiveVideoData = effectiveVideoData
        this.isDownloading = isDownloading
        this.onImageTap = onImageTap
        this.onCloseRequested = onCloseRequested
        this.onDownloadRequested = onDownloadRequested
        Log.d(DIAG_TAG, "bind stableId=${item.stableId} data=${item.data?.toString()?.take(80)} " +
            "itemVideoData=${item.videoData?.toString()?.take(80)} effectiveVideoData=${effectiveVideoData?.toString()?.take(80)} " +
            "isDownloading=$isDownloading")
        applyThemeColors()
        Glide.with(context)
            .load(item.data)
            .into(photoView)
        photoView.post {
            photoView.setScale(MIN_SCALE, false)
        }
        renderContent()
    }

    fun setDownloading(downloading: Boolean) {
        if (isDownloading == downloading) {
            return
        }
        isDownloading = downloading
        updateOverlay()
    }

    fun setEffectiveVideoData(videoData: Any?) {
        if (effectiveVideoData != videoData) {
            releaseInlinePlayer()
        }
        effectiveVideoData = videoData
        Log.d(DIAG_TAG, "setEffectiveVideoData stableId=${currentElement?.stableId} " +
            "effectiveVideoData=${videoData?.toString()?.take(80)}")
        renderContent()
    }

    fun onRelease() {
        releaseInlinePlayer()
    }

    fun releaseInlinePlayback() {
        releaseInlinePlayer()
    }

    fun pauseInlinePlaybackForHostStop(): Boolean {
        val playerView = inlinePlayerView ?: return false
        val wasPlaying = playerView.isPlaying()
        playerView.pausePlayback()
        return wasPlaying
    }

    fun resumeInlinePlayback() {
        inlinePlayerView?.resumePlayback()
    }

    private fun handleCenterTap() {
        val element = currentElement ?: return
        when (
            ImageViewerVideoPlaybackPolicy.centerTapAction(
                isDownloading = isDownloading,
                hasVideoData = effectiveVideoData != null
            )
        ) {
            ImageViewerVideoTapAction.None -> Unit
            ImageViewerVideoTapAction.RequestDownload -> onDownloadRequested?.invoke(element)
            else -> Unit
        }
    }

    private fun renderContent() {
        val element = currentElement
        val mode = ImageViewerVideoPlaybackPolicy.contentMode(hasVideoData = effectiveVideoData != null)
        Log.d(DIAG_TAG, "renderContent stableId=${element?.stableId} mode=$mode " +
            "hasEffectiveVideoData=${effectiveVideoData != null}")
        if (mode == ImageViewerVideoContentMode.PlayerPreview && element != null) {
            showPlayerPreview(element)
        } else {
            releaseInlinePlayer()
        }
        updateOverlay()
    }

    private fun showPlayerPreview(element: ImageElement) {
        val videoUri = resolveUri(effectiveVideoData)
        if (videoUri == null) {
            Log.d(DIAG_TAG, "showPlayerPreview skipped stableId=${element.stableId} " +
                "effectiveVideoData=${effectiveVideoData?.toString()?.take(80)}")
            return
        }
        val width = element.width.takeIf { it > 0 } ?: DEFAULT_VIDEO_WIDTH
        val height = element.height.takeIf { it > 0 } ?: DEFAULT_VIDEO_HEIGHT
        val reusedPlayer = inlinePlayerView != null
        val playerView = inlinePlayerView ?: createInlinePlayerView().also { inlinePlayerView = it }
        Log.d(DIAG_TAG, "showPlayerPreview stableId=${element.stableId} uri=$videoUri " +
            "width=$width height=$height reuse=$reusedPlayer")
        playerView.visibility = VISIBLE
        playerView.setPreviewImage(element.data)
        playerView.setBottomEndOverlayAvoidance(dpToPx(PLAYER_BOTTOM_END_AVOIDANCE_DP))
        if (currentPlayerVideoData != effectiveVideoData) {
            currentPlayerVideoData = effectiveVideoData
            playerView.setVideoUri(videoUri, width, height)
        }
    }

    private fun createInlinePlayerView(): VideoPlayerView {
        return VideoPlayerView(context).apply {
            visibility = GONE
            setOnCloseClickListener {
                when (ImageViewerVideoPlaybackPolicy.closeAction()) {
                    ImageViewerVideoCloseAction.ExitViewer -> onCloseRequested?.invoke()
                    else -> Unit
                }
            }
            this@VideoMediaPageView.addView(
                this,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
            ViewCompat.requestApplyInsets(this)
        }
    }

    private fun releaseInlinePlayer() {
        val hadInlinePlayer = inlinePlayerView != null
        inlinePlayerView?.let { playerView ->
            playerView.release()
            removeView(playerView)
        }
        inlinePlayerView = null
        currentPlayerVideoData = null
        if (hadInlinePlayer) {
            updateOverlay()
        }
    }

    private fun updateOverlay() {
        val isPlayingInline = inlinePlayerView != null
        photoView.visibility = if (isPlayingInline) GONE else VISIBLE
        loadingView.visibility = if (isDownloading) VISIBLE else GONE
        overlayButton.visibility = if (isDownloading || isPlayingInline) GONE else VISIBLE
        overlayButton.setImageResource(
            if (effectiveVideoData != null) {
                R.drawable.image_viewer_video_play_circle
            } else {
                R.drawable.image_viewer_video_download_circle
            }
        )
    }

    private fun applyThemeColors() {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorMask)
        loadingView.indeterminateTintList = ColorStateList.valueOf(colors.textColorAntiPrimary)
        overlayButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(colors.bgColorMask, CONTROL_ALPHA))
        }
    }

    private fun resolveUri(data: Any?): Uri? {
        return when (data) {
            is Uri -> data
            is String -> when {
                data.isBlank() -> null
                data.startsWith(FILE_URI_PREFIX) ||
                    data.startsWith(CONTENT_URI_PREFIX) ||
                    data.startsWith(HTTP_URI_PREFIX) ||
                    data.startsWith(HTTPS_URI_PREFIX) -> Uri.parse(data)

                else -> Uri.fromFile(File(data))
            }

            is File -> Uri.fromFile(data)
            else -> null
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CONTROL_ALPHA = 128
        private const val FILE_URI_PREFIX = "file://"
        private const val CONTENT_URI_PREFIX = "content://"
        private const val HTTP_URI_PREFIX = "http://"
        private const val HTTPS_URI_PREFIX = "https://"
        private const val MIN_SCALE = 1.0f
        private const val MID_SCALE = 1.75f
        private const val MAX_SCALE = 3.0f
        private const val DEFAULT_VIDEO_WIDTH = 1920
        private const val DEFAULT_VIDEO_HEIGHT = 1080
        private const val PLAYER_BOTTOM_END_AVOIDANCE_DP = 64
        private const val DIAG_TAG = "ImageViewerMediaDiag"
    }
}
