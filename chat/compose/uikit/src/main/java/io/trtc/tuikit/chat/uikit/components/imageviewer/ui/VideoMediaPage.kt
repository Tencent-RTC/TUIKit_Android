package io.trtc.tuikit.chat.uikit.components.imageviewer.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoCloseAction
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoContentMode
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoPlaybackPolicy
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoTapAction
import io.trtc.tuikit.chat.uikit.components.imageviewer.utils.ImageUtils
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.videoplayer.ui.MinimalControls
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun VideoMediaPage(
    element: ImageElement,
    effectiveVideoData: Any?,
    isDownloading: Boolean,
    isCurrentPage: Boolean,
    refreshSignal: Int,
    onImageTap: () -> Unit,
    onCloseRequested: () -> Unit,
    onDownloadRequested: (ImageElement) -> Unit,
    onPagerScrollEnabled: (Boolean) -> Unit = {},
    onPagerOverscroll: (Float) -> Unit = {},
    onPagerOverscrollEnd: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    val contentMode = ImageViewerVideoPlaybackPolicy.contentMode(hasVideoData = effectiveVideoData != null)
    val videoUri = remember(effectiveVideoData, refreshSignal) { resolveVideoUri(effectiveVideoData) }
    val showPlayer = contentMode == ImageViewerVideoContentMode.PlayerPreview && isCurrentPage && videoUri != null

    LaunchedEffect(showPlayer) {
        if (showPlayer) {
            onPagerScrollEnabled(true)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.bgColorMask)) {
        if (showPlayer) {
            InlineVideoPlayer(
                element = element,
                videoUri = videoUri!!,
                onCloseRequested = onCloseRequested
            )
        } else {
            ZoomablePhotoView(
                modifier = Modifier.fillMaxSize(),
                data = element.data,
                onTap = onImageTap,
                onPagerScrollEnabled = onPagerScrollEnabled,
                onPagerOverscroll = onPagerOverscroll,
                onPagerOverscrollEnd = onPagerOverscrollEnd
            )
        }

        when {
            isDownloading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(PROGRESS_SIZE_DP),
                    color = colors.textColorAntiPrimary
                )
            }

            !showPlayer -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(OVERLAY_BUTTON_SIZE_DP)
                        .background(colors.bgColorMask.copy(alpha = CONTROL_ALPHA), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val action = ImageViewerVideoPlaybackPolicy.centerTapAction(
                                isDownloading = isDownloading,
                                hasVideoData = effectiveVideoData != null
                            )
                            if (action == ImageViewerVideoTapAction.RequestDownload) {
                                onDownloadRequested(element)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            if (effectiveVideoData != null) {
                                R.drawable.image_viewer_video_play_circle
                            } else {
                                R.drawable.image_viewer_video_download_circle
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineVideoPlayer(
    element: ImageElement,
    videoUri: Uri,
    onCloseRequested: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalTheme.current.colors
    var videoWidth by remember(videoUri) { mutableIntStateOf(element.width.takeIf { it > 0 } ?: DEFAULT_VIDEO_WIDTH) }
    var videoHeight by remember(videoUri) { mutableIntStateOf(element.height.takeIf { it > 0 } ?: DEFAULT_VIDEO_HEIGHT) }

    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }
    var hasStartedPlayback by remember(videoUri) { mutableStateOf(false) }
    var isBuffering by remember(videoUri) { mutableStateOf(true) }
    var hasError by remember(videoUri) { mutableStateOf(false) }
    val showPreview = element.data != null && !hasStartedPlayback

    LaunchedEffect(player) {
        player.listen { events ->
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) {
                hasStartedPlayback = true
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                isBuffering = player.playbackState == Player.STATE_BUFFERING
            }
            if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
                val videoSize = player.videoSize
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }
            if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                hasError = true
                isBuffering = false
            }
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlaying = player.isPlaying
                    if (wasPlaying) {
                        player.pause()
                    }
                }

                Lifecycle.Event.ON_START -> {
                    if (wasPlaying) {
                        player.play()
                    }
                    wasPlaying = false
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stop()
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize()
                .layout { measurable, constraints ->
                    val srcSizePx = with(density) {
                        Size(
                            Dp(videoWidth.toFloat()).toPx(),
                            Dp(videoHeight.toFloat()).toPx()
                        )
                    }
                    val dstSizePx = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                    val scaleFactor = ContentScale.Fit.computeScaleFactor(srcSizePx, dstSizePx)
                    val placeable = measurable.measure(
                        constraints.copy(
                            maxWidth = (srcSizePx.width * scaleFactor.scaleX).roundToInt(),
                            maxHeight = (srcSizePx.height * scaleFactor.scaleY).roundToInt()
                        )
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
        )

        if (showPreview) {
            AsyncImage(
                model = element.data,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                imageLoader = ImageUtils.getImageLoader(),
                modifier = Modifier.fillMaxSize()
            )
        }

        val density = LocalDensity.current
        val windowInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
        val topInset = with(density) { windowInsets.getTop(density).toDp() }
        val bottomInset = with(density) { windowInsets.getBottom(density).toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(TOP_SCRIM_HEIGHT_DP + topInset)
                .background(Brush.verticalGradient(listOf(TOP_SCRIM_START_COLOR, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BOTTOM_SCRIM_HEIGHT_DP + bottomInset)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to BOTTOM_SCRIM_CENTER_COLOR,
                        1f to BOTTOM_SCRIM_START_COLOR
                    )
                )
        )

        if (isBuffering && !hasError) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.systemBarsIgnoringVisibility
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Bottom)
                )
                .padding(bottom = CONTROLS_BOTTOM_MARGIN_DP, end = PLAYER_BOTTOM_END_AVOIDANCE_DP)
        ) {
            MinimalControls(player)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(
                    WindowInsets.systemBarsIgnoringVisibility
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Start)
                )
                .padding(start = CLOSE_BUTTON_MARGIN_DP, top = CLOSE_BUTTON_MARGIN_DP)
                .size(CLOSE_BUTTON_SIZE_DP)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    when (ImageViewerVideoPlaybackPolicy.closeAction()) {
                        ImageViewerVideoCloseAction.ExitViewer -> onCloseRequested()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ShadowedIcon(
                painter = painterResource(R.drawable.video_player_close_icon),
                contentDescription = stringResource(R.string.video_player_close),
                shadowRadius = CLOSE_ICON_SHADOW_RADIUS_DP,
                iconModifier = Modifier.size(CLOSE_ICON_SIZE_DP)
            )
        }

        if (hasError) {
            VideoErrorOverlay(
                onRetry = {
                    hasError = false
                    isBuffering = true
                    hasStartedPlayback = false
                    player.prepare()
                },
                retryBackground = colors.buttonColorPrimaryDefault,
                retryTextColor = colors.textColorButton
            )
        }
    }
}

