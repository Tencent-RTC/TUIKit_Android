package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import android.content.Context
import android.view.View
import android.view.WindowManager
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorder
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.DateTimeUtils
import kotlin.math.abs
import kotlin.math.sin

internal data class ScreenRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun toGestureTarget(): AudioRecorderGestureTarget {
        return AudioRecorderGestureTarget(left, top, width, height)
    }
}

internal object AudioRecorderOverlayWindowSizePolicy {
    fun resolve(windowSize: IntSize, fallbackSize: IntSize): IntSize {
        return if (windowSize.width > 0 && windowSize.height > 0) {
            windowSize
        } else {
            fallbackSize
        }
    }
}

@Composable
internal fun AudioRecorderOverlay(
    visible: Boolean,
    uiState: RecordUiState,
    maxDurationMs: Int,
    bubbleAnchor: ScreenRect?,
    onCancelTargetChanged: (AudioRecorderGestureTarget) -> Unit,
    onTranscribeTargetChanged: (AudioRecorderGestureTarget) -> Unit,
) {
    val overlayAlpha = remember { Animatable(0f) }
    var keepShowing by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            keepShowing = true
            overlayAlpha.snapTo(OVERLAY_ENTER_START_ALPHA)
            overlayAlpha.animateTo(
                OVERLAY_ENTER_END_ALPHA,
                tween(OVERLAY_ENTER_DURATION_MS, easing = OverlayDecelerateEasing)
            )
        } else if (keepShowing) {
            overlayAlpha.animateTo(
                OVERLAY_EXIT_END_ALPHA,
                tween(OVERLAY_EXIT_DURATION_MS, easing = OverlayDecelerateEasing)
            )
            keepShowing = false
        }
    }
    if (!keepShowing) return
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val durationMs by AudioRecorder.currentTimeMs.collectAsState()
    val powerLevel by AudioRecorder.currentPower.collectAsState()
    val releaseAction = when (uiState) {
        RecordUiState.READY_TO_CANCEL -> AudioRecorderReleaseAction.CANCEL
        RecordUiState.READY_TO_TRANSCRIBE -> AudioRecorderReleaseAction.TRANSCRIBE
        else -> AudioRecorderReleaseAction.SEND_AUDIO
    }
    val countdown = AudioRecorderGesturePolicy.remainingSecondsBeforeAutoStop(durationMs, maxDurationMs)
    val statusText = when {
        releaseAction == AudioRecorderReleaseAction.CANCEL ->
            stringResource(R.string.message_input_release_cancel_hint)
        releaseAction == AudioRecorderReleaseAction.TRANSCRIBE ->
            stringResource(R.string.message_input_release_transcribe_hint)
        countdown != null ->
            stringResource(R.string.message_input_auto_stop_countdown, countdown)
        else -> stringResource(R.string.message_input_release_send_hint)
    }
    val bubbleColor = when (releaseAction) {
        AudioRecorderReleaseAction.CANCEL -> colors.buttonColorHangupDefault
        else -> colors.buttonColorPrimaryDefault
    }
    val operate = colors.bgColorOperate
    val configuration = LocalConfiguration.current
    val fallbackWindowSize = IntSize(
        width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
        height = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    )
    val overlayWindowSize = AudioRecorderOverlayWindowSizePolicy.resolve(
        windowSize = LocalWindowInfo.current.containerSize,
        fallbackSize = fallbackWindowSize
    )
    val overlayWidth = with(density) { overlayWindowSize.width.toDp() }
    val overlayHeight = with(density) { overlayWindowSize.height.toDp() }
    // The popup content measurement is capped at the visible display frame (system bars
    // excluded), so the content can be shorter than the app window. Align the popup to the
    // bottom of the window to keep the input area fully covered; the strip left uncovered
    // lands at the top of the screen where the scrim gradient is fully transparent.
    val bottomEdgePositionProvider = remember(overlayWindowSize) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(
                x = 0,
                y = (overlayWindowSize.height - popupContentSize.height).coerceAtLeast(0)
            )
        }
    }

    Popup(
        popupPositionProvider = bottomEdgePositionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val popupView = LocalView.current
        // PopupLayout resets the window size to the content size on every layout pass, so
        // forcing MATCH_PARENT here on each recomposition makes the window size oscillate and
        // the overlay appears to shake while recording updates stream in. Apply the
        // pass-through flags once and let PopupLayout own the window size.
        DisposableEffect(popupView) {
            applyPassThroughWindowFlags(popupView)
            onDispose { }
        }
        Box(
            modifier = Modifier
                .size(overlayWidth, overlayHeight)
                .alpha(overlayAlpha.value)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to operate.copy(alpha = 0f),
                            0.42f to operate.copy(alpha = 0.7f),
                            0.67f to operate,
                            1f to operate
                        )
                    )
                )
        ) {
            val actionSize = 80.dp
            val actionHorizontalMargin = 72.dp
            val actionBottomMargin = 107.dp
            Text(
                text = statusText,
                color = colors.textColorSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 199.dp)
                    .fillMaxWidth()
            )
            OverlayActionCircle(
                text = stringResource(R.string.message_input_cancel),
                active = releaseAction == AudioRecorderReleaseAction.CANCEL,
                activeColor = colors.buttonColorHangupDefault,
                idleColor = colors.buttonColorSecondaryDefault,
                activeTextColor = colors.textColorButton,
                idleTextColor = colors.textColorPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = actionHorizontalMargin, bottom = actionBottomMargin)
                    .size(actionSize)
                    .onGloballyPositioned { onCancelTargetChanged(it.toScreenRect().toGestureTarget()) }
            )
            OverlayActionCircle(
                text = stringResource(R.string.message_input_transcribe_to_text),
                active = releaseAction == AudioRecorderReleaseAction.TRANSCRIBE,
                activeColor = colors.buttonColorPrimaryDefault,
                idleColor = colors.buttonColorSecondaryDefault,
                activeTextColor = colors.textColorButton,
                idleTextColor = colors.textColorPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = actionHorizontalMargin, bottom = actionBottomMargin)
                    .size(actionSize)
                    .onGloballyPositioned { onTranscribeTargetChanged(it.toScreenRect().toGestureTarget()) }
            )
            val bubbleHeight = bubbleAnchor?.height?.takeIf { it > 0f }?.let { with(density) { it.toDp() } } ?: 40.dp
            // The popup is aligned to the bottom edge of the app window, so the bubble's bottom
            // offset must be measured from the window bottom (not the content height, which can
            // be capped below the window height), keeping the bubble exactly on the input field.
            val bubbleBottom = bubbleAnchor?.let { anchor ->
                (overlayWindowSize.height - (anchor.top + anchor.height)).coerceAtLeast(0f)
            } ?: with(density) { 43.dp.toPx() }
            RecordingWaveformBubble(
                color = bubbleColor,
                contentColor = colors.textColorButton,
                powerLevel = powerLevel,
                durationMs = if (durationMs > 0) durationMs else null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, -bubbleBottom.toInt()) }
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(bubbleHeight)
            )
        }
    }
}

