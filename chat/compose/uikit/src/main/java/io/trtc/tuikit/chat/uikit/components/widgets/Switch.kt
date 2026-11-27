package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.PI
import kotlin.math.cos


enum class SwitchSize(
    val width: Dp,
    val height: Dp,
    val thumbSize: Dp,
    val padding: Dp,
    val textSize: TextUnit
) {
    S(width = 26.dp, height = 16.dp, thumbSize = 12.dp, padding = 2.dp, textSize = 10.sp),
    M(width = 32.dp, height = 20.dp, thumbSize = 15.dp, padding = 2.5.dp, textSize = 12.sp),
    L(width = 48.dp, height = 28.dp, thumbSize = 24.dp, padding = 2.dp, textSize = 14.sp)
}

sealed class SwitchType {
    data object Basic : SwitchType()
    data object WithText : SwitchType()
    data object WithIcon : SwitchType()
}

private val AccelerateDecelerateEasing = Easing { fraction ->
    (cos((fraction + 1) * PI) / 2.0).toFloat() + 0.5f
}

@Composable
fun Switch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: SwitchSize = SwitchSize.L,
    type: SwitchType = SwitchType.Basic,
    animated: Boolean = true,
) {
    val colors = LocalTheme.current.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var visualChecked by remember { mutableStateOf(checked) }
    var animateVisualChange by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        if (checked != visualChecked) {
            animateVisualChange = false
            visualChecked = checked
        }
    }

    val thumbOffset by animateFloatAsState(
        targetValue = if (visualChecked) 1f else 0f,
        animationSpec = if (animated && animateVisualChange) {
            tween(durationMillis = 300, easing = AccelerateDecelerateEasing)
        } else {
            snap()
        },
        label = "thumbOffset"
    )

    var trackColor = if (visualChecked) colors.switchColorOn else colors.switchColorOff
    var thumbColor = colors.switchColorButton
    if (!enabled) {
        trackColor = trackColor.copy(alpha = 0.6f)
        thumbColor = thumbColor.copy(alpha = 0.6f)
    }

    val width = when (type) {
        SwitchType.Basic -> size.width
        SwitchType.WithIcon -> size.height * 2
        SwitchType.WithText -> size.height * 2
    }

    val maxOffset = width - size.thumbSize - size.padding * 2
    val visualThumbOffset = if (isRtl) 1f - thumbOffset else thumbOffset
    val showContentOnLeft = if (isRtl) !visualChecked else visualChecked
    val elevation = when (size) {
        SwitchSize.S -> 1.6.dp
        SwitchSize.M -> 2.dp
        SwitchSize.L -> 2.4.dp
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .width(width)
                .height(size.height)
                .clickable(enabled = enabled && !loading) {
                    val target = !visualChecked
                    animateVisualChange = true
                    visualChecked = target
                    onCheckedChange?.invoke(target)
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(color = trackColor)
            )
            if (type == SwitchType.WithText || type == SwitchType.WithIcon) {
                val contentWidth = width - size.thumbSize - size.padding * 2
                Box(
                    modifier = Modifier
                        .align(if (showContentOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(
                            start = if (showContentOnLeft) size.padding else 0.dp,
                            end = if (showContentOnLeft) 0.dp else size.padding
                        )
                        .width(contentWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    when (type) {
                        SwitchType.WithIcon -> {
                            if (visualChecked) {
                                SwitchCheckIcon(
                                    color = colors.textColorButton,
                                    thumbSize = size.thumbSize
                                )
                            } else {
                                SwitchCloseIcon(
                                    color = colors.textColorButton,
                                    thumbSize = size.thumbSize
                                )
                            }
                        }

                        SwitchType.WithText -> {
                            BasicText(
                                text = stringResource(
                                    if (visualChecked) {
                                        R.string.base_component_switch_open
                                    } else {
                                        R.string.base_component_switch_close
                                    }
                                ),
                                style = TextStyle(
                                    color = colors.textColorButton,
                                    fontSize = size.textSize * 0.8f,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 1
                            )
                        }

                        else -> {}
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .absoluteOffset(x = size.padding + maxOffset * visualThumbOffset)
                    .size(size.thumbSize)
                    .drawBehind {
                        val canvasSize = this@drawBehind.size
                        val elevationPx = elevation.toPx()
                        val shadowCenter = Offset(
                            canvasSize.width / 2f,
                            canvasSize.height / 2f + elevationPx / 2f
                        )
                        val thumbRadius = canvasSize.minDimension / 2f
                        val blurRadius = thumbRadius + elevationPx
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to Color(0x30000000),
                                    (thumbRadius / blurRadius).coerceIn(0f, 1f) to Color(0x30000000),
                                    1f to Color.Transparent
                                ),
                                center = shadowCenter,
                                radius = blurRadius
                            ),
                            radius = blurRadius,
                            center = shadowCenter
                        )
                    }
                    .background(
                        color = thumbColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    SwitchLoadingIndicator(
                        color = colors.switchColorOn,
                        indicatorSize = size.thumbSize * 0.6f
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchLoadingIndicator(
    color: Color,
    indicatorSize: Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "switchLoading")
    val loadingAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "switchLoadingAngle"
    )
    Canvas(modifier = Modifier.size(indicatorSize)) {
        val diameter = size.minDimension
        drawArc(
            color = color,
            startAngle = loadingAngle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            ),
            size = Size(diameter, diameter),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun SwitchCheckIcon(
    color: Color,
    thumbSize: Dp
) {
    val iconSize = thumbSize * 0.45f
    Canvas(modifier = Modifier.size(iconSize)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val markSize = size.minDimension
        val path = Path().apply {
            moveTo(cx - markSize * 0.4f, cy)
            lineTo(cx - markSize * 0.1f, cy + markSize * 0.3f)
            lineTo(cx + markSize * 0.4f, cy - markSize * 0.3f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = markSize * 0.2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun SwitchCloseIcon(
    color: Color,
    thumbSize: Dp
) {
    val iconSize = thumbSize * 0.35f
    Canvas(modifier = Modifier.size(iconSize * 2f)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val markSize = size.minDimension / 2f
        val path = Path().apply {
            moveTo(cx - markSize, cy - markSize)
            lineTo(cx + markSize, cy + markSize)
            moveTo(cx + markSize, cy - markSize)
            lineTo(cx - markSize, cy + markSize)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = markSize * 0.2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