@Composable
private fun ShadowedIcon(
    painter: Painter,
    contentDescription: String?,
    shadowRadius: Dp,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = ICON_SHADOW_COLOR,
            modifier = iconModifier
                .offset(y = ICON_SHADOW_OFFSET_Y_DP)
                .blur(shadowRadius)
        )
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = iconModifier
        )
    }
}

@Composable
private fun VideoErrorOverlay(
    onRetry: () -> Unit,
    retryBackground: Color,
    retryTextColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.video_player_load_failed),
            color = Color.White,
            fontSize = ERROR_TEXT_SIZE_SP
        )
        Spacer(Modifier.height(ERROR_RETRY_TOP_SPACING_DP))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(RETRY_BUTTON_CORNER_RADIUS_DP))
                .background(retryBackground)
                .clickable(onClick = onRetry)
                .padding(horizontal = RETRY_BUTTON_HORIZONTAL_PADDING_DP, vertical = RETRY_BUTTON_VERTICAL_PADDING_DP)
        ) {
            Text(
                text = stringResource(R.string.video_player_retry),
                color = retryTextColor,
                fontSize = RETRY_TEXT_SIZE_SP
            )
        }
    }
}

private fun resolveVideoUri(data: Any?): Uri? {
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

private const val CONTROL_ALPHA = 128f / 255f
private const val FILE_URI_PREFIX = "file://"
private const val CONTENT_URI_PREFIX = "content://"
private const val HTTP_URI_PREFIX = "http://"
private const val HTTPS_URI_PREFIX = "https://"
private const val DEFAULT_VIDEO_WIDTH = 1920
private const val DEFAULT_VIDEO_HEIGHT = 1080
private val OVERLAY_BUTTON_SIZE_DP = 60.dp
private val PROGRESS_SIZE_DP = 48.dp
private val CLOSE_BUTTON_SIZE_DP = 48.dp
private val CLOSE_ICON_SIZE_DP = 24.dp
private val CLOSE_BUTTON_MARGIN_DP = 8.dp
private val CLOSE_ICON_SHADOW_RADIUS_DP = 4.dp
private val CONTROLS_BOTTOM_MARGIN_DP = 8.dp
private val PLAYER_BOTTOM_END_AVOIDANCE_DP = 64.dp
private val TOP_SCRIM_HEIGHT_DP = 120.dp
private val BOTTOM_SCRIM_HEIGHT_DP = 180.dp
private val TOP_SCRIM_START_COLOR = Color(0xC7000000)
private val BOTTOM_SCRIM_START_COLOR = Color(0xE0000000)
private val BOTTOM_SCRIM_CENTER_COLOR = Color(0x8C000000)
private val ICON_SHADOW_COLOR = Color(0x8C000000)
private val ICON_SHADOW_OFFSET_Y_DP = 1.dp
private val ERROR_TEXT_SIZE_SP = 16.sp
private val ERROR_RETRY_TOP_SPACING_DP = 16.dp
private val RETRY_BUTTON_CORNER_RADIUS_DP = 18.dp
private val RETRY_BUTTON_HORIZONTAL_PADDING_DP = 20.dp
private val RETRY_BUTTON_VERTICAL_PADDING_DP = 10.dp
private val RETRY_TEXT_SIZE_SP = 14.sp