@Composable
private fun OverlayActionCircle(
    text: String,
    active: Boolean,
    activeColor: Color,
    idleColor: Color,
    activeTextColor: Color,
    idleTextColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) activeColor else idleColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) activeTextColor else idleTextColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecordingWaveformBubble(
    color: Color,
    contentColor: Color,
    powerLevel: Int,
    durationMs: Int?,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "waveform")
    // The tick is the elapsed time in seconds, matching the View implementation which feeds
    // real elapsed seconds into the same sin(t * freq + phase) waveform. The 60s cycle covers
    // the maximum recording length, so the repeat wrap is never visible in practice.
    val tick by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveformTick"
    )
    val durationText = durationMs?.let { DateTimeUtils.formatDurationMillis(it.toLong()) } ?: "--:--"
    // Seeded from a single generator drawn in the same order as the View implementation so
    // both UIKits render an identical bar sequence.
    val waveform = remember {
        val rng = java.util.Random(20260421L)
        val phases = FloatArray(24) { rng.nextFloat() * (Math.PI * 2).toFloat() }
        val freqs = FloatArray(24) { 2.4f + rng.nextFloat() * 3.2f }
        phases to freqs
    }
    val phases = waveform.first
    val freqs = waveform.second
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = 24
            val barWidth = 2.5.dp.toPx()
            val gap = 3.dp.toPx()
            val totalWidth = barCount * barWidth + (barCount - 1) * gap
            val startX = (size.width - totalWidth) / 2f
            val centerY = size.height / 2f
            val amplitude = 13.dp.toPx() * (0.45f + (powerLevel.coerceIn(0, 100) / 100f) * 0.55f)
            val minH = 3.dp.toPx()
            val t = tick.toDouble()
            for (i in 0 until barCount) {
                val wave = abs(sin(t * freqs[i] + phases[i])).toFloat()
                val barH = minH + amplitude * wave
                val x = startX + i * (barWidth + gap)
                drawRoundRect(
                    color = contentColor,
                    topLeft = Offset(x, centerY - barH / 2f),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
        Text(
            text = durationText,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

internal fun LayoutCoordinates.toScreenRect(): ScreenRect {
    val pos = positionOnScreen()
    return ScreenRect(
        left = pos.x,
        top = pos.y,
        width = size.width.toFloat(),
        height = size.height.toFloat()
    )
}

private val OverlayDecelerateEasing = Easing { fraction ->
    val inverted = 1f - fraction
    1f - inverted * inverted
}

private const val OVERLAY_ENTER_START_ALPHA = 0.92f
private const val OVERLAY_ENTER_END_ALPHA = 1f
private const val OVERLAY_EXIT_END_ALPHA = 0f
private const val OVERLAY_ENTER_DURATION_MS = 120
private const val OVERLAY_EXIT_DURATION_MS = 100

private fun applyPassThroughWindowFlags(view: View) {
    var current: View? = view
    while (current != null) {
        val lp = current.layoutParams
        if (lp is WindowManager.LayoutParams) {
            val passThroughFlags = (
                lp.flags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                ) and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
            if (lp.flags == passThroughFlags) {
                return
            }
            lp.flags = passThroughFlags
            try {
                val wm = current.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.updateViewLayout(current, lp)
            } catch (_: IllegalArgumentException) {
            }
            return
        }
        current = current.parent as? View
    }
}
