package io.trtc.tuikit.chat.uikit.components.videoplayer.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.DateTimeUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.ImageUtils
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPlayer(
    data: Uri,
    width: Float,
    height: Float,
    modifier: Modifier = Modifier,
    previewImage: Any? = null,
    onCloseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(data) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(data))
            prepare()
        }
    }

    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var hasStartedPlayback by remember(data) { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var videoWidth by remember(data) { mutableIntStateOf(width.toInt()) }
    var videoHeight by remember(data) { mutableIntStateOf(height.toInt()) }
    val showPreview = previewImage != null && !hasStartedPlayback

    LaunchedEffect(player) {
        player.listen { events ->
            if (events.contains(Player.EVENT_RENDERED_FIRST_FRAME)) {
                hasRenderedFirstFrame = true
            }
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
            }
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        var shouldResumePlayback = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    shouldResumePlayback = player.isPlaying
                    if (shouldResumePlayback) {
                        player.pause()
                    }
                }

                Lifecycle.Event.ON_START -> {
                    if (shouldResumePlayback) {
                        player.play()
                    }
                    shouldResumePlayback = false
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

    val density = LocalDensity.current
    val windowInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val topInset = with(density) { windowInsets.getTop(density).toDp() }
    val bottomInset = with(density) { windowInsets.getBottom(density).toDp() }

    val surfaceModifier = if (videoWidth > 0 && videoHeight > 0) {
        Modifier
            .fillMaxSize()
            .wrapContentSize()
            .layout { measurable, constraints ->
                val srcSizePx = Size(videoWidth.dp.toPx(), videoHeight.dp.toPx())
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
    } else {
        Modifier.fillMaxSize()
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(player = player, modifier = surfaceModifier)

        if (!hasRenderedFirstFrame) {
            Box(Modifier.matchParentSize().background(Color.Black))
        }

        if (showPreview) {
            AsyncImage(
                model = previewImage,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                imageLoader = ImageUtils.getImageLoader(),
                modifier = Modifier.fillMaxSize()
            )
        }

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

        CloseButton(
            onClick = onCloseClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = CLOSE_BUTTON_MARGIN_DP, top = CLOSE_BUTTON_MARGIN_DP + topInset)
        )

        MinimalControls(
            player = player,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CONTROLS_BOTTOM_MARGIN_DP + bottomInset)
        )

        if (hasError) {
            ErrorOverlay(
                onRetry = {
                    hasError = false
                    isBuffering = true
                    hasStartedPlayback = false
                    player.prepare()
                }
            )
        }
    }
}

