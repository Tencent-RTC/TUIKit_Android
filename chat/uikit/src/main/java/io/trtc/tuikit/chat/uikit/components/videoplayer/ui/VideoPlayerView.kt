package io.trtc.tuikit.chat.uikit.components.videoplayer.ui
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils
import io.trtc.tuikit.chat.uikit.components.videoplayer.ui.widget.ShadowImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val themeStore = ThemeStore.shared(context)
    private var viewScope: CoroutineScope? = null
    private var progressRunnable: Runnable? = null
    private var currentVideoUri: Uri? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var isPrepared = false
    private var isTracking = false
    private var shouldResumeAfterTracking = false
    private var hasStartedPlayback = false
    private var hasPreviewImage = false
    private var bottomEndOverlayAvoidance = 0
    private var onCloseClickListener: (() -> Unit)? = null

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null

    private val videoView = TextureView(context).apply {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        isOpaque = false
    }
    private val previewImageView = ImageView(context).apply {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = View.GONE
    }
    private val loadingView = ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
    }
    private val topScrim = View(context).apply {
        setBackgroundResource(R.drawable.video_player_top_scrim)
        isClickable = false
        isFocusable = false
    }
    private val bottomScrim = View(context).apply {
        setBackgroundResource(R.drawable.video_player_bottom_scrim)
        isClickable = false
        isFocusable = false
    }
    private val closeButton = ShadowImageView(context).apply {
        setImageResource(R.drawable.video_player_close_icon)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.video_player_close)
        setShadow(4f, 1f, Color.argb(140, 0, 0, 0))
    }
    private val controlsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
    }
    private val playPauseButton = ShadowImageView(context).apply {
        setImageResource(R.drawable.video_player_play_icon)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.video_player_play)
        setShadow(6f, 1f, Color.argb(140, 0, 0, 0))
    }
    private val rightControls = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.START
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
    }
    private val timeRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
    }
    private val currentTimeView = buildTimeTextView(alphaPercent = 1.0f)
    private val timeSeparatorView = buildTimeTextView(alphaPercent = 0.55f).apply {
        text = " / "
    }
    private val durationView = buildTimeTextView(alphaPercent = 0.6f)
    private val seekBar = SeekBar(context).apply {
        max = 0
        progress = 0
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        progressDrawable = resources.getDrawable(
            R.drawable.video_player_seekbar_progress,
            context.theme
        )
        thumb = resources.getDrawable(
            R.drawable.video_player_seekbar_thumb,
            context.theme
        )
        thumbOffset = dpToPx(2)
        setPadding(0, dpToPx(6), 0, dpToPx(6))
        splitTrack = false
    }
    private val errorOverlay = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    private val errorTextView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        text = context.getString(R.string.video_player_load_failed)
    }
    private val retryButton = TextView(context).apply {
        gravity = Gravity.CENTER
        setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        text = context.getString(R.string.video_player_retry)
        isClickable = true
        isFocusable = true
    }

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        isClickable = true
        isFocusable = true
        setBackgroundColor(Color.BLACK)
        setupLayout()
        setupTextureListener()
        setupListeners()
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            applyWindowInsets(insets)
            insets
        }
    }

    fun setOnCloseClickListener(listener: () -> Unit) {
        onCloseClickListener = listener
    }

    fun setVideoUri(videoUri: Uri, width: Int, height: Int) {
        currentVideoUri = videoUri
        videoWidth = width.coerceAtLeast(0)
        videoHeight = height.coerceAtLeast(0)
        isPrepared = false
        hasStartedPlayback = false
        hideErrorOverlay()
        loadingView.visibility = View.VISIBLE
        openVideo()
        startProgressUpdates()
        updatePlaybackUi()
    }

    fun setPreviewImage(data: Any?) {
        if (data == null) {
            hasPreviewImage = false
            previewImageView.visibility = View.GONE
            previewImageView.setImageDrawable(null)
            return
        }
        hasPreviewImage = true
        previewImageView.visibility = View.VISIBLE
        Glide.with(context)
            .load(data)
            .into(previewImageView)
        updatePreviewVisibility()
    }

    fun setBottomEndOverlayAvoidance(avoidance: Int) {
        bottomEndOverlayAvoidance = avoidance.coerceAtLeast(0)
        updateControlsPadding()
    }

    fun isPlaying(): Boolean {
        return isPrepared && (mediaPlayer?.isPlaying == true)
    }

    fun pausePlayback() {
        val player = mediaPlayer
        if (isPrepared && player != null && player.isPlaying) {
            player.pause()
        }
        updatePlaybackUi()
    }

    fun resumePlayback() {
        val player = mediaPlayer
        if (isPrepared && player != null && !player.isPlaying && currentVideoUri != null) {
            player.start()
            hasStartedPlayback = true
        }
        updatePlaybackUi()
    }

    fun release() {
        stopProgressUpdates()
        releaseMediaPlayer()
        viewScope?.cancel()
        viewScope = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            themeStore.themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }
        startProgressUpdates()
    }

    override fun onDetachedFromWindow() {
        stopProgressUpdates()
        viewScope?.cancel()
        viewScope = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        applyVideoTransform()
    }

    private fun setupLayout() {
        addView(
            videoView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            previewImageView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        addView(
            topScrim,
            LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(TOP_SCRIM_HEIGHT_DP), Gravity.TOP)
        )
        addView(
            bottomScrim,
            LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(BOTTOM_SCRIM_HEIGHT_DP), Gravity.BOTTOM)
        )

        addView(
            loadingView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        addView(
            closeButton,
            LayoutParams(dpToPx(48), dpToPx(48), Gravity.START or Gravity.TOP).apply {
                marginStart = dpToPx(8)
                topMargin = dpToPx(8)
            }
        )

        timeRow.apply {
            addView(
                currentTimeView,
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            )
            addView(
                timeSeparatorView,
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            )
            addView(
                durationView,
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            )
        }
        rightControls.apply {
            addView(
                timeRow,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )
            addView(
                seekBar,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    .apply {
                        topMargin = dpToPx(2)
                    }
            )
        }
        controlsContainer.apply {
            updateControlsPadding()
            addView(
                playPauseButton,
                LinearLayout.LayoutParams(dpToPx(44), dpToPx(44))
            )
            addView(
                rightControls,
                LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(14)
                }
            )
        }
        addView(
            controlsContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        )

        errorOverlay.apply {
            addView(
                errorTextView,
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            )
            addView(
                retryButton,
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpToPx(16)
                }
            )
        }
        addView(
            errorOverlay,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    private fun setupTextureListener() {
        videoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(texture)
                openVideo()
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                applyVideoTransform()
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                releaseMediaPlayer()
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                // no-op
            }
        }
    }

    private fun setupListeners() {
        closeButton.setOnClickListener {
            onCloseClickListener?.invoke()
        }
        playPauseButton.setOnClickListener {
            val player = mediaPlayer
            if (!isPrepared || player == null) {
                return@setOnClickListener
            }
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.duration > 0 && player.currentPosition >= player.duration) {
                    player.seekTo(0)
                }
                player.start()
                hasStartedPlayback = true
            }
            updatePlaybackUi()
        }
        retryButton.setOnClickListener {
            val targetUri = currentVideoUri ?: return@setOnClickListener
            setVideoUri(targetUri, videoWidth, videoHeight)
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTimeView.text = ChatDateTimeUtils.formatDurationMillis(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                val player = mediaPlayer
                if (!isPrepared || player == null) {
                    return
                }
                isTracking = true
                shouldResumeAfterTracking = player.isPlaying
                if (shouldResumeAfterTracking) {
                    player.pause()
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val player = mediaPlayer
                if (!isPrepared || player == null) {
                    isTracking = false
                    return
                }
                val targetProgress = seekBar?.progress ?: 0
                player.seekTo(targetProgress)
                if (shouldResumeAfterTracking) {
                    player.start()
                    hasStartedPlayback = true
                }
                isTracking = false
                updatePlaybackUi()
            }
        })
    }

    private fun openVideo() {
        val uri = currentVideoUri ?: return
        val targetSurface = surface ?: return

        releaseMediaPlayer()
        try {
            val player = MediaPlayer().apply {
                setSurface(targetSurface)
                setDataSource(context, uri)
                setOnPreparedListener { mp ->
                    isPrepared = true
                    mp.isLooping = false
                    if (this@VideoPlayerView.videoWidth <= 0 || this@VideoPlayerView.videoHeight <= 0) {
                        this@VideoPlayerView.videoWidth = mp.videoWidth.coerceAtLeast(0)
                        this@VideoPlayerView.videoHeight = mp.videoHeight.coerceAtLeast(0)
                    }
                    applyVideoTransform()
                    loadingView.visibility = View.GONE
                    seekBar.max = mp.duration.coerceAtLeast(0)
                    durationView.text = ChatDateTimeUtils.formatDurationMillis(mp.duration.toLong())
                    updatePlaybackUi()
                }
                setOnVideoSizeChangedListener { _, width, height ->
                    if (width > 0 && height > 0) {
                        this@VideoPlayerView.videoWidth = width
                        this@VideoPlayerView.videoHeight = height
                        applyVideoTransform()
                    }
                }
                setOnBufferingUpdateListener { _, percent ->
                    val durationMs = seekBar.max
                    if (durationMs > 0) {
                        seekBar.secondaryProgress = (durationMs * percent / 100).coerceIn(0, durationMs)
                    }
                }
                setOnCompletionListener {
                    updateProgress(forceDuration = true)
                    updatePlaybackUi()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                    showErrorOverlay()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "openVideo failed", e)
            showErrorOverlay()
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: IllegalStateException) {
                // ignore
            }
            player.release()
        }
        mediaPlayer = null
        isPrepared = false
        hasStartedPlayback = false
    }

    private fun startProgressUpdates() {
        if (!isAttachedToWindow) {
            return
        }
        if (progressRunnable != null) {
            return
        }
        progressRunnable = object : Runnable {
            override fun run() {
                updateProgress()
                postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }.also { post(it) }
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { removeCallbacks(it) }
        progressRunnable = null
    }

    private fun updateProgress(forceDuration: Boolean = false) {
        val player = mediaPlayer
        if (!isPrepared || isTracking || player == null) {
            return
        }
        val duration = player.duration.coerceAtLeast(0)
        val currentPosition = when {
            forceDuration && duration > 0 -> duration
            else -> player.currentPosition.coerceAtLeast(0)
        }
        seekBar.max = duration
        seekBar.progress = currentPosition
        currentTimeView.text = ChatDateTimeUtils.formatDurationMillis(currentPosition.toLong())
        durationView.text = ChatDateTimeUtils.formatDurationMillis(duration.toLong())
    }

    private fun updatePlaybackUi() {
        val isPlaying = isPrepared && (mediaPlayer?.isPlaying == true)
        updatePreviewVisibility()
        playPauseButton.setImageResource(
            if (isPlaying) R.drawable.video_player_pause_icon else R.drawable.video_player_play_icon
        )
        playPauseButton.contentDescription = context.getString(
            if (isPlaying) R.string.video_player_pause else R.string.video_player_play
        )
    }

    private fun showErrorOverlay() {
        isPrepared = false
        loadingView.visibility = View.GONE
        errorOverlay.visibility = View.VISIBLE
        updatePlaybackUi()
    }

    private fun hideErrorOverlay() {
        errorOverlay.visibility = View.GONE
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(Color.BLACK)
        errorTextView.setTextColor(Color.WHITE)
        retryButton.setTextColor(colors.textColorButton)
        retryButton.background = GradientDrawable().apply {
            cornerRadius = dpToPx(18).toFloat()
            setColor(colors.buttonColorPrimaryDefault)
        }
        controlsContainer.background = null
        playPauseButton.background = null
        closeButton.background = null
    }

    /**
     * Compute a transform Matrix that fits the video into the TextureView while
     * preserving aspect ratio (letter-box). TextureView's content is drawn into
     * its full bounds by default, which would stretch the video; the transform
     * rescales it to the proper size and centers it.
     */
    private fun applyVideoTransform() {
        val viewWidth = videoView.width.toFloat()
        val viewHeight = videoView.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) {
            return
        }

        val videoW = videoWidth.toFloat()
        val videoH = videoHeight.toFloat()

        var finalVideoHeight = viewHeight
        var finalVideoWidth = viewHeight * videoW / videoH
        if (finalVideoWidth > viewWidth) {
            finalVideoWidth = viewWidth
            finalVideoHeight = viewWidth * videoH / videoW
        }

        val scaleX = finalVideoWidth / viewWidth
        val scaleY = finalVideoHeight / viewHeight
        val dx = (viewWidth - finalVideoWidth) / 2f
        val dy = (viewHeight - finalVideoHeight) / 2f
        val matrix = Matrix().apply {
            postScale(scaleX, scaleY)
            postTranslate(dx, dy)
        }
        videoView.setTransform(matrix)
        videoView.invalidate()
    }

    private fun applyWindowInsets(insets: WindowInsetsCompat) {
        val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val topInset = maxOf(systemBarsInsets.top, cutoutInsets.top)
        val bottomInset = maxOf(systemBarsInsets.bottom, cutoutInsets.bottom)
        (closeButton.layoutParams as? LayoutParams)?.let { layoutParams ->
            layoutParams.topMargin = topInset + dpToPx(8)
            closeButton.layoutParams = layoutParams
        }
        (controlsContainer.layoutParams as? LayoutParams)?.let { layoutParams ->
            layoutParams.bottomMargin = bottomInset + dpToPx(8)
            controlsContainer.layoutParams = layoutParams
        }
        (bottomScrim.layoutParams as? LayoutParams)?.let { layoutParams ->
            layoutParams.height = dpToPx(BOTTOM_SCRIM_HEIGHT_DP) + bottomInset
            bottomScrim.layoutParams = layoutParams
        }
        (topScrim.layoutParams as? LayoutParams)?.let { layoutParams ->
            layoutParams.height = dpToPx(TOP_SCRIM_HEIGHT_DP) + topInset
            topScrim.layoutParams = layoutParams
        }
    }

    private fun updateControlsPadding() {
        val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        controlsContainer.setPadding(
            dpToPx(18) + if (isRtl) bottomEndOverlayAvoidance else 0,
            dpToPx(12),
            dpToPx(18) + if (isRtl) 0 else bottomEndOverlayAvoidance,
            dpToPx(12)
        )
    }

    private fun updatePreviewVisibility() {
        previewImageView.visibility = if (VideoPlayerPreviewVisibilityPolicy.shouldShowPreview(
                hasPreviewImage = hasPreviewImage,
                isPrepared = isPrepared,
                hasStartedPlayback = hasStartedPlayback
            )
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun buildTimeTextView(alphaPercent: Float = 1.0f): TextView {
        return TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = ChatDateTimeUtils.formatDurationMillis(0)
            setTextColor(Color.WHITE)
            alpha = alphaPercent
            setShadowLayer(
                dpToPx(3).toFloat(),
                0f,
                dpToPx(1).toFloat(),
                Color.argb(140, 0, 0, 0)
            )
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "VideoPlayerView"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val TOP_SCRIM_HEIGHT_DP = 120
        private const val BOTTOM_SCRIM_HEIGHT_DP = 180
    }
}

internal object VideoPlayerPreviewVisibilityPolicy {
    fun shouldShowPreview(
        hasPreviewImage: Boolean,
        @Suppress("UNUSED_PARAMETER") isPrepared: Boolean,
        hasStartedPlayback: Boolean
    ): Boolean {
        return hasPreviewImage && !hasStartedPlayback
    }
}