@Composable
internal fun MinimalControls(player: Player, modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }
    var shouldResumeAfterTracking by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        player.listen { events ->
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                isPlaying = player.isPlaying
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                playbackState = player.playbackState
            }
        }
    }

    LaunchedEffect(player, isDragging) {
        while (true) {
            durationMs = player.duration.coerceAtLeast(0L)
            if (!isDragging) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
            }
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            delay(PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    val displayPositionMs = if (isDragging) dragPositionMs else positionMs
    val progress = if (durationMs > 0) {
        (displayPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedProgress = if (durationMs > 0) {
        (bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CONTROLS_HORIZONTAL_PADDING_DP, vertical = CONTROLS_VERTICAL_PADDING_DP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = {
                if (isPlaying) {
                    player.pause()
                } else {
                    if (playbackState == Player.STATE_ENDED) {
                        player.seekTo(0)
                    }
                    player.play()
                }
            }
        )
        Spacer(Modifier.width(PLAY_PAUSE_END_SPACING_DP))
        Column(Modifier.weight(1f)) {
            TimeRow(positionMs = displayPositionMs, durationMs = durationMs)
            Spacer(Modifier.height(SEEK_BAR_TOP_SPACING_DP))
            VideoSeekBar(
                progress = progress,
                bufferedProgress = bufferedProgress,
                enabled = durationMs > 0,
                onSeekStart = {
                    isDragging = true
                    dragPositionMs = positionMs
                    shouldResumeAfterTracking = player.isPlaying
                    if (shouldResumeAfterTracking) {
                        player.pause()
                    }
                },
                onSeekPreview = { fraction ->
                    dragPositionMs = (fraction * durationMs).toLong()
                },
                onSeekCommit = { fraction ->
                    val targetMs = (fraction * durationMs).toLong()
                    player.seekTo(targetMs)
                    positionMs = targetMs
                    if (shouldResumeAfterTracking) {
                        player.play()
                    }
                    isDragging = false
                }
            )
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(PLAY_PAUSE_BUTTON_SIZE_DP)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ShadowedIcon(
            painter = painterResource(
                if (isPlaying) R.drawable.video_player_pause_icon else R.drawable.video_player_play_icon
            ),
            contentDescription = stringResource(
                if (isPlaying) R.string.video_player_pause else R.string.video_player_play
            ),
            shadowRadius = PLAY_PAUSE_ICON_SHADOW_RADIUS_DP,
            iconModifier = Modifier.size(PLAY_PAUSE_ICON_SIZE_DP)
        )
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(CLOSE_BUTTON_SIZE_DP)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ShadowedIcon(
            painter = painterResource(R.drawable.video_player_close_icon),
            contentDescription = stringResource(R.string.video_player_close),
            shadowRadius = CLOSE_ICON_SHADOW_RADIUS_DP,
            iconModifier = Modifier.size(CLOSE_ICON_SIZE_DP)
        )
    }
}

/**
 * Draws a white icon with a soft drop shadow so that it stays legible on
 * bright video frames, mirroring the View-based ShadowImageView. The shadow
 * is a black silhouette of the same painter, offset downwards and blurred
 * (the blur is applied on API 31+ and degrades to a plain offset copy below).
 */
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
private fun TimeRow(positionMs: Long, durationMs: Long) {
    val density = LocalDensity.current
    val timeTextStyle = with(density) {
        TextStyle(
            shadow = Shadow(
                color = ICON_SHADOW_COLOR,
                offset = Offset(0f, TIME_TEXT_SHADOW_DY_DP.toPx()),
                blurRadius = TIME_TEXT_SHADOW_RADIUS_DP.toPx()
            )
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeText(text = DateTimeUtils.formatDurationMillis(positionMs), alpha = TIME_TEXT_CURRENT_ALPHA, style = timeTextStyle)
        TimeText(text = TIME_SEPARATOR, alpha = TIME_TEXT_SEPARATOR_ALPHA, style = timeTextStyle)
        TimeText(text = DateTimeUtils.formatDurationMillis(durationMs), alpha = TIME_TEXT_DURATION_ALPHA, style = timeTextStyle)
    }
}

@Composable
private fun TimeText(text: String, alpha: Float, style: TextStyle) {
    Text(
        text = text,
        color = Color.White,
        fontSize = TIME_TEXT_SIZE_SP,
        maxLines = 1,
        style = style,
        modifier = Modifier.alpha(alpha)
    )
}

@Composable
private fun VideoSeekBar(
    progress: Float,
    bufferedProgress: Float,
    enabled: Boolean,
    onSeekStart: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val thumbSizePx = with(LocalDensity.current) { SEEK_BAR_THUMB_SIZE_DP.roundToPx() }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SEEK_BAR_TOUCH_HEIGHT_DP)
            .pointerInput(isRtl, enabled) {
                if (!enabled) {
                    return@pointerInput
                }
                var dragProgress = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        dragProgress = positionToProgress(offset.x, size.width, isRtl)
                        onSeekStart()
                        onSeekPreview(dragProgress)
                    },
                    onDragEnd = { onSeekCommit(dragProgress) },
                    onDragCancel = { onSeekCommit(dragProgress) },
                    onDrag = { change, _ ->
                        change.consume()
                        dragProgress = positionToProgress(change.position.x, size.width, isRtl)
                        onSeekPreview(dragProgress)
                    }
                )
            }
            .pointerInput(isRtl, enabled) {
                if (!enabled) {
                    return@pointerInput
                }
                detectTapGestures { offset ->
                    val target = positionToProgress(offset.x, size.width, isRtl)
                    onSeekStart()
                    onSeekPreview(target)
                    onSeekCommit(target)
                }
            }
    ) {
        val thumbTravelPx = (constraints.maxWidth - thumbSizePx).coerceAtLeast(0)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(SEEK_BAR_TRACK_HEIGHT_DP)
                .clip(RoundedCornerShape(SEEK_BAR_TRACK_CORNER_RADIUS_DP))
                .background(SEEK_BAR_TRACK_BACKGROUND_COLOR)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(bufferedProgress)
                .height(SEEK_BAR_TRACK_HEIGHT_DP)
                .clip(RoundedCornerShape(SEEK_BAR_TRACK_CORNER_RADIUS_DP))
                .background(SEEK_BAR_BUFFERED_COLOR)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(progress)
                .height(SEEK_BAR_TRACK_HEIGHT_DP)
                .clip(RoundedCornerShape(SEEK_BAR_TRACK_CORNER_RADIUS_DP))
                .background(SEEK_BAR_PROGRESS_COLOR)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((progress * thumbTravelPx).roundToInt(), 0) }
                .size(SEEK_BAR_THUMB_SIZE_DP),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxSize().background(SEEK_BAR_THUMB_HALO_COLOR, CircleShape))
            Box(
                Modifier
                    .size(SEEK_BAR_THUMB_INNER_SIZE_DP)
                    .border(SEEK_BAR_THUMB_BORDER_WIDTH_DP, SEEK_BAR_THUMB_BORDER_COLOR, CircleShape)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun ErrorOverlay(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTheme.current.colors
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
                .background(colors.buttonColorPrimaryDefault)
                .clickable(onClick = onRetry)
                .padding(horizontal = RETRY_BUTTON_HORIZONTAL_PADDING_DP, vertical = RETRY_BUTTON_VERTICAL_PADDING_DP)
        ) {
            Text(
                text = stringResource(R.string.video_player_retry),
                color = colors.textColorButton,
                fontSize = RETRY_TEXT_SIZE_SP
            )
        }
    }
}

private fun positionToProgress(x: Float, width: Int, isRtl: Boolean): Float {
    if (width <= 0) {
        return 0f
    }
    val rawProgress = (x / width).coerceIn(0f, 1f)
    return if (isRtl) 1f - rawProgress else rawProgress
}

private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
private const val TIME_SEPARATOR = " / "

private val TOP_SCRIM_HEIGHT_DP = 120.dp
private val BOTTOM_SCRIM_HEIGHT_DP = 180.dp
private val TOP_SCRIM_START_COLOR = Color(0xC7000000)
private val BOTTOM_SCRIM_START_COLOR = Color(0xE0000000)
private val BOTTOM_SCRIM_CENTER_COLOR = Color(0x8C000000)

private val ICON_SHADOW_COLOR = Color(0x8C000000)
private val ICON_SHADOW_OFFSET_Y_DP = 1.dp

private val CLOSE_BUTTON_SIZE_DP = 48.dp
private val CLOSE_ICON_SIZE_DP = 24.dp
private val CLOSE_BUTTON_MARGIN_DP = 8.dp
private val CLOSE_ICON_SHADOW_RADIUS_DP = 4.dp

private val PLAY_PAUSE_BUTTON_SIZE_DP = 44.dp
private val PLAY_PAUSE_ICON_SIZE_DP = 36.dp
private val PLAY_PAUSE_ICON_SHADOW_RADIUS_DP = 6.dp
private val PLAY_PAUSE_END_SPACING_DP = 14.dp

private val CONTROLS_HORIZONTAL_PADDING_DP = 18.dp
private val CONTROLS_VERTICAL_PADDING_DP = 12.dp
private val CONTROLS_BOTTOM_MARGIN_DP = 8.dp

private val TIME_TEXT_SIZE_SP = 13.sp
private val TIME_TEXT_SHADOW_RADIUS_DP = 3.dp
private val TIME_TEXT_SHADOW_DY_DP = 1.dp
private const val TIME_TEXT_CURRENT_ALPHA = 1.0f
private const val TIME_TEXT_SEPARATOR_ALPHA = 0.55f
private const val TIME_TEXT_DURATION_ALPHA = 0.6f

private val SEEK_BAR_TOP_SPACING_DP = 2.dp
private val SEEK_BAR_TOUCH_HEIGHT_DP = 32.dp
private val SEEK_BAR_TRACK_HEIGHT_DP = 3.dp
private val SEEK_BAR_TRACK_CORNER_RADIUS_DP = 2.dp
private val SEEK_BAR_THUMB_SIZE_DP = 20.dp
private val SEEK_BAR_THUMB_INNER_SIZE_DP = 14.dp
private val SEEK_BAR_THUMB_BORDER_WIDTH_DP = 0.5.dp
private val SEEK_BAR_TRACK_BACKGROUND_COLOR = Color(0x59FFFFFF)
private val SEEK_BAR_BUFFERED_COLOR = Color(0x99FFFFFF)
private val SEEK_BAR_PROGRESS_COLOR = Color(0xFF0AC75C)
private val SEEK_BAR_THUMB_HALO_COLOR = Color(0x33000000)
private val SEEK_BAR_THUMB_BORDER_COLOR = Color(0x26000000)

private val ERROR_TEXT_SIZE_SP = 16.sp
private val ERROR_RETRY_TOP_SPACING_DP = 16.dp
private val RETRY_BUTTON_CORNER_RADIUS_DP = 18.dp
private val RETRY_BUTTON_HORIZONTAL_PADDING_DP = 20.dp
private val RETRY_BUTTON_VERTICAL_PADDING_DP = 10.dp
private val RETRY_TEXT_SIZE_SP = 14.sp
